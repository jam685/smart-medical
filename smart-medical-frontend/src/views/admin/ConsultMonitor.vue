<template>
  <div class="consult-monitor-page">
    <!-- 页面头部 -->
    <div class="page-head">
      <div class="head-left">
        <h2 class="page-title">问诊监控</h2>
        <p class="page-desc">管理员全局视角：问诊统计 / 状态分布 / 慢问诊预警 / 会话详情</p>
      </div>
      <div class="head-right">
        <el-button @click="refreshAll"><el-icon><Refresh /></el-icon>&nbsp;刷新</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card stat-total">
        <div class="stat-num">{{ stats.total ?? '—' }}</div>
        <div class="stat-label">问诊总数</div>
      </div>
      <div class="stat-card stat-pending">
        <div class="stat-num">{{ stats.pending ?? '—' }}</div>
        <div class="stat-label">进行中</div>
      </div>
      <div class="stat-card stat-replied">
        <div class="stat-num">{{ stats.replied ?? '—' }}</div>
        <div class="stat-label">已回复</div>
      </div>
      <div class="stat-card stat-closed">
        <div class="stat-num">{{ stats.closed ?? '—' }}</div>
        <div class="stat-label">已关闭</div>
      </div>
      <div class="stat-card stat-today">
        <div class="stat-num">{{ stats.todayCount ?? '—' }}</div>
        <div class="stat-label">今日问诊</div>
      </div>
      <div class="stat-card stat-slow">
        <div class="stat-num">{{ stats.slowCount ?? '—' }}</div>
        <div class="stat-label">慢问诊（>15min）</div>
      </div>
    </div>

    <el-card shadow="never" class="main-card">
      <!-- 筛选 -->
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="全部状态" clearable class="filter-item" @change="onSearch">
          <el-option label="进行中" value="PENDING" />
          <el-option label="已回复" value="REPLIED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-select v-model="query.department" placeholder="全部科室" clearable class="filter-item" @change="onSearch">
          <el-option v-for="d in departmentOptions" :key="d" :label="d" :value="d" />
        </el-select>
        <el-button type="primary" @click="onSearch"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
        <el-button @click="onReset"><el-icon><RefreshLeft /></el-icon>&nbsp;重置</el-button>
      </div>

      <!-- 列表 -->
      <el-table v-loading="loading" :data="list" stripe class="data-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="患者" width="110">
          <template #default="{ row }">{{ row.patientName }}</template>
        </el-table-column>
        <el-table-column label="接诊医生" width="110">
          <template #default="{ row }">{{ row.doctorName }}</template>
        </el-table-column>
        <el-table-column prop="department" label="科室" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="消息数" width="90" align="center">
          <template #default="{ row }">{{ row.messageCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="时长" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ 'slow-text': row.durationMinutes > 15 }">{{ row.durationMinutes ?? 0 }} 分钟</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 管理端「问诊监控」列表页
 * 职责：管理员全局视角 — 统计卡片 + 筛选 + 分页列表
 * 数据来源：GET /admin/online-consults（列表）+ GET /admin/online-consults/stats（统计）
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import { fetchAdminConsults, fetchAdminConsultStats } from '@/api/admin'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({})

const query = reactive({ page: 1, size: 10, status: '', department: '' })

/** 科室选项：从数据里收集 + 常用预设（避免去后端再拉一个接口） */
const departmentOptions = ref([
  '内科', '外科', '儿科', '急诊科', '中医科', '骨科', '精神科', '神经外科',
  '耳鼻喉科', '呼吸科', '内分泌科'
])

function statusName(s) {
  return { PENDING: '进行中', REPLIED: '已回复', CLOSED: '已关闭' }[s] || s || '—'
}
function statusTag(s) {
  return { PENDING: 'warning', REPLIED: 'success', CLOSED: 'info' }[s] || 'info'
}

function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function loadList() {
  loading.value = true
  try {
    const data = await fetchAdminConsults({
      page: query.page,
      size: query.size,
      status: query.status || undefined,
      department: query.department || undefined
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await fetchAdminConsultStats()
  } catch {}
}

function onSearch() { query.page = 1; loadList() }
function onReset() { query.status = ''; query.department = ''; query.page = 1; loadList() }
function onPageChange() { loadList() }

function refreshAll() {
  loadList()
  loadStats()
}

function goDetail(row) {
  router.push(`/admin/online-consults/${row.id}`)
}

onMounted(() => {
  loadList()
  loadStats()
})
</script>

<style scoped>
.consult-monitor-page { padding: 24px; }
.page-head { margin-bottom: 18px; display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-desc { font-size: 14px; color: #64748b; margin: 0; }

.stats-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.stat-card {
  padding: 16px 20px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #eef0f4;
  text-align: center;
}
.stat-num { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 12px; color: #64748b; margin-top: 6px; }
.stat-total  .stat-num { color: #1E7AE6; }
.stat-pending .stat-num { color: #F59E0B; }
.stat-replied .stat-num { color: #10B981; }
.stat-closed  .stat-num { color: #64748b; }
.stat-today   .stat-num { color: #8B5CF6; }
.stat-slow    .stat-num { color: #EF4444; }

.main-card { border-radius: 12px; border: 1px solid #eef0f4; }

.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-item { width: 150px; }
.data-table { width: 100%; }
.slow-text { color: #EF4444; font-weight: 600; }

.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>

<script setup>
/**
 * 医生端「问诊处理」列表页组件
 * 职责：加载当前医生接诊的问诊，按状态分组（待处理/已回复），点击进入处理聊天页。
 * 数据来源：GET /online-consults（后端按医生本人过滤，含已关闭问诊）
 * 状态口径（后端枚举）：
 *   PENDING / AI_ANALYZED / DOCTOR_REVIEWING → 待处理
 *   REPLIED → 已回复；CLOSED → 已关闭
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchConsults } from '@/api/onlineconsult'

/** Vue Router 实例：用于跳转问诊处理页 */
const router = useRouter()

/**
 * 状态筛选 Tab 配置
 * @property {string} key   Tab 键：all 全部 / pending 待处理 / replied 已回复
 * @property {string} label Tab 显示文案
 */
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待处理' },
  { key: 'replied', label: '已回复' },
]

/** 当前选中的 Tab 键 */
const activeTab = ref('all')

/** 问诊视图对象数组（由后端实体经 normalize 转换） */
const consults = ref([])
/** 列表加载状态 */
const loading = ref(false)

/** 分页：每页条数（每满 6 条换页） */
const PAGE_SIZE = 6
/** 当前页码 */
const currentPage = ref(1)
/** 总记录数（后端返回） */
const total = ref(0)

/**
 * 把后端问诊对象归一化为页面渲染对象（医生视角含姓名 / 叫号时间）
 * @param {object} raw 后端 OnlineConsultRecord（医生视角为增强 VO）
 * @returns {object} 视图对象
 */
function normalize(raw) {
  return {
    id: raw.id,
    department: raw.department || '未指定科室',
    patientName: raw.patientName || '未知患者',
    chiefComplaint: raw.chiefComplaint || '未填写主诉',
    status: mapStatus(raw.status),
    statusText: statusText(raw.status),
    createdAt: formatTime(raw.createdAt),
    callTime: formatTime(raw.callTime),
  }
}

/**
 * 后端状态码映射为页面状态键
 * @param {string} s 后端状态码
 * @returns {string} pending/replied/closed（未知状态兜底为 pending）
 */
function mapStatus(s) {
  if (s === 'REPLIED') return 'replied'
  if (s === 'CLOSED') return 'closed'
  return 'pending' // PENDING / AI_ANALYZED / DOCTOR_REVIEWING 均视为待处理
}

/**
 * 后端状态码映射为中文文案
 * @param {string} s 后端状态码
 * @returns {string} 中文文案
 */
function statusText(s) {
  if (s === 'REPLIED') return '已回复'
  if (s === 'CLOSED') return '已关闭'
  if (s === 'AI_ANALYZED') return '分析中'
  if (s === 'DOCTOR_REVIEWING') return '处理中'
  return '待接诊'
}

/**
 * 时间戳格式化：ISO 字符串 → 'YYYY-MM-DD HH:mm'
 */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 从后端加载当前医生的问诊列表（每页 6 条，后端已按医生最新回复时间倒序） */
async function loadConsults(page = 1) {
  loading.value = true
  try {
    const res = await fetchConsults({ page, size: PAGE_SIZE })
    currentPage.value = page
    total.value = res.total || 0
    consults.value = (res.list || []).map(normalize)
  } catch (e) {
    // 请求失败提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 翻页 */
function onPageChange(p) {
  loadConsults(p)
}

/** 状态展示配置 */
const statusMap = {
  pending: { label: '待处理', cls: 'st-pending' },
  replied: { label: '已回复', cls: 'st-replied' },
  closed: { label: '已关闭', cls: 'st-closed' },
}

/** 当前 Tab 下应展示的问诊 */
const filtered = computed(() =>
  activeTab.value === 'all'
    ? consults.value
    : consults.value.filter(c => c.status === activeTab.value)
)

/** 统计指定 Tab 数量 */
function countOf(key) {
  return key === 'all' ? consults.value.length : consults.value.filter(c => c.status === key).length
}

/** 跳转问诊处理页 */
function goHandle(c) { router.push(`/doctor/online-consults/${c.id}`) }

onMounted(loadConsults)
</script>

<template>
  <div class="consult-list-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="page-title brand-font">问诊处理</h2>
        <p class="page-sub">查看并回复患者的线上问诊</p>
      </div>
    </div>

    <!-- 状态 Tab -->
    <div class="tab-row">
      <button
        v-for="t in tabs" :key="t.key"
        :class="['tab-pill', { active: activeTab === t.key }]"
        @click="activeTab = t.key"
      >
        {{ t.label }}<span class="tab-count">{{ countOf(t.key) }}</span>
      </button>
    </div>

    <!-- 问诊卡片列表 -->
    <div class="card-list">
      <div v-for="c in filtered" :key="c.id" class="consult-card" @click="goHandle(c)">
        <div class="consult-main">
          <div class="main-top">
            <span class="patient-name">{{ c.patientName }}</span>
            <span class="status-tag" :class="statusMap[c.status].cls">{{ c.statusText }}</span>
          </div>
          <div class="main-meta">
            <span class="dept-tag">{{ c.department }}</span>
            <span class="dot-sep">·</span>
            <span>叫号时间 {{ c.callTime || '—' }}</span>
          </div>
          <div class="chief-line">{{ c.chiefComplaint }}</div>
        </div>
        <div class="consult-actions" @click.stop>
          <el-button text class="detail-link" @click="goHandle(c)">去处理 ›</el-button>
        </div>
      </div>
    </div>

    <!-- 分页（每满 6 条换页） -->
    <div v-if="total > PAGE_SIZE" class="pager">
      <el-pagination
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="currentPage"
        @current-change="onPageChange"
      />
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && filtered.length === 0" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#F0F6F2"/>
        <circle cx="70" cy="60" r="9" fill="#DCEBE2"/>
        <circle cx="100" cy="60" r="9" fill="#DCEBE2"/>
        <path d="M55 78c6 6 24 6 30 0" stroke="#C9E0D2" stroke-width="2.4" stroke-linecap="round"/>
        <path d="M118 46l4 4 8-9" stroke="#10B981" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <p class="empty-title">暂无待处理问诊</p>
      <p class="empty-sub">当前分类下还没有问诊，患者发起问诊后会出现在这里</p>
    </div>
  </div>
</template>

<style scoped>
.consult-list-page { padding: 4px 2px 20px; }

/* 页头 */
.page-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0 0 6px; }
.page-sub { font-size: 13px; color: #8492A6; margin: 0; }

/* Tab */
.tab-row { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.tab-pill {
  border: 1.5px solid #E2EAF4; background: #fff; color: #5A6B82; border-radius: 100px;
  padding: 7px 16px; font-size: 13.5px; cursor: pointer; display: inline-flex; align-items: center; gap: 7px;
  transition: all 0.18s;
}
.tab-pill:hover { border-color: #A7F3D0; color: #10B981; }
.tab-pill.active { background: linear-gradient(135deg, #10B981, #34D399); border-color: transparent; color: #fff; box-shadow: 0 5px 13px rgba(16,185,129,0.28); }
.tab-count {
  background: #EDF2F8; color: #8492A6; border-radius: 100px; font-size: 11.5px;
  min-width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; padding: 0 6px;
}
.tab-pill.active .tab-count { background: rgba(255,255,255,0.25); color: #fff; }

/* 卡片 */
.card-list { display: flex; flex-direction: column; gap: 12px; }
.consult-card {
  display: flex; align-items: center; gap: 18px; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 14px; padding: 18px 20px; cursor: pointer;
  transition: all 0.22s;
}
.consult-card:hover { border-color: #A7F3D0; box-shadow: 0 10px 24px rgba(16,185,129,0.1); transform: translateY(-2px); }
.consult-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.main-top { display: flex; align-items: center; gap: 12px; }
.patient-name { font-size: 16px; font-weight: 700; color: #1A2B45; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-tag { margin-left: auto; font-size: 12px; font-weight: 600; padding: 3px 12px; border-radius: 100px; }
.st-pending { color: #1E7AE6; background: #E3F2FF; }
.st-replied { color: #0E9F6E; background: #E2F7EF; }
.st-closed { color: #8492A6; background: #EFF2F6; }
.main-meta { font-size: 12.5px; color: #A7B1BF; display: flex; align-items: center; gap: 7px; }
.dept-tag { font-size: 12px; color: #10B981; background: #E8F9F1; padding: 2px 9px; border-radius: 100px; }
.dot-sep { color: #C3CCD8; }
.chief-line { font-size: 13px; color: #5A6B82; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.consult-actions { flex-shrink: 0; }
.detail-link { color: #10B981; font-weight: 600; padding: 0; }

/* 分页 */
.pager { display: flex; justify-content: center; margin-top: 20px; }
.pager :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: linear-gradient(135deg, #10B981, #34D399);
  border-color: transparent;
}

/* 空状态 */
.empty-state { text-align: center; padding: 50px 0 30px; }
.empty-state svg { width: 170px; }
.empty-title { font-size: 16px; font-weight: 700; color: #2C3E50; margin: 14px 0 4px; }
.empty-sub { font-size: 13px; color: #A7B1BF; margin: 0 0 18px; }
</style>

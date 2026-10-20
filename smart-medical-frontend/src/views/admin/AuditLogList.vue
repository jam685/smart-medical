<template>
  <div class="audit-log-page">
    <!-- 页面头部 -->
    <div class="page-head">
      <div class="head-left">
        <h2 class="page-title">审计日志</h2>
        <p class="page-desc">记录管理员关键操作：角色 / 操作 / 详情 / 敏感等级 / 审核状态</p>
      </div>
    </div>

    <el-card shadow="never" class="main-card">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-select v-model="query.role" placeholder="全部角色" clearable class="filter-item" @change="onSearch">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="医生" value="DOCTOR" />
          <el-option label="患者" value="PATIENT" />
        </el-select>
        <el-select v-model="query.action" placeholder="全部操作" clearable class="filter-item" @change="onSearch">
          <el-option label="新增用户" value="CREATE_USER" />
          <el-option label="修改用户" value="UPDATE_USER" />
        </el-select>
        <el-button type="primary" @click="onSearch"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
        <el-button @click="onReset"><el-icon><RefreshLeft /></el-icon>&nbsp;重置</el-button>
      </div>

      <!-- 日志数据表 -->
      <el-table v-loading="loading" :data="list" stripe class="data-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)" size="small">{{ roleName(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-tag type="primary" size="small">{{ actionName(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.detail || '—' }}</template>
        </el-table-column>
        <el-table-column label="敏感等级" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.sensitiveLevel" :type="sensitiveTag(row.sensitiveLevel)" size="small">{{ sensitiveName(row.sensitiveLevel) }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="reviewTag(row.reviewStatus)" size="small">{{ reviewName(row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.reviewStatus === 'PENDING'">
              <el-button link type="success" @click="doReview(row, 'APPROVED')">通过</el-button>
              <el-button link type="danger" @click="doReview(row, 'REJECTED')">驳回</el-button>
            </template>
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
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
 * 管理端「审计日志」列表页
 * 职责：分页展示审计日志，支持按角色 / 操作筛选，点击「详情」跳转日志详情页。
 * 数据来源：GET /admin/audit-logs（后端实时查询 sm_audit_log 表）。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { fetchAuditLogs, reviewAuditLog } from '@/api/admin'

const router = useRouter()

/** 加载状态与数据 */
const loading = ref(false)
const list = ref([])
const total = ref(0)

/** 查询参数：page 页码 / size 每页条数 / role 角色筛选 / action 操作筛选 */
const query = reactive({ page: 1, size: 10, role: '', action: '' })

/**
 * 角色中文名映射
 * @param {string} r 角色枚举（PATIENT/DOCTOR/ADMIN）
 * @returns {string} 中文名称，未知值原样返回
 */
function roleName(r) {
  return { PATIENT: '患者', DOCTOR: '医生', ADMIN: '管理员' }[r] || r || '—'
}

/**
 * 角色标签颜色映射
 * @param {string} r 角色枚举
 * @returns {string} el-tag type
 */
function roleTag(r) {
  return { PATIENT: 'primary', DOCTOR: 'success', ADMIN: 'warning' }[r] || 'info'
}

/**
 * 操作类型中文名映射
 * @param {string} a 操作枚举（CREATE_USER/UPDATE_USER）
 * @returns {string} 中文名称，未知值原样返回
 */
function actionName(a) {
  return { CREATE_USER: '新增用户', UPDATE_USER: '修改用户' }[a] || a || '—'
}

/**
 * 敏感等级中文名映射
 * @param {string} s 敏感等级值（LOW/MEDIUM/HIGH）
 * @returns {string} 中文名称，未知值原样返回
 */
function sensitiveName(s) {
  if (!s) return '—'
  return { LOW: '低敏感', MEDIUM: '中敏感', HIGH: '高敏感' }[s] || s
}

/**
 * 敏感等级标签颜色映射
 * @param {string} s 敏感等级值（LOW/MEDIUM/HIGH）
 * @returns {string} el-tag type
 */
function sensitiveTag(s) {
  return { LOW: 'info', MEDIUM: 'warning', HIGH: 'danger' }[s] || 'info'
}

/**
 * 审核状态中文名映射
 * @param {string} s 审核状态值，空值显示占位符
 * @returns {string} 中文名称
 */
function reviewName(s) {
  if (!s) return '—'
  return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s
}

/**
 * 审核状态标签颜色映射
 * @param {string} s 审核状态值
 * @returns {string} el-tag type
 */
function reviewTag(s) {
  if (!s) return 'info'
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

/**
 * 审核一条日志（通过/驳回），带二次确认后调用后端
 * @param {object} row 当前行日志对象
 * @param {string} decision APPROVED 通过 / REJECTED 驳回（驳回将禁用操作人账号）
 */
async function doReview(row, decision) {
  const isApprove = decision === 'APPROVED'
  const tip = isApprove
    ? `确认通过日志 #${row.id} 的审核？`
    : `确认驳回日志 #${row.id}？驳回后将自动禁用该操作人的管理员账号，需在用户管理中重新启用。`
  try {
    await ElMessageBox.confirm(tip, isApprove ? '审核通过' : '审核驳回', {
      type: isApprove ? 'success' : 'warning',
      confirmButtonText: isApprove ? '确认通过' : '确认驳回',
      cancelButtonText: '取消'
    })
  } catch {
    // 用户取消，不执行审核
    return
  }
  await reviewAuditLog(row.id, decision)
  ElMessage.success(isApprove ? '已通过该日志审核' : '已驳回该日志，操作人账号已禁用')
  loadList()
}

/**
 * 拉取审计日志列表（带角色/操作筛选，分页）
 */
async function loadList() {
  loading.value = true
  try {
    const data = await fetchAuditLogs({
      page: query.page,
      size: query.size,
      role: query.role || undefined,
      action: query.action || undefined
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

/**
 * 点击查询：回到第 1 页并重新加载
 */
function onSearch() {
  query.page = 1
  loadList()
}

/**
 * 点击重置：清空筛选条件并回到第 1 页重新加载
 */
function onReset() {
  query.role = ''
  query.action = ''
  query.page = 1
  loadList()
}

/**
 * 翻页回调：切换页码后重新加载
 */
function onPageChange() {
  loadList()
}

/**
 * 跳转日志详情页
 * @param {object} row 当前行日志对象
 */
function goDetail(row) {
  router.push(`/admin/audit-logs/${row.id}`)
}

/**
 * 时间格式化：yyyy-MM-dd HH:mm
 * @param {string|number} t 时间值
 * @returns {string} 格式化字符串
 */
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(loadList)
</script>

<style scoped>
.audit-log-page { padding: 24px; }

.page-head { margin-bottom: 18px; }
.page-title { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-desc { font-size: 14px; color: #64748b; margin: 0; }

.main-card { border-radius: 12px; border: 1px solid #eef0f4; }

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-item { width: 150px; }

.data-table { width: 100%; }

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

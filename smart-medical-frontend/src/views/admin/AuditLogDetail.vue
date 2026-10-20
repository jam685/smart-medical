<template>
  <div class="audit-detail-page">
    <!-- 返回栏 -->
    <div class="back-bar">
      <el-button text @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回审计日志
      </el-button>
    </div>

    <div v-loading="loading">
      <template v-if="log">
        <!-- 页头信息 -->
        <div class="card head-card">
          <div class="head-left">
            <h2>日志 #{{ log.id }}</h2>
            <div class="tags">
              <el-tag :type="roleTag(log.role)" size="small">{{ roleName(log.role) }}</el-tag>
              <el-tag type="primary" size="small">{{ actionName(log.action) }}</el-tag>
              <span class="meta-text">时间：{{ formatTime(log.createdAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 核心详情 -->
        <div class="card section">
          <h3 class="section-title">日志详情</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="操作角色">{{ roleName(log.role) }}</el-descriptions-item>
            <el-descriptions-item label="操作类型">{{ actionName(log.action) }}</el-descriptions-item>
            <el-descriptions-item label="操作人 ID">{{ log.userId || '—' }}</el-descriptions-item>
            <el-descriptions-item label="目标 ID">{{ log.targetId || '—' }}</el-descriptions-item>
            <el-descriptions-item label="敏感等级">{{ log.sensitiveLevel || '无' }}</el-descriptions-item>
            <el-descriptions-item label="审核状态">
              <el-tag :type="reviewTag(log.reviewStatus)" size="small">{{ reviewName(log.reviewStatus) }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 操作详情描述 -->
        <div class="card section">
          <h3 class="section-title">操作说明</h3>
          <div class="detail-text">{{ log.detail || '无详细描述' }}</div>
        </div>

        <!-- 原始信息 -->
        <div class="card section">
          <h3 class="section-title">原始信息</h3>
          <div class="raw-text">创建时间：{{ formatTime(log.createdAt) }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
/**
 * 管理端「日志详情」页
 * 职责：展示单条审计日志的全字段信息。
 * 数据来源：后端无独立详情接口，复用 GET /admin/audit-logs（size=100）
 *          按 id 匹配目标日志（数据仍来自数据库，实时拉取）。
 */
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { fetchAuditLogs } from '@/api/admin'

const route = useRoute()
const router = useRouter()

/** 日志 ID（路由参数） */
const logId = route.params.id

/** 日志实体 */
const log = ref(null)
const loading = ref(false)

/** 角色/动作展示映射 */
function roleName(r) {
  return { PATIENT: '患者', DOCTOR: '医生', ADMIN: '管理员' }[r] || r || '—'
}

function roleTag(r) {
  return { PATIENT: 'primary', DOCTOR: 'success', ADMIN: 'warning' }[r] || 'info'
}

function actionName(a) {
  return { CREATE_USER: '新增用户', UPDATE_USER: '修改用户' }[a] || a || '—'
}

/**
 * 审核状态中文名映射（与列表页一致）
 * @param {string} s 审核状态值，空值显示占位符
 * @returns {string} 中文名称
 */
function reviewName(s) {
  if (!s) return '—'
  return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s
}

/**
 * 审核状态标签颜色映射（与列表页一致）
 * @param {string} s 审核状态值
 * @returns {string} el-tag type
 */
function reviewTag(s) {
  if (!s) return 'info'
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

/**
 * 从审计日志列表接口拉取并匹配目标日志
 */
async function loadLog() {
  loading.value = true
  try {
    // 列表接口按 id 倒序，取最大一页足够覆盖详情查询
    const data = await fetchAuditLogs({ page: 1, size: 100 })
    const list = data?.list || []
    log.value = list.find((l) => String(l.id) === String(logId)) || null
  } catch (e) {
    log.value = null
  } finally {
    loading.value = false
  }
}

/** 时间格式化 */
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(loadLog)
</script>

<style scoped>
.audit-detail-page { padding: 24px; }

.back-bar { margin-bottom: 14px; }
.back-bar .el-button { color: #64748b; }

.card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef0f4;
  box-shadow: 0 1px 3px rgba(16, 42, 67, 0.06);
  padding: 20px;
  margin-bottom: 20px;
}

.head-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.head-left h2 { font-size: 20px; font-weight: 700; color: #1a1a2e; margin: 0 0 10px; }

.tags {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.meta-text { font-size: 13px; color: #64748b; }

.section-title { font-size: 15px; font-weight: 600; color: #1a1a2e; margin: 0 0 14px; }

.detail-text {
  font-size: 14px;
  line-height: 1.8;
  color: #334155;
  background: #f8fafc;
  border: 1px solid #eef0f4;
  border-radius: 10px;
  padding: 14px 16px;
}

.raw-text {
  font-size: 13px;
  color: #64748b;
  line-height: 2;
}
</style>

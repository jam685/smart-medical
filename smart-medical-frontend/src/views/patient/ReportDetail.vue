<script setup>
/**
 * 患者端「报告详情」页组件
 * 职责：展示单份报告内容（图片 + 基础信息），数据实时取自后端。
 * 数据来源（真实接口）：
 *   GET /reports/{id}  报告详情（含归属校验）
 *     返回：{id, patientId, reportType, imageUrl, imageMeta, uploadedBy, status, createdAt}
 */
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { fetchReportDetail } from '@/api/report'

const route = useRoute()
const router = useRouter()

/** 报告 ID（路由参数） */
const reportId = route.params.id

/** 报告详情 */
const report = ref(null)
/** 加载状态 */
const loading = ref(true)

/** 报告状态 → 中文标签与样式类 */
function statusMeta(s) {
  if (s === 'UPLOADED') return { label: '已上传', cls: 'st-uploaded' }
  if (s === 'ANALYZED') return { label: '已解读', cls: 'st-analyzed' }
  if (s === 'SIGNED') return { label: '已签名', cls: 'st-signed' }
  return { label: s || '未知', cls: 'st-pending' }
}

/** 报告类型兜底文案 */
function typeText(type) {
  return type || '检查报告'
}

/** 报告图片地址解析（兼容绝对/相对路径） */
function resolveImg(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return url
}

/**
 * 时间戳格式化：'YYYY-MM-DD HH:mm'
 */
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 加载报告详情 */
async function loadDetail() {
  loading.value = true
  try {
    report.value = await fetchReportDetail(reportId)
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 返回列表 */
function goBack() {
  router.push('/patient/reports')
}

onMounted(loadDetail)
</script>

<template>
  <div class="report-detail-page">
    <div class="detail-header">
      <el-button text class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回我的报告
      </el-button>
    </div>

    <div class="report-detail" v-loading="loading">
      <template v-if="report">
        <!-- 报告信息头 -->
        <div class="report-head">
          <div class="rh-left">
            <h2 class="report-title">{{ typeText(report.reportType) }}</h2>
            <div class="rh-meta">
              <span class="status-tag" :class="statusMeta(report.status).cls">{{ statusMeta(report.status).label }}</span>
              <span class="rh-date">出具时间 {{ formatTime(report.createdAt) }}</span>
              <span class="rh-id">报告ID {{ report.id }}</span>
            </div>
          </div>
          <div class="rh-right">
            <el-button type="primary" plain @click="goBack">返回列表</el-button>
          </div>
        </div>

        <!-- 报告图片 -->
        <div class="report-body">
          <div v-if="resolveImg(report.imageUrl)" class="report-img-wrap">
            <el-image
              :src="resolveImg(report.imageUrl)"
              :alt="typeText(report.reportType)"
              class="report-img"
              fit="contain"
              :preview-src-list="[resolveImg(report.imageUrl)]"
              preview-teleported
              hide-on-click-modal
            />
            <p class="img-tip">点击图片可放大查看</p>
          </div>
          <div v-else class="no-img">
            <p>该报告暂无可查看的图片内容</p>
          </div>
        </div>

        <!-- 附加信息 -->
        <div v-if="report.imageMeta" class="report-meta-card">
          <h3 class="meta-title">报告备注</h3>
          <p class="meta-text">{{ report.imageMeta }}</p>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-if="!loading && !report" class="empty-state">
        <p>报告不存在或已被删除</p>
        <el-button type="primary" @click="goBack">返回列表</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-detail-page { padding: 4px 2px 20px; }
.detail-header { margin-bottom: 16px; }
.back-btn { font-size: 14px; color: #5A6B82; }

.report-detail { max-width: 860px; margin: 0 auto; }

/* 头部 */
.report-head {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  background: #fff; border: 1px solid #ECF1F7; border-radius: 14px; padding: 22px 26px; margin-bottom: 16px;
}
.report-title { font-size: 20px; color: #1A2B45; margin: 0 0 10px; }
.rh-meta { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.status-tag { font-size: 12px; font-weight: 600; padding: 3px 12px; border-radius: 100px; }
.st-uploaded { color: #B7791F; background: #FFF3DC; }
.st-analyzed { color: #1E7AE6; background: #E3F2FF; }
.st-signed { color: #0E9F6E; background: #E2F7EF; }
.st-pending { color: #8492A6; background: #EEF1F5; }
.rh-date { font-size: 13px; color: #8492A6; }
.rh-id { font-size: 12.5px; color: #9CA3AF; font-family: 'SF Mono', 'Consolas', monospace; }
.rh-right { flex-shrink: 0; }

/* 报告图片 */
.report-body {
  background: #fff; border: 1px solid #ECF1F7; border-radius: 14px; padding: 24px; margin-bottom: 16px;
}
.report-img-wrap { text-align: center; }
.report-img { display: block; cursor: zoom-in; }
.report-img :deep(.el-image__inner) { max-width: 100%; max-height: 640px; border-radius: 10px; box-shadow: 0 6px 20px rgba(0,0,0,0.08); cursor: zoom-in; }
.img-tip { font-size: 12px; color: #A7B1BF; margin: 12px 0 0; }
.no-img { text-align: center; color: #A7B1BF; font-size: 14px; padding: 60px 0; }

/* 备注 */
.report-meta-card { background: #fff; border: 1px solid #ECF1F7; border-radius: 14px; padding: 20px 24px; }
.meta-title { font-size: 15px; font-weight: 600; color: #1A2B45; margin: 0 0 10px; }
.meta-text { font-size: 14px; color: #5A6B82; line-height: 1.7; margin: 0; white-space: pre-wrap; }

/* 空状态 */
.empty-state { text-align: center; padding: 60px 0; }
.empty-state p { color: #8B95A7; font-size: 15px; margin: 0 0 20px; }
</style>
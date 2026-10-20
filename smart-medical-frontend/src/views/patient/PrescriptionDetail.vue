<script setup>
/**
 * 患者端「处方详情」页组件
 * 职责：展示医生开具的处方内容（诊断、医嘱、药品明细）。
 * 数据来源（真实接口）：
 *   GET /prescriptions/by-visit/{visitId}  按就诊 ID 查询处方详情（含药品明细，患者归属校验）
 *     返回：{prescriptionNo, department, patientName, doctorName, diagnosis, advice, status, createdAt, items[]}
 */
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { fetchPrescriptionByVisit } from '@/api/prescription'

const route = useRoute()
const router = useRouter()

/** 就诊进度 ID（路由参数） */
const visitId = route.params.visitId

/** 处方详情 VO */
const rx = ref(null)
/** 加载状态 */
const loading = ref(true)

/** 处方状态 → 中文标签与样式类 */
function rxStatus(s) {
  if (s === 'ISSUED') return { label: '已开具', cls: 'st-issued' }
  if (s === 'DRAFT') return { label: '草稿', cls: 'st-draft' }
  return { label: s || '已开具', cls: 'st-issued' }
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

/** 加载处方详情 */
async function loadDetail() {
  loading.value = true
  try {
    rx.value = await fetchPrescriptionByVisit(visitId)
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 返回我的报告 */
function goBack() {
  router.push('/patient/reports')
}

onMounted(loadDetail)
</script>

<template>
  <div class="rx-detail-page">
    <div class="detail-header">
      <el-button text class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回我的报告
      </el-button>
    </div>

    <div class="rx-detail" v-loading="loading">
      <template v-if="rx">
        <!-- 处方信息头 -->
        <div class="rx-head">
          <div class="rh-left">
            <h2 class="rx-title">处方详情</h2>
            <div class="rh-meta">
              <span class="status-tag" :class="rxStatus(rx.status).cls">{{ rxStatus(rx.status).label }}</span>
              <span class="rh-no">处方单号 {{ rx.prescriptionNo }}</span>
            </div>
          </div>
          <div class="rh-right">
            <el-button type="primary" plain @click="goBack">返回列表</el-button>
          </div>
        </div>

        <!-- 基本信息 -->
        <div class="rx-card">
          <h3 class="card-title">基本信息</h3>
          <ul class="info-list">
            <li><span class="i-label">患者</span><span class="i-value">{{ rx.patientName }}</span></li>
            <li><span class="i-label">就诊科室</span><span class="i-value">{{ rx.department }}</span></li>
            <li><span class="i-label">开方医生</span><span class="i-value">{{ rx.doctorName }}</span></li>
            <li><span class="i-label">开方时间</span><span class="i-value">{{ formatTime(rx.createdAt) }}</span></li>
          </ul>
        </div>

        <!-- 诊断与医嘱 -->
        <div class="rx-card">
          <h3 class="card-title">诊断</h3>
          <p class="field-text">{{ rx.diagnosis || '—' }}</p>
          <h3 class="card-title sub">医嘱</h3>
          <p class="field-text">{{ rx.advice || '—' }}</p>
        </div>

        <!-- 药品明细 -->
        <div class="rx-card">
          <h3 class="card-title">药品明细</h3>
          <div class="drug-table-wrap">
            <table class="drug-table">
              <thead>
                <tr>
                  <th>药品名称</th>
                  <th>规格</th>
                  <th>单次剂量</th>
                  <th>频次</th>
                  <th>天数</th>
                  <th>数量</th>
                  <th>用法</th>
                  <th>备注</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="it in rx.items" :key="it.id">
                  <td class="drug-name">{{ it.drugName || '—' }}</td>
                  <td>{{ it.specification || '—' }}</td>
                  <td>{{ it.dosage || '—' }}</td>
                  <td>{{ it.frequency || '—' }}</td>
                  <td>{{ it.days ?? '—' }}</td>
                  <td>{{ it.quantity || '—' }}</td>
                  <td>{{ it.usageMethod || '—' }}</td>
                  <td class="remark-cell">{{ it.remark || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-if="!loading && !rx" class="empty-state">
        <p>该就诊暂无处方记录</p>
        <el-button type="primary" @click="goBack">返回列表</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rx-detail-page { padding: 4px 2px 20px; }
.detail-header { margin-bottom: 16px; }
.back-btn { font-size: 14px; color: #5A6B82; }

.rx-detail { max-width: 880px; margin: 0 auto; }

/* 头部 */
.rx-head {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  background: #fff; border: 1px solid #ECF1F7; border-radius: 14px; padding: 22px 26px; margin-bottom: 16px;
}
.rx-title { font-size: 20px; color: #1A2B45; margin: 0 0 10px; }
.rh-meta { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.status-tag { font-size: 12px; font-weight: 600; padding: 3px 12px; border-radius: 100px; }
.st-issued { color: #0E9F6E; background: #E2F7EF; }
.st-draft { color: #8492A6; background: #EEF1F5; }
.rh-no { font-size: 13px; color: #8492A6; font-family: 'SF Mono', 'Consolas', monospace; }
.rh-right { flex-shrink: 0; }

/* 信息卡 */
.rx-card {
  background: #fff; border: 1px solid #ECF1F7; border-radius: 14px;
  padding: 22px 26px; margin-bottom: 16px;
}
.card-title { font-size: 15px; font-weight: 600; color: #1A2B45; margin: 0 0 14px; }
.card-title.sub { margin-top: 20px; }

.info-list { list-style: none; padding: 0; margin: 0; }
.info-list li {
  display: flex; align-items: center; justify-content: space-between;
  padding: 9px 0; border-bottom: 1px solid #F5F7FA;
}
.info-list li:last-child { border-bottom: none; }
.i-label { font-size: 13.5px; color: #8B95A7; flex-shrink: 0; width: 84px; }
.i-value { font-size: 14px; color: #1A2332; font-weight: 500; }

.field-text { font-size: 14.5px; color: #2C3E50; line-height: 1.8; margin: 0; white-space: pre-wrap; }

/* 药品明细表 */
.drug-table-wrap { overflow-x: auto; }
.drug-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.drug-table th, .drug-table td {
  border: 1px solid #EEF1F5; padding: 10px 12px; text-align: left; white-space: nowrap;
}
.drug-table th { background: #F7FAFD; color: #5A6B82; font-weight: 600; }
.drug-table td { color: #2C3E50; }
.drug-table .drug-name { font-weight: 600; color: #1A2B45; }
.drug-table .remark-cell { white-space: pre-wrap; min-width: 120px; }

/* 空状态 */
.empty-state { text-align: center; padding: 60px 0; }
.empty-state p { color: #8B95A7; font-size: 15px; margin: 0 0 20px; }
</style>
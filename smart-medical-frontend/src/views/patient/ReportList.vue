<script setup>
/**
 * 患者端「我的报告」列表页组件
 * 职责：分为「处方报告 / 体检报告」两个 Tab：
 *   - 处方报告：医生开具的处方（诊断、医嘱、药品明细），点击进入处方详情；
 *   - 体检报告：检查/检验报告（sm_report 图片），点击进入报告详情。
 * 数据来源（真实接口）：
 *   GET /prescriptions/patient/me  本人处方列表（按处方编号分组，含药品明细）
 *   GET /reports                   体检报告分页列表（后端按当前患者过滤）
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchPatientPrescriptions } from '@/api/prescription'
import { fetchReports } from '@/api/report'

/** Vue Router 实例：跳转详情页 */
const router = useRouter()

/** Tab 配置 */
const tabs = [
  { key: 'prescription', label: '处方报告' },
  { key: 'physical', label: '体检报告' },
]
/** 当前选中的 Tab */
const activeTab = ref('prescription')

/** 处方列表 */
const prescriptions = ref([])
/** 体检报告列表 */
const reports = ref([])
/** 加载状态 */
const loading = ref(false)

/** 报告状态 → 中文标签与样式类 */
function statusMeta(s) {
  if (s === 'UPLOADED') return { label: '已上传', cls: 'st-uploaded' }
  if (s === 'ANALYZED') return { label: '已解读', cls: 'st-analyzed' }
  if (s === 'SIGNED') return { label: '已签名', cls: 'st-signed' }
  return { label: s || '未知', cls: 'st-pending' }
}

/** 处方状态 → 中文标签 */
function rxStatusText(s) {
  return s === 'ISSUED' ? '已开具' : (s === 'DRAFT' ? '草稿' : (s || '已开具'))
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
 * 时间戳格式化：ISO 字符串 → 'YYYY-MM-DD HH:mm'
 */
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 加载我的处方 + 报告 */
async function loadAll() {
  loading.value = true
  try {
    const [rx, rep] = await Promise.all([
      fetchPatientPrescriptions(),
      fetchReports({ page: 1, size: 50 }),
    ])
    prescriptions.value = rx || []
    reports.value = rep.list || []
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 跳转处方详情 */
function goPrescription(p) {
  router.push(`/patient/prescriptions/${p.visitId}`)
}

/** 跳转体检报告详情 */
function goReport(r) {
  router.push(`/patient/reports/${r.id}`)
}

/** 跳转上传体检报告 */
function goUpload() {
  router.push('/patient/reports/upload')
}

onMounted(loadAll)
</script>

<template>
  <div class="report-list-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="page-title brand-font">我的报告</h2>
        <p class="page-sub">处方报告与体检报告一站式查看</p>
      </div>
      <el-button v-if="activeTab === 'physical'" type="primary" class="upload-btn" @click="goUpload">
        <span style="font-size: 18px; line-height: 1; margin-right: 4px;">＋</span>上传报告
      </el-button>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-row">
      <button
        v-for="t in tabs" :key="t.key"
        :class="['tab-pill', { active: activeTab === t.key }]"
        @click="activeTab = t.key"
      >
        {{ t.label }}<span class="tab-count">{{ t.key === 'prescription' ? prescriptions.length : reports.length }}</span>
      </button>
    </div>

    <!-- ============ 处方报告 ============ -->
    <div v-if="activeTab === 'prescription'" class="card-list">
      <div v-for="p in prescriptions" :key="p.prescriptionNo" class="rx-card" @click="goPrescription(p)">
        <span class="rx-icon">
          <svg viewBox="0 0 24 24" fill="none">
            <rect x="4" y="3" width="16" height="18" rx="2" stroke="currentColor" stroke-width="1.6"/>
            <path d="M8 8h8M8 12h8M8 16h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          </svg>
        </span>
        <div class="rx-meta">
          <div class="rx-name-line">
            <strong>{{ p.diagnosis || '未填写诊断' }}</strong>
            <span class="status-tag st-issued">{{ rxStatusText(p.status) }}</span>
          </div>
          <div class="rx-info-line">
            <span class="rx-dept">{{ p.department }}</span>
            <span class="dot-sep">·</span>
            <span>开方医生 {{ p.doctorName }}</span>
            <span class="dot-sep">·</span>
            <span>共 {{ p.items.length }} 种药品</span>
          </div>
          <div class="rx-time-line">
            <span>开方时间 {{ formatTime(p.createdAt) }}</span>
            <span class="dot-sep">·</span>
            <span class="rx-no">处方单号 {{ p.prescriptionNo }}</span>
          </div>
        </div>
        <div class="rx-actions">
          <el-button text class="detail-link" @click.stop="goPrescription(p)">查看处方 ›</el-button>
        </div>
      </div>

      <!-- 处方空状态 -->
      <div v-if="!loading && prescriptions.length === 0" class="empty-state">
        <svg viewBox="0 0 160 120" fill="none">
          <rect x="25" y="20" width="110" height="80" rx="10" fill="#F0F6F2"/>
          <circle cx="70" cy="60" r="9" fill="#DCEBE2"/>
          <circle cx="100" cy="60" r="9" fill="#DCEBE2"/>
          <path d="M55 78c6 6 24 6 30 0" stroke="#C9E0D2" stroke-width="2.4" stroke-linecap="round"/>
          <path d="M118 46l4 4 8-9" stroke="#10B981" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <p class="empty-title">暂无处方报告</p>
        <p class="empty-sub">医生开具的处方会显示在这里，可在「就诊详情」中查看</p>
      </div>
    </div>

    <!-- ============ 体检报告 ============ -->
    <div v-else class="card-list">
      <div v-for="r in reports" :key="r.id" class="report-card" @click="goReport(r)">
        <!-- 缩略图 -->
        <div class="r-thumb" :style="{ background: 'linear-gradient(135deg,#1E7AE6,#4A9EF5)' }">
          <svg v-if="!resolveImg(r.imageUrl)" viewBox="0 0 24 24" fill="none" class="thumb-icon">
            <rect x="4" y="3" width="16" height="18" rx="2" stroke="#fff" stroke-width="1.6"/>
            <path d="M8 8h8M8 12h8M8 16h5" stroke="#fff" stroke-width="1.6" stroke-linecap="round"/>
          </svg>
          <img v-else :src="resolveImg(r.imageUrl)" :alt="typeText(r.reportType)" class="thumb-img" />
        </div>

        <!-- 报告信息 -->
        <div class="r-meta">
          <div class="r-name-line">
            <strong>{{ typeText(r.reportType) }}</strong>
            <span class="status-tag" :class="statusMeta(r.status).cls">{{ statusMeta(r.status).label }}</span>
          </div>
          <div class="r-info-line">
            <span class="r-date">出具时间 {{ formatTime(r.createdAt) }}</span>
            <span class="dot-sep">·</span>
            <span class="r-id">报告ID {{ r.id }}</span>
          </div>
          <div v-if="r.imageMeta" class="r-extra">{{ r.imageMeta }}</div>
        </div>

        <div class="r-actions">
          <el-button text class="detail-link" @click.stop="goReport(r)">查看报告 ›</el-button>
        </div>
      </div>

      <!-- 体检报告空状态 -->
      <div v-if="!loading && reports.length === 0" class="empty-state">
        <svg viewBox="0 0 160 120" fill="none">
          <rect x="25" y="20" width="110" height="80" rx="10" fill="#F0F4FA"/>
          <rect x="42" y="38" width="76" height="8" rx="4" fill="#D5E1F0"/>
          <rect x="42" y="56" width="60" height="7" rx="3.5" fill="#E1E9F4"/>
          <rect x="42" y="72" width="66" height="7" rx="3.5" fill="#E1E9F4"/>
          <path d="M112 84l8 8" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round"/>
          <circle cx="102" cy="88" r="12" fill="#1E7AE6" opacity="0.14"/>
          <path d="M96 88l4 4 8-9" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <p class="empty-title">暂无体检报告</p>
        <p class="empty-sub">就诊出具的报告会显示在这里，也可手动上传图片报告</p>
        <el-button type="primary" plain @click="goUpload">上传报告</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-list-page { padding: 4px 2px 20px; }

/* 页头 */
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0 0 6px; }
.page-sub { font-size: 13px; color: #8492A6; margin: 0; }
.upload-btn { border-radius: 10px; }

/* Tab */
.tab-row { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.tab-pill {
  border: 1.5px solid #E2EAF4; background: #fff; color: #5A6B82; border-radius: 100px;
  padding: 7px 16px; font-size: 13.5px; cursor: pointer; display: inline-flex; align-items: center; gap: 7px;
  transition: all 0.18s;
}
.tab-pill:hover { border-color: #BFD9F7; color: #1E7AE6; }
.tab-pill.active { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); border-color: transparent; color: #fff; box-shadow: 0 5px 13px rgba(30,122,230,0.28); }
.tab-count {
  background: #EDF2F8; color: #8492A6; border-radius: 100px; font-size: 11.5px;
  min-width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; padding: 0 6px;
}
.tab-pill.active .tab-count { background: rgba(255,255,255,0.25); color: #fff; }

/* 通用卡片 */
.card-list { display: flex; flex-direction: column; gap: 12px; }

/* 处方卡片 */
.rx-card {
  display: flex; align-items: center; gap: 18px; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 14px; padding: 16px 20px; cursor: pointer;
  transition: all 0.22s;
}
.rx-card:hover { border-color: #A7F3D0; box-shadow: 0 10px 24px rgba(16,185,129,0.1); transform: translateY(-2px); }
.rx-icon {
  width: 52px; height: 52px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; color: #fff;
  background: linear-gradient(135deg, #10B981, #34D399);
  box-shadow: 0 4px 12px rgba(16,185,129,0.2);
}
.rx-icon svg { width: 26px; height: 26px; }
.rx-meta { flex: 1; min-width: 0; }
.rx-name-line { display: flex; align-items: center; gap: 10px; }
.rx-name-line strong { font-size: 16px; color: #1A2B45; font-weight: 700; }
.status-tag { font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 100px; }
.st-issued { color: #0E9F6E; background: #E2F7EF; }
.rx-info-line { font-size: 12.5px; color: #8492A6; display: flex; align-items: center; gap: 7px; margin-top: 7px; flex-wrap: wrap; }
.rx-dept { font-size: 12px; color: #10B981; background: #E8F9F1; padding: 2px 9px; border-radius: 100px; }
.rx-time-line { font-size: 12px; color: #9CA3AF; display: flex; align-items: center; gap: 7px; margin-top: 6px; flex-wrap: wrap; }
.rx-no { font-family: 'SF Mono', 'Consolas', monospace; }
.dot-sep { color: #C3CCD8; }
.rx-actions { flex-shrink: 0; }
.detail-link { color: #10B981; font-weight: 600; padding: 0; }

/* 体检报告卡片 */
.report-card {
  display: flex; align-items: center; gap: 18px; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 14px; padding: 14px 18px; cursor: pointer;
  transition: all 0.22s;
}
.report-card:hover { border-color: #BFD9F7; box-shadow: 0 10px 24px rgba(30,122,230,0.09); transform: translateY(-2px); }
.r-thumb {
  width: 58px; height: 58px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
  box-shadow: 0 4px 12px rgba(30,122,230,0.16);
}
.thumb-icon { width: 28px; height: 28px; }
.thumb-img { width: 100%; height: 100%; object-fit: cover; }
.r-meta { flex: 1; min-width: 0; }
.r-name-line { display: flex; align-items: center; gap: 10px; }
.r-name-line strong { font-size: 16px; color: #1A2B45; font-weight: 700; }
.st-uploaded { color: #B7791F; background: #FFF3DC; }
.st-analyzed { color: #1E7AE6; background: #E3F2FF; }
.st-signed { color: #0E9F6E; background: #E2F7EF; }
.st-pending { color: #8492A6; background: #EEF1F5; }
.r-info-line { font-size: 12.5px; color: #8492A6; display: flex; align-items: center; gap: 7px; margin-top: 7px; }
.r-id { font-family: 'SF Mono', 'Consolas', monospace; }
.r-extra { font-size: 12px; color: #9CA3AF; margin-top: 4px; }
.r-actions { flex-shrink: 0; }
.report-card .detail-link { color: #1E7AE6; }

/* 空状态 */
.empty-state { text-align: center; padding: 50px 0 30px; }
.empty-state svg { width: 170px; }
.empty-title { font-size: 16px; font-weight: 700; color: #2C3E50; margin: 14px 0 4px; }
.empty-sub { font-size: 13px; color: #A7B1BF; margin: 0 0 18px; }
</style>
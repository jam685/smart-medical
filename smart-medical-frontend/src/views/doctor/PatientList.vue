<script setup>
/**
 * 医生端「我的患者」列表页组件
 * 职责：展示当前医生名下就诊过的患者（按最近就诊时间倒序），点击可前往接诊管理。
 * 数据来源（真实接口）：
 *   GET /doctors/me/patients  我的患者列表 [{patientId, realName, phone, lastVisitTime}]
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMyPatients } from '@/api/doctor'

/** Vue Router 实例：跳转接诊管理 */
const router = useRouter()

/** 患者列表 */
const patients = ref([])
/** 加载状态 */
const loading = ref(false)

/**
 * 时间戳格式化：ISO 字符串 → 'YYYY-MM-DD HH:mm'
 */
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 手机号脱敏：保留前 3 位与后 4 位 */
function maskPhone(phone) {
  if (!phone) return '未留手机号'
  const s = String(phone)
  if (s.length < 7) return s
  return `${s.slice(0, 3)}****${s.slice(-4)}`
}

/** 取姓名首字（头像占位文字） */
function initialOf(name) {
  return (name || '患').slice(0, 1)
}

/** 前往接诊管理 */
function goAppointments() {
  router.push('/doctor/appointments')
}

/** 页面加载时请求我的患者列表 */
onMounted(async () => {
  loading.value = true
  try {
    patients.value = await fetchMyPatients()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="patient-list-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="page-title brand-font">我的患者</h2>
        <p class="page-sub">您接诊过的患者，共 {{ patients.length }} 人</p>
      </div>
      <el-button type="primary" class="go-app-btn" @click="goAppointments">
        接诊管理
      </el-button>
    </div>

    <!-- 患者卡片列表 -->
    <div class="card-list">
      <div v-for="p in patients" :key="p.patientId" class="patient-card">
        <span class="p-avatar">{{ initialOf(p.realName) }}</span>
        <div class="p-meta">
          <div class="p-name-line">
            <strong>{{ p.realName }}</strong>
            <span class="p-id-tag">患者ID {{ p.patientId }}</span>
          </div>
          <div class="p-info-line">
            <span class="p-phone">{{ maskPhone(p.phone) }}</span>
            <span class="dot-sep">·</span>
            <span>最近就诊 {{ formatTime(p.lastVisitTime) }}</span>
          </div>
        </div>
        <div class="p-actions">
          <el-button text class="detail-link" @click="goAppointments">去接诊 ›</el-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && patients.length === 0" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#EFF3F8"/>
        <circle cx="68" cy="56" r="10" fill="#D6E0EB"/>
        <path d="M58 84c7 6 15 6 20 0" stroke="#B9C6D6" stroke-width="2.4" stroke-linecap="round"/>
        <path d="M96 78h18v18h-18z" rx="2" fill="#C9D6E4"/>
        <path d="M114 84l7 7-7 7" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <p class="empty-title">暂无患者</p>
      <p class="empty-sub">您接诊的患者会出现在这里，可前往「接诊管理」查看候诊名单</p>
      <el-button type="primary" plain @click="goAppointments">前往接诊管理</el-button>
    </div>
  </div>
</template>

<style scoped>
.patient-list-page { padding: 4px 2px 20px; }

/* 页头 */
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0 0 6px; }
.page-sub { font-size: 13px; color: #8492A6; margin: 0; }
.go-app-btn { border-radius: 10px; }

/* 卡片 */
.card-list { display: flex; flex-direction: column; gap: 12px; }
.patient-card {
  display: flex; align-items: center; gap: 16px; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 14px; padding: 16px 20px;
  transition: all 0.22s;
}
.patient-card:hover { border-color: #BFD9F7; box-shadow: 0 10px 24px rgba(30,122,230,0.09); transform: translateY(-2px); }
.p-avatar {
  width: 52px; height: 52px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1E7AE6, #4A9EF5);
  color: #fff; font-size: 22px; font-weight: 700;
  box-shadow: 0 4px 12px rgba(30,122,230,0.2);
}
.p-meta { flex: 1; min-width: 0; }
.p-name-line { display: flex; align-items: center; gap: 8px; }
.p-name-line strong { font-size: 16.5px; color: #1A2B45; font-weight: 700; }
.p-id-tag { font-size: 11.5px; color: #5A6B82; background: #F0F4F9; padding: 2px 8px; border-radius: 6px; font-family: 'SF Mono', 'Consolas', monospace; }
.p-info-line { font-size: 12.5px; color: #8492A6; display: flex; align-items: center; gap: 7px; margin-top: 6px; }
.p-phone { font-family: 'SF Mono', 'Consolas', monospace; }
.dot-sep { color: #C3CCD8; }
.p-actions { flex-shrink: 0; }
.detail-link { color: #1E7AE6; font-weight: 600; padding: 0; }

/* 空状态 */
.empty-state { text-align: center; padding: 50px 0 30px; }
.empty-state svg { width: 170px; }
.empty-title { font-size: 16px; font-weight: 700; color: #2C3E50; margin: 14px 0 4px; }
.empty-sub { font-size: 13px; color: #A7B1BF; margin: 0 0 18px; }
</style>

<template>
  <div class="doctor-dashboard">
    <!-- 欢迎 Banner -->
    <div class="welcome-banner">
      <div class="wb-left">
        <h1>您好，<span class="highlight">{{ userName }}</span> 医生 👋</h1>
        <p>今天是 {{ today }}，{{ greetingByStatus }}</p>
        <div class="wb-actions">
          <button class="btn-primary" @click="$router.push('/doctor/appointments')">
            <svg viewBox="0 0 16 16"><path d="M2 3h12v9H6l-4 3z" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linejoin="round"/><path d="M5 6h6M5 9h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            进入接诊
          </button>
          <button class="btn-ghost" @click="$router.push('/doctor/online-consults')">
            <svg viewBox="0 0 16 16"><path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            在线问诊
          </button>
        </div>
      </div>
      <div class="wb-illu">
        <svg viewBox="0 0 280 200" fill="none" class="wb-svg">
          <!-- 听诊器圆形听头 -->
          <circle cx="60" cy="60" r="22" fill="#fff" stroke="#1E7AE6" stroke-width="2.6"/>
          <circle cx="60" cy="60" r="10" fill="#1E7AE6"/>
          <circle cx="60" cy="60" r="4" fill="#fff"/>
          <!-- 听诊器管子分两支 -->
          <path d="M82 60 q40-2 60-26 q14-18 36-18" stroke="#1E7AE6" stroke-width="3" fill="none" stroke-linecap="round"/>
          <path d="M82 60 q40 2 60 26 q14 18 36 18" stroke="#1E7AE6" stroke-width="3" fill="none" stroke-linecap="round"/>
          <!-- 耳塞 -->
          <circle cx="184" cy="14" r="6" fill="#1E7AE6"/>
          <circle cx="184" cy="106" r="6" fill="#1E7AE6"/>
          <!-- 病历夹 -->
          <rect x="190" y="40" width="68" height="92" rx="6" fill="#fff" stroke="#1E7AE6" stroke-width="2.5"/>
          <rect x="190" y="40" width="68" height="20" rx="6" fill="#1E7AE6"/>
          <rect x="204" y="74" width="40" height="5" rx="2.5" fill="#C9D8EC"/>
          <rect x="204" y="86" width="40" height="5" rx="2.5" fill="#C9D8EC"/>
          <rect x="204" y="98" width="28" height="5" rx="2.5" fill="#C9D8EC"/>
          <rect x="204" y="110" width="34" height="5" rx="2.5" fill="#C9D8EC"/>
          <!-- 心电波 -->
          <path d="M20 178 h36 l5-12 l9 22 l8-26 l6 16 h46" stroke="#10B981" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          <circle cx="148" cy="178" r="5" fill="#E53935"/>
          <!-- 十字医疗 -->
          <rect x="220" y="146" width="38" height="38" rx="8" fill="#FDECEC" stroke="#E53935" stroke-width="2"/>
          <path d="M239 156 v18 M230 165 h18" stroke="#E53935" stroke-width="3" stroke-linecap="round"/>
        </svg>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card s1">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><path d="M3 12l2-7h14l2 7M3 12v7a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-7M3 12h18" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><circle cx="7" cy="16" r="1.5" fill="currentColor"/><circle cx="17" cy="16" r="1.5" fill="currentColor"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.todayVisits }}</strong>
          <span>今日接诊</span>
        </div>
      </div>
      <div class="stat-card s2">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M12 8v4l3 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.waiting }}</strong>
          <span>候诊中</span>
        </div>
      </div>
      <div class="stat-card s3">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><path d="M12 2v6m0 0a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm-6.4 9.6A9.6 9.6 0 0 0 12 21.6m6.4-10A9.6 9.6 0 0 1 12 21.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.inProgress }}</strong>
          <span>就诊中</span>
        </div>
      </div>
      <div class="stat-card s4">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><path d="M21 11.5a8.38 8.38 0 0 1-8.5 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.7a8.5 8.5 0 1 1 16.1-3.8z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.pendingConsults }}</strong>
          <span>待回复问诊</span>
        </div>
      </div>
    </div>

    <!-- 两栏布局 -->
    <div class="two-col">
      <!-- 左：今日接诊列表 -->
      <div class="panel">
        <div class="panel-head">
          <h3>今日接诊</h3>
          <a class="view-all" @click="$router.push('/doctor/appointments')">查看全部 →</a>
        </div>
        <div class="visit-list" v-loading="loading">
          <div v-for="v in todayVisitList" :key="v.id" class="visit-card" @click="$router.push('/doctor/appointments')">
            <div class="visit-avatar" :class="statusClass(v.status)">
              <span>{{ v.patientName?.slice(0, 1) || '?' }}</span>
            </div>
            <div class="visit-body">
              <div class="visit-top">
                <strong>{{ v.patientName }}</strong>
                <span :class="['status-tag', v.status]">{{ v.statusDesc }}</span>
              </div>
              <p class="visit-sub">{{ v.department }} · {{ v.doctorTitle }}</p>
              <p class="visit-sub subtle" v-if="v.appointmentTime">就诊 {{ v.appointmentTime.slice(11, 16) }}</p>
              <p class="visit-sub subtle" v-else>当前节点：{{ v.currentNode }}</p>
            </div>
            <div class="visit-action">
              <svg viewBox="0 0 16 16"><path d="M6 3l5 5-5 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </div>
          </div>
          <div v-if="!loading && todayVisitList.length === 0" class="empty-state-mini">
            <svg viewBox="0 0 120 90" fill="none">
              <rect x="20" y="15" width="80" height="60" rx="8" fill="#F2F6FC"/>
              <rect x="34" y="32" width="40" height="5" rx="2.5" fill="#C9D8EC"/>
              <rect x="34" y="44" width="52" height="4" rx="2" fill="#E1E9F4"/>
              <rect x="34" y="54" width="36" height="4" rx="2" fill="#E1E9F4"/>
            </svg>
            <p>今日暂无接诊</p>
          </div>
        </div>
      </div>

      <!-- 右：累计数据 + 快捷入口 -->
      <div class="panel">
        <div class="panel-head">
          <h3>累计数据</h3>
        </div>
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-num">{{ stats.totalPatients }}</div>
            <div class="kpi-label">累计接诊患者</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-num">{{ stats.finishedToday }}</div>
            <div class="kpi-label">今日已完成</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-num">{{ stats.totalPrescriptions }}</div>
            <div class="kpi-label">累计处方</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-num">{{ stats.pendingConsults }}</div>
            <div class="kpi-label">待回复问诊</div>
          </div>
        </div>

        <div class="panel-head" style="margin-top: 22px;">
          <h3>快捷入口</h3>
        </div>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/doctor/appointments')">
            <svg viewBox="0 0 32 32"><rect x="5" y="6" width="22" height="22" rx="4" fill="#E3F2FF" stroke="#1E7AE6" stroke-width="1.8"/><path d="M16 11v10M11 16h10" stroke="#1E7AE6" stroke-width="2.2" stroke-linecap="round"/></svg>
            <span>接诊管理</span>
          </div>
          <div class="quick-item" @click="$router.push('/doctor/online-consults')">
            <svg viewBox="0 0 32 32"><path d="M5 8h22v15H10l-5 4z" fill="#E6F9F2" stroke="#10B981" stroke-width="1.8" stroke-linejoin="round"/><path d="M11 14h10M11 18h7" stroke="#10B981" stroke-width="2" stroke-linecap="round"/></svg>
            <span>在线问诊</span>
          </div>
          <div class="quick-item" @click="$router.push('/doctor/medical-notes')">
            <svg viewBox="0 0 32 32"><rect x="6" y="5" width="20" height="22" rx="3" fill="#F0ECFE" stroke="#7A5AF8" stroke-width="1.8"/><path d="M11 11h10M11 16h10M11 21h6" stroke="#7A5AF8" stroke-width="1.8" stroke-linecap="round"/></svg>
            <span>病历管理</span>
          </div>

        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 医生端首页工作台
 * 职责：从后端 /stats/doctor 接口拉取当前登录医生的聚合统计与今日接诊列表，
 *       渲染欢迎横幅、4 张关键统计卡片、今日接诊列表与累计数据 / 快捷入口。
 * 数据来源：GET /stats/doctor（单次请求聚合，避免多请求）
 */
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { fetchDoctorDashboard } from '@/api/stats'

/** 登录用户 store（取姓名） */
const userStore = useUserStore()
/** 欢迎语中的医生姓名（兼容 user store 与后端 /me） */
const userName = computed(() => userStore.user?.realName || '医生')
/** 今日日期 + 中文星期 */
const today = computed(() => {
  const d = new Date()
  const w = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${w}`
})

/** 统计指标（与后端 DoctorStats 字段一致） */
const stats = ref({
  todayVisits: 0, waiting: 0, inProgress: 0, finishedToday: 0,
  totalPatients: 0, totalPrescriptions: 0, pendingConsults: 0,
})

/** 今日接诊列表（后端 VisitVO） */
const todayVisitList = ref([])
/** 加载状态 */
const loading = ref(false)

/**
 * 欢迎语：根据候诊 / 就诊状态动态切换
 */
const greetingByStatus = computed(() => {
  if (stats.value.inProgress > 0) return `当前有 ${stats.value.inProgress} 位患者正在就诊`
  if (stats.value.waiting > 0) return `当前有 ${stats.value.waiting} 位患者正在候诊`
  return '暂无候诊患者，可处理在线问诊'
})

/**
 * 状态 → 头像背景色 class
 */
function statusClass(s) {
  return ({
    WAITING: 'av-waiting', CALLING: 'av-calling',
    IN_PROGRESS: 'av-progress', FINISHED: 'av-finished',
  })[s] || 'av-waiting'
}

/**
 * 加载医生端仪表盘数据（单次请求）
 */
async function loadDashboard() {
  loading.value = true
  try {
    const dash = await fetchDoctorDashboard()
    if (dash?.user) userStore.user = { ...userStore.user, ...dash.user }
    if (dash?.stats) stats.value = { ...stats.value, ...dash.stats }
    if (dash?.todayVisitList) todayVisitList.value = dash.todayVisitList
  } catch {
    /* 拦截器已处理 */
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.doctor-dashboard { padding: 24px; }

/* 欢迎 Banner */
.welcome-banner {
  display: grid; grid-template-columns: 1.25fr 1fr; gap: 24px;
  background: linear-gradient(135deg, #1E7AE6 0%, #4A9EF5 100%);
  border-radius: 24px; padding: 32px 36px; color: #fff;
  box-shadow: 0 12px 36px rgba(30,122,230,0.25);
  position: relative; overflow: hidden; margin-bottom: 24px;
}
.welcome-banner::before {
  content: ''; position: absolute; right: -60px; top: -60px; width: 260px; height: 260px;
  border-radius: 50%; background: rgba(255,255,255,0.07);
}
.welcome-banner::after {
  content: ''; position: absolute; left: 40%; bottom: -120px; width: 220px; height: 220px;
  border-radius: 50%; background: rgba(255,255,255,0.05);
}
.wb-left { position: relative; z-index: 2; }
.wb-left h1 { font-size: 28px; font-weight: 700; margin: 0 0 8px; }
.wb-left .highlight { color: #FFE27A; }
.wb-left p { font-size: 14px; opacity: 0.9; margin: 0 0 22px; }
.wb-actions { display: flex; gap: 12px; }
.btn-primary, .btn-ghost {
  padding: 10px 22px; border-radius: 12px; font-size: 14px; font-weight: 600;
  cursor: pointer; display: inline-flex; align-items: center; gap: 7px;
  border: none; transition: all 0.2s;
}
.btn-primary { background: #fff; color: #1E7AE6; }
.btn-primary:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.2); }
.btn-ghost { background: rgba(255,255,255,0.15); color: #fff; border: 1px solid rgba(255,255,255,0.4); }
.btn-ghost:hover { background: rgba(255,255,255,0.28); }
.btn-primary svg, .btn-ghost svg { width: 16px; height: 16px; }
.wb-svg { width: 100%; max-width: 320px; }
.wb-illu { display: flex; justify-content: flex-end; align-items: center; position: relative; z-index: 2; }

/* 统计卡 */
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.stat-card {
  background: #fff; border-radius: 16px; padding: 18px 20px;
  display: flex; align-items: center; gap: 14px;
  box-shadow: 0 2px 12px rgba(15,23,42,0.05);
  transition: all 0.25s;
}
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 10px 28px rgba(15,23,42,0.08); }
.sc-icon {
  width: 46px; height: 46px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.sc-icon svg { width: 24px; height: 24px; color: #fff; }
.stat-card.s1 .sc-icon { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); }
.stat-card.s2 .sc-icon { background: linear-gradient(135deg, #F59E0B, #FBBF24); }
.stat-card.s3 .sc-icon { background: linear-gradient(135deg, #10B981, #34D399); }
.stat-card.s4 .sc-icon { background: linear-gradient(135deg, #7A5AF8, #9B7DFB); }
.sc-info strong { font-size: 24px; font-weight: 700; color: #1a1a2e; display: block; }
.sc-info span { font-size: 12px; color: #64748b; }

/* 两栏 */
.two-col { display: grid; grid-template-columns: 1.25fr 1fr; gap: 20px; }
.panel { background: #fff; border-radius: 18px; padding: 22px; box-shadow: 0 2px 12px rgba(15,23,42,0.05); }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.panel-head h3 { font-size: 17px; font-weight: 700; margin: 0; color: #1a1a2e; }
.view-all { font-size: 13px; color: #1E7AE6; cursor: pointer; }
.view-all:hover { text-decoration: underline; }

/* 今日接诊卡片 */
.visit-list { display: flex; flex-direction: column; gap: 12px; }
.visit-card {
  display: flex; gap: 14px; padding: 14px; border-radius: 14px;
  background: #F8FBFF; border: 1px solid #E8F1FC; cursor: pointer;
  transition: all 0.2s;
}
.visit-card:hover { border-color: #1E7AE6; background: #fff; box-shadow: 0 6px 20px rgba(30,122,230,0.1); transform: translateX(2px); }
.visit-avatar {
  width: 44px; height: 44px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 18px;
}
.av-waiting   { background: linear-gradient(135deg, #F59E0B, #FBBF24); }
.av-calling   { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); }
.av-progress  { background: linear-gradient(135deg, #10B981, #34D399); }
.av-finished  { background: linear-gradient(135deg, #94A3B8, #CBD5E1); }
.visit-body { flex: 1; min-width: 0; }
.visit-top { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.visit-top strong { font-size: 15px; color: #1a1a2e; }
.status-tag { font-size: 11px; padding: 3px 9px; border-radius: 10px; font-weight: 500; }
.status-tag.WAITING     { background: #FEF3C7; color: #92400E; }
.status-tag.CALLING     { background: #DBEAFE; color: #1E40AF; }
.status-tag.IN_PROGRESS { background: #D1FAE5; color: #065F46; }
.status-tag.FINISHED    { background: #E2E8F0; color: #475569; }
.visit-sub { font-size: 13px; color: #475569; margin: 0 0 2px; }
.visit-sub.subtle { color: #94A3B8; font-size: 12px; }
.visit-action { display: flex; align-items: center; color: #CBD5E1; }
.visit-action svg { width: 16px; height: 16px; }

.empty-state-mini { text-align: center; padding: 30px 10px; color: #94A3B8; }
.empty-state-mini svg { width: 120px; height: 90px; }
.empty-state-mini p { margin: 8px 0 0; font-size: 13px; }

/* 累计数据 KPI */
.kpi-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.kpi-card {
  background: linear-gradient(135deg, #F8FBFF 0%, #F0ECFE 100%);
  border-radius: 14px; padding: 18px 16px; text-align: center;
  border: 1px solid #E8F1FC;
}
.kpi-num { font-size: 26px; font-weight: 800; color: #1E7AE6; line-height: 1.1; }
.kpi-label { font-size: 12px; color: #64748b; margin-top: 4px; }

/* 快捷入口 */
.quick-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.quick-item {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 16px 10px; border-radius: 14px; background: #F8FBFF;
  border: 1px solid #E8F1FC; cursor: pointer; transition: all 0.2s;
}
.quick-item:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(30,122,230,0.12); background: #fff; }
.quick-item svg { width: 36px; height: 36px; }
.quick-item span { font-size: 13px; color: #334155; font-weight: 500; }

@media (max-width: 1024px) {
  .welcome-banner { grid-template-columns: 1fr; }
  .wb-illu { justify-content: center; }
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .two-col { grid-template-columns: 1fr; }
}
</style>

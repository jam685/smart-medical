<template>
  <div class="patient-dashboard">
    <!-- 欢迎 Banner -->
    <div class="welcome-banner">
      <div class="wb-left">
        <h1>你好，<span class="highlight">{{ userName }}</span> 👋</h1>
        <p>今天是 {{ today }}，有 {{ upcomingCount }} 个即将到来的预约</p>
        <div class="wb-actions">
          <button class="btn-primary" @click="$router.push('/patient/appointments/create')">
            <svg viewBox="0 0 16 16"><path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            立即挂号
          </button>
          <button class="btn-ghost" @click="$router.push('/patient/online-consults/create')">
            <svg viewBox="0 0 16 16"><path d="M2 3h12v9H6l-4 3z" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linejoin="round"/></svg>
            发起问诊
          </button>
        </div>
      </div>
      <div class="wb-illu">
        <svg viewBox="0 0 280 200" fill="none" class="wb-svg">
          <!-- 背后大屏 -->
          <rect x="110" y="30" width="140" height="120" rx="14" fill="#fff" stroke="#1E7AE6" stroke-width="2.5"/>
          <rect x="110" y="30" width="140" height="28" rx="14" fill="#1E7AE6"/>
          <rect x="110" y="46" width="140" height="12" fill="#1E7AE6"/>
          <circle cx="128" cy="44" r="3" fill="#fff" opacity="0.9"/>
          <circle cx="138" cy="44" r="3" fill="#fff" opacity="0.6"/>
          <!-- 屏幕：挂号卡片列表 -->
          <rect x="124" y="74" width="112" height="18" rx="4" fill="#E3F2FF"/>
          <rect x="124" y="98" width="112" height="18" rx="4" fill="#E6F9F2"/>
          <rect x="124" y="122" width="70" height="18" rx="4" fill="#F0ECFE"/>
          <!-- 患者小人（左） -->
          <circle cx="60" cy="78" r="18" fill="none" stroke="#1E7AE6" stroke-width="2.5"/>
          <path d="M30 170 q0-52 30-52 q30 0 30 52" fill="#E3F2FF" stroke="#1E7AE6" stroke-width="2.5"/>
          <rect x="44" y="136" width="32" height="10" rx="4" fill="#1E7AE6" opacity="0.8"/>
          <!-- 对话气泡 -->
          <path d="M82 54 q20-10 30 0 q0 12-14 12 l-4 6 l-4-6 q-8 2-12-6z" fill="#fff" stroke="#7A5AF8" stroke-width="2"/>
          <path d="M90 58 h12 M90 63 h8" stroke="#7A5AF8" stroke-width="1.8" stroke-linecap="round"/>
          <!-- 底部心电 -->
          <path d="M30 184 h40 l6-12 l8 20 l8-24 l6 16 h46"
                stroke="#10B981" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          <circle cx="180" cy="184" r="5" fill="#E53935"/>
        </svg>
      </div>
    </div>

    <!-- 数据统计 -->
    <div class="stats-row">
      <div class="stat-card s1">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="3" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.appointments }}</strong>
          <span>历史挂号</span>
        </div>
      </div>
      <div class="stat-card s2">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 8h18M8 12h8M8 16h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.reports }}</strong>
          <span>检查报告</span>
        </div>
      </div>
      <div class="stat-card s3">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><path d="M21 11.5a8.38 8.38 0 0 1-8.5 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.7a8.5 8.5 0 1 1 16.1-3.8z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.consults }}</strong>
          <span>在线问诊</span>
        </div>
      </div>
      <div class="stat-card s4">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="3" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.pending }}</strong>
          <span>待就诊</span>
        </div>
      </div>
    </div>

    <!-- 两栏布局 -->
    <div class="two-col">
      <!-- 左：即将到来的挂号 -->
      <div class="panel">
        <div class="panel-head">
          <h3>即将到来</h3>
          <a class="view-all" @click="$router.push('/patient/appointments')">查看全部 →</a>
        </div>
        <div class="appt-list">
          <div v-for="a in upcomingAppts" :key="a.id" class="appt-card" @click="$router.push('/patient/appointments/' + a.id)">
            <div class="appt-date">
              <span class="day">{{ a.day }}</span>
              <span class="month">{{ a.month }}</span>
            </div>
            <div class="appt-body">
              <div class="appt-top">
                <strong>{{ a.department }}</strong>
                <span :class="['status-tag', a.status]">{{ a.statusLabel }}</span>
              </div>
              <p class="appt-sub">{{ a.doctor }} · {{ a.time }}</p>
              <p class="appt-sub subtle">{{ a.hospital }}</p>
            </div>
            <div class="appt-action">
              <svg viewBox="0 0 16 16"><path d="M6 3l5 5-5 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：快捷入口 + 健康小贴士 -->
      <div class="panel">
        <div class="panel-head">
          <h3>快捷入口</h3>
        </div>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/patient/appointments/create')">
            <svg viewBox="0 0 32 32"><rect x="5" y="6" width="22" height="22" rx="4" fill="#E3F2FF" stroke="#1E7AE6" stroke-width="1.8"/><path d="M16 11v10M11 16h10" stroke="#1E7AE6" stroke-width="2.2" stroke-linecap="round"/></svg>
            <span>预约挂号</span>
          </div>
          <div class="quick-item" @click="$router.push('/patient/reports/upload')">
            <svg viewBox="0 0 32 32"><rect x="5" y="6" width="22" height="22" rx="4" fill="#F0ECFE" stroke="#7A5AF8" stroke-width="1.8"/><path d="M16 20V11M12 15l4-4 4 4" stroke="#7A5AF8" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <span>上传报告</span>
          </div>
          <div class="quick-item" @click="$router.push('/patient/online-consults/create')">
            <svg viewBox="0 0 32 32"><path d="M5 8h22v15H10l-5 4z" fill="#E6F9F2" stroke="#10B981" stroke-width="1.8" stroke-linejoin="round"/><path d="M11 14h10M11 18h7" stroke="#10B981" stroke-width="2" stroke-linecap="round"/></svg>
            <span>在线问诊</span>
          </div>
          <div class="quick-item" @click="$router.push('/patient/ai-chat')">
            <svg viewBox="0 0 32 32"><rect x="5" y="6" width="22" height="22" rx="4" fill="#FDECEC" stroke="#E53935" stroke-width="1.8"/><circle cx="16" cy="16" r="5" fill="none" stroke="#E53935" stroke-width="2"/><path d="M14 14l4 4M18 14l-4 4" stroke="#E53935" stroke-width="2" stroke-linecap="round"/></svg>
            <span>AI 助手</span>
          </div>
        </div>

        <div class="health-tip">
          <div class="tip-head">
            <svg viewBox="0 0 24 24" fill="none"><path d="M12 2l4 4v4l4 4-4 4v4l-4 4-4-4v-4l-4-4 4-4V6z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M10 12l2 2 3-3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <span>今日健康小贴士</span>
          </div>
          <p>{{ healthTip }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 患者端首页仪表盘
 * 所有统计数字与「即将到来」列表均来自后端真实聚合接口（刷新即与数据库一致）：
 *   GET /stats/patient  一次性返回：5 项统计指标 + 5 条即将到来挂号列表
 */
import { computed, ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { fetchPatientDashboard } from '@/api/stats'

/** 登录用户 store（取姓名） */
const userStore = useUserStore()
/** 欢迎语中的患者姓名 */
const userName = computed(() => userStore.user?.realName || '患者')
/** 今日日期与星期文案 */
const today = computed(() => {
  const d = new Date()
  const w = ['周日','周一','周二','周三','周四','周五','周六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth()+1} 月 ${d.getDate()} 日 · ${w}`
})

/**
 * 四张统计卡数据（全部来自后端 stats 接口；初始 0，加载完成后更新）
 * appointments 历史挂号总数 / reports 报告总数 / consults 问诊总数 / pending 待就诊数
 */
const stats = ref({ appointments: 0, reports: 0, consults: 0, pending: 0 })

/** 即将到来的挂号视图列表（后端 upcoming 字段，最多 5 条） */
const upcomingAppts = ref([])
/** 即将到来预约数（欢迎语中展示） */
const upcomingCount = computed(() => upcomingAppts.value.length)

/** 平台医院名称常量（品牌配置，非业务数据） */
const HOSPITAL_NAME = '小林智疗互联网医院'
/** 中文星期表 */
const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/**
 * 将后端挂号 VO 转为首页卡片视图对象
 * @param {object} raw 后端 AppointmentVO
 * @returns {object} {id, day, month, department, doctor, time, hospital, status, statusLabel}
 */
function toUpcoming(raw) {
  const [datePart, timePart = ''] = (raw.appointmentTime || '').split(' ')
  const dateObj = datePart ? new Date(`${datePart}T00:00:00`) : null
  return {
    id: raw.id,
    day: datePart ? Number(datePart.slice(8, 10)) : '',
    month: dateObj ? WEEK_LABELS[dateObj.getDay()] : '',
    department: raw.department,
    doctor: raw.doctorName,
    time: timePart.slice(0, 5),
    hospital: HOSPITAL_NAME,
    status: raw.status === 'CONFIRMED' ? 'confirmed' : 'upcoming',
    statusLabel: raw.status === 'CONFIRMED' ? '已确认' : '待就诊',
  }
}

/**
 * 加载首页全部数据：单次调用 /stats/patient 聚合接口
 * 后端已按"状态非已取消 + 时间不早于今天 + 时间升序"返回最多 5 条 upcoming
 */
async function loadDashboard() {
  try {
    const dash = await fetchPatientDashboard()
    if (dash?.user) userStore.user = { ...userStore.user, ...dash.user }
    if (dash?.stats) stats.value = { ...stats.value, ...dash.stats }
    if (dash?.upcoming) {
      upcomingAppts.value = dash.upcoming
        .filter(a => a.status !== 'CANCELLED')
        .slice(0, 3)
        .map(toUpcoming)
    }
  } catch {
    /* 拦截器已统一处理 */
  }
}

/** 健康小贴士（平台运营内容，后续可由内容接口下发） */
const healthTip = ref('每天喝 8 杯水（约 2 升），有助于新陈代谢和排除体内毒素。建议分次饮用，每次 200-300 ml。')

onMounted(loadDashboard)
</script>

<style scoped>
.patient-dashboard { padding: 24px; }

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
.stat-card.s2 .sc-icon { background: linear-gradient(135deg, #7A5AF8, #9B7DFB); }
.stat-card.s3 .sc-icon { background: linear-gradient(135deg, #10B981, #34D399); }
.stat-card.s4 .sc-icon { background: linear-gradient(135deg, #E53935, #F56B68); }
.sc-info strong { font-size: 24px; font-weight: 700; color: #1a1a2e; display: block; }
.sc-info span { font-size: 12px; color: #64748b; }

/* 两栏 */
.two-col { display: grid; grid-template-columns: 1.25fr 1fr; gap: 20px; }
.panel { background: #fff; border-radius: 18px; padding: 22px; box-shadow: 0 2px 12px rgba(15,23,42,0.05); }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.panel-head h3 { font-size: 17px; font-weight: 700; margin: 0; color: #1a1a2e; }
.view-all { font-size: 13px; color: #1E7AE6; cursor: pointer; }
.view-all:hover { text-decoration: underline; }

/* 挂号列表 */
.appt-list { display: flex; flex-direction: column; gap: 12px; }
.appt-card {
  display: flex; gap: 14px; padding: 14px; border-radius: 14px;
  background: #F8FBFF; border: 1px solid #E8F1FC; cursor: pointer;
  transition: all 0.2s;
}
.appt-card:hover { border-color: #1E7AE6; background: #fff; box-shadow: 0 6px 20px rgba(30,122,230,0.1); transform: translateX(2px); }
.appt-date {
  width: 64px; flex-shrink: 0; background: linear-gradient(135deg, #1E7AE6, #4A9EF5);
  border-radius: 12px; display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: #fff; padding: 8px;
}
.appt-date .day { font-size: 22px; font-weight: 700; line-height: 1; }
.appt-date .month { font-size: 11px; opacity: 0.9; margin-top: 4px; }
.appt-body { flex: 1; min-width: 0; }
.appt-top { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.appt-top strong { font-size: 15px; color: #1a1a2e; }
.status-tag { font-size: 11px; padding: 3px 9px; border-radius: 10px; font-weight: 500; }
.status-tag.upcoming { background: #E53935; color: #fff; }
.status-tag.confirmed { background: #10B981; color: #fff; }
.appt-sub { font-size: 13px; color: #475569; margin: 0 0 2px; }
.appt-sub.subtle { color: #94A3B8; font-size: 12px; }
.appt-action { display: flex; align-items: center; color: #CBD5E1; }
.appt-action svg { width: 16px; height: 16px; }

/* 快捷入口 */
.quick-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 18px; }
.quick-item {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 16px 10px; border-radius: 14px; background: #F8FBFF;
  border: 1px solid #E8F1FC; cursor: pointer; transition: all 0.2s;
}
.quick-item:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(30,122,230,0.12); background: #fff; }
.quick-item svg { width: 36px; height: 36px; }
.quick-item span { font-size: 13px; color: #334155; font-weight: 500; }

/* 健康小贴士 */
.health-tip {
  background: linear-gradient(135deg, #F0ECFE 0%, #E3F2FF 100%);
  border-radius: 14px; padding: 16px;
}
.tip-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; color: #7A5AF8; font-size: 13px; font-weight: 600; }
.tip-head svg { width: 18px; height: 18px; }
.health-tip p { font-size: 13px; line-height: 1.8; color: #475569; margin: 0; }

@media (max-width: 1024px) {
  .welcome-banner { grid-template-columns: 1fr; }
  .wb-illu { justify-content: center; }
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .two-col { grid-template-columns: 1fr; }
}
</style>

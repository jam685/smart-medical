<script setup>
/**
 * 患者端「我的挂号」列表页组件
 * 职责：从后端接口加载当前患者的全部挂号单，支持按状态分组筛选，
 *       待就诊单可调用取消接口（取消成功后重新拉取列表，实现热更新）。
 * 数据来源：GET  /appointments（sm_appointment 联 sm_doctor 组装的 VO）
 *           PUT  /appointments/{id}/cancel
 * 状态口径：PENDING 待确认、CONFIRMED 已确认  —— 页面归为「待就诊」
 *           FINISHED 已完成、CANCELLED 已取消
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Clock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchAppointments, cancelAppointment } from '@/api/appointment'

/** Vue Router 实例，用于跳转到新建挂号页 / 挂号详情页 */
const router = useRouter()

// ============== 状态 Tab ==============
/**
 * 状态筛选 Tab 配置
 * @property {string} key   Tab 键：all 全部 / pending 待就诊 / finished 已完成 / canceled 已取消
 * @property {string} label Tab 显示文案
 */
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待就诊' },
  { key: 'finished', label: '已完成' },
  { key: 'canceled', label: '已取消' },
]

/** 当前选中的 Tab 键 */
const activeTab = ref('all')

// ============== 接口数据 ==============
/** 挂号单视图对象数组（由后端 VO 经 normalize 转换而来），初始为空，页面加载时请求 */
const appointments = ref([])
/** 列表加载状态：true 时显示加载动画 */
const loading = ref(false)

/** 医院名称（平台级常量配置，非挂号业务数据） */
const HOSPITAL_NAME = '小林智疗互联网医院'

/** 中文星期表（下标 0=周日） */
const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/**
 * 把后端挂号 VO 归一化为页面渲染对象
 * @param {object} raw 后端 AppointmentVO
 * @returns {object} 视图对象：日期/星期/时段/状态均从 appointmentTime 与 status 派生
 */
function normalize(raw) {
  // appointmentTime 形如 '2026-09-20 09:00:00'
  const [datePart, timePart = ''] = (raw.appointmentTime || '').split(' ')
  const startHHmm = timePart.slice(0, 5)
  const hour = Number(timePart.slice(0, 2)) || 0
  // 号源为半小时一个，结束时间 = 开始 + 30 分钟
  const end = addHalfHour(timePart)
  const dateObj = datePart ? new Date(`${datePart}T00:00:00`) : null

  return {
    id: raw.id,
    orderNo: raw.orderNo,
    dept: raw.department,
    doctor: raw.doctorName,
    doctorTitle: raw.doctorTitle,
    hospital: HOSPITAL_NAME,
    date: datePart,
    weekday: dateObj ? WEEK_LABELS[dateObj.getDay()] : '',
    period: hour < 12 ? '上午' : '下午',
    timeRange: startHHmm ? `${startHHmm} - ${end}` : '',
    fee: raw.fee,
    status: mapStatus(raw.status),
  }
}

/**
 * 后端状态码映射为页面状态键
 * @param {string} backendStatus PENDING/CONFIRMED/FINISHED/CANCELLED
 * @returns {string} pending/finished/canceled（未知状态兜底为 pending）
 */
function mapStatus(backendStatus) {
  if (backendStatus === 'FINISHED') return 'finished'
  if (backendStatus === 'CANCELLED') return 'canceled'
  return 'pending' // PENDING、CONFIRMED 均视为待就诊
}

/**
 * 计算 HH:mm:ss 加 30 分钟后的 HH:mm（号源结束时间）
 * @param {string} hms 形如 '09:00:00'
 * @returns {string} 形如 '09:30'
 */
function addHalfHour(hms) {
  const [h, m] = hms.split(':').map(Number)
  const total = h * 60 + m + 30
  const eh = String(Math.floor(total / 60)).padStart(2, '0')
  const em = String(total % 60).padStart(2, '0')
  return `${eh}:${em}`
}

/**
 * 从后端加载当前患者的挂号列表（热更新的唯一数据入口）
 * 设计：一次取足量数据（size=100），各 Tab 的筛选在前端基于同一份数据计算。
 */
async function loadAppointments() {
  loading.value = true
  try {
    const page = await fetchAppointments({ page: 1, size: 100 })
    appointments.value = (page.list || []).map(normalize)
  } catch (e) {
    // 请求失败提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/**
 * 各状态展示配置：label 文案 + 标签样式类
 */
const statusMap = {
  pending: { label: '待就诊', cls: 'st-pending' },
  finished: { label: '已完成', cls: 'st-finished' },
  canceled: { label: '已取消', cls: 'st-canceled' },
}

/**
 * 当前 Tab 下应展示的挂号单（响应式）
 * @returns {Array} 全部时返回整表，否则按页面状态键过滤
 */
const filtered = computed(() =>
  activeTab.value === 'all'
    ? appointments.value
    : appointments.value.filter(a => a.status === activeTab.value)
)

/**
 * 统计指定 Tab 的挂号单数量（用于角标）
 * @param {string} key Tab 键，all 表示全部
 * @returns {number} 数量
 */
function countOf(key) {
  return key === 'all' ? appointments.value.length : appointments.value.filter(a => a.status === key).length
}

/**
 * 把 YYYY-MM-DD 拆成左侧日期徽章所需的月、日
 * @param {string} d 日期字符串
 * @returns {{month: string, day: string}} month 形如 '9月'，day 形如 '20'
 */
function dateParts(d) {
  const [, m, day] = (d || '').split('-')
  return { month: `${+m}月`, day: `${+day}` }
}

// ============== 页面操作方法 ==============
/** 跳转新建挂号页 */
function goCreate() { router.push('/patient/appointments/create') }

/**
 * 跳转挂号详情页
 * @param {object} a 挂号单视图对象
 */
function goDetail(a) { router.push(`/patient/appointments/${a.id}`) }

/**
 * 取消挂号：二次确认 → 调后端取消接口 → 成功后重新拉取列表（热更新持久化）
 * @param {object} a 待取消的挂号单视图对象
 */
async function cancelOrder(a) {
  try {
    await ElMessageBox.confirm(
      `确定取消 ${a.date}（${a.weekday}）${a.period}「${a.dept} · ${a.doctor}」的挂号吗？`,
      '取消挂号确认',
      { confirmButtonText: '确认取消', cancelButtonText: '再想想', type: 'warning', roundButton: true }
    )
  } catch {
    return // 用户点击「再想想」，放弃操作
  }
  await cancelAppointment(a.id)
  ElMessage.success('挂号已取消')
  await loadAppointments() // 重新请求后端，页面与数据库保持一致
}

// 页面挂载即从后端加载数据（刷新页面也始终以数据库为准）
onMounted(loadAppointments)
</script>

<template>
  <div class="appt-list-page" v-loading="loading">
    <!-- 页头：标题 + 新建按钮 -->
    <div class="page-head">
      <div>
        <h2 class="page-title brand-font">我的挂号</h2>
        <p class="page-sub">查看与管理您的全部预约挂号记录</p>
      </div>
      <el-button type="primary" size="large" class="create-btn" @click="goCreate">
        <el-icon class="el-icon--left"><Plus /></el-icon>新建挂号
      </el-button>
    </div>

    <!-- 状态筛选 Tab（角标数量全部来自接口数据） -->
    <div class="tab-row">
      <button
        v-for="t in tabs" :key="t.key"
        :class="['tab-pill', { active: activeTab === t.key }]"
        @click="activeTab = t.key"
      >
        {{ t.label }}<span class="tab-count">{{ countOf(t.key) }}</span>
      </button>
    </div>

    <!-- 挂号卡片列表 -->
    <div class="card-list">
      <div v-for="a in filtered" :key="a.id" class="appt-card" :class="`is-${a.status}`" @click="goDetail(a)">
        <!-- 左：日期徽章 -->
        <div class="date-badge" :class="`is-${a.status}`">
          <span class="db-month">{{ dateParts(a.date).month }}</span>
          <span class="db-day">{{ dateParts(a.date).day }}</span>
          <span class="db-week">{{ a.weekday }}</span>
        </div>

        <!-- 中：挂号信息 -->
        <div class="appt-main">
          <div class="main-top">
            <span class="dept-name">{{ a.dept }}</span>
            <span class="clinic-tag">{{ a.doctorTitle }}</span>
            <span class="status-tag" :class="statusMap[a.status].cls">{{ statusMap[a.status].label }}</span>
          </div>
          <div class="main-doctor">
            <span class="doctor-avatar-mini">
              <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="9" r="4" fill="currentColor"/><path d="M4 21 Q4 14 12 14 Q20 14 20 21" fill="currentColor"/></svg>
            </span>
            <strong>{{ a.doctor }}</strong>
            <span class="dot-sep">·</span>
            <el-icon><Clock /></el-icon>
            <span>{{ a.period }} {{ a.timeRange }}</span>
          </div>
          <div class="main-meta">
            <span>{{ a.hospital }}</span>
            <span class="dot-sep">·</span>
            <span>单号 {{ a.orderNo }}</span>
            <span class="dot-sep">·</span>
            <span class="fee">挂号费 ¥{{ a.fee ?? '--' }}</span>
          </div>
        </div>

        <!-- 右：操作按钮（点击不冒泡触发整卡跳转） -->
        <div class="appt-actions" @click.stop>
          <el-button text class="detail-link" @click="goDetail(a)">详情 ›</el-button>
          <el-button v-if="a.status === 'pending'" plain type="danger" size="small" @click="cancelOrder(a)">
            取消挂号
          </el-button>
          <el-button v-else-if="a.status === 'finished'" plain type="primary" size="small" @click="router.push('/patient/reports')">
            查看报告
          </el-button>
        </div>
      </div>
    </div>

    <!-- 空状态：接口无数据或当前 Tab 无匹配 -->
    <div v-if="!loading && filtered.length === 0" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#F2F6FC"/>
        <rect x="42" y="40" width="50" height="7" rx="3.5" fill="#C9D8EC"/>
        <rect x="42" y="56" width="76" height="6" rx="3" fill="#E1E9F4"/>
        <rect x="42" y="70" width="60" height="6" rx="3" fill="#E1E9F4"/>
        <circle cx="115" cy="78" r="14" fill="#1E7AE6" opacity="0.12"/>
        <path d="M109 78l4 4 8-9" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <p class="empty-title">暂无挂号记录</p>
      <p class="empty-sub">当前分类下还没有挂号单，去预约一位医生吧</p>
      <el-button type="primary" @click="goCreate">
        <el-icon class="el-icon--left"><Plus /></el-icon>立即挂号
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.appt-list-page { padding: 4px 2px 20px; }

/* 页头 */
.page-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0 0 6px; }
.page-sub { font-size: 13px; color: #8492A6; margin: 0; }
.create-btn { border-radius: 10px; font-weight: 600; box-shadow: 0 6px 16px rgba(30,122,230,0.25); }

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

/* 卡片 */
.card-list { display: flex; flex-direction: column; gap: 12px; }
.appt-card {
  display: flex; align-items: stretch; gap: 18px; background: #fff;
  border: 1px solid #ECF1F7; border-radius: 14px; padding: 16px 20px; cursor: pointer;
  transition: all 0.22s;
}
.appt-card:hover { border-color: #BFD9F7; box-shadow: 0 10px 24px rgba(30,122,230,0.1); transform: translateY(-2px); }

/* 日期徽章 */
.date-badge {
  width: 72px; flex-shrink: 0; border-radius: 12px; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 1px;
  background: linear-gradient(160deg, #EAF3FF, #DCEBFF); color: #1E7AE6;
}
.date-badge.is-finished { background: #EEF6F1; color: #10B981; }
.date-badge.is-canceled { background: #F4F6F9; color: #A7B1BF; }
.db-month { font-size: 12px; }
.db-day { font-size: 24px; font-weight: 800; line-height: 1.1; }
.db-week { font-size: 11.5px; }

/* 中间信息 */
.appt-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 7px; }
.main-top { display: flex; align-items: center; gap: 10px; }
.dept-name { font-size: 16.5px; font-weight: 700; color: #1A2B45; }
.clinic-tag { font-size: 11.5px; color: #7A5AF8; background: #F1EDFF; padding: 2px 9px; border-radius: 100px; }
.status-tag { margin-left: auto; font-size: 12px; font-weight: 600; padding: 3px 12px; border-radius: 100px; }
.st-pending { color: #1E7AE6; background: #E3F2FF; }
.st-finished { color: #0E9F6E; background: #E2F7EF; }
.st-canceled { color: #8492A6; background: #EEF1F5; }

.main-doctor { display: flex; align-items: center; gap: 7px; font-size: 13.5px; color: #4A5A70; }
.doctor-avatar-mini { width: 22px; height: 22px; border-radius: 50%; background: #10B98122; color: #10B981; display: inline-flex; align-items: center; justify-content: center; }
.doctor-avatar-mini svg { width: 14px; height: 14px; }
.main-doctor strong { color: #1A2B45; font-size: 14px; }
.dot-sep { color: #C3CCD8; }
.main-meta { font-size: 12.5px; color: #A7B1BF; display: flex; align-items: center; gap: 7px; }
.main-meta .fee { color: #E53935; font-weight: 600; }

/* 右侧操作 */
.appt-actions { display: flex; flex-direction: column; align-items: flex-end; justify-content: space-between; gap: 8px; flex-shrink: 0; }
.detail-link { color: #1E7AE6; font-weight: 600; padding: 0; }

/* 空状态 */
.empty-state { text-align: center; padding: 50px 0 30px; }
.empty-state svg { width: 170px; }
.empty-title { font-size: 16px; font-weight: 700; color: #2C3E50; margin: 14px 0 4px; }
.empty-sub { font-size: 13px; color: #A7B1BF; margin: 0 0 18px; }

@media (max-width: 1100px) {
  .appt-card { flex-wrap: wrap; }
  .appt-actions { width: 100%; flex-direction: row; justify-content: flex-end; }
}
</style>

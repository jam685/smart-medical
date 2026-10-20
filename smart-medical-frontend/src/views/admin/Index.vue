<template>
  <div class="admin-dashboard">
    <!-- 欢迎 Banner -->
    <div class="welcome-banner">
      <div class="wb-left">
        <h1>欢迎，<span class="highlight">{{ userName }}</span> 管理员 👋</h1>
        <p>今天是 {{ today }}，系统运行正常 · {{ pendingWorkSummary }}</p>
        <div class="wb-actions">
          <button class="btn-primary" @click="$router.push('/admin/sensitive-review')">
            <svg viewBox="0 0 16 16"><path d="M8 2l6 3v5c0 3.5-2.6 5.6-6 6.6C4.6 15.6 2 13.5 2 10V5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M5.5 8.5l2 2 3-3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
            敏感内容审核
          </button>
          <button class="btn-ghost" @click="$router.push('/admin/audit-logs')">
            <svg viewBox="0 0 16 16"><rect x="2" y="3" width="12" height="11" rx="1.5" stroke="currentColor" stroke-width="1.6" fill="none"/><path d="M5 7h6M5 10h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            审计日志
          </button>
        </div>
      </div>
      <div class="wb-illu">
        <svg viewBox="0 0 280 200" fill="none" class="wb-svg">
          <!-- 仪表盘外圈 -->
          <circle cx="150" cy="100" r="70" fill="#fff" stroke="#1E7AE6" stroke-width="2.5"/>
          <!-- 表盘刻度 -->
          <g stroke="#1E7AE6" stroke-width="2" stroke-linecap="round">
            <path d="M150 32v8M210 60l-7 5M218 100h-8M210 140l-7-5"/>
            <path d="M90 60l7 5M82 100h8M90 140l7-5" stroke-width="1.4" opacity="0.6"/>
          </g>
          <!-- 表盘指针 -->
          <path d="M150 100l30-22" stroke="#E53935" stroke-width="3" stroke-linecap="round"/>
          <circle cx="150" cy="100" r="6" fill="#1E7AE6"/>
          <circle cx="150" cy="100" r="2" fill="#fff"/>
          <!-- 左侧齿轮 -->
          <g stroke="#1E7AE6" stroke-width="2" fill="#E3F2FF">
            <path d="M48 70l4-8 8 5-2 9 9 4 5-8 8 5-5 7 5 8-8 4-5-7-9 4 4-8-7-5-8 6 4-8-7-3z" stroke-linejoin="round"/>
            <circle cx="65" cy="80" r="6" fill="#fff"/>
          </g>
          <!-- 盾牌（右下） -->
          <path d="M232 138l-12-4-12 4v12c0 8 4 14 12 18 8-4 12-10 12-18z" fill="#E6F9F2" stroke="#10B981" stroke-width="2" stroke-linejoin="round"/>
          <path d="M216 152l3 3 6-7" stroke="#10B981" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
          <!-- 顶部数据点 -->
          <circle cx="220" cy="40" r="6" fill="#F59E0B"/>
          <path d="M220 40v-12" stroke="#F59E0B" stroke-width="2" stroke-linecap="round"/>
          <circle cx="208" cy="34" r="4" fill="#7A5AF8" opacity="0.7"/>
          <circle cx="232" cy="34" r="4" fill="#E53935" opacity="0.7"/>
        </svg>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card s1">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><circle cx="9" cy="8" r="3.5" stroke="currentColor" stroke-width="1.8"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 11l2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.totalPatients }}</strong>
          <span>注册患者</span>
        </div>
      </div>
      <div class="stat-card s2">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><rect x="4" y="5" width="16" height="16" rx="3" stroke="currentColor" stroke-width="1.8"/><path d="M9 3v4M15 3v4M8 12h8M8 16h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.totalDoctors }}</strong>
          <span>在职医生</span>
        </div>
      </div>
      <div class="stat-card s3">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="3" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.todayAppointments }}</strong>
          <span>今日新增挂号</span>
        </div>
      </div>
      <div class="stat-card s4">
        <div class="sc-icon">
          <svg viewBox="0 0 24 24" fill="none"><path d="M12 2l7 3v6c0 4.5-3 8-7 9-4-1-7-4.5-7-9V5z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </div>
        <div class="sc-info">
          <strong>{{ stats.sensitivePending }}</strong>
          <span>待审核敏感内容</span>
        </div>
      </div>
    </div>

    <!-- 两栏布局 -->
    <div class="two-col">
      <!-- 左：今日运营指标 + 快捷入口 -->
      <div class="panel">
        <div class="panel-head">
          <h3>今日运营指标</h3>
          <a class="view-all" @click="loadDashboard">刷新 →</a>
        </div>
        <div class="metric-list" v-loading="loading">
          <div class="metric-row">
            <div class="m-left">
              <div class="m-icon mi-blue"><svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="3" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></div>
              <div class="m-text">
                <strong>今日新增挂号</strong>
                <span>sm_appointment 当日 created_at</span>
              </div>
            </div>
            <div class="m-num">{{ stats.todayAppointments }}</div>
          </div>
          <div class="metric-row">
            <div class="m-left">
              <div class="m-icon mi-green"><svg viewBox="0 0 24 24" fill="none"><path d="M3 12l2-7h14l2 7M3 12v7a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-7M3 12h18" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg></div>
              <div class="m-text">
                <strong>今日就诊记录</strong>
                <span>sm_visit 当日 created_at</span>
              </div>
            </div>
            <div class="m-num">{{ stats.todayVisits }}</div>
          </div>
          <div class="metric-row">
            <div class="m-left">
              <div class="m-icon mi-purple"><svg viewBox="0 0 24 24" fill="none"><path d="M21 11.5a8.38 8.38 0 0 1-8.5 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.7a8.5 8.5 0 1 1 16.1-3.8z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg></div>
              <div class="m-text">
                <strong>待处理在线问诊</strong>
                <span>sm_online_consult_record status=PENDING</span>
              </div>
            </div>
            <div class="m-num">{{ stats.pendingConsults }}</div>
          </div>
          <div class="metric-row">
            <div class="m-left">
              <div class="m-icon mi-amber"><svg viewBox="0 0 24 24" fill="none"><rect x="4" y="3" width="16" height="18" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M8 8h8M8 12h6M8 16h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></div>
              <div class="m-text">
                <strong>今日审计日志</strong>
                <span>sm_audit_log 当日 created_at</span>
              </div>
            </div>
            <div class="m-num">{{ stats.todayAuditLogs }}</div>
          </div>
        </div>

        <div class="panel-head" style="margin-top: 22px;">
          <h3>快捷入口</h3>
        </div>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/admin/users')">
            <svg viewBox="0 0 32 32"><rect x="5" y="6" width="22" height="22" rx="4" fill="#E3F2FF" stroke="#1E7AE6" stroke-width="1.8"/><circle cx="16" cy="13" r="4" fill="none" stroke="#1E7AE6" stroke-width="2"/><path d="M9 22c0-3.5 3-6 7-6s7 2.5 7 6" fill="none" stroke="#1E7AE6" stroke-width="2" stroke-linecap="round"/></svg>
            <span>用户管理</span>
          </div>
          <div class="quick-item" @click="$router.push('/admin/audit-logs')">
            <svg viewBox="0 0 32 32"><rect x="6" y="5" width="20" height="22" rx="3" fill="#F0ECFE" stroke="#7A5AF8" stroke-width="1.8"/><path d="M11 11h10M11 16h10M11 21h6" stroke="#7A5AF8" stroke-width="1.8" stroke-linecap="round"/></svg>
            <span>审计日志</span>
          </div>
          <div class="quick-item" @click="$router.push('/admin/knowledge-base')">
            <svg viewBox="0 0 32 32"><path d="M6 6h11a4 4 0 0 1 4 4v16a3 3 0 0 0-3-3H6z" fill="#E6F9F2" stroke="#10B981" stroke-width="1.8" stroke-linejoin="round"/><path d="M26 6h-5a4 4 0 0 0-4 4v16a3 3 0 0 1 3-3h6z" fill="#E6F9F2" stroke="#10B981" stroke-width="1.8" stroke-linejoin="round"/></svg>
            <span>知识库</span>
          </div>
          <div class="quick-item" @click="$router.push('/admin/model-configs')">
            <svg viewBox="0 0 32 32"><rect x="5" y="6" width="22" height="22" rx="4" fill="#FDECEC" stroke="#E53935" stroke-width="1.8"/><path d="M16 11v10M11 16h10" stroke="#E53935" stroke-width="2.2" stroke-linecap="round"/></svg>
            <span>模型配置</span>
          </div>
          <div class="quick-item" @click="$router.push('/admin/data-quality')">
            <svg viewBox="0 0 32 32"><rect x="5" y="8" width="22" height="18" rx="3" fill="#FEF3C7" stroke="#F59E0B" stroke-width="1.8"/><path d="M10 14l3 3 5-5M10 20h12" stroke="#F59E0B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
            <span>数据质量</span>
          </div>
          <div class="quick-item" @click="$router.push('/admin/sensitive-review')">
            <svg viewBox="0 0 32 32"><path d="M16 4l9 4v6c0 6-4 10-9 12-5-2-9-6-9-12V8z" fill="#FDECEC" stroke="#E53935" stroke-width="1.8" stroke-linejoin="round"/><path d="M11 15l3 3 6-7" stroke="#E53935" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <span>敏感审核</span>
          </div>
        </div>
      </div>

      <!-- 右：最新审计日志 -->
      <div class="panel">
        <div class="panel-head">
          <h3>最新审计日志</h3>
          <a class="view-all" @click="$router.push('/admin/audit-logs')">查看全部 →</a>
        </div>
        <div class="audit-list" v-loading="loading">
          <div v-for="log in recentAuditLogs" :key="log.id" class="audit-card">
            <div class="audit-icon" :class="log.role.toLowerCase()">
              {{ actionInitial(log.action) }}
            </div>
            <div class="audit-body">
              <div class="audit-top">
                <strong>{{ log.detail || log.action }}</strong>
                <span class="audit-role" :class="log.role.toLowerCase()">{{ roleLabel(log.role) }}</span>
              </div>
              <p class="audit-sub">
                {{ actionLabel(log.action) }} · {{ log.targetType || '-' }} #{{ log.targetId || '-' }}
              </p>
              <p class="audit-time">{{ formatTime(log.createdAt) }} · IP {{ log.ip || '-' }}</p>
            </div>
          </div>
          <div v-if="!loading && recentAuditLogs.length === 0" class="empty-state-mini">
            <svg viewBox="0 0 120 90" fill="none">
              <rect x="20" y="15" width="80" height="60" rx="8" fill="#F2F6FC"/>
              <rect x="34" y="32" width="40" height="5" rx="2.5" fill="#C9D8EC"/>
              <rect x="34" y="44" width="52" height="4" rx="2" fill="#E1E9F4"/>
              <rect x="34" y="54" width="36" height="4" rx="2" fill="#E1E9F4"/>
            </svg>
            <p>暂无审计日志</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 管理员端首页工作台
 * 职责：从后端 /stats/admin 接口拉取系统级聚合统计与最新审计日志，
 *       渲染欢迎横幅、4 张关键统计卡片、今日运营指标列表、最新审计日志与快捷入口。
 * 数据来源：GET /stats/admin（单次请求聚合，避免多请求）
 */
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { fetchAdminDashboard } from '@/api/stats'

/** 登录用户 store（取姓名） */
const userStore = useUserStore()
/** 欢迎语中的管理员姓名 */
const userName = computed(() => userStore.user?.realName || '管理员')
/** 今日日期 + 中文星期 */
const today = computed(() => {
  const d = new Date()
  const w = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${w}`
})

/** 统计指标（与后端 AdminStats 字段一致） */
const stats = ref({
  totalPatients: 0, totalDoctors: 0, todayAppointments: 0, todayVisits: 0,
  pendingConsults: 0, sensitivePending: 0, todayAuditLogs: 0,
})

/** 最新审计日志列表 */
const recentAuditLogs = ref([])
/** 加载状态 */
const loading = ref(false)

/**
 * Banner 文案：根据待办敏感内容 + 待处理问诊数动态展示
 */
const pendingWorkSummary = computed(() => {
  const arr = []
  if (stats.value.sensitivePending > 0) arr.push(`${stats.value.sensitivePending} 条敏感内容待审核`)
  if (stats.value.pendingConsults > 0) arr.push(`${stats.value.pendingConsults} 个问诊待处理`)
  return arr.length ? arr.join(' · ') : '暂无待办'
})

/**
 * 操作类型 → 首字母（圆形头像占位）
 */
function actionInitial(action) {
  return (action || '?').slice(0, 1).toUpperCase()
}

/**
 * 操作类型 → 中文文案
 */
function actionLabel(action) {
  const map = {
    LOGIN: '登录', LOGOUT: '登出', APPOINT: '挂号', CANCEL: '取消挂号',
    UPLOAD: '上传报告', CONSULT: '发起问诊', AI_CHAT: 'AI 对话',
    PRESCRIPTION: '开具处方', VISIT_STATUS: '推进就诊',
    MEDICAL_NOTE: '病历操作', SENSITIVE: '触发敏感',
  }
  return map[action] || action || '操作'
}

/**
 * 角色码 → 中文标签
 */
function roleLabel(role) {
  return ({ PATIENT: '患者', DOCTOR: '医生', ADMIN: '管理员' })[role] || role || '用户'
}

/**
 * ISO 时间字符串 → 显示用 yyyy-MM-dd HH:mm
 */
function formatTime(iso) {
  if (!iso) return '-'
  const s = String(iso).replace('T', ' ').slice(0, 16)
  return s
}

/**
 * 加载管理员端仪表盘数据
 */
async function loadDashboard() {
  loading.value = true
  try {
    const dash = await fetchAdminDashboard()
    if (dash?.user) userStore.user = { ...userStore.user, ...dash.user }
    if (dash?.stats) stats.value = { ...stats.value, ...dash.stats }
    if (dash?.recentAuditLogs) recentAuditLogs.value = dash.recentAuditLogs
  } catch {
    /* 拦截器已处理 */
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.admin-dashboard { padding: 24px; }

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

/* 今日运营指标列表 */
.metric-list { display: flex; flex-direction: column; gap: 10px; }
.metric-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px; border-radius: 12px;
  background: #F8FBFF; border: 1px solid #E8F1FC; transition: all 0.2s;
}
.metric-row:hover { background: #fff; border-color: #1E7AE6; box-shadow: 0 4px 14px rgba(30,122,230,0.06); }
.m-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.m-icon {
  width: 40px; height: 40px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.m-icon svg { width: 22px; height: 22px; color: #fff; }
.mi-blue   { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); }
.mi-green  { background: linear-gradient(135deg, #10B981, #34D399); }
.mi-purple { background: linear-gradient(135deg, #7A5AF8, #9B7DFB); }
.mi-red    { background: linear-gradient(135deg, #E53935, #F56B68); }
.mi-amber  { background: linear-gradient(135deg, #F59E0B, #FBBF24); }
.m-text { display: flex; flex-direction: column; min-width: 0; }
.m-text strong { font-size: 14px; color: #1a1a2e; }
.m-text span { font-size: 11px; color: #94A3B8; margin-top: 2px; }
.m-num { font-size: 22px; font-weight: 800; color: #1E7AE6; }

/* 快捷入口 */
.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.quick-item {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 16px 8px; border-radius: 14px; background: #F8FBFF;
  border: 1px solid #E8F1FC; cursor: pointer; transition: all 0.2s;
}
.quick-item:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(30,122,230,0.12); background: #fff; }
.quick-item svg { width: 36px; height: 36px; }
.quick-item span { font-size: 13px; color: #334155; font-weight: 500; }

/* 最新审计日志 */
.audit-list { display: flex; flex-direction: column; gap: 10px; }
.audit-card {
  display: flex; gap: 12px; padding: 12px 14px; border-radius: 12px;
  background: #F8FBFF; border: 1px solid #E8F1FC; transition: all 0.2s;
}
.audit-card:hover { background: #fff; border-color: #7A5AF8; }
.audit-icon {
  width: 38px; height: 38px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 15px;
}
.audit-icon.patient { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); }
.audit-icon.doctor  { background: linear-gradient(135deg, #10B981, #34D399); }
.audit-icon.admin   { background: linear-gradient(135deg, #7A5AF8, #9B7DFB); }
.audit-body { flex: 1; min-width: 0; }
.audit-top { display: flex; align-items: center; gap: 8px; justify-content: space-between; margin-bottom: 4px; }
.audit-top strong { font-size: 13px; color: #1a1a2e; }
.audit-role { font-size: 10px; padding: 2px 7px; border-radius: 8px; font-weight: 500; }
.audit-role.patient { background: #DBEAFE; color: #1E40AF; }
.audit-role.doctor  { background: #D1FAE5; color: #065F46; }
.audit-role.admin   { background: #EDE9FE; color: #5B21B6; }
.audit-sub { font-size: 12px; color: #475569; margin: 0 0 2px; }
.audit-time { font-size: 11px; color: #94A3B8; margin: 0; }

.empty-state-mini { text-align: center; padding: 30px 10px; color: #94A3B8; }
.empty-state-mini svg { width: 120px; height: 90px; }
.empty-state-mini p { margin: 8px 0 0; font-size: 13px; }

@media (max-width: 1024px) {
  .welcome-banner { grid-template-columns: 1fr; }
  .wb-illu { justify-content: center; }
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .two-col { grid-template-columns: 1fr; }
  .quick-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>

<template>
  <!--
    挂号详情页（接口驱动）
    数据来源：GET /appointments/{id} → AppointmentVO
    操作：取消挂号（PUT /appointments/{id}/cancel）、查看报告（跳转报告页）
    所有字段均从后端 VO 渲染，刷新页面数据与数据库一致
  -->
  <div class="appt-detail" v-loading="loading">
    <!-- 顶部返回栏 -->
    <div class="detail-header">
      <el-button text class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回挂号列表
      </el-button>
    </div>

    <!-- 详情主体（仅在有数据时渲染） -->
    <template v-if="appt">
      <!-- 状态横幅 -->
      <div :class="['status-banner', `is-${statusKey}`]">
        <div class="sb-left">
          <span class="sb-icon">
            <svg v-if="appt.status === 'PENDING' || appt.status === 'CONFIRMED'" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M12 8v4l3 2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            <svg v-else-if="appt.status === 'FINISHED'" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M8 12l3 3 5-6" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <svg v-else viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M15 9l-6 6M9 9l6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          </span>
          <div class="sb-text">
            <strong>{{ statusInfo.label }}</strong>
            <span>{{ statusInfo.desc }}</span>
          </div>
        </div>
        <div class="sb-right">
          <span class="sb-fee">¥{{ appt.fee }}</span>
          <span class="sb-fee-label">挂号费</span>
        </div>
      </div>

      <!-- 订单信息卡 -->
      <div class="info-card">
        <h3 class="card-title">挂号信息</h3>
        <ul class="info-list">
          <li>
            <span class="i-label">订单编号</span>
            <span class="i-value mono">{{ appt.orderNo }}</span>
          </li>
          <li>
            <span class="i-label">就诊科室</span>
            <span class="i-value">{{ appt.department }}</span>
          </li>
          <li>
            <span class="i-label">接诊医生</span>
            <span class="i-value">{{ appt.doctorName }} <span class="title-tag">{{ appt.doctorTitle }}</span></span>
          </li>
          <li>
            <span class="i-label">就诊时间</span>
            <span class="i-value">{{ formatTime(appt.appointmentTime) }}</span>
          </li>
          <li>
            <span class="i-label">就诊地点</span>
            <span class="i-value">小林智疗互联网医院 · 门诊 {{ appt.department }}候诊区</span>
          </li>
          <li>
            <span class="i-label">创建时间</span>
            <span class="i-value">{{ formatTime(appt.createdAt) }}</span>
          </li>
        </ul>
      </div>

      <!-- 就诊提示卡 -->
      <div class="tips-card">
        <div class="tips-head">
          <el-icon><InfoFilled /></el-icon>
          <span>就诊提示</span>
        </div>
        <ol class="tips-list">
          <li>请提前 15 分钟到院，在自助机或分诊台完成签到候诊；</li>
          <li>如需取消，请在就诊日前一天 24:00 前操作；</li>
          <li>就诊当日请携带本人身份证 / 医保卡。</li>
        </ol>
      </div>

      <!-- 底部操作区（按状态显示不同按钮） -->
      <div class="action-bar" v-if="appt.status === 'PENDING' || appt.status === 'CONFIRMED'">
        <el-button type="danger" size="large" :loading="cancelling" @click="handleCancel">
          取消挂号
        </el-button>
      </div>
      <div class="action-bar" v-else-if="appt.status === 'FINISHED'">
        <el-button type="primary" size="large" @click="$router.push('/patient/reports')">
          查看检查报告
        </el-button>
      </div>
    </template>

    <!-- 空状态（接口无数据） -->
    <div v-if="!loading && !appt" class="empty-state">
      <svg viewBox="0 0 160 120" fill="none">
        <rect x="25" y="20" width="110" height="80" rx="10" fill="#F2F6FC"/>
        <rect x="42" y="40" width="50" height="7" rx="3.5" fill="#C9D8EC"/>
        <rect x="42" y="56" width="76" height="6" rx="3" fill="#E1E9F4"/>
        <rect x="42" y="70" width="60" height="6" rx="3" fill="#E1E9F4"/>
        <circle cx="115" cy="78" r="14" fill="#1E7AE6" opacity="0.12"/>
        <path d="M109 78l4 4 8-9" stroke="#1E7AE6" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <p>该挂号不存在或已被删除</p>
      <el-button type="primary" @click="goBack">返回列表</el-button>
    </div>
  </div>
</template>

<script setup>
/**
 * 挂号详情页
 * 职责：从路由参数取挂号 ID，调 GET /appointments/{id} 拉取真实数据渲染；
 *       取消操作调 PUT /appointments/{id}/cancel，成功后重新拉取并更新页面。
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, InfoFilled } from '@element-plus/icons-vue'
import { fetchAppointmentDetail, cancelAppointment } from '@/api/appointment'

/** 路由实例（取 :id 参数）和路由器（跳转） */
const route = useRoute()
const router = useRouter()

/** 页面加载中状态 */
const loading = ref(true)
/** 取消按钮 loading（防重复提交） */
const cancelling = ref(false)
/** 挂号详情数据（接口返回的 AppointmentVO） */
const appt = ref(null)

/** 状态映射表：后端枚举 → 前端文案与样式 */
const STATUS_MAP = {
  PENDING:   { label: '待确认', desc: '医院将在就诊前确认您的挂号', cls: 'pending' },
  CONFIRMED: { label: '待就诊', desc: '请按时到院就诊', cls: 'confirmed' },
  FINISHED:  { label: '已完成', desc: '本次就诊已结束', cls: 'finished' },
  CANCELLED: { label: '已取消', desc: '该挂号已被取消', cls: 'cancelled' },
}

/** 当前挂号的状态映射对象（模板用） */
const statusInfo = computed(() => STATUS_MAP[appt.value?.status] || STATUS_MAP.PENDING)
/** 状态样式 key（用于 banner class 绑定） */
const statusKey = computed(() => statusInfo.value.cls)

/**
 * 将后端时间字符串（yyyy-MM-dd HH:mm:ss）格式化为中文展示
 * @param {string} str 后端时间字符串
 * @returns {string} 格式化后的中文时间
 */
function formatTime(str) {
  if (!str) return '--'
  const [date, time] = str.split(' ')
  const d = new Date(`${date}T${time || '00:00:00'}`)
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${date}（${week}）${time ? time.slice(0, 5) : ''}`
}

/**
 * 加载挂号详情数据
 */
async function loadDetail() {
  loading.value = true
  try {
    const id = route.params.id
    appt.value = await fetchAppointmentDetail(id)
  } catch {
    appt.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 取消挂号：弹窗确认 → 调接口 → 成功后重新拉取详情
 */
async function handleCancel() {
  try {
    await ElMessageBox.confirm(
      `确定取消 ${formatTime(appt.value.appointmentTime)}「${appt.value.department} · ${appt.value.doctorName}」的挂号吗？`,
      '取消挂号确认',
      { confirmButtonText: '确认取消', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return // 用户点了"再想想"
  }

  cancelling.value = true
  try {
    await cancelAppointment(appt.value.id)
    ElMessage.success('挂号已取消')
    await loadDetail() // 重新拉取，状态热更新
  } finally {
    cancelling.value = false
  }
}

/** 返回挂号列表页 */
function goBack() {
  router.push('/patient/appointments')
}

onMounted(loadDetail)
</script>

<style scoped>
.appt-detail { padding: 24px; max-width: 640px; margin: 0 auto; }

/* 顶部返回栏 */
.detail-header { margin-bottom: 16px; }
.back-btn { color: #5A6B82; font-size: 14px; }
.back-btn:hover { color: #1E7AE6; }

/* 状态横幅 */
.status-banner {
  display: flex; align-items: center; justify-content: space-between;
  border-radius: 16px; padding: 24px 28px; margin-bottom: 20px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
}
.status-banner.is-pending,
.status-banner.is-confirmed { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; }
.status-banner.is-finished { background: linear-gradient(135deg, #10B981, #34D399); color: #fff; }
.status-banner.is-cancelled { background: linear-gradient(135deg, #9CA3AF, #C4B5A0); color: #fff; }

.sb-left { display: flex; align-items: center; gap: 16px; }
.sb-icon { width: 48px; height: 48px; display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.2); border-radius: 50%; }
.sb-icon svg { width: 28px; height: 28px; }
.sb-text { display: flex; flex-direction: column; gap: 4px; }
.sb-text strong { font-size: 20px; font-weight: 700; }
.sb-text span { font-size: 13px; opacity: 0.85; }

.sb-right { text-align: right; }
.sb-fee { font-size: 28px; font-weight: 700; display: block; }
.sb-fee-label { font-size: 12px; opacity: 0.8; }

/* 信息卡 */
.info-card { background: #fff; border: 1px solid #ECF1F7; border-radius: 16px; padding: 24px 28px; margin-bottom: 16px; }
.card-title { font-size: 16px; font-weight: 600; color: #1A2332; margin: 0 0 16px; }

.info-list { list-style: none; padding: 0; margin: 0; }
.info-list li {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 12px 0; border-bottom: 1px solid #F5F7FA;
}
.info-list li:last-child { border-bottom: none; }
.i-label { font-size: 14px; color: #8B95A7; flex-shrink: 0; width: 90px; }
.i-value { font-size: 14px; color: #1A2332; text-align: right; font-weight: 500; }
.i-value.mono { font-family: 'SF Mono', 'Consolas', monospace; letter-spacing: 0.5px; }
.title-tag {
  display: inline-block; padding: 1px 8px; border-radius: 4px;
  background: #E3F2FF; color: #1E7AE6; font-size: 12px; margin-left: 4px;
}

/* 提示卡 */
.tips-card { background: #FFFBF0; border: 1px solid #FFF0C4; border-radius: 12px; padding: 20px 24px; margin-bottom: 24px; }
.tips-head { display: flex; align-items: center; gap: 6px; color: #B8860B; font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.tips-list { padding-left: 20px; margin: 0; }
.tips-list li { font-size: 13px; color: #7C6B3A; line-height: 1.8; }

/* 底部操作区 */
.action-bar { display: flex; justify-content: center; padding: 8px 0 16px; }
.action-bar .el-button { min-width: 200px; }

/* 空状态 */
.empty-state { text-align: center; padding: 60px 0; }
.empty-state svg { width: 160px; height: 120px; }
.empty-state p { color: #8B95A7; font-size: 15px; margin: 16px 0 24px; }
</style>

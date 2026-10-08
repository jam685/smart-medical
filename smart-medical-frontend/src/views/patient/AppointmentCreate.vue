<script setup>
/**
 * 患者端「新建挂号」流程页组件
 * 职责：引导患者完成挂号四步——①选择科室 ②选择医生 ③选择就诊日期与号源 ④确认并提交。
 * 数据来源（真实接口）：
 *   GET  /appointments/doctors 可约医生（科室列表由医生所属科室去重派生）
 *   POST /appointments          创建挂号（写 sm_appointment，后端联动建 sm_visit）
 * 唯一保留的演示数据：号源余量（remainOf），因后端尚无号源表，后续建表后替换。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowRight, Check, User, Location, Clock, Wallet,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { fetchAvailableDoctors, createAppointment } from '@/api/appointment'

/** 路由实例：用于返回挂号列表、提交成功后跳转 */
const router = useRouter()
/** 当前登录患者信息（JWT 登录态），确认页展示就诊人、提交时取患者 ID 由后端从 token 解析 */
const userStore = useUserStore()

// ============================================================
// 一、流程步骤
// ============================================================
/**
 * 步骤条配置数组
 * @property {number} step 步骤序号，从 1 开始
 * @property {string} title 步骤名称
 */
const steps = [
  { step: 1, title: '选择科室' },
  { step: 2, title: '选择医生' },
  { step: 3, title: '选择时间' },
  { step: 4, title: '确认订单' },
]

/** 当前所处步骤（1~4） */
const currentStep = ref(1)

// ============================================================
// 二、科室与医生（均来自后端 /appointments/doctors）
// ============================================================
/** 医生加载状态 */
const doctorLoading = ref(false)

/**
 * 后端返回的全部在线医生原始数组
 * 单项字段：{id, employeeNo, realName, title, department, onlineStatus, fee}
 */
const allDoctors = ref([])

/**
 * 科室图标规则（仅影响卡片图标样式，不是业务数据）
 * 统一蓝色调风格：图标色 #2563EB，背景色 #EFF6FF；
 * 内分泌科沿用参考图的紫色调（#7C3AED / #F3EEFF）以作区分。
 * 按科室名关键词匹配 icon 标识，未匹配时使用十字默认图标。
 */
const DEPT_ICON_RULES = [
  { keywords: ['中医'],     icon: 'acupuncture', color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['儿科'],     icon: 'bottle',      color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['内分泌'],   icon: 'drop',        color: '#7C3AED', bg: '#F3EEFF' },
  { keywords: ['呼吸', '肺'], icon: 'lung',      color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['外科'],     icon: 'surgery',     color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['妇'],       icon: 'female',      color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['急诊'],     icon: 'plus',        color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['皮肤'],     icon: 'syringe',     color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['眼'],       icon: 'eye',         color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['神经'],     icon: 'brain',       color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['骨'],       icon: 'plus',        color: '#2563EB', bg: '#EFF6FF' },
  { keywords: ['心'],       icon: 'heart',       color: '#2563EB', bg: '#EFF6FF' },
]

/**
 * 根据科室名匹配图标标识与配色
 * @param {string} name 科室名
 * @returns {{icon: string, color: string, bg: string}}
 */
function iconOfDept(name) {
  const hit = DEPT_ICON_RULES.find(r => r.keywords.some(k => (name || '').includes(k)))
  return hit ? { icon: hit.icon, color: hit.color, bg: hit.bg }
            : { icon: 'plus', color: '#2563EB', bg: '#EFF6FF' }
}

/**
 * 医生头像背景色（按性别区分，女医生粉紫、男医生蓝色，未知灰色）
 * 头像无 URL 时用姓名首字母 + 此背景色渲染占位圆。
 * @param {string} gender 性别
 * @returns {string} CSS 背景色
 */
function avatarBg(gender) {
  if (gender === '女') return 'linear-gradient(135deg, #EC4899, #F472B6)'
  if (gender === '男') return 'linear-gradient(135deg, #1E7AE6, #4A9EF5)'
  return 'linear-gradient(135deg, #6B7280, #9CA3AF)'
}

/**
 * 取医生姓名首字（头像占位文字）
 * @param {string} name 医生姓名
 * @returns {string} 首字
 */
function initialOf(name) {
  return (name || '医').slice(0, 1)
}

/**
 * 科室列表（响应式）：对后端医生按 department 去重派生
 * @returns {Array<{key:string, name:string, desc:string, icon:string, color:string}>}
 */
const departments = computed(() => {
  const map = new Map()
  allDoctors.value.forEach(d => {
    if (!d.department || map.has(d.department)) return
    map.set(d.department, { key: d.department, name: d.department, desc: '', ...iconOfDept(d.department) })
  })
  return [...map.values()]
})

/** 患者已选科室对象（未选为 null） */
const selectedDept = ref(null)

/** 患者已选医生对象（接口原始结构，未选为 null） */
const selectedDoctor = ref(null)

/**
 * 当前所选科室下的可约医生（响应式）：用科室名过滤后端医生数据
 */
const doctorsOfDept = computed(() =>
  allDoctors.value.filter(d => d.department === selectedDept.value?.name)
)

/**
 * 统计指定科室的可约医生数量（科室卡片副文案）
 * @param {string} deptName 科室名
 * @returns {number} 医生人数
 */
function doctorsCount(deptName) {
  return allDoctors.value.filter(d => d.department === deptName).length
}

// ============================================================
// 三、日期与号源
// ============================================================
const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/**
 * 生成从今天起连续 7 天的日期选项
 * @returns {Array<{date:string, day:number, month:number, week:string}>}
 */
function buildDateOptions() {
  const list = []
  const today = new Date()
  for (let i = 0; i < 7; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() + i)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    list.push({
      date: `${y}-${m}-${day}`,
      day: d.getDate(),
      month: d.getMonth() + 1,
      week: i === 0 ? '今天' : (i === 1 ? '明天' : WEEK_LABELS[d.getDay()]),
    })
  }
  return list
}

/** 未来 7 天日期选项（进入页面时固定） */
const dateOptions = buildDateOptions()

/** 已选日期下标（-1 未选） */
const selectedDateIndex = ref(-1)

/**
 * 号源时段模板：上午/下午各 6 个半小时号段
 */
const slotGroups = [
  { period: '上午', times: ['08:00 - 08:30', '08:30 - 09:00', '09:00 - 09:30', '09:30 - 10:00', '10:00 - 10:30', '10:30 - 11:00'] },
  { period: '下午', times: ['14:00 - 14:30', '14:30 - 15:00', '15:00 - 15:30', '15:30 - 16:00', '16:00 - 16:30', '16:30 - 17:00'] },
]

/** 已选号源时段字符串 */
const selectedSlot = ref('')

/**
 * 号源剩余数量（⚠️ 演示用前端规则生成：后端尚无号源表，建表后改为接口数据）
 * @param {number} dateIndex 日期下标
 * @param {string} time 号源时间段
 * @returns {number} 0~8，0 表示号满
 */
function remainOf(dateIndex, time) {
  let hash = dateIndex * 7
  for (const ch of time) hash += ch.charCodeAt(0)
  return hash % 9
}

// ============================================================
// 四、步骤导航与提交
// ============================================================
/**
 * 当前步是否允许进入下一步（响应式校验）
 */
const canNext = computed(() => {
  if (currentStep.value === 1) return !!selectedDept.value
  if (currentStep.value === 2) return !!selectedDoctor.value
  if (currentStep.value === 3) return selectedDateIndex.value !== -1 && !!selectedSlot.value
  return false
})

/**
 * 选择科室（切换科室时清空已选医生）
 * @param {object} dept 科室对象
 */
function chooseDept(dept) {
  selectedDept.value = dept
  selectedDoctor.value = null
}

/**
 * 双击科室：选中并直接进入下一步
 * @param {object} dept 科室对象
 */
function chooseDeptAndNext(dept) {
  chooseDept(dept)
  nextStep()
}

/**
 * 选择医生
 * @param {object} doc 医生对象（接口原始结构）
 */
function chooseDoctor(doc) {
  selectedDoctor.value = doc
}

/**
 * 选择日期（切换日期清空已选号源）
 * @param {number} index 日期下标
 */
function chooseDate(index) {
  selectedDateIndex.value = index
  selectedSlot.value = ''
}

/**
 * 选择号源
 * @param {string} time 号源时间段
 */
function chooseSlot(time) {
  selectedSlot.value = time
}

/** 进入下一步 */
function nextStep() {
  if (canNext.value) currentStep.value += 1
}

/** 返回上一步 */
function prevStep() {
  if (currentStep.value > 1) currentStep.value -= 1
}

/** 返回挂号列表 */
function goBack() {
  router.push('/patient/appointments')
}

/** 就诊人姓名：取自登录态 realName */
const patientName = computed(() => userStore.user?.realName || '就诊人')

/** 已选日期对象（未选为 null） */
const chosenDate = computed(() =>
  selectedDateIndex.value === -1 ? null : dateOptions[selectedDateIndex.value]
)

/** 提交按钮 loading（防重复提交） */
const submitting = ref(false)

/**
 * 提交挂号：调用真实 POST /appointments，成功后跳回列表（列表页重新拉取即可见新单）
 */
async function submitOrder() {
  submitting.value = true
  try {
    // 载荷字段与后端 CreateAppointmentRequest 对齐；患者 ID 由后端从 JWT 解析，无需前端传
    const payload = {
      doctorId: selectedDoctor.value.id,
      department: selectedDept.value.name,
      appointmentTime: `${chosenDate.value.date} ${selectedSlot.value.split(' - ')[0]}:00`,
    }
    await createAppointment(payload)
    ElMessage.success('挂号成功！请按时前往医院就诊')
    router.push('/patient/appointments')
  } catch (e) {
    // 失败提示由请求拦截器统一处理（如时间已过、医生不存在）
  } finally {
    submitting.value = false
  }
}

/**
 * 页面加载时请求可约医生（科室列表由其去重派生）
 */
onMounted(async () => {
  doctorLoading.value = true
  try {
    allDoctors.value = await fetchAvailableDoctors()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    doctorLoading.value = false
  }
})
</script>

<template>
  <div class="create-page">
    <!-- 顶部返回栏 -->
    <div class="top-bar">
      <el-button text class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回挂号列表
      </el-button>
      <h2 class="page-title brand-font">预约挂号</h2>
      <span class="top-placeholder"></span>
    </div>

    <!-- 步骤条 -->
    <div class="stepper">
      <template v-for="(s, idx) in steps" :key="s.step">
        <div :class="['step-item', { active: currentStep >= s.step, current: currentStep === s.step }]">
          <span class="step-dot">
            <el-icon v-if="currentStep > s.step"><Check /></el-icon>
            <span v-else>{{ s.step }}</span>
          </span>
          <span class="step-label">{{ s.title }}</span>
        </div>
        <div v-if="idx < steps.length - 1" :class="['step-line', { done: currentStep > s.step }]"></div>
      </template>
    </div>

    <!-- ========== 第一步：选择科室 ========== -->
    <div v-show="currentStep === 1" class="step-panel">
      <p class="panel-hint">请选择您要就诊的科室</p>
      <div class="dept-grid">
        <div
          v-for="d in departments" :key="d.key"
          :class="['dept-card', { selected: selectedDept?.key === d.key }]"
          @click="chooseDept(d)"
          @dblclick="chooseDeptAndNext(d)"
        >
          <span class="dept-icon" :style="{ background: d.bg, color: d.color }">
            <!-- 中医科：针灸针+艾草 -->
            <svg v-if="d.icon === 'acupuncture'" viewBox="0 0 24 24" fill="none"><path d="M12 3v11" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M9 6l3-3 3 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/><path d="M12 14c-2 1.5-2 4 0 5.5 2-1.5 2-4 0-5.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
            <!-- 儿科：婴儿奶瓶 -->
            <svg v-else-if="d.icon === 'bottle'" viewBox="0 0 24 24" fill="none"><path d="M9 3h6M10 3v3M14 3v3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M8 6h8v2.5c0 1.4-1 2.5-2 2.8V19a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-7.7c-1-.3-2-1.4-2-2.8V6Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M9 13h6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
            <!-- 心内科：心形 -->
            <svg v-else-if="d.icon === 'heart'" viewBox="0 0 24 24" fill="none"><path d="M12 20s-7-4.6-9.2-9C1.3 8 2.7 4.8 6 4.8c2 0 3.2 1.2 4 2.4.8-1.2 2-2.4 4-2.4 3.3 0 4.7 3.2 3.2 6.2C19 15.4 12 20 12 20Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
            <!-- 呼吸科：肺 -->
            <svg v-else-if="d.icon === 'lung'" viewBox="0 0 24 24" fill="none"><path d="M12 4v9M12 7c-2-2-5-2-6 .5-1 2.5 0 8 .5 9.5.5 1.5 3.5 1.5 4 0 .5-1.6.5-6.5.5-8M12 7c2-2 5-2 6 .5 1 2.5 0 8-.5 9.5-.5 1.5-3.5 1.5-4 0-.5-1.6-.5-6.5-.5-8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            <!-- 内分泌科：水滴 -->
            <svg v-else-if="d.icon === 'drop'" viewBox="0 0 24 24" fill="none"><path d="M12 3s6 7 6 11a6 6 0 0 1-12 0c0-4 6-11 6-11Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></svg>
            <!-- 外科：手术刀 -->
            <svg v-else-if="d.icon === 'surgery'" viewBox="0 0 24 24" fill="none"><path d="M4 18l5-5 2 2-5 5H4v-2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M9 13l4-4 2 2-4 4" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M13 9l4.5-4.5c.7-.7 1.8-.3 1.8.7L19 8l-4 1-2 0Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
            <!-- 妇科：女性符号 -->
            <svg v-else-if="d.icon === 'female'" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="9" r="4" stroke="currentColor" stroke-width="1.7"/><path d="M12 13v7M9 17h6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
            <!-- 急诊科/骨科：十字加号 -->
            <svg v-else-if="d.icon === 'plus'" viewBox="0 0 24 24" fill="none"><rect x="4" y="4" width="16" height="16" rx="3" stroke="currentColor" stroke-width="1.7"/><path d="M12 8v8M8 12h8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            <!-- 皮肤科：注射器 -->
            <svg v-else-if="d.icon === 'syringe'" viewBox="0 0 24 24" fill="none"><path d="M18 2l4 4-2 2-4-4 2-2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M16 4l-8 8-2 4 4-2 8-8" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M14 6l2 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            <!-- 眼科：眼睛 -->
            <svg v-else-if="d.icon === 'eye'" viewBox="0 0 24 24" fill="none"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.6" stroke="currentColor" stroke-width="1.7"/></svg>
            <!-- 神经外科：大脑 -->
            <svg v-else viewBox="0 0 24 24" fill="none"><path d="M9 4a3 3 0 0 0-3 3 3 3 0 0 0-2 5 3 3 0 0 0 1 5 3 3 0 0 0 4 1V4Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M15 4a3 3 0 0 1 3 3 3 3 0 0 1 2 5 3 3 0 0 1-1 5 3 3 0 0 1-4 1V4Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
          </span>
          <span class="dept-name">{{ d.name }}</span>
          <span class="dept-desc">{{ doctorsCount(d.key) }} 位医生可约</span>
          <span v-if="selectedDept?.key === d.key" class="dept-check"><el-icon><Check /></el-icon></span>
        </div>
      </div>
    </div>

    <!-- ========== 第二步：选择医生 ========== -->
    <div v-show="currentStep === 2" class="step-panel">
      <p class="panel-hint">{{ selectedDept?.name }} · 请选择接诊医生</p>
      <div class="doctor-list" v-loading="doctorLoading">
        <div
          v-for="doc in doctorsOfDept" :key="doc.id"
          :class="['doctor-card', { selected: selectedDoctor?.id === doc.id }]"
          @click="chooseDoctor(doc)"
        >
          <!-- 头像：有 avatar URL 用图片，否则用姓名首字 + 性别配色 -->
          <span class="doc-avatar" :style="{ background: avatarBg(doc.gender) }">
            <img v-if="doc.avatar" :src="doc.avatar" :alt="doc.realName" class="doc-avatar-img" />
            <span v-else class="doc-avatar-text">{{ initialOf(doc.realName) }}</span>
            <i class="online-dot"></i>
          </span>
          <!-- 医生核心信息 -->
          <div class="doc-meta">
            <div class="doc-name-line">
              <strong>{{ doc.realName }}</strong>
              <span class="doc-title">{{ doc.title }}</span>
              <span class="doc-gender" v-if="doc.gender">{{ doc.gender }}</span>
            </div>
            <div class="doc-id-line">
              <span class="doc-id-tag">工号 {{ doc.employeeNo }}</span>
              <span class="doc-dept-tag">{{ doc.department }}</span>
              <span class="doc-online">
                <i class="online-dot-sm"></i>在线可约
              </span>
            </div>
            <!-- 简介/成就（来自后端 bio 字段） -->
            <p class="doc-bio" v-if="doc.bio">{{ doc.bio }}</p>
            <p class="doc-bio empty" v-else>暂无简介</p>
          </div>
          <!-- 右侧挂号费 -->
          <div class="doc-fee">
            <span class="fee-num">¥{{ doc.fee }}</span>
            <span class="fee-label">挂号费</span>
          </div>
        </div>
        <!-- 该科室暂无可约医生时的空提示 -->
        <div v-if="!doctorLoading && doctorsOfDept.length === 0" class="no-doctor">
          该科室暂无可约医生，请选择其他科室
        </div>
      </div>
    </div>

    <!-- ========== 第三步：选择日期与号源 ========== -->
    <div v-show="currentStep === 3" class="step-panel">
      <p class="panel-hint">选择就诊日期</p>
      <!-- 日期横条 -->
      <div class="date-row">
        <div
          v-for="(d, i) in dateOptions" :key="d.date"
          :class="['date-item', { active: selectedDateIndex === i }]"
          @click="chooseDate(i)"
        >
          <span class="date-week">{{ d.week }}</span>
          <span class="date-day">{{ d.month }}/{{ d.day }}</span>
        </div>
      </div>

      <p v-if="selectedDateIndex !== -1" class="panel-hint sub">选择号源时段</p>
      <!-- 号源时段 -->
      <div v-if="selectedDateIndex !== -1" class="slot-groups">
        <div v-for="g in slotGroups" :key="g.period" class="slot-group">
          <div class="slot-group-title">
            <el-icon><Clock /></el-icon>{{ g.period }}
          </div>
          <div class="slot-row">
            <button
              v-for="t in g.times" :key="t"
              :class="['slot-btn', { active: selectedSlot === t, full: remainOf(selectedDateIndex, t) === 0 }]"
              :disabled="remainOf(selectedDateIndex, t) === 0"
              @click="chooseSlot(t)"
            >
              <span class="slot-time">{{ t }}</span>
              <span class="slot-remain">
                {{ remainOf(selectedDateIndex, t) === 0 ? '号满' : `剩 ${remainOf(selectedDateIndex, t)}` }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 第四步：确认订单 ========== -->
    <div v-show="currentStep === 4" class="step-panel">
      <div class="confirm-card">
        <h3 class="confirm-title">订单信息确认</h3>
        <ul class="confirm-list">
          <li><span class="c-label"><el-icon><User /></el-icon>就诊人</span><span class="c-value">{{ patientName }}</span></li>
          <li><span class="c-label">就诊科室</span><span class="c-value">{{ selectedDept?.name }}</span></li>
          <li><span class="c-label">接诊医生</span><span class="c-value">{{ selectedDoctor?.realName }} · {{ selectedDoctor?.title }}</span></li>
          <li>
            <span class="c-label"><el-icon><Clock /></el-icon>就诊时间</span>
            <span class="c-value">{{ chosenDate?.date }}（{{ chosenDate?.week }}）{{ selectedSlot }}</span>
          </li>
          <li>
            <span class="c-label"><el-icon><Location /></el-icon>就诊地点</span>
            <span class="c-value">小林智疗互联网医院 · 门诊 {{ selectedDept?.name }}候诊区（以当日分诊屏为准）</span>
          </li>
          <li>
            <span class="c-label"><el-icon><Wallet /></el-icon>挂号费用</span>
            <span class="c-value fee">¥{{ selectedDoctor?.fee }}.00（现场支付）</span>
          </li>
        </ul>
        <!-- 温馨提示 -->
        <div class="tips-box">
          <p>就诊提示：</p>
          <ol>
            <li>请提前 15 分钟到院，在自助机或分诊台完成签到候诊；</li>
            <li>如需取消，请在就诊日前一天 24:00 前于「我的挂号」中操作；</li>
            <li>就诊当日请携带本人身份证/医保卡。</li>
          </ol>
        </div>
      </div>
    </div>

    <!-- 底部导航按钮 -->
    <div class="footer-bar">
      <el-button v-if="currentStep > 1" size="large" class="foot-btn" @click="prevStep">上一步</el-button>
      <el-button v-else size="large" class="foot-btn" @click="goBack">取消</el-button>
      <el-button
        v-if="currentStep < 4" type="primary" size="large" class="foot-btn primary"
        :disabled="!canNext" @click="nextStep"
      >
        下一步<el-icon class="el-icon--right"><ArrowRight /></el-icon>
      </el-button>
      <el-button
        v-else type="primary" size="large" class="foot-btn primary"
        :loading="submitting" @click="submitOrder"
      >
        确认挂号
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.create-page { padding: 4px 2px 90px; }

/* 顶部栏 */
.top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.back-btn { font-size: 14px; color: #5A6B82; }
.page-title { font-size: 22px; color: #1A2B45; margin: 0; }
.top-placeholder { width: 110px; }

/* 步骤条 */
.stepper { display: flex; align-items: center; justify-content: center; margin: 6px 0 26px; }
.step-item { display: flex; align-items: center; gap: 9px; }
.step-dot {
  width: 30px; height: 30px; border-radius: 50%; background: #E8EEF6; color: #8492A6;
  display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700;
  transition: all 0.25s;
}
.step-label { font-size: 14px; color: #8492A6; font-weight: 500; }
.step-item.active .step-dot { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; box-shadow: 0 4px 12px rgba(30,122,230,0.35); }
.step-item.active .step-label { color: #1E7AE6; font-weight: 700; }
.step-item.current .step-dot { transform: scale(1.12); }
.step-line { width: 70px; height: 2px; background: #E8EEF6; margin: 0 14px; border-radius: 2px; transition: background 0.25s; }
.step-line.done { background: #1E7AE6; }

.panel-hint { font-size: 15px; font-weight: 600; color: #2C3E50; margin: 0 0 14px; }
.panel-hint.sub { margin-top: 22px; }

/* 第一步：科室卡片 */
.dept-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.dept-card {
  position: relative; background: #fff; border: 1.5px solid #ECF1F7; border-radius: 14px;
  padding: 18px; display: flex; flex-direction: column; gap: 8px; cursor: pointer;
  transition: all 0.22s;
}
.dept-card:hover { transform: translateY(-3px); border-color: #BFD9F7; box-shadow: 0 10px 24px rgba(30,122,230,0.1); }
.dept-card.selected { border-color: #1E7AE6; box-shadow: 0 8px 22px rgba(30,122,230,0.16); background: #F7FBFF; }
.dept-icon { width: 50px; height: 50px; border-radius: 14px; display: flex; align-items: center; justify-content: center; }
.dept-icon svg { width: 26px; height: 26px; }
.dept-name { font-size: 16px; font-weight: 700; color: #1A2B45; }
.dept-desc { font-size: 12.5px; color: #8492A6; }
.dept-check {
  position: absolute; top: 12px; right: 12px; width: 22px; height: 22px; border-radius: 50%;
  background: #1E7AE6; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px;
}

/* 第二步：医生卡片 */
.doctor-list { display: flex; flex-direction: column; gap: 12px; }
.no-doctor { text-align: center; color: #A7B1BF; font-size: 13px; padding: 26px 0; background: #FAFCFE; border: 1px dashed #E2EAF4; border-radius: 12px; }
.doctor-card {
  display: flex; align-items: center; gap: 16px; background: #fff;
  border: 1.5px solid #ECF1F7; border-radius: 14px; padding: 16px 20px; cursor: pointer; transition: all 0.22s;
}
.doctor-card:hover { border-color: #BFD9F7; box-shadow: 0 8px 20px rgba(30,122,230,0.09); transform: translateY(-2px); }
.doctor-card.selected { border-color: #1E7AE6; background: #F7FBFF; box-shadow: 0 8px 22px rgba(30,122,230,0.15); }
.doc-avatar {
  position: relative; width: 60px; height: 60px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; overflow: hidden; border: 2px solid #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
}
.doc-avatar-img { width: 100%; height: 100%; object-fit: cover; }
.doc-avatar-text { color: #fff; font-size: 24px; font-weight: 700; }
.online-dot { position: absolute; right: -1px; bottom: -1px; width: 14px; height: 14px; border-radius: 50%; background: #22C55E; border: 2px solid #fff; }
.doc-meta { flex: 1; min-width: 0; }
.doc-name-line { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.doc-name-line strong { font-size: 17px; color: #1A2B45; font-weight: 700; }
.doc-title { font-size: 12px; color: #1E7AE6; background: #E3F2FF; padding: 2px 9px; border-radius: 100px; font-weight: 600; }
.doc-gender { font-size: 12px; color: #6B7280; background: #F3F4F6; padding: 2px 8px; border-radius: 100px; }
.doc-id-line { display: flex; align-items: center; gap: 8px; margin: 6px 0; flex-wrap: wrap; }
.doc-id-tag { font-size: 12px; color: #4B5563; background: #F9FAFB; padding: 2px 8px; border-radius: 6px; font-family: 'SF Mono','Consolas',monospace; }
.doc-dept-tag { font-size: 12px; color: #1E7AE6; }
.doc-online { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #10B981; }
.online-dot-sm { width: 8px; height: 8px; border-radius: 50%; background: #22C55E; }
.doc-bio {
  font-size: 12.5px; color: #6B7280; margin: 0; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.doc-bio.empty { color: #C5CBD6; font-style: italic; }
.doc-fee { text-align: right; flex-shrink: 0; }
.fee-num { font-size: 22px; font-weight: 800; color: #E53935; }
.fee-label { display: block; font-size: 11px; color: #A7B1BF; }

/* 第三步：日期与号源 */
.date-row { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 6px; }
.date-item {
  min-width: 76px; padding: 12px 8px; border-radius: 12px; background: #fff;
  border: 1.5px solid #ECF1F7; text-align: center; cursor: pointer; transition: all 0.2s;
}
.date-item:hover { border-color: #BFD9F7; }
.date-item.active { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); border-color: transparent; box-shadow: 0 6px 14px rgba(30,122,230,0.3); }
.date-week { display: block; font-size: 12px; color: #8492A6; margin-bottom: 4px; }
.date-day { font-size: 15px; font-weight: 700; color: #1A2B45; }
.date-item.active .date-week, .date-item.active .date-day { color: #fff; }

.slot-groups { display: flex; flex-direction: column; gap: 18px; }
.slot-group-title { display: flex; align-items: center; gap: 6px; font-size: 13.5px; font-weight: 600; color: #4A5A70; margin-bottom: 10px; }
.slot-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.slot-btn {
  border: 1.5px solid #E2EAF4; background: #fff; border-radius: 10px; padding: 10px 6px;
  cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 3px; transition: all 0.18s;
}
.slot-btn:hover:not(:disabled) { border-color: #1E7AE6; background: #F7FBFF; }
.slot-btn.active { border-color: #1E7AE6; background: linear-gradient(135deg, #1E7AE6, #4A9EF5); box-shadow: 0 5px 12px rgba(30,122,230,0.28); }
.slot-btn.active .slot-time, .slot-btn.active .slot-remain { color: #fff; }
.slot-btn.full { background: #F6F8FB; cursor: not-allowed; opacity: 0.7; }
.slot-time { font-size: 13.5px; font-weight: 600; color: #2C3E50; }
.slot-remain { font-size: 11px; color: #10B981; }
.slot-btn.full .slot-time, .slot-btn.full .slot-remain { color: #A7B1BF; }

/* 第四步：确认订单 */
.confirm-card { background: #fff; border: 1px solid #ECF1F7; border-radius: 16px; padding: 24px 28px; max-width: 720px; margin: 0 auto; }
.confirm-title { font-size: 18px; color: #1A2B45; margin: 0 0 16px; padding-bottom: 14px; border-bottom: 1px dashed #E2EAF4; }
.confirm-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 14px; }
.confirm-list li { display: flex; align-items: flex-start; font-size: 14px; }
.c-label { width: 110px; flex-shrink: 0; color: #8492A6; display: inline-flex; align-items: center; gap: 5px; }
.c-value { color: #1A2B45; font-weight: 500; }
.c-value.fee { color: #E53935; font-weight: 700; font-size: 16px; }
.tips-box { margin-top: 20px; background: #F7FBFF; border: 1px solid #DCEBFB; border-radius: 12px; padding: 14px 18px; }
.tips-box p { margin: 0 0 6px; font-size: 13px; font-weight: 700; color: #1E7AE6; }
.tips-box ol { margin: 0; padding-left: 18px; color: #5A6B82; font-size: 12.5px; line-height: 1.9; }

/* 底部按钮栏 */
.footer-bar {
  position: fixed; left: 220px; right: 0; bottom: 0; z-index: 50;
  background: rgba(255,255,255,0.92); backdrop-filter: blur(8px);
  border-top: 1px solid #ECF1F7; padding: 14px 28px; display: flex; justify-content: center; gap: 14px;
}
.foot-btn { min-width: 130px; border-radius: 10px; }
.foot-btn.primary { box-shadow: 0 6px 16px rgba(30,122,230,0.28); }

@media (max-width: 1100px) {
  .dept-grid { grid-template-columns: repeat(2, 1fr); }
  .slot-row { grid-template-columns: repeat(2, 1fr); }
  .footer-bar { left: 0; }
}
</style>

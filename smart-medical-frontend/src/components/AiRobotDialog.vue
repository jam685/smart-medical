<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { Close, Minus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import doctorFemale from '@/assets/doctor-female.png'
import { streamAiChatMessage } from '@/api/aiagent'
import { useUserStore } from '@/stores/user'

// ============== 状态 ==============
const visible = ref(false)
const minimized = ref(false)
const hidden = ref(false)
const input = ref('')
const chatBodyRef = ref(null)

/** 用户态（登录身份/Token） */
const userStore = useUserStore()
/** 路由实例（卡片"查看详情"跳转用） */
const router = useRouter()
/** 发送中标记（防重复提交） */
const sending = ref(false)

/** 当前登录角色（PATIENT/DOCTOR，未登录为空） */
const role = computed(() => userStore.user?.role || '')

/** 是否医生端小林（医生端只做就诊进度查询） */
const isDoctor = computed(() => role.value === 'DOCTOR')

/** 客服会话 ID（按账号 + 角色隔离持久化，与预问诊会话完全独立） */
const sessionId = ref(localStorage.getItem(sessionKey()) || '')

/** 对话消息（按角色在挂载时初始化开场白） */
const messages = reactive([])

/** 快捷提问（患者端业务全开，医生端只做就诊进度） */
const quickReplies = computed(() => isDoctor.value
  ? ['查询患者就诊进度', '查看今天的就诊进度', '查看本周的就诊进度']
  : ['查询就诊进度', '帮我预约挂号', '解读我的报告', '查看电子病历'])

/** 输入框占位文案（按角色区分） */
const inputPlaceholder = computed(() => isDoctor.value
  ? '向数字医生小林查询患者就诊进度...'
  : '向数字医生小林提问...')

/**
 * 客服会话 localStorage Key（按账号 + 角色隔离，避免多账号/多角色串会话）
 * @returns {string} 存储 Key
 */
function sessionKey() {
  const id = userStore.user?.id || 'guest'
  return isDoctor.value ? `ai_ds_sid_${id}` : `ai_cs_sid_${id}`
}

/**
 * 初始化开场白（患者端介绍全部业务能力；医生端只介绍就诊进度查询）
 */
function initMessages() {
  messages.splice(0, messages.length)
  if (isDoctor.value) {
    messages.push({ side: 'ai', text: '您好！我是数字医生小林 👨‍⚕️✨' })
    messages.push({ side: 'ai', text: '我可以帮您查询患者就诊进度：告诉我患者姓名和时间段即可，例如「帮我查一下张伟今天上午的就诊进度」。' })
  } else {
    messages.push({ side: 'ai', text: '您好呀！我是数字医生小林 👨‍⚕️✨' })
    messages.push({ side: 'ai', text: '很高兴认识您！我可以帮您：查询就诊进度、挂号指引、报告查询等。请问有什么需要帮忙的？ 😊' })
  }
}

/**
 * 卡片按钮跳转（可携带查询条件，如接诊管理列表的患者/时间筛选）
 * @param {string} path 目标路由
 * @param {object} [query] 查询参数
 */
function go(path, query) {
  visible.value = false
  if (query && Object.keys(query).length) {
    router.push({ path, query })
  } else {
    router.push(path)
  }
}

/**
 * 医生端进度卡片的跳转筛选条件（患者姓名 + 查询时间区间）
 * @param {object} card 医生端进度卡片
 * @returns {object} 路由 query
 */
function progressQuery(card) {
  const q = {}
  if (card.patientName) q.patientName = card.patientName
  if (card.dateFrom) q.dateFrom = card.dateFrom
  if (card.dateTo) q.dateTo = card.dateTo
  return q
}

/**
 * 就诊状态 → 状态徽章样式类（候诊蓝 / 叫号橙 / 就诊中绿 / 已完成灰）
 * @param {string} status 状态码
 * @returns {string} 样式类名
 */
function statusClass(status) {
  return {
    WAITING: 'st-waiting',
    CALLING: 'st-calling',
    IN_PROGRESS: 'st-progress',
    FINISHED: 'st-finished',
  }[status] || 'st-finished'
}

/**
 * 报告状态 → 报告卡徽章样式类（已上传橙 / 已解读蓝 / 已签名绿 / 其他灰）
 * @param {string} status 报告状态码（UPLOADED/ANALYZED/SIGNED）
 * @returns {string} 样式类名
 */
function reportStatusClass(status) {
  return {
    UPLOADED: 'st-uploaded',
    ANALYZED: 'st-analyzed',
    SIGNED: 'st-signed',
  }[status] || 'st-finished'
}

/**
 * 报告卡片是否有内容（报告或处方任一非空），用于空态提示
 * @param {object} card 报告卡片数据
 * @returns {boolean} true=有内容
 */
function hasReportItems(card) {
  return (card.reports && card.reports.length > 0) || (card.prescriptions && card.prescriptions.length > 0)
}

// 位置
const pos = reactive({ x: 0, y: 0 })
const panelPos = reactive({ x: 0, y: 0 })

/** 弹窗宽高（放大便于患者阅读，需与下方样式中的 width / chat-inner 高度保持一致） */
const PANEL_W = 560
const PANEL_H = 710

function clamp(n, min, max) { return Math.max(min, Math.min(n, max)) }

// ============== 数字人拖拽（超简单版） ==============
let isDragging = false
let movedFar = false
let downX = 0, downY = 0, downPosX = 0, downPosY = 0

function onDown(e) {
  downX = e.clientX; downY = e.clientY
  downPosX = pos.x; downPosY = pos.y
  isDragging = true; movedFar = false
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}
function onMove(e) {
  if (!isDragging) return
  const dx = e.clientX - downX
  const dy = e.clientY - downY
  if (Math.hypot(dx, dy) > 5) movedFar = true
  const w = window.innerWidth, h = window.innerHeight
  const nx = clamp(downPosX + dx, -40, w - 40)
  const ny = clamp(downPosY + dy, 40, h - 110)
  pos.x = nx; pos.y = ny
}
function onUp() {
  isDragging = false
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerup', onUp)
  // 没怎么移动才算 click
  if (!movedFar) toggle()
}

// ============== 对话框拖拽 ==============
let pDragging = false
function onPanelDown(e) {
  const sx = panelPos.x, sy = panelPos.y
  const px = e.clientX, py = e.clientY
  pDragging = true
  function mv(ev) {
    if (!pDragging) return
    panelPos.x = clamp(sx + ev.clientX - px, 0, window.innerWidth - PANEL_W)
    panelPos.y = clamp(sy + ev.clientY - py, 0, window.innerHeight - PANEL_H)
  }
  function up() {
    pDragging = false
    window.removeEventListener('pointermove', mv)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', mv)
  window.addEventListener('pointerup', up)
}

// ============== 其他 ==============
function toggle() { visible.value = !visible.value; minimized.value = false; scrollToBottom() }
function scrollToBottom() { nextTick(() => { if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight }) }
/**
 * 发送消息：调用后端数字医生小林流式接口（患者 source=assistant → cs_ 会话；
 * 医生 source=doctor_assistant → ds_ 会话），增量文本实时渲染，done 事件回填结构化卡片。
 * 未登录或不支持的角色不做真实调用，给出引导文案。
 */
async function handleSend() {
  const t = input.value.trim()
  if (!t || sending.value) return
  messages.push({ side: 'user', text: t })
  input.value = ''
  scrollToBottom()
  if (!userStore.token || !['PATIENT', 'DOCTOR'].includes(role.value)) {
    messages.push({ side: 'ai', text: '请先登录患者或医生账号后再使用数字医生小林。' })
    scrollToBottom()
    return
  }
  const source = isDoctor.value ? 'doctor_assistant' : 'assistant'
  const aiMsg = reactive({ side: 'ai', text: '', typing: true, card: null })
  messages.push(aiMsg)
  sending.value = true
  scrollToBottom()
  try {
    await streamAiChatMessage(
      { sessionId: sessionId.value || undefined, content: t, source },
      {
        // 增量文本：逐字追加（业务类回复无 token，靠 done 回填）
        onToken: (delta) => {
          aiMsg.typing = false
          aiMsg.text += delta
          scrollToBottom()
        },
        // 流转完成：持久化会话 ID + 回填回复与卡片
        onDone: (data) => {
          aiMsg.typing = false
          if (data.sessionId) {
            sessionId.value = data.sessionId
            localStorage.setItem(sessionKey(), data.sessionId)
          }
          if (!aiMsg.text && data.reply) {
            aiMsg.text = data.reply
          }
          aiMsg.card = data.card || null
          scrollToBottom()
        },
        // 失败降级：给出友好提示，不阻塞后续提问
        onError: (err) => {
          aiMsg.typing = false
          if (!aiMsg.text) {
            aiMsg.text = `抱歉，我暂时没能连上服务（${err.message}）。您可以稍后再试，或前往对应功能页面办理。`
          }
          scrollToBottom()
        },
      }
    )
  } finally {
    sending.value = false
  }
}
function handleQuick(q) { input.value = q; handleSend() }
function hideAvatar() { hidden.value = true; visible.value = false }
function showAvatar() { hidden.value = false }

onMounted(() => {
  // 按当前登录角色初始化开场白与会话（患者端/医生端能力不同）
  initMessages()
  pos.x = window.innerWidth - 150
  pos.y = window.innerHeight - 170
  // 对话框初始位置：数字人上方（右下角），按放大后的弹窗尺寸定位
  panelPos.x = Math.max(0, window.innerWidth - PANEL_W - 20)
  panelPos.y = Math.max(0, window.innerHeight - PANEL_H - 20)
})
</script>

<template>
  <!-- ============ 数字医生人 ============ -->
  <transition name="doc-scale">
    <div
      v-if="!hidden && !visible"
      class="doctor-avatar"
      :style="{ transform: `translate(${pos.x}px, ${pos.y}px)` }"
      @pointerdown="onDown"
      title="拖动数字医生 · 点击对话 · 双击隐藏"
      @dblclick="hideAvatar"
    >
      <span class="avatar-hide" @pointerdown.stop @click.stop="hideAvatar" title="隐藏">×</span>

      <!-- 女医生主体（真实图片，圆形裁剪） -->
      <div class="avatar-body">
        <img :src="doctorFemale" class="doc-img" alt="数字女医生小林" draggable="false" />
      </div>

      <!-- 对话气泡 -->
      <div class="speech-bubble">嗨！👋<span class="sb-tail"></span></div>
      <span class="pulse-dot"></span>
    </div>
  </transition>

  <!-- ============ 隐藏后的小尾巴 ============ -->
  <transition name="doc-scale">
    <div
      v-if="hidden"
      class="avatar-tail"
      :style="{ transform: `translate(${pos.x + 40}px, ${pos.y + 40}px)` }"
      @click="showAvatar"
      title="点击恢复数字医生"
    >
      <svg viewBox="0 0 32 32" fill="none">
        <circle cx="16" cy="16" r="14" fill="#ffffff" stroke="#1E7AE6" stroke-width="2"/>
        <circle cx="16" cy="14" r="5" fill="#FFD9B8"/>
        <path d="M8 26 Q8 19 16 19 Q24 19 24 26" fill="#1E7AE6"/>
      </svg>
    </div>
  </transition>

  <!-- ============ 对话框 ============ -->
  <teleport to="body">
    <div
      v-if="visible"
      class="chat-panel"
      :class="{ minimized }"
      :style="{ transform: `translate(${panelPos.x}px, ${panelPos.y}px)` }"
    >
      <div class="chat-header" @pointerdown="onPanelDown">
        <div class="chat-title">
          <svg class="mini-doc" viewBox="0 0 40 40" fill="none">
            <circle cx="20" cy="20" r="18" fill="#fff" opacity="0.25"/>
            <path d="M9 33 Q8 25 14 22 L26 22 Q32 25 31 33 Z" fill="#5B9FA9"/>
            <circle cx="20" cy="15" r="6.5" fill="#F8CFA8"/>
            <path d="M13 14 Q13 7 20 7 Q27 7 27 14 Q24 10 20 11 Q15 10 13 14 Z" fill="#4A342A"/>
            <path d="M26 12 Q30 16 28 24 Q26 20 25 15 Z" fill="#3E2B22"/>
            <circle cx="17.6" cy="15" r="0.9" fill="#2A1A12"/>
            <circle cx="22.4" cy="15" r="0.9" fill="#2A1A12"/>
            <path d="M17.5 18.5 Q20 20.5 22.5 18.5" stroke="#C96A5E" stroke-width="1.1" fill="none" stroke-linecap="round"/>
          </svg>
          <div class="chat-title-text">
            <strong>数字医生 · 小林</strong>
            <span><i class="online-dot"></i> 在线</span>
          </div>
        </div>
        <div class="chat-actions" @pointerdown.stop>
          <el-icon class="action-btn" @click="minimized = !minimized"><Minus /></el-icon>
          <el-icon class="action-btn" @click="visible = false"><Close /></el-icon>
        </div>
      </div>

      <div v-show="!minimized" class="chat-inner">
        <div class="chat-body" ref="chatBodyRef">
          <div v-for="(m, idx) in messages" :key="idx" :class="['msg', m.side]">
            <div v-if="m.side === 'ai'" class="msg-avatar">
              <svg viewBox="0 0 32 32" fill="none">
                <circle cx="16" cy="16" r="15" fill="#E9F4F5" stroke="#5B9FA9" stroke-width="1.4"/>
                <path d="M7 27 Q7 20 12 18 L20 18 Q25 20 25 27 Z" fill="#5B9FA9"/>
                <circle cx="16" cy="12.5" r="5" fill="#F8CFA8"/>
                <path d="M10.5 12 Q10.5 6 16 6 Q21.5 6 21.5 12 Q19 8.5 16 9.5 Q12.5 8.5 10.5 12 Z" fill="#4A342A"/>
                <path d="M21 10 Q24 13 22.5 20 Q21 16.5 20 13 Z" fill="#3E2B22"/>
                <circle cx="14" cy="12.5" r="0.8" fill="#2A1A12"/>
                <circle cx="18" cy="12.5" r="0.8" fill="#2A1A12"/>
                <path d="M14 15.5 Q16 17 18 15.5" stroke="#C96A5E" stroke-width="1" fill="none" stroke-linecap="round"/>
              </svg>
            </div>
            <div class="msg-main">
              <div class="msg-bubble">
                {{ m.text }}<span v-if="m.typing" class="typing-hint">正在输入…</span>
              </div>
              <!-- 结构化卡片：挂号成功卡 / 就诊进度卡 -->
              <div v-if="m.card" class="ai-card">
                <template v-if="m.card.type === 'APPOINTMENT'">
                  <div class="ai-card-head"><span class="ai-card-dot"></span>挂号成功</div>
                  <div class="ai-card-body">
                    <div class="ai-row"><span class="ai-k">医生</span><span class="ai-v">{{ m.card.doctorName }}</span></div>
                    <div class="ai-row"><span class="ai-k">科室</span><span class="ai-v">{{ m.card.department }}</span></div>
                    <div class="ai-row"><span class="ai-k">单号</span><span class="ai-v">{{ m.card.appointmentNo }}</span></div>
                    <div class="ai-row"><span class="ai-k">时间</span><span class="ai-v">{{ m.card.appointmentTime }}</span></div>
                  </div>
                  <div class="ai-card-actions">
                    <el-button size="default" @click="go('/patient/appointments')">查看我的挂号</el-button>
                  </div>
                </template>
                <template v-else-if="m.card.type === 'PROGRESS'">
                  <div class="ai-card-head"><span class="ai-card-dot"></span>就诊进度</div>
                  <div v-for="v in m.card.visits" :key="v.id" class="visit-item">
                    <div class="visit-top">
                      <span class="visit-dept">{{ v.department }}</span>
                      <span class="visit-status" :class="statusClass(v.status)">{{ v.statusDesc }}</span>
                    </div>
                    <div class="ai-row"><span class="ai-k">医生</span><span class="ai-v">{{ v.doctorName }}<template v-if="v.doctorTitle">（{{ v.doctorTitle }}）</template></span></div>
                    <div class="ai-row"><span class="ai-k">时间</span><span class="ai-v">{{ v.appointmentTime || '—' }}</span></div>
                    <div class="ai-row"><span class="ai-k">单号</span><span class="ai-v">{{ v.orderNo || '—' }}</span></div>
                    <div class="ai-row"><span class="ai-k">进度</span><span class="ai-v">{{ v.currentNode }}<template v-if="v.nextNode"> → {{ v.nextNode }}</template></span></div>
                    <div class="ai-card-actions">
                      <el-button size="default" @click="go(`/patient/visits/${v.id}`)">查看详情</el-button>
                    </div>
                  </div>
                </template>
                <template v-else-if="m.card.type === 'DOCTOR_PROGRESS'">
                  <div class="ai-card-head"><span class="ai-card-dot"></span>患者就诊进度</div>
                  <div v-if="m.card.timeLabel" class="ai-row"><span class="ai-k">查询时间</span><span class="ai-v">{{ m.card.timeLabel }}</span></div>
                  <div v-if="m.card.patientName" class="ai-row"><span class="ai-k">患者</span><span class="ai-v">{{ m.card.patientName }}</span></div>
                  <div v-for="v in m.card.visits" :key="v.id" class="visit-item">
                    <div class="visit-top">
                      <span class="visit-dept">{{ v.patientName }}</span>
                      <span class="visit-status" :class="statusClass(v.status)">{{ v.statusDesc }}</span>
                    </div>
                    <div class="ai-row"><span class="ai-k">科室</span><span class="ai-v">{{ v.department }}</span></div>
                    <div class="ai-row"><span class="ai-k">时间</span><span class="ai-v">{{ v.appointmentTime || '—' }}</span></div>
                    <div class="ai-row"><span class="ai-k">单号</span><span class="ai-v">{{ v.orderNo || '—' }}</span></div>
                    <div class="ai-row"><span class="ai-k">进度</span><span class="ai-v">{{ v.currentNode }}<template v-if="v.nextNode"> → {{ v.nextNode }}</template></span></div>
                  </div>
                  <div class="ai-card-actions">
                    <el-button size="default" @click="go('/doctor/appointments', progressQuery(m.card))">查看详情</el-button>
                  </div>
                </template>
                <template v-else-if="m.card.type === 'REPORT'">
                  <div class="ai-card-head"><span class="ai-card-dot"></span>我的报告</div>
                  <div v-for="r in m.card.reports" :key="'r' + r.id" class="visit-item">
                    <div class="visit-top">
                      <span class="visit-dept">{{ r.reportType }}</span>
                      <span class="visit-status" :class="reportStatusClass(r.status)">{{ r.statusDesc }}</span>
                    </div>
                    <div class="report-body">
                      <el-image
                        v-if="r.imageUrl"
                        :src="r.imageUrl"
                        :preview-src-list="[r.imageUrl]"
                        fit="cover"
                        preview-teleported
                        class="report-thumb"
                      >
                        <template #error>
                          <div class="report-thumb-ph">
                            <svg viewBox="0 0 24 24" fill="none">
                              <rect x="4" y="3" width="16" height="18" rx="2" stroke="currentColor" stroke-width="1.6"/>
                              <path d="M8 8h8M8 12h8M8 16h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
                            </svg>
                          </div>
                        </template>
                      </el-image>
                      <div class="report-info">
                        <div class="ai-row"><span class="ai-k">出具时间</span><span class="ai-v">{{ r.createdAt || '—' }}</span></div>
                        <div v-if="r.imageMeta" class="ai-row"><span class="ai-k">内容摘要</span><span class="ai-v">{{ r.imageMeta }}</span></div>
                      </div>
                    </div>
                    <div class="ai-card-actions">
                      <el-button size="default" @click="go(`/patient/reports/${r.id}`)">查看报告</el-button>
                    </div>
                  </div>
                  <div v-for="p in m.card.prescriptions" :key="'p' + p.id" class="visit-item">
                    <div class="visit-top">
                      <span class="visit-dept">{{ p.diagnosis || '处方' }}</span>
                      <span class="visit-status st-issued">已开具</span>
                    </div>
                    <div class="ai-row"><span class="ai-k">开方医生</span><span class="ai-v">{{ p.doctorName }}<template v-if="p.department">（{{ p.department }}）</template></span></div>
                    <div class="ai-row"><span class="ai-k">开方时间</span><span class="ai-v">{{ p.createdAt || '—' }}</span></div>
                    <div v-if="p.drugNames && p.drugNames.length" class="ai-row"><span class="ai-k">药品</span><span class="ai-v">{{ p.drugNames.join('、') }}</span></div>
                    <div class="ai-card-actions">
                      <el-button size="default" @click="go(`/patient/prescriptions/${p.visitId}`)">查看处方</el-button>
                    </div>
                  </div>
                  <div v-if="!hasReportItems(m.card)" class="ai-row"><span class="ai-k">提示</span><span class="ai-v">暂无报告或处方记录</span></div>
                </template>
              </div>
            </div>
            <div v-if="m.side === 'user'" class="msg-avatar user-avatar">我</div>
          </div>
        </div>

        <div class="quick-row">
          <el-button v-for="q in quickReplies" :key="q" size="default" class="quick-btn" @click="handleQuick(q)">{{ q }}</el-button>
        </div>

        <div class="chat-footer">
          <el-input v-model="input" :placeholder="inputPlaceholder" size="large" @keyup.enter="handleSend">
            <template #append>
              <el-button @click="handleSend" class="send-btn" :loading="sending">
                <svg viewBox="0 0 16 16"><path d="M2 8h10M9 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
                发送
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
/* ===== 数字人 ===== */
.doctor-avatar {
  position: fixed; left: 0; top: 0; z-index: 1000;
  width: 110px; height: 120px;
  cursor: grab; user-select: none; touch-action: none;
}
.doctor-avatar:active { cursor: grabbing; }
.avatar-hide {
  position: absolute; top: -4px; right: -4px; width: 20px; height: 20px;
  background: #E53935; color: #fff; border-radius: 50%;
  font-size: 13px; line-height: 18px; text-align: center;
  cursor: pointer; opacity: 0; transition: opacity 0.2s; z-index: 10; border: 2px solid #fff;
}
.doctor-avatar:hover .avatar-hide { opacity: 1; }

/* 圆形真实图片头像 */
.avatar-body {
  position: absolute; inset: 0;
  width: 100px; height: 100px;
  border-radius: 50%; overflow: hidden;
  border: 3px solid #ffffff;
  box-shadow: 0 10px 26px rgba(31,45,61,0.28), 0 2px 8px rgba(31,45,61,0.12);
  animation: floatY 3.2s ease-in-out infinite;
  background: #EAF3F8;
}
@keyframes floatY { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }

.doc-img {
  width: 100%; height: 100%;
  object-fit: cover;
  object-position: 50% 18%; /* 让脸部居中 */
  animation: headTilt 5s ease-in-out infinite;
}
@keyframes headTilt { 0%,100% { transform: rotate(-1.5deg) scale(1.02); } 50% { transform: rotate(1.5deg) scale(1.02); } }
@keyframes blink { 0%, 94%, 100% { opacity: 1; } 96% { opacity: 0.3; } 98% { opacity: 1; } }

.speech-bubble {
  position: absolute; top: -28px; left: 50%; transform: translateX(-50%);
  background: #fff; color: #1E7AE6; font-size: 13px; font-weight: 600;
  padding: 5px 12px; border-radius: 16px; white-space: nowrap;
  box-shadow: 0 4px 14px rgba(30,122,230,0.18); border: 1.5px solid #E3F2FF;
  animation: bubbleShow 4s ease-in-out infinite;
}
@keyframes bubbleShow { 0%, 60%, 100% { opacity: 0; transform: translateX(-50%) translateY(8px); } 70%, 90% { opacity: 1; transform: translateX(-50%) translateY(0); } }
.sb-tail { position: absolute; bottom: -5px; left: 50%; transform: translateX(-50%); border-left: 6px solid transparent; border-right: 6px solid transparent; border-top: 6px solid #fff; }

.pulse-dot { position: absolute; top: 2px; right: 2px; width: 14px; height: 14px; background: #E53935; border-radius: 50%; border: 2px solid #fff; animation: pulseDot 2s infinite; }
@keyframes pulseDot { 0%, 100% { box-shadow: 0 0 0 0 rgba(229,57,53,0.55); } 50% { box-shadow: 0 0 0 8px rgba(229,57,53,0); } }

/* ===== 隐藏后的小尾巴（白色） ===== */
.avatar-tail {
  position: fixed; left: 0; top: 0; z-index: 1000;
  width: 36px; height: 36px; border-radius: 50%;
  background: #ffffff;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 6px 18px rgba(0,0,0,0.15); cursor: pointer;
  border: 2px solid #E3F2FF;
}
.avatar-tail svg { width: 24px; height: 24px; }

/* ===== 对话框 ===== */
.chat-panel {
  position: fixed; left: 0; top: 0; z-index: 1000;
  width: 560px; max-width: calc(100vw - 16px);
  background: #fff; border-radius: 24px; overflow: hidden;
  box-shadow: 0 20px 60px rgba(15,23,42,0.22);
  display: flex; flex-direction: column; user-select: none;
  animation: panelIn 0.4s cubic-bezier(0.22,1,0.36,1);
}
@keyframes panelIn { 0% { opacity: 0; transform: translateY(30px) scale(0.9); } 100% { opacity: 1; transform: translateY(0) scale(1); } }

.chat-header {
  height: 72px; padding: 0 20px; cursor: grab;
  background: linear-gradient(135deg, #1E7AE6, #7A5AF8); color: #fff;
  display: flex; align-items: center; justify-content: space-between;
}
.chat-header:active { cursor: grabbing; }
.chat-title { display: flex; align-items: center; gap: 12px; }
.mini-doc { width: 42px; height: 42px; }
.chat-title-text strong { font-size: 19px; font-weight: 600; display: block; }
.chat-title-text span { font-size: 13px; opacity: 0.9; display: flex; align-items: center; gap: 5px; }
.online-dot { width: 6px; height: 6px; border-radius: 50%; background: #22C55E; display: inline-block; animation: blink 1.6s infinite; }
.chat-actions { display: flex; gap: 10px; }
.action-btn { cursor: pointer; opacity: 0.85; }
.action-btn:hover { opacity: 1; }

.chat-inner { display: flex; flex-direction: column; height: min(620px, calc(100vh - 150px)); }
.chat-body {
  flex: 1; padding: 20px; overflow-y: auto;
  background: linear-gradient(180deg, #F8FBFF 0%, #fff 100%);
  display: flex; flex-direction: column; gap: 16px;
}
.msg { display: flex; gap: 12px; align-items: flex-start; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; background: #fff; border: 1.5px solid #E3F2FF; }
.msg-avatar svg { width: 100%; height: 100%; }
.user-avatar { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; font-size: 14px; font-weight: 600; border-color: #1E7AE6; }
.msg-bubble { max-width: 76%; padding: 14px 18px; border-radius: 20px; font-size: 17px; line-height: 1.8; color: #1a1a2e; background: #fff; border: 1px solid #E8F1FC; box-shadow: 0 1px 3px rgba(0,0,0,0.04); white-space: pre-wrap; word-break: break-word; }
.msg.user .msg-bubble { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; border-color: transparent; }
.quick-row { padding: 14px 16px; border-top: 1px solid #F1F5F9; display: flex; flex-wrap: wrap; gap: 10px; background: #fff; }
.quick-btn { font-size: 15px; border-radius: 100px !important; background: #E3F2FF !important; color: #1E7AE6 !important; border: 1px solid #D0E5FA !important; }
.quick-btn:hover { background: #1E7AE6 !important; color: #fff !important; }
.chat-footer { padding: 16px; border-top: 1px solid #E8F1FC; background: #fff; }
.send-btn { font-weight: 600 !important; font-size: 15px !important; display: inline-flex; align-items: center; gap: 4px; }
.send-btn svg { width: 16px; height: 16px; }
.chat-panel.minimized .chat-inner { display: none; }

.doc-scale-enter-active, .doc-scale-leave-active { transition: transform 0.25s, opacity 0.25s; }
.doc-scale-enter-from, .doc-scale-leave-to { transform: scale(0.5); opacity: 0; }

/* ===== 气泡容器（气泡 + 结构化卡片纵向排列） ===== */
.msg-main { display: flex; flex-direction: column; gap: 10px; max-width: 84%; min-width: 0; }
.msg.user .msg-main { align-items: flex-end; }
.msg-main .msg-bubble { max-width: 100%; }
.typing-hint { color: #94a3b8; font-size: 15px; margin-left: 4px; }

/* ===== 结构化卡片：挂号成功 / 就诊进度 ===== */
.ai-card {
  background: #fff;
  border: 1px solid #E3F2FF;
  border-radius: 16px;
  padding: 16px 18px;
  box-shadow: 0 2px 8px rgba(30, 122, 230, 0.08);
  width: 100%;
  box-sizing: border-box;
}
.ai-card-head {
  display: flex; align-items: center; gap: 8px;
  font-size: 17px; font-weight: 600; color: #1E7AE6; margin-bottom: 12px;
}
.ai-card-dot { width: 8px; height: 8px; border-radius: 50%; background: #22C55E; display: inline-block; }
.ai-card-body { display: flex; flex-direction: column; gap: 8px; }
.ai-row { display: flex; justify-content: space-between; gap: 14px; font-size: 15px; line-height: 1.7; }
.ai-k { color: #8494a8; flex-shrink: 0; }
.ai-v { color: #1a1a2e; text-align: right; word-break: break-all; }
.ai-card-actions { display: flex; justify-content: flex-end; margin-top: 12px; }

/* 进度卡：单条就诊记录 */
.visit-item { padding: 14px 0; border-top: 1px dashed #EEF2F7; display: flex; flex-direction: column; gap: 8px; }
.visit-item:first-of-type { border-top: none; padding-top: 0; }
.visit-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 2px; }
.visit-dept { font-size: 17px; font-weight: 600; color: #1a1a2e; }
.visit-status { font-size: 13px; padding: 4px 12px; border-radius: 100px; white-space: nowrap; }

/* 报告卡：缩略图 + 内容摘要 */
.report-body { display: flex; gap: 12px; align-items: flex-start; }
.report-thumb { width: 64px; height: 82px; border-radius: 8px; flex-shrink: 0; border: 1px solid #E8F1FC; cursor: zoom-in; }
.report-thumb-ph {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  background: #F1F5F9; color: #94a3b8;
}
.report-thumb-ph svg { width: 26px; height: 26px; }
.report-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }

.st-waiting { background: #E3F2FF; color: #1E7AE6; }
.st-calling { background: #FFF4E5; color: #E67E22; }
.st-progress { background: #E8F8EF; color: #22C55E; }
.st-finished { background: #F1F5F9; color: #64748b; }
.st-uploaded { background: #FFF3DC; color: #B7791F; }
.st-analyzed { background: #E3F2FF; color: #1E7AE6; }
.st-signed { background: #E2F7EF; color: #0E9F6E; }
.st-issued { background: #E2F7EF; color: #0E9F6E; }
</style>

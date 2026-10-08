<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { Close, Minus } from '@element-plus/icons-vue'
import doctorFemale from '@/assets/doctor-female.png'

// ============== 状态 ==============
const visible = ref(false)
const minimized = ref(false)
const hidden = ref(false)
const input = ref('')
const chatBodyRef = ref(null)

const messages = reactive([
  { side: 'ai', text: '您好呀！我是数字医生小林 👨‍⚕️✨' },
  { side: 'ai', text: '很高兴认识您！我可以帮您：挂号指引、报告解读、初步问诊、用药提醒等。请问有什么需要帮忙的？ 😊' },
])
const quickReplies = ['帮我预约挂号', '解读我的报告', '最近有点咳嗽怎么办', '查看电子病历']

// 位置
const pos = reactive({ x: 0, y: 0 })
const panelPos = reactive({ x: 0, y: 0 })

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
    panelPos.x = clamp(sx + ev.clientX - px, 0, window.innerWidth - 400)
    panelPos.y = clamp(sy + ev.clientY - py, 0, window.innerHeight - 520)
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
function handleSend() {
  const t = input.value.trim(); if (!t) return
  messages.push({ side: 'user', text: t }); input.value = ''; scrollToBottom()
  setTimeout(() => {
    messages.push({ side: 'ai', text: '收到您的提问啦～正式版本中这里会调用 AI 接口返回真实回答。当前为演示环境，如有紧急情况请及时前往医院就诊 🙏' })
    scrollToBottom()
  }, 600)
}
function handleQuick(q) { input.value = q; handleSend() }
function hideAvatar() { hidden.value = true; visible.value = false }
function showAvatar() { hidden.value = false }

onMounted(() => {
  pos.x = window.innerWidth - 150
  pos.y = window.innerHeight - 170
  // 对话框初始位置：数字人上方（右下角）
  panelPos.x = Math.max(0, window.innerWidth - 420)
  panelPos.y = Math.max(0, window.innerHeight - 570)
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
            <div class="msg-bubble">{{ m.text }}</div>
            <div v-if="m.side === 'user'" class="msg-avatar user-avatar">我</div>
          </div>
        </div>

        <div class="quick-row">
          <el-button v-for="q in quickReplies" :key="q" size="small" class="quick-btn" @click="handleQuick(q)">{{ q }}</el-button>
        </div>

        <div class="chat-footer">
          <el-input v-model="input" placeholder="向数字医生小林提问..." size="default" @keyup.enter="handleSend">
            <template #append>
              <el-button @click="handleSend" class="send-btn">
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
  width: 400px; background: #fff; border-radius: 20px; overflow: hidden;
  box-shadow: 0 20px 60px rgba(15,23,42,0.22);
  display: flex; flex-direction: column; user-select: none;
  animation: panelIn 0.4s cubic-bezier(0.22,1,0.36,1);
}
@keyframes panelIn { 0% { opacity: 0; transform: translateY(30px) scale(0.9); } 100% { opacity: 1; transform: translateY(0) scale(1); } }

.chat-header {
  height: 56px; padding: 0 16px; cursor: grab;
  background: linear-gradient(135deg, #1E7AE6, #7A5AF8); color: #fff;
  display: flex; align-items: center; justify-content: space-between;
}
.chat-header:active { cursor: grabbing; }
.chat-title { display: flex; align-items: center; gap: 10px; }
.mini-doc { width: 34px; height: 34px; }
.chat-title-text strong { font-size: 15px; font-weight: 600; display: block; }
.chat-title-text span { font-size: 11px; opacity: 0.9; display: flex; align-items: center; gap: 5px; }
.online-dot { width: 6px; height: 6px; border-radius: 50%; background: #22C55E; display: inline-block; animation: blink 1.6s infinite; }
.chat-actions { display: flex; gap: 10px; }
.action-btn { cursor: pointer; opacity: 0.85; }
.action-btn:hover { opacity: 1; }

.chat-inner { display: flex; flex-direction: column; height: 464px; }
.chat-body {
  flex: 1; padding: 16px; overflow-y: auto;
  background: linear-gradient(180deg, #F8FBFF 0%, #fff 100%);
  display: flex; flex-direction: column; gap: 12px;
}
.msg { display: flex; gap: 8px; align-items: flex-start; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; background: #fff; border: 1.5px solid #E3F2FF; }
.msg-avatar svg { width: 100%; height: 100%; }
.user-avatar { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; font-size: 12px; font-weight: 600; border-color: #1E7AE6; }
.msg-bubble { max-width: 75%; padding: 10px 14px; border-radius: 16px; font-size: 13px; line-height: 1.75; color: #1a1a2e; background: #fff; border: 1px solid #E8F1FC; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.msg.user .msg-bubble { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; border-color: transparent; }
.quick-row { padding: 10px 12px; border-top: 1px solid #F1F5F9; display: flex; flex-wrap: wrap; gap: 8px; background: #fff; }
.quick-btn { font-size: 12px; border-radius: 100px !important; background: #E3F2FF !important; color: #1E7AE6 !important; border: 1px solid #D0E5FA !important; }
.quick-btn:hover { background: #1E7AE6 !important; color: #fff !important; }
.chat-footer { padding: 12px; border-top: 1px solid #E8F1FC; background: #fff; }
.send-btn { font-weight: 600 !important; display: inline-flex; align-items: center; gap: 4px; }
.send-btn svg { width: 14px; height: 14px; }
.chat-panel.minimized .chat-inner { display: none; }

.doc-scale-enter-active, .doc-scale-leave-active { transition: transform 0.25s, opacity 0.25s; }
.doc-scale-enter-from, .doc-scale-leave-to { transform: scale(0.5); opacity: 0; }
</style>

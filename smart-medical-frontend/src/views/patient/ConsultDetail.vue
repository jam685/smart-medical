<script setup>
/**
 * 患者端「问诊聊天」详情页组件
 * 职责：展示与医生的对话（消息气泡），患者可发送文本消息追问，医生回复实时可见。
 * 数据来源（真实接口）：
 *   GET  /online-consults/{id}          问诊详情（医生信息 + 状态）
 *   GET  /online-consults/{id}/messages 消息记录（按时间正序）
 *   POST /online-consults/{id}/messages 发送消息（命中敏感词返回 blocked）
 * 刷新策略：页面每 5 秒轮询消息，保证医生回复自动出现；发送成功后立即刷新。
 */
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Promotion } from '@element-plus/icons-vue'
import { fetchConsultDetail, fetchConsultMessages, sendConsultMessage } from '@/api/onlineconsult'

const route = useRoute()
const router = useRouter()

/** 问诊 ID（路由参数） */
const consultId = route.params.id

/** 问诊详情：{consult, doctor, patient} */
const detail = ref(null)
/** 消息数组（按时间正序） */
const messages = ref([])
/** 详情/消息加载状态 */
const loading = ref(false)
/** 发送中状态（防重复发送） */
const sending = ref(false)
/** 待发送的输入内容 */
const input = ref('')

/** 轮询定时器句柄（页面卸载时清理） */
let pollTimer = null

/** 消息容器 DOM 引用（自动滚动到底部用） */
const chatBody = ref(null)

/**
 * 问诊状态文案
 * @param {string} s 后端状态码
 * @returns {string} 中文文案
 */
function statusText(s) {
  if (s === 'REPLIED') return '已回复'
  if (s === 'CLOSED') return '已关闭'
  if (s === 'AI_ANALYZED') return '分析中'
  if (s === 'DOCTOR_REVIEWING') return '处理中'
  return '待接诊'
}

/** 问诊是否已关闭（关闭后禁止继续发送） */
const isClosed = computed(() => detail.value?.consult?.status === 'CLOSED')

/** 医生信息 */
const doctor = computed(() => detail.value?.doctor || {})

/**
 * 时间戳格式化：ISO 字符串 → 'MM-DD HH:mm'
 * @param {string} t ISO 时间字符串
 * @returns {string} 格式化后的时间
 */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 加载问诊详情 + 消息（首次进入与轮询复用）
 */
async function loadAll() {
  try {
    const [d, msgs] = await Promise.all([
      fetchConsultDetail(consultId),
      fetchConsultMessages(consultId),
    ])
    detail.value = d
    messages.value = msgs || []
  } catch (e) {
    // 请求失败提示已由 axios 拦截器统一处理
  }
}

/**
 * 加载消息（轮询用，仅刷新消息避免频繁重绘头部）
 */
async function loadMessages() {
  try {
    messages.value = await fetchConsultMessages(consultId)
    await scrollToBottom()
  } catch (e) {
    // 轮询失败静默忽略
  }
}

/** 消息更新后滚动到底部 */
async function scrollToBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}

/**
 * 发送消息：调 POST /messages，成功后清空输入并刷新消息流。
 * 命中敏感词时后端返回 {blocked:true, message}，前端按该文案提示。
 */
async function send() {
  const text = input.value.trim()
  if (!text) return
  sending.value = true
  try {
    const res = await sendConsultMessage(consultId, { content: text })
    if (res && res.blocked) {
      ElMessage.warning(res.message || '消息包含不适宜内容，已被拦截')
    } else {
      input.value = ''
      await loadMessages()
    }
  } catch (e) {
    // 失败提示已由请求拦截器统一处理
  } finally {
    sending.value = false
  }
}

/** 回车发送（Shift+Enter 换行） */
function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onMounted(async () => {
  loading.value = true
  await loadAll()
  loading.value = false
  // 每 5 秒轮询消息，保证医生回复自动出现
  pollTimer = setInterval(loadMessages, 5000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div class="chat-page" v-loading="loading">
    <!-- 顶部：医生信息 + 状态 -->
    <div class="chat-head">
      <el-button text class="back-btn" @click="router.push('/patient/online-consults')">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="doc-info">
        <span class="doc-name">{{ doctor.realName || '医生' }}</span>
        <span class="doc-meta">{{ doctor.title }} · {{ doctor.department }}</span>
      </div>
      <span v-if="detail" class="status-tag" :class="isClosed ? 'is-closed' : 'is-active'">
        {{ statusText(detail.consult.status) }}
      </span>
    </div>

    <!-- 消息区 -->
    <div class="chat-body" ref="chatBody">
      <div class="msg-list">
        <template v-for="m in messages" :key="m.id">
          <!-- 系统消息：居中 -->
          <div v-if="m.sender === 'SYSTEM'" class="sys-msg">
            <span>{{ m.content }}</span>
          </div>
          <!-- 患者消息：右侧蓝色 -->
          <div v-else-if="m.sender === 'PATIENT'" class="msg-row right">
            <div class="bubble patient">
              <img v-if="m.messageType === 'IMAGE' && m.imageUrl" :src="m.imageUrl" class="msg-img" alt="图片" />
              <div v-if="m.content" class="bubble-text">{{ m.content }}</div>
              <div class="bubble-time">{{ formatTime(m.createdAt) }}</div>
            </div>
          </div>
          <!-- 医生消息：左侧绿色 -->
          <div v-else-if="m.sender === 'DOCTOR'" class="msg-row left">
            <div class="bubble doctor">
              <div class="bubble-text">{{ m.content }}</div>
              <div class="bubble-time">{{ formatTime(m.createdAt) }}</div>
            </div>
          </div>
        </template>

        <div v-if="messages.length === 0" class="chat-empty">暂无消息，开始对话吧</div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-foot">
      <template v-if="!isClosed">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          placeholder="输入您的问题，回车发送（Shift+Enter 换行）"
          @keydown="onKeydown"
        />
        <el-button type="primary" :loading="sending" class="send-btn" @click="send">
          <el-icon class="el-icon--left"><Promotion /></el-icon>发送
        </el-button>
      </template>
      <div v-else class="closed-tip">问诊已关闭，如需继续咨询请重新发起问诊</div>
    </div>
  </div>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 14px; overflow: hidden; }

/* 顶部 */
.chat-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 1px solid #ECF1F7; background: #fff;
}
.back-btn { font-size: 16px; color: #5A6B82; padding: 4px; }
.doc-info { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.doc-name { font-size: 16px; font-weight: 700; color: #1A2B45; }
.doc-meta { font-size: 12.5px; color: #8492A6; margin-top: 2px; }
.status-tag { font-size: 12px; font-weight: 600; padding: 4px 12px; border-radius: 100px; }
.status-tag.is-active { color: #1E7AE6; background: #E3F2FF; }
.status-tag.is-closed { color: #8492A6; background: #EEF1F5; }

/* 消息区 */
.chat-body { flex: 1; overflow-y: auto; background: #F7F9FC; padding: 18px; }
.msg-list { display: flex; flex-direction: column; gap: 14px; max-width: 820px; margin: 0 auto; }
.sys-msg { display: flex; justify-content: center; }
.sys-msg span { font-size: 12px; color: #A7B1BF; background: #EEF1F5; padding: 4px 12px; border-radius: 100px; }
.msg-row { display: flex; }
.msg-row.right { justify-content: flex-end; }
.msg-row.left { justify-content: flex-start; }
.bubble { max-width: 68%; border-radius: 14px; padding: 10px 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.bubble.patient { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; border-bottom-right-radius: 4px; }
.bubble.doctor { background: #fff; color: #1A2B45; border: 1px solid #ECF1F7; border-bottom-left-radius: 4px; }
.bubble-text { font-size: 14px; line-height: 1.6; word-break: break-word; white-space: pre-wrap; }
.msg-img { max-width: 200px; border-radius: 10px; margin-bottom: 6px; display: block; }
.bubble-time { font-size: 11px; margin-top: 4px; opacity: 0.75; text-align: right; }
.chat-empty { text-align: center; color: #A7B1BF; font-size: 13px; padding: 40px 0; }

/* 输入区 */
.chat-foot { display: flex; align-items: flex-end; gap: 12px; padding: 14px 18px; border-top: 1px solid #ECF1F7; background: #fff; }
.chat-foot .el-textarea { flex: 1; }
.send-btn { border-radius: 10px; height: 40px; }
.closed-tip { flex: 1; text-align: center; color: #A7B1BF; font-size: 13px; padding: 10px 0; background: #FAFCFE; border-radius: 10px; }
</style>

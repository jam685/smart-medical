<script setup>
/**
 * 医生端「问诊处理」聊天页组件
 * 职责：展示患者主诉与对话，医生可回复患者、关闭问诊；医生回复经确认后写入消息流，患者端可见。
 * 数据来源（真实接口）：
 *   GET  /online-consults/{id}            问诊详情（患者脱敏信息 + 状态）
 *   GET  /online-consults/{id}/messages   消息记录（按时间正序）
 *   POST /online-consults/{id}/confirm-reply  医生确认回复
 *   POST /online-consults/{id}/close      关闭问诊
 * 刷新策略：每 5 秒轮询消息，保证患者追问自动出现；发送/关闭后立即刷新。
 */
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Promotion } from '@element-plus/icons-vue'
import {
  fetchConsultDetail, fetchConsultMessages, confirmConsultReply, closeConsult,
} from '@/api/onlineconsult'

const route = useRoute()
const router = useRouter()

/** 问诊 ID（路由参数） */
const consultId = route.params.id

/** 问诊详情：{consult, doctor, patient} */
const detail = ref(null)
/** 消息数组（按时间正序） */
const messages = ref([])
/** 加载状态 */
const loading = ref(false)
/** 回复提交中状态 */
const replying = ref(false)
/** 关闭中状态 */
const closing = ref(false)
/** 回复输入内容 */
const input = ref('')

/** 轮询定时器句柄 */
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

/** 问诊是否已关闭 */
const isClosed = computed(() => detail.value?.consult?.status === 'CLOSED')

/** 患者信息（脱敏） */
const patient = computed(() => detail.value?.patient || {})

/**
 * 时间戳格式化：ISO 字符串 → 'MM-DD HH:mm'
 */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 加载问诊详情 + 消息 */
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

/** 加载消息（轮询用） */
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
 * 发送回复：调 POST /confirm-reply，成功后清空输入并刷新消息流与状态。
 * 后端会将回复写入消息流（DOCTOR 消息）并置状态为 REPLIED。
 */
async function reply() {
  const text = input.value.trim()
  if (!text) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replying.value = true
  try {
    await confirmConsultReply(consultId, { finalReply: text })
    ElMessage.success('回复已发送给患者')
    input.value = ''
    await loadAll()
  } catch (e) {
    // 失败提示已由请求拦截器统一处理
  } finally {
    replying.value = false
  }
}

/**
 * 关闭问诊：二次确认后调用 POST /close，后端自动生成病历草稿。
 */
async function handleClose() {
  try {
    await ElMessageBox.confirm('关闭后患者将无法继续追问，且会自动生成病历草稿。确定关闭本次问诊吗？', '关闭问诊确认', {
      confirmButtonText: '确认关闭', cancelButtonText: '再想想', type: 'warning', roundButton: true,
    })
  } catch {
    return
  }
  closing.value = true
  try {
    await closeConsult(consultId)
    ElMessage.success('问诊已关闭')
    await loadAll()
  } catch (e) {
    // 失败提示已由请求拦截器统一处理
  } finally {
    closing.value = false
  }
}

/** 回车发送（Shift+Enter 换行） */
function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    reply()
  }
}

onMounted(async () => {
  loading.value = true
  await loadAll()
  loading.value = false
  pollTimer = setInterval(loadMessages, 5000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div class="chat-page" v-loading="loading">
    <!-- 顶部：患者信息 + 状态 + 关闭按钮 -->
    <div class="chat-head">
      <el-button text class="back-btn" @click="router.push('/doctor/online-consults')">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="patient-info">
        <span class="patient-name">{{ patient.realName || '患者' }}</span>
        <span class="patient-meta">{{ patient.gender }} · {{ patient.phone }}</span>
      </div>
      <span v-if="detail" class="status-tag" :class="isClosed ? 'is-closed' : 'is-active'">
        {{ statusText(detail.consult.status) }}
      </span>
      <el-button v-if="!isClosed" plain type="danger" size="small" :loading="closing" @click="handleClose">
        关闭问诊
      </el-button>
    </div>

    <!-- 主诉摘要条 -->
    <div v-if="detail" class="chief-bar">
      <span class="chief-label">主诉</span>
      <span class="chief-text">{{ detail.consult.chiefComplaint }}</span>
      <span class="dept-text">{{ detail.consult.department }}</span>
    </div>

    <!-- 消息区 -->
    <div class="chat-body">
      <div class="msg-list">
        <template v-for="m in messages" :key="m.id">
          <div v-if="m.sender === 'SYSTEM'" class="sys-msg"><span>{{ m.content }}</span></div>
          <!-- 患者消息：左侧 -->
          <div v-else-if="m.sender === 'PATIENT'" class="msg-row left">
            <div class="bubble patient">
              <img v-if="m.messageType === 'IMAGE' && m.imageUrl" :src="m.imageUrl" class="msg-img" alt="图片" />
              <div v-if="m.content" class="bubble-text">{{ m.content }}</div>
              <div class="bubble-time">{{ formatTime(m.createdAt) }}</div>
            </div>
          </div>
          <!-- 医生消息：右侧 -->
          <div v-else-if="m.sender === 'DOCTOR'" class="msg-row right">
            <div class="bubble doctor">
              <div class="bubble-text">{{ m.content }}</div>
              <div class="bubble-time">{{ formatTime(m.createdAt) }}</div>
            </div>
          </div>
        </template>
        <div v-if="messages.length === 0" class="chat-empty">暂无消息</div>
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
          maxlength="1000"
          placeholder="输入回复内容，回车发送（Shift+Enter 换行）"
          @keydown="onKeydown"
        />
        <el-button type="success" :loading="replying" class="send-btn" @click="reply">
          <el-icon class="el-icon--left"><Promotion /></el-icon>发送回复
        </el-button>
      </template>
      <div v-else class="closed-tip">问诊已关闭</div>
    </div>
  </div>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 14px; overflow: hidden; }

/* 顶部 */
.chat-head { display: flex; align-items: center; gap: 12px; padding: 14px 18px; border-bottom: 1px solid #ECF1F7; }
.back-btn { font-size: 16px; color: #5A6B82; padding: 4px; }
.patient-info { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.patient-name { font-size: 16px; font-weight: 700; color: #1A2B45; }
.patient-meta { font-size: 12.5px; color: #8492A6; margin-top: 2px; }
.status-tag { font-size: 12px; font-weight: 600; padding: 4px 12px; border-radius: 100px; }
.status-tag.is-active { color: #10B981; background: #E2F7EF; }
.status-tag.is-closed { color: #8492A6; background: #EEF1F5; }

/* 主诉摘要 */
.chief-bar { display: flex; align-items: center; gap: 10px; padding: 10px 18px; background: #F7FBFF; border-bottom: 1px solid #ECF1F7; }
.chief-label { font-size: 12px; color: #1E7AE6; background: #E3F2FF; padding: 2px 10px; border-radius: 100px; font-weight: 600; flex-shrink: 0; }
.chief-text { font-size: 13.5px; color: #1A2B45; font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dept-text { font-size: 12px; color: #8492A6; flex-shrink: 0; }

/* 消息区 */
.chat-body { flex: 1; overflow-y: auto; background: #F7F9FC; padding: 18px; }
.msg-list { display: flex; flex-direction: column; gap: 14px; max-width: 820px; margin: 0 auto; }
.sys-msg { display: flex; justify-content: center; }
.sys-msg span { font-size: 12px; color: #A7B1BF; background: #EEF1F5; padding: 4px 12px; border-radius: 100px; }
.msg-row { display: flex; }
.msg-row.right { justify-content: flex-end; }
.msg-row.left { justify-content: flex-start; }
.bubble { max-width: 68%; border-radius: 14px; padding: 10px 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.bubble.patient { background: #fff; color: #1A2B45; border: 1px solid #ECF1F7; border-bottom-left-radius: 4px; }
.bubble.doctor { background: linear-gradient(135deg, #10B981, #34D399); color: #fff; border-bottom-right-radius: 4px; }
.bubble-text { font-size: 14px; line-height: 1.6; word-break: break-word; white-space: pre-wrap; }
.msg-img { max-width: 200px; border-radius: 10px; margin-bottom: 6px; display: block; }
.bubble-time { font-size: 11px; margin-top: 4px; opacity: 0.75; text-align: right; }
.chat-empty { text-align: center; color: #A7B1BF; font-size: 13px; padding: 40px 0; }

/* 输入区 */
.chat-foot { display: flex; align-items: flex-end; gap: 12px; padding: 14px 18px; border-top: 1px solid #ECF1F7; }
.chat-foot .el-textarea { flex: 1; }
.send-btn { border-radius: 10px; height: 40px; }
.closed-tip { flex: 1; text-align: center; color: #A7B1BF; font-size: 13px; padding: 10px 0; background: #FAFCFE; border-radius: 10px; }
</style>

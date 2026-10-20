<template>
  <div class="preconsult-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2>智能预问诊</h2>
      <p>挂号前先和 AI 聊聊症状，它会帮你梳理病情并引导挂号（仅做采集与引导，不诊断、不开方）</p>
    </div>

    <div class="consult-grid">
      <!-- 左：历史会话列表 -->
      <div class="card session-card" v-loading="sessionsLoading">
        <div class="card-title">
          <h3>历史会话</h3>
          <el-button size="small" type="primary" plain @click="startNewSession">＋ 新会话</el-button>
        </div>
        <div v-if="sessions.length === 0" class="empty">暂无预问诊记录</div>
        <div
          v-for="s in sessions"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === currentSessionId }"
          @click="openSession(s.sessionId)"
        >
          <div class="session-text">{{ s.lastContent || '（图片消息）' }}</div>
          <div class="session-time">{{ formatTime(s.lastTime) }}</div>
        </div>
      </div>

      <!-- 右：对话窗口 -->
      <div class="card chat-card">
        <div ref="msgBoxRef" class="msg-box" v-loading="sending">
          <div v-if="messages.length === 0" class="chat-empty">
            <div class="robot-icon">🤖</div>
            <p>您好，我是智能预问诊助手</p>
            <p>描述一下您的不适症状，我会帮您梳理并引导挂号。</p>
            <div class="quick-chips">
              <button class="chip" @click="quickAsk('最近老是咳嗽，还有点发热，该挂什么科？')">咳嗽发热怎么办</button>
              <button class="chip" @click="quickAsk('最近觉得口渴多尿，会不会是血糖问题？')">口渴多尿</button>
              <button class="chip" @click="quickAsk('经常心慌胸痛，想了解一下挂什么科室')">心慌胸痛</button>
            </div>
          </div>
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.sender === 'AI' ? 'msg-ai' : 'msg-me'">
            <div class="avatar">{{ m.sender === 'AI' ? 'AI' : '我' }}</div>
            <div class="bubble">
              <div v-if="m.imageUrl" class="msg-img">
                <img :src="m.imageUrl" alt="图片" />
              </div>
              <div v-if="m.content" class="msg-content">{{ m.content }}</div>
              <div class="msg-time">{{ formatTime(m.createdAt) }}</div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="input-bar">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="500"
            placeholder="描述您的症状，例如：头痛三天，伴有低烧……（仅作预问诊采集）"
            @keydown.enter.prevent.exact="send"
          />
          <div class="input-actions">
            <span class="hint">AI 只做症状采集与挂号引导，诊断与用药请以医生为准</span>
            <el-button type="primary" :disabled="sending || !draft.trim()" @click="send">发送</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 患者端「智能预问诊」页
 * 数据全部对接后端 /pre-consults（PRE-01~04）：
 *   POST /pre-consults                    发送消息，返回 AI 引导回复 {sessionId, reply, aiMessage}
 *   GET  /pre-consults/{sessionId}         拉取会话历史
 *   GET  /pre-consults                     历史会话列表 {sessionId, lastContent, lastTime}
 *   POST /pre-consults/{sessionId}/close   结束会话（表无状态列，仅做存在性校验）
 * 合规边界：AI 只做症状采集与挂号引导，不诊断、不开方。
 */
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  sendPreConsultMessage,
  fetchPreConsultHistory,
  listPreConsultSessions,
  closePreConsultSession,
} from '@/api/preconsult'

/** 消息框 DOM（滚动到底部用） */
const msgBoxRef = ref(null)

/** 会话状态：当前会话 ID、草稿、发送中 */
const currentSessionId = ref('')
const draft = ref('')
const sending = ref(false)

/** 历史会话列表 */
const sessions = ref([])
const sessionsLoading = ref(false)

/** 当前会话消息列表 */
const messages = ref([])

/** 拉取历史会话列表（页面加载时） */
async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = await listPreConsultSessions()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    sessionsLoading.value = false
  }
}

/** 打开指定会话：拉取历史并滚动到底部 */
async function openSession(sessionId) {
  currentSessionId.value = sessionId
  messages.value = await fetchPreConsultHistory(sessionId)
  scrollToBottom()
}

/** 开始新会话：清空当前消息与会话 ID */
function startNewSession() {
  currentSessionId.value = ''
  messages.value = []
  draft.value = ''
  scrollToBottom()
}

/** 快捷提问 */
function quickAsk(text) {
  draft.value = text
  send()
}

/** 发送消息：无会话时由服务端生成新 sessionId，返回 AI 回复后追加进消息流 */
async function send() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  sending.value = true
  try {
    const data = await sendPreConsultMessage({
      sessionId: currentSessionId.value || undefined,
      content,
    })
    // 更新会话 ID（首次发送时由服务端下发）
    currentSessionId.value = data.sessionId || currentSessionId.value
    // 追加患者消息 + AI 回复（AI 回复来自返回体）
    messages.value.push({
      sender: 'PATIENT',
      messageType: 'TEXT',
      content,
      createdAt: new Date().toISOString(),
    })
    const ai = data.aiMessage || {}
    messages.value.push({
      sender: 'AI',
      messageType: 'TEXT',
      content: data.reply || ai.content || '',
      createdAt: ai.createdAt || new Date().toISOString(),
    })
    draft.value = ''
    scrollToBottom()
    // 刷新历史会话列表（新会话出现在顶部）
    if (sessions.value.length) await loadSessions()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    sending.value = false
  }
}

/** 滚动消息框到底部 */
function scrollToBottom() {
  nextTick(() => {
    const box = msgBoxRef.value
    if (box) box.scrollTop = box.scrollHeight
  })
}

/** 时间格式化 */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.preconsult-page { padding: 24px; }

.page-head h2 { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 6px; }
.page-head p { font-size: 13px; color: #64748b; margin: 0 0 20px; }

.consult-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 20px;
  align-items: start;
}

.card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef0f4;
  box-shadow: 0 1px 3px rgba(16, 42, 67, 0.06);
}

.session-card { padding: 16px; max-height: 640px; display: flex; flex-direction: column; }
.card-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.card-title h3 { font-size: 15px; font-weight: 600; color: #1a1a2e; margin: 0; }

.empty { color: #94a3b8; font-size: 13px; text-align: center; padding: 40px 0; }
.session-item {
  padding: 10px 12px; border-radius: 8px; cursor: pointer;
  border: 1px solid transparent; margin-bottom: 6px;
  transition: all 0.2s;
}
.session-item:hover { background: #f6f9ff; }
.session-item.active { background: #eaf2ff; border-color: #bfd9ff; }
.session-text {
  font-size: 13px; color: #334155; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.session-time { font-size: 12px; color: #94a3b8; margin-top: 4px; }

.chat-card { display: flex; flex-direction: column; max-height: 640px; }
.msg-box { flex: 1; overflow: auto; padding: 20px; }
.chat-empty { text-align: center; color: #94a3b8; font-size: 14px; padding: 48px 0 16px; }
.robot-icon { font-size: 40px; margin-bottom: 12px; }
.chat-empty p { margin: 4px 0; }

.quick-chips { margin-top: 18px; display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.chip {
  border: 1px solid #c7dfff; background: #f2f8ff; color: #1E7AE6;
  font-size: 13px; padding: 6px 14px; border-radius: 100px; cursor: pointer;
  transition: all 0.2s;
}
.chip:hover { background: #1E7AE6; color: #fff; }

.msg { display: flex; gap: 10px; margin-bottom: 16px; }
.msg-ai .avatar {
  background: linear-gradient(135deg, #1E7AE6, #4D94EC); color: #fff; flex-shrink: 0;
}
.msg-me { flex-direction: row-reverse; }
.msg-me .avatar { background: #e2e8f0; color: #475569; flex-shrink: 0; }
.avatar {
  width: 34px; height: 34px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600;
}
.bubble {
  max-width: 70%; padding: 10px 14px; border-radius: 10px; font-size: 14px; line-height: 1.7;
}
.msg-ai .bubble { background: #f2f7ff; color: #1e293b; }
.msg-me .bubble { background: #1E7AE6; color: #fff; }
.msg-content { word-break: break-word; }
.msg-img img { max-width: 180px; max-height: 140px; border-radius: 8px; display: block; }
.msg-time { font-size: 11px; color: #94a3b8; margin-top: 6px; }

.input-bar { border-top: 1px solid #eef0f4; padding: 12px 16px; }
.input-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.hint { font-size: 12px; color: #94a3b8; }
</style>

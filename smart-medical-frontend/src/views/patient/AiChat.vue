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
        <div ref="msgBoxRef" class="msg-box">
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
              <div v-else-if="m.sender === 'AI' && m.typing" class="msg-content typing">AI 正在输入…</div>
              <div v-if="m.deptTag" class="dept-tag">建议科室：{{ m.deptTag }}</div>
              <!-- 结构化卡片：挂号成功卡 / 就诊进度卡（实时 done 与历史回放共用） -->
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
                    <el-button size="small" @click="go('/patient/appointments')">查看我的挂号</el-button>
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
                      <el-button size="small" @click="go(`/patient/visits/${v.id}`)">查看详情</el-button>
                    </div>
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
                      <el-button size="small" @click="go(`/patient/reports/${r.id}`)">查看报告</el-button>
                    </div>
                  </div>
                  <div v-for="p in m.card.prescriptions" :key="'p' + p.id" class="visit-item">
                    <div class="visit-top">
                      <span class="visit-dept">{{ p.diagnosis || '处方' }}</span>
                      <span class="visit-status st-finished">已开具</span>
                    </div>
                    <div class="ai-row"><span class="ai-k">开方医生</span><span class="ai-v">{{ p.doctorName }}<template v-if="p.department">（{{ p.department }}）</template></span></div>
                    <div class="ai-row"><span class="ai-k">开方时间</span><span class="ai-v">{{ p.createdAt || '—' }}</span></div>
                    <div v-if="p.drugNames && p.drugNames.length" class="ai-row"><span class="ai-k">药品</span><span class="ai-v">{{ p.drugNames.join('、') }}</span></div>
                    <div class="ai-card-actions">
                      <el-button size="small" @click="go(`/patient/prescriptions/${p.visitId}`)">查看处方</el-button>
                    </div>
                  </div>
                  <div v-if="!hasReportItems(m.card)" class="ai-row"><span class="ai-k">提示</span><span class="ai-v">暂无报告或处方记录</span></div>
                </template>
              </div>
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
            <el-button type="primary" :loading="sending" :disabled="sending || !draft.trim()" @click="send">发送</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 患者端「智能预问诊」页
 * 数据对接后端 AI 多智能体入口 /ai/patient-chat（AiAgentController）：
 *   POST /ai/patient-chat               发送消息，走 LangGraph 图流转，返回 {sessionId, reply, suggestDept, symptomSummary, currentStage, draftVersion, ...}
 *   GET  /ai/patient-chat/{id}/history  拉取会话历史
 *   GET  /ai/patient-chat/sessions      历史会话列表
 * 合规边界：AI 只做症状采集与挂号引导，不诊断、不开方；医生侧草稿仅作参考需人工审核。
 */
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchAiChatHistory,
  listAiChatSessions,
  streamAiChatMessage,
} from '@/api/aiagent'

/** 路由实例（卡片"查看详情/查看我的挂号"跳转用） */
const router = useRouter()

/**
 * 卡片按钮跳转（就诊详情/挂号列表）
 * @param {string} path 目标路由
 */
function go(path) {
  router.push(path)
}

/**
 * 就诊状态 → 卡片状态徽章样式类（候诊蓝 / 叫号橙 / 就诊中绿 / 已完成灰）
 * @param {string} status 状态码（WAITING/CALLING/IN_PROGRESS/FINISHED）
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
    sessions.value = await listAiChatSessions()
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    sessionsLoading.value = false
  }
}

/** 打开指定会话：拉取历史并滚动到底部 */
async function openSession(sessionId) {
  currentSessionId.value = sessionId
  messages.value = await fetchAiChatHistory(sessionId)
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

/**
 * 发送消息（SSE 流式）：无会话时由服务端生成新 sessionId。
 * 发送后立即创建 AI 气泡，模型增量文本实时追加（无加载遮罩）；
 * done 事件回填会话 ID 与推荐科室标签。
 */
async function send() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  sending.value = true
  draft.value = ''
  // 追加患者消息
  messages.value.push({
    sender: 'PATIENT',
    messageType: 'TEXT',
    content,
    createdAt: new Date().toISOString(),
  })
  // 创建空的 AI 气泡（流式逐字填充；typing 占位让首字前有即时反馈，无空白等待）
  const aiMsg = {
    sender: 'AI',
    messageType: 'TEXT',
    content: '',
    deptTag: '',
    typing: true,
    createdAt: new Date().toISOString(),
  }
  messages.value.push(aiMsg)
  scrollToBottom()
  try {
    await streamAiChatMessage(
      { sessionId: currentSessionId.value || undefined, content },
      {
        // 模型增量文本：逐字/逐句追加到 AI 气泡
        onToken: (delta) => {
          aiMsg.typing = false
          aiMsg.content += delta
          scrollToBottom()
        },
        // 流转完成：回填会话 ID 与建议科室标签；业务类节点（挂号/进度/报告）不产流式 token，
        // 回复在 done 事件 data.reply 中，需回填到气泡（content 为空时才回填，避免覆盖流式已填内容）
        onDone: (data) => {
          aiMsg.typing = false
          currentSessionId.value = data.sessionId || currentSessionId.value
          aiMsg.deptTag = data.suggestDept || ''
          // 结构化卡片（挂号成功/就诊进度），SSE done 事件回填实时渲染
          aiMsg.card = data.card || null
          if (!aiMsg.content && data.reply) {
            aiMsg.content = data.reply
            scrollToBottom()
          }
          if (sessions.value.length) loadSessions()
        },
        // 出错：在 AI 气泡中展示错误信息
        onError: (err) => {
          aiMsg.typing = false
          if (!aiMsg.content) {
            aiMsg.content = err.message
          } else {
            aiMsg.content += `\n（${err.message}）`
          }
        },
      }
    )
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
.msg-content.typing { color: #999; font-style: italic; }
.msg-img img { max-width: 180px; max-height: 140px; border-radius: 8px; display: block; }
.dept-tag {
  display: inline-block; margin-top: 6px; font-size: 12px;
  background: #eef6ff; color: #1E7AE6; border: 1px solid #c7dfff;
  padding: 2px 10px; border-radius: 100px;
}
.msg-time { font-size: 11px; color: #94a3b8; margin-top: 6px; }

.input-bar { border-top: 1px solid #eef0f4; padding: 12px 16px; }
.input-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.hint { font-size: 12px; color: #94a3b8; }

/* ===== 结构化卡片（挂号成功 / 就诊进度）===== */
.ai-card {
  margin-top: 10px; background: #fff; border: 1px solid #e6eefc;
  border-radius: 10px; overflow: hidden; min-width: 240px;
}
.ai-card-head {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 12px; font-size: 13px; font-weight: 600; color: #166534;
  background: #f0fdf4; border-bottom: 1px solid #e6f4ea;
}
.ai-card-dot { width: 7px; height: 7px; border-radius: 50%; background: #22c55e; }
.ai-card-body { padding: 10px 12px; }
.ai-row { display: flex; font-size: 13px; line-height: 1.9; color: #334155; }
.ai-k { width: 44px; flex-shrink: 0; color: #94a3b8; }
.ai-v { flex: 1; word-break: break-all; }
.ai-card-actions { padding: 0 12px 10px; text-align: right; }

/* 进度卡片：多条就诊记录 */
.visit-item { padding: 10px 12px; border-bottom: 1px solid #f1f5f9; }
.visit-item:last-child { border-bottom: none; }
.visit-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 2px; }
.visit-dept { font-size: 14px; font-weight: 600; color: #1e293b; }
.visit-status {
  font-size: 12px; padding: 1px 8px; border-radius: 100px; border: 1px solid transparent;
}
.st-waiting { background: #eff6ff; color: #1d4ed8; border-color: #bfdbfe; }
.st-calling { background: #fff7ed; color: #c2410c; border-color: #fed7aa; }
.st-progress { background: #ecfdf5; color: #047857; border-color: #a7f3d0; }
.st-finished { background: #f1f5f9; color: #64748b; border-color: #e2e8f0; }

/* 报告卡片：缩略图 + 内容摘要 */
.report-body { display: flex; gap: 10px; align-items: flex-start; margin: 4px 0; }
.report-thumb { width: 52px; height: 68px; border-radius: 6px; flex-shrink: 0; border: 1px solid #e8f1fc; cursor: zoom-in; }
.report-thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #f1f5f9; color: #94a3b8; }
.report-thumb-ph svg { width: 22px; height: 22px; }
.report-info { flex: 1; min-width: 0; }
.st-uploaded { background: #fff7e6; color: #b45309; border-color: #fde68a; }
.st-analyzed { background: #eff6ff; color: #1d4ed8; border-color: #bfdbfe; }
.st-signed { background: #ecfdf5; color: #047857; border-color: #a7f3d0; }
.st-issued { background: #ecfdf5; color: #047857; border-color: #a7f3d0; }
</style>

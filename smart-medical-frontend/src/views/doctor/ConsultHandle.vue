<script setup>
/**
 * 医生端「问诊处理」聊天页组件
 * 职责：展示患者主诉与对话，医生可回复患者、关闭问诊；医生回复经确认后写入消息流，患者端可见。
 * 数据来源（真实接口）：
 *   GET  /online-consults/{id}            问诊详情（患者脱敏信息 + 状态）
 *   GET  /online-consults/{id}/messages   消息记录（按时间正序）
 *   POST /online-consults/{id}/confirm-reply  医生确认回复
 *   POST /online-consults/{id}/close      关闭问诊
 * 刷新策略：每 10 秒轮询消息（高并发调优），保证患者追问自动出现；发送/关闭后立即刷新。
 */
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Promotion, Upload, Picture, MagicStick, Refresh, DocumentCopy } from '@element-plus/icons-vue'
import {
  fetchConsultDetail, fetchConsultMessages, confirmConsultReply, closeConsult, sendConsultMessage, uploadChatImage,
  streamAnalyzeConsult, fetchLatestDraft,
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
/** 图片上传中状态 */
const uploading = ref(false)
/** 关闭中状态 */
const closing = ref(false)
/** 回复输入内容 */
const input = ref('')

/** AI 辅助分析边栏开关（默认展开） */
const sidebarOpen = ref(true)
/** AI 分析中状态 */
const analyzing = ref(false)
/** AI 分析结果（null=尚未分析；流式过程中逐字段填充） */
const analysis = ref(null)
/** 档案区数据（流式首个 profile 事件，模型返回前即可展示） */
const profile = ref(null)
/** 草稿逐字显示的文本（token 增量累积） */
const draftText = ref('')
/** 分析失败信息（空=无错误） */
const analyzeError = ref('')
/** 已分析过的最后一条患者消息 ID（成功后才记录，避免失败后永久跳过） */
let lastAnalyzedMsgId = 0
/** 自动分析失败的消息 ID（不再对该消息轮询重试，避免每 10s 反复请求；可手动重试） */
let autoFailedMsgId = 0
/** 是否正在等待自动分析（防止轮询并发重复触发） */
let autoAnalyzePending = false

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

/**
 * 是否未接诊（就诊状态非 IN_PROGRESS/FINISHED）：
 * 医生必须先到「接诊管理」点击「接诊」，才能与患者对话
 */
const chatBlocked = computed(() => detail.value != null && detail.value.chatEnabled === false)

/** 患者信息（脱敏） */
const patient = computed(() => detail.value?.patient || {})

/** 患者显示名（无姓名时兜底「患者」） */
const patientName = computed(() => patient.value.realName || '患者')

/** 当前医生显示名（取自问诊详情，无则兜底「医生」） */
const doctorName = computed(() => detail.value?.doctor?.realName || '医生')

/**
 * 头像文字：取姓名首字（无姓名时返回占位符）
 * @param {string} name 姓名
 * @returns {string} 单个字符
 */
function avatarText(name) {
  return (name || '').trim().slice(0, 1) || '?'
}

/**
 * 时间戳格式化：ISO 字符串 → 'MM-DD HH:mm'
 */
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 加载问诊详情 + 消息，先查 AI 分析缓存 */
async function loadAll() {
  try {
    const [d, msgs] = await Promise.all([
      fetchConsultDetail(consultId),
      fetchConsultMessages(consultId),
    ])
    detail.value = d
    messages.value = msgs || []
    // 先查缓存：有缓存直接渲染，患者没发新消息时零等待
    try {
      const cached = await fetchLatestDraft(consultId)
      if (cached) {
        // 把缓存里的档案区数据提出来（profile 区显示用）
        if (cached.patientInfo || cached.preConsultSummary || cached.medicalHistory) {
          profile.value = {
            patientInfo: cached.patientInfo,
            preConsultSummary: cached.preConsultSummary,
            medicalHistory: cached.medicalHistory,
          }
        }
        // 移除内部元数据字段，避免污染 UI 渲染
        const { _cached, _version, _cachedAt, _draftId, ...cleanAnalysis } = cached
        analysis.value = cleanAnalysis
        draftText.value = cached.draftReply || ''
        // 标记「已缓存」：让 maybeAutoAnalyze 跳过（除非患者发了新消息）
        if (cached._cachedAt) {
          lastAnalyzedMsgId = messages.value.length  // 粗略标记：消息数作为「已覆盖」水位
        }
        return
      }
    } catch (_) {
      // 缓存查询失败静默降级，走正常自动分析
    }
    maybeAutoAnalyze()
  } catch (e) {
    // 请求失败提示已由 axios 拦截器统一处理
  }
}

/** 加载消息（轮询用） */
async function loadMessages() {
  try {
    messages.value = await fetchConsultMessages(consultId)
    await scrollToBottom()
    maybeAutoAnalyze()
  } catch (e) {
    // 轮询失败静默忽略
  }
}

/**
 * 自动触发 AI 分析：最后一条消息为患者消息且未分析过该消息时触发。
 * 医生自己回复后最后一条变为医生消息，不会重复触发。
 */
function maybeAutoAnalyze() {
  const list = messages.value
  if (!list || list.length === 0) return
  const last = list[list.length - 1]
  if (last.sender !== 'PATIENT') return
  if (last.id === lastAnalyzedMsgId || last.id === autoFailedMsgId || autoAnalyzePending) return
  runAnalyze(true, last.id)
}

/**
 * 执行 AI 辅助分析（流式：档案区秒出 → 字段逐节渲染 → 草稿逐字打印）。
 * @param {boolean} auto 是否轮询自动触发（自动触发不弹错误提示，改为面板内提示重试）
 * @param {number} msgId 触发本次分析的患者消息 ID（成功后记录，避免重复分析）
 */
async function runAnalyze(auto = false, msgId = 0) {
  if (analyzing.value) return
  analyzing.value = true
  autoAnalyzePending = true
  analysis.value = null
  draftText.value = ''
  analyzeError.value = ''
  let success = false
  try {
    await streamAnalyzeConsult(consultId, {
      // 档案区（患者档案/预问诊/病史）先到，不依赖模型
      onProfile: (p) => { profile.value = p },
      // 顶层字段完成：逐节渲染卡片
      onSection: (key, value) => {
        if (!analysis.value) analysis.value = {}
        analysis.value[key] = value
      },
      // 草稿增量文本：打字机效果
      onToken: (delta) => { draftText.value += delta },
      // 完整结果：补齐 inHospital 标注、分析时间
      onDone: (res) => {
        analysis.value = res
        draftText.value = res?.draftReply || draftText.value
        success = true
      },
    })
    if (success) sidebarOpen.value = true
  } catch (e) {
    analyzeError.value = e?.message || 'AI 分析失败'
    if (!auto) ElMessage.error(analyzeError.value)
    // 自动分析失败：不再对该消息轮询重试（面板已提示可手动重试）
    if (auto && msgId) autoFailedMsgId = msgId
  } finally {
    analyzing.value = false
    autoAnalyzePending = false
  }
  // 仅成功才标记已分析；失败由 autoFailedMsgId 阻断轮询重试，面板可手动重试
  if (success && msgId) lastAnalyzedMsgId = msgId
  // 分析期间若有新患者消息到达，流结束后补跑一次
  if (success) maybeAutoAnalyze()
}

/** 信息是否充足（可给出诊断与处方建议） */
const sufficient = computed(() => analysis.value?.sufficiency === 'SUFFICIENT')

/** 年龄展示文案（档案区与结果区共用） */
const ageText = computed(() => {
  const age = profile.value?.patientInfo?.age || analysis.value?.patientInfo?.age
  return age ? age + ' 岁' : '—'
})

/** 将 AI 回复草稿插入医生输入框（不直接发送，走 confirm-reply 确认流程） */
function insertDraft() {
  const text = draftText.value || analysis.value?.draftReply
  if (!text) return
  input.value = text
  ElMessage.success('草稿已插入输入框，请确认后发送')
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
 * 上传图片：医生选择本机任意图片（不限于系统报告）→ 上传至 uploads/chat/ →
 * 作为 IMAGE 图片消息发送给患者（患者端聊天流中直接可见，不写入报告表）。
 * 由 el-upload 的 http-request 调用（options.file 为所选文件）。
 */
async function customUpload(options) {
  const f = options?.file
  if (!f) return
  uploading.value = true
  try {
    const res = await uploadChatImage(f)
    await sendConsultMessage(consultId, {
      content: '医生发送了一张图片，请查看',
      imageUrl: res.imageUrl,
    })
    ElMessage.success('图片已发送给患者')
    await loadAll()
  } catch (err) {
    // 失败提示已由请求拦截器统一处理
  } finally {
    uploading.value = false
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
  pollTimer = setInterval(loadMessages, 10000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div class="chat-wrap" v-loading="loading">
    <div class="chat-page">
    <!-- 顶部：患者信息 + 状态 + 关闭按钮 -->
    <div class="chat-head">
      <el-button text class="back-btn" @click="router.push('/doctor/online-consults')">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="patient-avatar">{{ avatarText(patientName) }}</div>
      <div class="patient-info">
        <span class="patient-name">{{ patientName }}</span>
        <span class="patient-meta">{{ patient.gender }} · {{ patient.phone }}</span>
      </div>
      <span v-if="detail" class="status-tag" :class="isClosed ? 'is-closed' : 'is-active'">
        {{ statusText(detail.consult.status) }}
      </span>
      <el-tooltip content="AI 辅助分析" placement="bottom">
        <el-button text class="ai-toggle" :class="{ 'is-on': sidebarOpen }" @click="sidebarOpen = !sidebarOpen">
          <el-icon><MagicStick /></el-icon>
        </el-button>
      </el-tooltip>
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
            <div class="avatar patient">{{ avatarText(patientName) }}</div>
            <div class="bubble patient">
              <el-image v-if="m.messageType === 'IMAGE' && m.imageUrl" :src="m.imageUrl" class="msg-img" fit="cover" :preview-src-list="[m.imageUrl]" preview-teleported hide-on-click-modal>
                <template #error>
                  <div class="img-failed"><el-icon><Picture /></el-icon><span>图片已失效</span></div>
                </template>
              </el-image>
              <div v-if="m.content" class="bubble-text">{{ m.content }}</div>
              <div class="bubble-time">{{ formatTime(m.createdAt) }}</div>
            </div>
          </div>
          <!-- 医生消息：右侧 -->
          <div v-else-if="m.sender === 'DOCTOR'" class="msg-row right">
            <div class="bubble doctor">
              <el-image v-if="m.messageType === 'IMAGE' && m.imageUrl" :src="m.imageUrl" class="msg-img" fit="cover" :preview-src-list="[m.imageUrl]" preview-teleported hide-on-click-modal>
                <template #error>
                  <div class="img-failed"><el-icon><Picture /></el-icon><span>图片已失效</span></div>
                </template>
              </el-image>
              <div v-if="m.content" class="bubble-text">{{ m.content }}</div>
              <div class="bubble-time">{{ formatTime(m.createdAt) }}</div>
            </div>
            <div class="avatar doctor">{{ avatarText(doctorName) }}</div>
          </div>
        </template>
        <div v-if="messages.length === 0" class="chat-empty">暂无消息</div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-foot">
      <template v-if="!isClosed && !chatBlocked">
        <el-upload
          :show-file-list="false"
          :http-request="customUpload"
          accept=".jpg,.jpeg,.png,image/jpeg,image/png"
          :disabled="uploading"
        >
          <el-button text class="upload-btn" :loading="uploading">
            <el-icon><Upload /></el-icon>&nbsp;上传图片
          </el-button>
        </el-upload>
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
      <div v-else-if="chatBlocked" class="blocked-tip">
        <el-icon class="el-icon--left"><Promotion /></el-icon>
        请先在「接诊管理」中点击「接诊」，接诊后即可回复患者
      </div>
      <div v-else class="closed-tip">问诊已关闭</div>
    </div>
    </div>

    <!-- 右侧：AI 辅助分析边栏 -->
    <aside v-show="sidebarOpen" class="ai-side">
      <div class="ai-head">
        <div class="ai-title">
          <el-icon><MagicStick /></el-icon>
          <span>AI 辅助分析</span>
          <span v-if="analyzing" class="ai-ing">分析中…</span>
        </div>
        <el-button text size="small" :loading="analyzing" @click="runAnalyze(false)">
          <el-icon><Refresh /></el-icon>&nbsp;重新分析
        </el-button>
      </div>

      <div class="ai-body">
        <!-- 患者档案区 -->
        <section class="ai-sec">
          <div class="sec-title">患者基本情况</div>
          <div class="profile-grid">
            <span class="p-label">姓名</span><span class="p-value">{{ profile?.patientInfo?.name || analysis?.patientInfo?.name || patientName }}</span>
            <span class="p-label">性别</span><span class="p-value">{{ profile?.patientInfo?.gender || analysis?.patientInfo?.gender || patient.gender || '—' }}</span>
            <span class="p-label">年龄</span><span class="p-value">{{ ageText }}</span>
            <span class="p-label">电话</span><span class="p-value">{{ profile?.patientInfo?.phone || analysis?.patientInfo?.phone || patient.phone || '—' }}</span>
          </div>
        </section>

        <section class="ai-sec">
          <div class="sec-title">预问诊摘要</div>
          <p class="sec-text">{{ profile?.preConsultSummary || analysis?.preConsultSummary || '暂无预问诊记录' }}</p>
        </section>

        <section class="ai-sec">
          <div class="sec-title">历史病史</div>
          <p class="sec-text pre-wrap">{{ profile?.medicalHistory || analysis?.medicalHistory || '暂无历史病历' }}</p>
        </section>

        <!-- 分析结果区 -->
        <template v-if="analysis">
          <section class="ai-sec">
            <div class="sec-title">病情判断</div>
            <div class="suff-badge" :class="sufficient ? 'ok' : 'lack'">
              {{ sufficient ? '信息充足 · 可初步诊断' : '信息不足 · 建议继续追问' }}
            </div>
            <div v-if="!sufficient && analysis.missingItems?.length" class="miss-chips">
              <span class="miss-label">待补充：</span>
              <span v-for="(item, i) in analysis.missingItems" :key="i" class="miss-chip">{{ item }}</span>
            </div>
          </section>

          <template v-if="sufficient">
            <section class="ai-sec">
              <div class="sec-title">初步诊断</div>
              <p class="sec-text pre-wrap">{{ analysis.diagnosis || '—' }}</p>
            </section>
            <section class="ai-sec">
              <div class="sec-title">就诊建议</div>
              <p class="sec-text pre-wrap">{{ analysis.advice || '—' }}</p>
            </section>
            <section v-if="analysis.prescription?.length" class="ai-sec">
              <div class="sec-title">处方建议<span class="sec-sub">（仅供参考，需医生确认）</span></div>
              <div class="rx-table">
                <div class="rx-row rx-head">
                  <span>药品</span><span>用法用量</span><span>天数</span>
                </div>
                <div v-for="(d, i) in analysis.prescription" :key="i" class="rx-row">
                  <span class="rx-name">
                    {{ d.drugName }}
                    <em v-if="d.inHospital" class="rx-tag">本院开过</em>
                    <i class="rx-spec">{{ d.specification }}</i>
                  </span>
                  <span class="rx-usage">{{ d.dosage }} · {{ d.frequency }}<br>{{ d.usageMethod }}</span>
                  <span class="rx-days">{{ d.days != null ? d.days + '天' : '—' }}</span>
                </div>
              </div>
            </section>

            <!-- 临床指南依据来源 -->
            <section v-if="analysis.ragSources?.length" class="ai-sec rag-sec">
              <div class="sec-title">依据来源<span class="sec-sub">（临床指南库检索）</span></div>
              <div class="rag-list">
                <div v-for="(src, i) in analysis.ragSources" :key="i" class="rag-item">
                  <span class="rag-doc">📖 {{ src.documentName }}</span>
                  <span v-if="src.chunkTitle" class="rag-chunk">· {{ src.chunkTitle }}</span>
                  <span class="rag-score">{{ Math.round(src.score * 100) }}%</span>
                </div>
              </div>
            </section>
          </template>

          <section class="ai-sec">
            <div class="sec-title">{{ sufficient ? '回复草稿' : '引导追问草稿' }}</div>
            <div class="draft-card">
              <p class="sec-text pre-wrap">{{ draftText || analysis.draftReply }}<span v-if="analyzing" class="typing-caret">▍</span></p>
              <el-button size="small" type="primary" plain :disabled="isClosed || chatBlocked" @click="insertDraft">
                <el-icon class="el-icon--left"><DocumentCopy /></el-icon>插入输入框
              </el-button>
            </div>
          </section>

          <div v-if="analysis.analyzedAt" class="ai-foot">分析时间 {{ analysis.analyzedAt }} · AI 结果仅供参考，最终诊疗以医生判断为准</div>
        </template>

        <!-- 分析中骨架占位（首个字段到达前，避免整块 loading） -->
        <template v-else-if="analyzing">
          <section class="ai-sec">
            <div class="sec-title">AI 正在分析对话…</div>
            <div class="sk-line" style="width: 42%"></div>
            <div class="sk-line" style="width: 88%"></div>
            <div class="sk-line" style="width: 64%"></div>
          </section>
          <section class="ai-sec">
            <div class="sk-line" style="width: 30%"></div>
            <div class="sk-block"></div>
          </section>
        </template>

        <!-- 分析失败：面板内提示并可重试 -->
        <div v-else-if="analyzeError" class="ai-error">
          <p>{{ analyzeError }}</p>
          <el-button size="small" @click="runAnalyze(false)">重新分析</el-button>
        </div>

        <div v-else class="ai-empty">
          <el-icon><MagicStick /></el-icon>
          <p>患者发来新消息时将自动分析<br>也可点击右上角「重新分析」</p>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.chat-wrap { display: flex; gap: 14px; height: 100%; }
.chat-page { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 14px; overflow: hidden; flex: 1; min-width: 0; }
.ai-toggle { font-size: 17px; color: #8492A6; padding: 4px; }
.ai-toggle.is-on { color: #10B981; }

/* AI 辅助分析边栏 */
.ai-side { width: 348px; flex-shrink: 0; display: flex; flex-direction: column; background: #fff; border-radius: 14px; overflow: hidden; }
.ai-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid #ECF1F7; }
.ai-title { display: flex; align-items: center; gap: 6px; font-size: 14.5px; font-weight: 700; color: #1A2B45; }
.ai-title .el-icon { color: #10B981; font-size: 16px; }
.ai-body { flex: 1; overflow-y: auto; padding: 12px 14px; }
.ai-sec { padding: 10px 0; border-bottom: 1px dashed #EEF1F5; }
.ai-sec:last-of-type { border-bottom: none; }
.sec-title { font-size: 13px; font-weight: 700; color: #3A4B63; margin-bottom: 8px; }
.sec-sub { font-size: 11.5px; font-weight: 400; color: #A7B1BF; margin-left: 4px; }
.sec-text { font-size: 12.8px; color: #5A6B82; line-height: 1.7; margin: 0; }
.sec-text.pre-wrap { white-space: pre-wrap; word-break: break-word; }

/* 患者档案 */
.profile-grid { display: grid; grid-template-columns: 40px 1fr 40px 1fr; row-gap: 6px; column-gap: 8px; }
.p-label { font-size: 12px; color: #A7B1BF; }
.p-value { font-size: 12.8px; color: #1A2B45; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 充足度徽章与缺失项 */
.suff-badge { display: inline-block; font-size: 12px; font-weight: 600; padding: 5px 12px; border-radius: 100px; }
.suff-badge.ok { color: #059669; background: #E2F7EF; }
.suff-badge.lack { color: #B7791F; background: #FFF3DE; }
.miss-chips { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 8px; }
.miss-label { font-size: 12px; color: #A7B1BF; }
.miss-chip { font-size: 11.5px; color: #B7791F; background: #FFF7E6; border: 1px solid #F0C36D; padding: 2px 8px; border-radius: 100px; }

/* 处方建议表 */
.rx-table { border: 1px solid #EEF1F5; border-radius: 10px; overflow: hidden; }
.rx-row { display: grid; grid-template-columns: 1.3fr 1fr 42px; gap: 8px; padding: 8px 10px; font-size: 12.3px; color: #3A4B63; border-bottom: 1px solid #F3F6FA; align-items: center; }
.rx-row:last-child { border-bottom: none; }
.rx-head { background: #F7F9FC; font-weight: 700; color: #8492A6; font-size: 11.8px; }
.rx-name { font-weight: 600; color: #1A2B45; }
.rx-spec { display: block; font-style: normal; font-weight: 400; font-size: 11.3px; color: #A7B1BF; margin-top: 2px; }
.rx-tag { display: inline-block; font-style: normal; font-size: 10.5px; font-weight: 600; color: #1E7AE6; background: #E3F2FF; border-radius: 4px; padding: 1px 5px; margin-left: 4px; vertical-align: 1px; }
.rx-usage { line-height: 1.5; color: #5A6B82; }
.rx-days { color: #5A6B82; text-align: right; }

/* 草稿卡 */
.draft-card { background: #F7FBFF; border: 1px solid #E3F2FF; border-radius: 10px; padding: 10px 12px; }
.draft-card .sec-text { color: #3A4B63; margin-bottom: 8px; }

.ai-foot { font-size: 11px; color: #A7B1BF; padding: 10px 0 4px; line-height: 1.6; }
.ai-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; color: #A7B1BF; padding: 48px 0; text-align: center; }
.ai-empty .el-icon { font-size: 30px; color: #D5DCE5; }
.ai-empty p { font-size: 12.5px; line-height: 1.8; margin: 0; }

/* 分析中标识 / 骨架 / 打字光标 / 失败提示 */
.ai-ing { font-size: 11.5px; font-weight: 400; color: #10B981; margin-left: 4px; }
.sk-line { height: 12px; border-radius: 6px; margin-bottom: 8px; background: linear-gradient(90deg, #EEF3F8 25%, #F7FAFD 37%, #EEF3F8 63%); background-size: 400% 100%; animation: sk-shimmer 1.4s ease infinite; }
.sk-block { height: 92px; border-radius: 8px; background: linear-gradient(90deg, #EEF3F8 25%, #F7FAFD 37%, #EEF3F8 63%); background-size: 400% 100%; animation: sk-shimmer 1.4s ease infinite; }
@keyframes sk-shimmer { 0% { background-position: 100% 0; } 100% { background-position: -100% 0; } }
.typing-caret { color: #1E7AE6; font-weight: 400; animation: caret-blink 0.9s steps(2, start) infinite; }
@keyframes caret-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.ai-error { padding: 24px 0; text-align: center; }
.ai-error p { font-size: 12.5px; color: #B7791F; line-height: 1.8; margin: 0 0 10px; }

/* 顶部 */
.chat-head { display: flex; align-items: center; gap: 12px; padding: 14px 18px; border-bottom: 1px solid #ECF1F7; background: #fff; }
.back-btn { font-size: 16px; color: #5A6B82; padding: 4px; }
.patient-avatar { width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 17px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #6C9BE8, #3B6FE0); box-shadow: 0 4px 10px rgba(59, 111, 224, 0.25); }
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
.chat-body { flex: 1; overflow-y: auto; background: linear-gradient(180deg, #F3F8FE 0%, #EDF3FB 100%); padding: 20px 18px; }
.msg-list { display: flex; flex-direction: column; gap: 16px; max-width: 820px; margin: 0 auto; }
.sys-msg { display: flex; justify-content: center; }
.sys-msg span { font-size: 12px; color: #A7B1BF; background: rgba(238, 241, 245, 0.9); padding: 4px 12px; border-radius: 100px; }
.msg-row { display: flex; align-items: flex-start; gap: 10px; }
.msg-row.right { justify-content: flex-end; }
.msg-row.left { justify-content: flex-start; }
/* 头像：患者蓝 / 医生绿（姓名首字圆形徽标） */
.avatar { width: 38px; height: 38px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 700; color: #fff; user-select: none; }
.avatar.patient { background: linear-gradient(135deg, #7EA6E8, #3B6FE0); box-shadow: 0 3px 8px rgba(59, 111, 224, 0.22); }
.avatar.doctor { background: linear-gradient(135deg, #34D399, #059669); box-shadow: 0 3px 8px rgba(5, 150, 105, 0.22); }
.bubble { max-width: 62%; border-radius: 16px; padding: 11px 15px; box-shadow: 0 2px 10px rgba(23, 43, 77, 0.06); }
.bubble.patient { background: #fff; color: #1A2B45; border: 1px solid #E9EFF7; border-top-left-radius: 4px; }
.bubble.doctor { background: linear-gradient(135deg, #10B981, #34D399); color: #fff; border-top-right-radius: 4px; box-shadow: 0 4px 12px rgba(16, 185, 129, 0.22); }
.bubble-text { font-size: 14px; line-height: 1.65; word-break: break-word; white-space: pre-wrap; }
.msg-img { width: 220px; height: 160px; border-radius: 12px; margin-bottom: 6px; display: block; cursor: zoom-in; overflow: hidden; background: #F2F5F9; }
.img-failed { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; color: #A7B1BF; font-size: 12.5px; background: #F7F9FC; }
.img-failed .el-icon { font-size: 22px; }
.bubble-time { font-size: 11px; margin-top: 4px; text-align: right; color: #A7B1BF; }
.bubble.doctor .bubble-time { color: rgba(255, 255, 255, 0.82); }
.chat-empty { text-align: center; color: #A7B1BF; font-size: 13px; padding: 40px 0; }

/* 输入区 */
.chat-foot { display: flex; align-items: flex-end; gap: 12px; padding: 14px 18px; border-top: 1px solid #ECF1F7; background: #fff; }
.chat-foot .el-textarea { flex: 1; }
.chat-foot :deep(.el-textarea__inner) { border-radius: 12px; background: #F7F9FC; border-color: #E4EBF5; padding: 10px 12px; font-size: 13.5px; box-shadow: none; }
.chat-foot :deep(.el-textarea__inner:focus) { background: #fff; border-color: #10B981; box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12); }
.upload-btn { color: #5A6B82; font-size: 13px; padding: 0 4px; height: 40px; }
.upload-btn:hover { color: #10B981; }
.send-btn { border-radius: 10px; height: 40px; }
.closed-tip { flex: 1; text-align: center; color: #A7B1BF; font-size: 13px; padding: 10px 0; background: #FAFCFE; border-radius: 10px; }
.blocked-tip { flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px; color: #B7791F; font-size: 13px; padding: 12px 0; background: #FFF7E6; border: 1px dashed #F0C36D; border-radius: 10px; }

/* 依据来源区块 */
.rag-sec { padding: 8px 10px 10px; background: linear-gradient(135deg, #F0F9FF 0%, #F5FBFF 100%); border: 1px solid #D6ECFF; border-radius: 10px; margin-top: 6px; }
.rag-list { display: flex; flex-direction: column; gap: 4px; margin-top: 6px; }
.rag-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #4B5A70; padding: 4px 6px; background: rgba(255,255,255,0.6); border-radius: 6px; }
.rag-doc { font-weight: 500; color: #1E40AF; }
.rag-chunk { color: #6B7D95; }
.rag-score { margin-left: auto; font-size: 11px; color: #2563EB; background: #DBEAFE; padding: 1px 7px; border-radius: 10px; font-weight: 500; font-variant-numeric: tabular-nums; }
</style>

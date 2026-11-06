/**
 * 线上面诊业务接口模块
 * 对应后端 OnlineConsultController（前缀 /online-consults）：
 *   GET  /my-doctors                  我的挂号主治医生列表（问诊发起专用，按有效挂号派生）
 *   GET  /online-doctors              在线医生列表（脱敏视图，不含密码）
 *   POST /                            发起问诊
 *   GET  /                            问诊分页列表（按角色过滤）
 *   GET  /{id}                        问诊详情（含医生、患者脱敏信息）
 *   POST /{id}/messages               发送消息（患者补充描述）
 *   GET  /{id}/messages               消息记录（按时间正序）
 *   POST /{id}/confirm-reply          医生确认回复（写入消息流）
 *   POST /{id}/close                  关闭问诊
 *   GET  /{id}/supplement-records   补充材料记录与次数
 */
import request from './request'
import { useUserStore } from '@/stores/user'

/**
 * 查询患者自己的「挂号主治医生」列表（发起问诊时选择医生用）
 * 业务规则：患者只能对自己挂号成功（挂号状态非 CANCELLED）的主治医生发起在线问诊。
 * @returns {Promise<Array>} 医生数组：{id, employeeNo, realName, gender, title, department, avatar, bio, onlineStatus}
 */
export function fetchMyDoctors() {
  return request.get('/online-consults/my-doctors')
}

/**
 * 查询在线医生列表（患者发起问诊时选择医生用；仅返回 onlineStatus=ONLINE 的医生）
 * @param {object} [params] 查询参数
 * @param {string} [params.department] 科室名，不传则返回全部科室医生
 * @param {string} [params.keyword]    医生姓名关键词，不传则不按姓名过滤
 * @returns {Promise<Array>} 医生数组：{id, employeeNo, realName, gender, title, department, avatar, bio, onlineStatus}
 */
export function fetchOnlineDoctors(params = {}) {
  return request.get('/online-consults/online-doctors', { params })
}

/**
 * 患者发起线上问诊
 * @param {object} payload 问诊载荷
 * @param {number} payload.doctorId        接诊医生 ID（必须在线）
 * @param {string} payload.department      科室名
 * @param {string} payload.chiefComplaint  主诉
 * @param {string} payload.description     病情描述
 * @returns {Promise<object>} 新建的问诊实体 {id, patientId, doctorId, department, chiefComplaint, description, status, supplementCount, ...}
 */
export function createConsult(payload) {
  return request.post('/online-consults', payload)
}

/**
 * 分页查询当前登录用户的问诊列表（患者返回本人、医生返回本人接诊、管理员返回全部）
 * @param {object} [params] 查询参数
 * @param {number} [params.page=1]   页码
 * @param {number} [params.size=100] 每页条数（列表页一次取足量数据，Tab 在前端过滤）
 * @returns {Promise<object>} 分页对象 {total, list, page, size}，list 为问诊实体数组
 */
export function fetchConsults(params = { page: 1, size: 100 }) {
  return request.get('/online-consults', { params })
}

/**
 * 查询单条问诊详情（含归属校验；医生信息脱敏、患者身份证脱敏）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<object>} {consult: 问诊实体, doctor: 医生视图, patient: 患者脱敏视图}
 */
export function fetchConsultDetail(id) {
  return request.get(`/online-consults/${id}`)
}

/**
 * 患者发送消息（补充描述或上传图片）
 * @param {number|string} id 问诊 ID
 * @param {object} payload 消息载荷
 * @param {string} [payload.content]  文本内容
 * @param {string} [payload.imageUrl] 图片地址（非空即图片消息，会递增补充次数）
 * @returns {Promise<object>} 发送成功的消息实体；命中敏感词时返回 {blocked:true, message, keyword}
 */
export function sendConsultMessage(id, payload) {
  return request.post(`/online-consults/${id}/messages`, payload)
}

/**
 * 上传聊天图片（患者/医生均可，任意路径的图片文件）
 * 后端仅保存文件到 uploads/chat/，不写入报告表；返回 imageUrl 后前端以 IMAGE 消息发送。
 * @param {File} file 图片文件（jpg/jpeg/png/webp/gif）
 * @returns {Promise<object>} {imageUrl}
 */
export function uploadChatImage(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/online-consults/upload-image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 查询问诊消息记录（按时间正序，含系统消息、医患消息）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<Array>} 消息数组 {id, consultId, sender, messageType, content, imageUrl, sensitiveFlag, createdAt}
 */
export function fetchConsultMessages(id) {
  return request.get(`/online-consults/${id}/messages`)
}

/**
 * 医生确认并回复患者（写入消息流，患者端可见；仅接诊医生本人可操作）
 * @param {number|string} id 问诊 ID
 * @param {object} payload 回复载荷
 * @param {string} payload.finalReply 最终回复内容
 * @returns {Promise<void>} 成功无返回体，失败由请求拦截器统一弹错
 */
export function confirmConsultReply(id, payload) {
  return request.post(`/online-consults/${id}/confirm-reply`, payload)
}

/**
 * 关闭问诊（医生本人或管理员可操作；关闭时后端自动生成病历草稿）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<void>} 成功无返回体
 */
export function closeConsult(id) {
  return request.post(`/online-consults/${id}/close`)
}

/**
 * 查询补充材料记录与次数（仅统计图片上传）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<Array>} 补充记录数组 {id, consultId, supplementNo, materialType, content, imageUrl, createdAt}
 */
export function fetchSupplementRecords(id) {
  return request.get(`/online-consults/${id}/supplement-records`)
}

/**
 * 医生端：读取某问诊最新 AI 分析草稿（缓存命中场景，医生打开问诊页先调这个）。
 * 若患者未发新消息、已有完整分析草稿，后端直接返回缓存 JSON，前端零等待渲染。
 * 返回 null 表示无草稿，前端改走流式分析。
 */
export function fetchLatestDraft(id) {
  return request.get(`/ai/doctor-assist/draft/${id}`)
}

/**
 * 医生端 AI 辅助问诊分析（仅接诊医生本人可调用）
 * 后端装配患者档案/预问诊摘要/历史病历/对话上下文 + 临床指南 RAG + LLM 结构化分析。
 * 非流式版本保留给后端 /analyze 端点，页面统一使用流式 streamAnalyzeConsult。
 *
 * @param {number|string} id 问诊 ID
 * @returns {Promise<object>} {sufficiency, missingItems, draftReply, diagnosis, advice, prescription[], patientInfo, preConsultSummary, medicalHistory, analyzedAt}
 */
export function analyzeConsult(id) {
  // LLM 分析耗时长（通常 10~60s），单独放宽超时，避免默认 15s 超时导致自动分析静默失败
  return request.post('/ai/doctor-assist/analyze', { consultId: Number(id) }, { timeout: 120000 })
}

/**
 * 医生端 AI 辅助问诊分析（流式，SSE）。
 * 事件序列：profile（患者档案区秒出）→ section*（顶层字段完成，分节渲染）→
 * token*（draftReply 增量文本，打字机效果）→ done（完整结果）→ error。
 * 实现方式与 aiagent.js 一致：fetch + ReadableStream 逐帧解析（后端响应可能被代理缓冲，故用 fetch 而非 EventSource）。
 * @param {number|string} id 问诊 ID
 * @param {object} handlers 事件回调
 * @param {Function} [handlers.onProfile] 档案区数据 {patientInfo, preConsultSummary, medicalHistory}
 * @param {Function} [handlers.onSection] 字段完成（key, value）
 * @param {Function} [handlers.onToken] 文本增量（delta）
 * @param {Function} [handlers.onDone] 完整结果（含 analyzedAt / inHospital）
 * @param {Function} [handlers.onError] 错误（Error）
 * @returns {Promise<void>}
 */
export function streamAnalyzeConsult(id, { onProfile, onSection, onToken, onDone, onError } = {}) {
  const userStore = useUserStore()
  return fetch('/api/smart-medical/v1/ai/doctor-assist/analyze-stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userStore.token}`,
    },
    body: JSON.stringify({ consultId: Number(id) }),
  }).then(async (resp) => {
    if (!resp.ok || !resp.body) {
      throw new Error(`分析请求失败（${resp.status}）`)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let settled = false
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // 按空行切分 SSE 帧，最后可能不完整留待下轮
        const frames = buffer.split('\n\n')
        buffer = frames.pop()
        for (const frame of frames) {
          const ev = parseSseFrame(frame)
          if (!ev) continue
          if (ev.name === 'profile') {
            onProfile && onProfile(JSON.parse(ev.data || '{}'))
          } else if (ev.name === 'section') {
            const p = JSON.parse(ev.data || '{}')
            onSection && onSection(p.key, p.value)
          } else if (ev.name === 'token') {
            onToken && onToken(JSON.parse(ev.data || '{}').delta || '')
          } else if (ev.name === 'done') {
            settled = true
            onDone && onDone(JSON.parse(ev.data || '{}'))
          } else if (ev.name === 'error') {
            settled = true
            onError && onError(new Error(JSON.parse(ev.data || '{}').message || 'AI 分析失败'))
          }
        }
      }
    } finally {
      reader.releaseLock()
      if (!settled) {
        onError && onError(new Error('连接已断开，请重试'))
      }
    }
  })
}

/**
 * 解析单个 SSE 帧：提取 event 名与 data 内容。
 * @param {string} frame 如 "event: token\ndata: {...}"
 * @returns {{name: string, data: string}|null}
 */
function parseSseFrame(frame) {
  let name = null
  let data = null
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data = line.slice(5).trim()
    }
  }
  return name ? { name, data: data || '' } : null
}

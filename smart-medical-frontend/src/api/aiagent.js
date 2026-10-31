/**
 * AI 多智能体对话接口模块（患者客服 + 医生辅助统一入口）
 * 对应后端 AiAgentController（前缀 /ai/patient-chat）：
 *   POST /ai/patient-chat                      发送消息，走 LangGraph 图流转返回 {sessionId, reply, suggestDept, symptomSummary, currentStage, draftVersion, replyDraft, prescriptionDraft, errorMsg}
 *   GET  /ai/patient-chat/{sessionId}/history  拉取会话历史消息
 *   GET  /ai/patient-chat/sessions             历史会话列表 {sessionId, lastContent, lastTime}
 */
import request from './request'
import { useUserStore } from '@/stores/user'

/**
 * 发送 AI 对话消息（图流转主入口）
 * 首次调用不传 sessionId 时，服务端会生成新会话。
 * @param {object} payload 消息载荷
 * @param {string} [payload.sessionId] 会话标识；为空则服务端生成新会话
 * @param {string} [payload.content]   用户消息文本
 * @param {string[]} [payload.imageUrls] 本次上传图片地址集合（可空）
 * @returns {Promise<object>} 图流转结果：{sessionId, reply, suggestDept, symptomSummary, currentStage, draftVersion, replyDraft, prescriptionDraft, errorMsg}
 */
export function sendAiChatMessage(payload) {
  return request.post('/ai/patient-chat', payload)
}

/**
 * AI 对话流式发送（SSE，text/event-stream）
 * 事件序列：start → token*（模型增量文本实时推送）→ done（含全量结果）。
 * 通过回调即时渲染增量文本，模型生成期间无需等待，无加载阶段。
 * 实现说明：使用 fetch + ReadableStream（标准流式方案）——已在浏览器实测确认
 * resp.body 可用、chunked 实时分块到达（Content-Encoding 为空，未被压缩破坏流式）。
 * @param {object} payload 消息载荷（同 sendAiChatMessage）
 * @param {object} handlers 事件回调
 * @param {Function} [handlers.onStart] 收到 start 事件
 * @param {Function} [handlers.onToken] 收到增量文本（参数 delta）
 * @param {Function} [handlers.onDone] 收到 done 事件（参数全量结果）
 * @param {Function} [handlers.onError] 收到 error 事件（参数 Error）
 * @returns {Promise<void>}
 */
export function streamAiChatMessage(payload, { onStart, onToken, onDone, onError } = {}) {
  const userStore = useUserStore()
  return fetch('/api/smart-medical/v1/ai/patient-chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userStore.token}`,
    },
    body: JSON.stringify(payload),
  }).then(async (resp) => {
    if (!resp.ok || !resp.body) {
      throw new Error(`请求失败（${resp.status}）`)
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
        // 按空行切分 SSE 帧，最后一帧可能不完整留待下轮
        const frames = buffer.split('\n\n')
        buffer = frames.pop()
        for (const frame of frames) {
          const event = parseSseFrame(frame)
          if (!event) continue
          if (event.name === 'start') {
            onStart && onStart(JSON.parse(event.data || '{}'))
          } else if (event.name === 'token') {
            onToken && onToken(JSON.parse(event.data || '{}').delta || '')
          } else if (event.name === 'done') {
            settled = true
            onDone && onDone(JSON.parse(event.data || '{}'))
          } else if (event.name === 'error') {
            settled = true
            const msg = JSON.parse(event.data || '{}').message || 'AI 回复失败'
            onError && onError(new Error(msg))
          }
        }
      }
    } finally {
      reader.releaseLock()
      // 流结束但未收到 done/error（异常断开）时补一个错误
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

/**
 * 拉取 AI 会话历史消息
 * @param {string} sessionId 会话标识
 * @returns {Promise<Array>} 消息数组（按时间正序）：{sender, messageType, content, imageUrl, createdAt}
 */
export function fetchAiChatHistory(sessionId) {
  return request.get(`/ai/patient-chat/${sessionId}/history`)
}

/**
 * 历史会话列表（患者端）
 * @returns {Promise<Array>} 会话列表：{sessionId, lastContent, lastTime}
 */
export function listAiChatSessions() {
  return request.get('/ai/patient-chat/sessions')
}

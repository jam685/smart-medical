/**
 * 智能预问诊业务接口模块（患者端）
 * 对应后端 PreConsultController（前缀 /pre-consults，PRE-01~04）：
 *   POST /pre-consults                     发送预问诊消息（AI 症状采集，返回患者侧回复）
 *   GET  /pre-consults/{sessionId}         获取会话历史
 *   GET  /pre-consults                     我的预问诊记录列表
 *   POST /pre-consults/{sessionId}/close   结束预问诊会话
 * AI 只做症状采集与挂号引导，不做诊断或开药。
 */
import request from './request'

/**
 * 患者发送预问诊消息（PRE-01）
 * 首次调用不传 sessionId 时，服务端会生成新会话。
 * @param {object} payload 消息载荷
 * @param {string} [payload.sessionId] 会话标识；为空则服务端生成新会话
 * @param {string} [payload.content]    文本内容（图片消息时可为空）
 * @param {string} [payload.imageUrl]   图片地址（非空即图片消息）
 * @returns {Promise<object>} 返回 {sessionId, reply, replies?, ...}，含患者侧 AI 引导回复
 */
export function sendPreConsultMessage(payload) {
  return request.post('/pre-consults', payload)
}

/**
 * 获取预问诊会话历史（PRE-02）
 * @param {string} sessionId 会话标识
 * @returns {Promise<Array>} 消息数组（按时间正序）：{id, sessionId, sender, role, content, imageUrl, createdAt}
 */
export function fetchPreConsultHistory(sessionId) {
  return request.get(`/pre-consults/${sessionId}`)
}

/**
 * 查看当前患者的预问诊记录列表（PRE-03）
 * @returns {Promise<Array>} 会话列表：{sessionId, startTime, endTime, status, messageCount, ...}
 */
export function listPreConsultSessions() {
  return request.get('/pre-consults')
}

/**
 * 结束预问诊会话（PRE-04）
 * @param {string} sessionId 会话标识
 * @returns {Promise<void>} 成功无返回体，失败由拦截器统一提示
 */
export function closePreConsultSession(sessionId) {
  return request.post(`/pre-consults/${sessionId}/close`)
}
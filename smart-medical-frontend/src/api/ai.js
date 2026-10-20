/**
 * AI 辅助分析业务接口模块（医生端）
 * 对应后端 AiController（前缀 /ai，V2.3 移除旧 /ai/chat，仅留医生端 4 个 AI 辅助接口）：
 *   POST /ai/clinical-analysis       病情分析
 *   POST /ai/examination-suggestions 检查建议
 *   POST /ai/reply-drafts            回复草稿生成
 *   POST /ai/image-analysis          图片/报告分析
 * 当前为 Demo 模拟实现，接入真实 FastAPI 后本层不变。
 * 约束：AI 输出仅供医生参考，须医生审阅后生效，不对患者直接展示。
 */
import request from './request'

/**
 * 医生病情分析（AI-1）
 * @param {object} payload 分析载荷
 * @param {string} payload.chiefComplaint 主诉
 * @param {string} payload.description     病情描述
 * @param {object} [payload.patientInfo]  患者信息（年龄/性别/病史等）
 * @returns {Promise<object>} {analysis, differentialDiagnosis?, suggestions?, confidence?, references?}
 */
export function clinicalAnalysis(payload) {
  return request.post('/ai/clinical-analysis', payload)
}

/**
 * 检查建议（AI-2）
 * @param {object} payload
 * @param {string} payload.chiefComplaint 主诉
 * @param {string} payload.description     病情描述
 * @returns {Promise<object>} {examinations: [...], rationale?, confidence?}
 */
export function examinationSuggestions(payload) {
  return request.post('/ai/examination-suggestions', payload)
}

/**
 * 回复草稿生成（AI-3，供医生编辑后发给患者）
 * @param {object} payload
 * @param {string} payload.chiefComplaint 主诉
 * @param {string} payload.description     病情描述
 * @returns {Promise<object>} {draft, tips?}
 */
export function replyDrafts(payload) {
  return request.post('/ai/reply-drafts', payload)
}

/**
 * 图片/报告分析（AI-4）
 * @param {object} payload
 * @param {number} payload.reportId 报告 ID
 * @returns {Promise<object>} {analysis, findings?, confidence?, references?}
 */
export function imageAnalysis(payload) {
  return request.post('/ai/image-analysis', payload)
}
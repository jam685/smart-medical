/**
 * 处方业务接口模块
 * 对应后端 PrescriptionController（前缀 /prescriptions）：
 *   POST /                开具处方（医生接诊时调用）
 *   GET  /by-visit/{id}   按就诊 ID 查询处方详情（含药品明细）
 *   GET  /my              查询当前医生开具的处方列表
 */
import request from './request'

/**
 * 开具处方
 * @param {object} payload 处方载荷
 * @param {number} payload.visitId    就诊进度 ID
 * @param {string} payload.diagnosis  诊断
 * @param {string} payload.advice     医嘱
 * @param {Array}  payload.items      药品明细数组：{drugName, specification, dosage, frequency, days, quantity, usageMethod, remark}
 * @returns {Promise<object>} 新建处方 VO（含明细列表）
 */
export function createPrescription(payload) {
  return request.post('/prescriptions', payload)
}

/**
 * 按就诊 ID 查询处方详情（含药品明细）
 * @param {number|string} visitId 就诊进度 ID
 * @returns {Promise<object|null>} 处方 VO，无处方时返回 null
 */
export function fetchPrescriptionByVisit(visitId) {
  return request.get(`/prescriptions/by-visit/${visitId}`)
}

/**
 * 查询当前医生开具的处方列表
 * @returns {Promise<Array>} 处方 VO 数组
 */
export function fetchMyPrescriptions() {
  return request.get('/prescriptions/my')
}

/**
 * 就诊进度接口模块
 * 对应后端 VisitController（前缀 /visits）：
 *   GET  /visits            就诊进度分页列表（按角色隔离 + 可选状态过滤）
 *   GET  /visits/{id}       就诊进度详情（含完整进度节点）
 *   PUT  /visits/{id}/status 推进就诊状态（仅医生/管理员，患者端不调用）
 */
import request from './request'

/**
 * 分页查询当前登录用户的就诊进度列表
 * @param {object} [params] 查询参数
 * @param {number} [params.page=1]   页码
 * @param {number} [params.size=100]  每页条数（列表页一次取足量，Tab 在前端过滤）
 * @param {string} [params.status]   状态过滤：WAITING 候诊 / CALLING 叫号 / IN_PROGRESS 就诊中 / FINISHED 已完成，不传为全部
 * @returns {Promise<object>} 分页对象 {total, list, page, size}，list 项为 VisitVO
 */
export function fetchVisits(params = { page: 1, size: 100 }) {
  return request.get('/visits', { params })
}

/**
 * 查询单条就诊进度详情
 * @param {number|string} id 就诊进度 ID
 * @returns {Promise<object>} VisitVO（含完整 progressNodes 节点数组）
 */
export function fetchVisitDetail(id) {
  return request.get(`/visits/${id}`)
}

/**
 * 推进就诊状态（患者端按业务规则不直接调用，仅供医生端/管理员后续使用）
 * @param {number|string} id     就诊进度 ID
 * @param {string}        status 目标状态：CALLING/IN_PROGRESS/FINISHED
 * @returns {Promise<void>}
 */
export function updateVisitStatus(id, status) {
  return request.put(`/visits/${id}/status`, { status })
}

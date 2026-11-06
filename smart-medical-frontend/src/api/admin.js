/**
 * 管理员端接口模块
 * 对应后端 AdminController（前缀 /admin）：
 *   POST /login                      管理员登录（employeeNo / phone / idCard 任一匹配，Demo 简化无密码）
 *   GET  /me                         当前管理员信息
 *   GET  /users?role=&page=&size=    分页查询用户（PATIENT→sm_patient / DOCTOR→sm_doctor），支持 keyword 模糊搜索
 *   PUT  /users/{id}/status?role=    启用 / 禁用用户
 *   POST /users?role=                新增用户（患者/医生，密码缺省 123456）
 *   PUT  /users/{id}?role=           修改用户（字段为 null 表示不修改，password 非空则重置密码）
 *   GET  /roles                      角色列表
 *   GET  /audit-logs                 审计日志（分页）
 */
import request from './request'

/**
 * 管理员登录（Demo 简化：employeeNo / phone / idCard 任一匹配即通过）
 * @param {object} payload 三者至少提供一个
 * @returns {Promise<{token:string, user:object}>}
 */
export function adminLogin(payload) {
  return request.post('/admin/login', payload)
}

/**
 * 查询当前管理员信息
 * @returns {Promise<object>} {id, employeeNo, realName, phone, idCard, status, role}
 */
export function fetchAdminMe() {
  return request.get('/admin/me')
}

/**
 * 分页查询用户列表（患者或医生）
 * @param {string} role PATIENT | DOCTOR
 * @param {object} params 查询参数
 * @param {number} [params.page=1] 页码
 * @param {number} [params.size=10] 每页条数
 * @param {string} [params.keyword] 模糊关键词（患者：用户名/姓名/手机号/身份证；医生：工号/姓名/手机号/科室）
 * @returns {Promise<object>} {total, list, page, size}
 */
export function fetchAdminUsers(role, params = {}) {
  return request.get('/admin/users', { params: { role, ...params } })
}

/**
 * 启用 / 禁用用户
 * @param {string} role PATIENT | DOCTOR
 * @param {number} id 用户 ID
 * @param {string} status ACTIVE | DISABLED
 * @returns {Promise<void>}
 */
export function updateUserStatus(role, id, status) {
  return request.put(`/admin/users/${id}/status`, { status }, { params: { role } })
}

/**
 * 新增用户（患者或医生）
 * @param {string} role PATIENT | DOCTOR
 * @param {object} payload 见 AdminUserRequest：通用 realName/gender/phone/status/password；
 *   PATIENT 附加 username/idCard；DOCTOR 附加 employeeNo/title/department/bio/onlineStatus
 * @returns {Promise<object>} 新建的用户视图
 */
export function createAdminUser(role, payload) {
  return request.post('/admin/users', payload, { params: { role } })
}

/**
 * 修改用户（患者或医生，字段为 null 不修改）
 * @param {string} role PATIENT | DOCTOR
 * @param {number} id 用户 ID
 * @param {object} payload 同 AdminUserRequest；password 提供非空值则重置密码
 * @returns {Promise<object>} 更新后的用户视图
 */
export function updateAdminUser(role, id, payload) {
  return request.put(`/admin/users/${id}`, payload, { params: { role } })
}

/**
 * 查询固定角色列表
 * @returns {Promise<string[]>} ['PATIENT','DOCTOR','ADMIN']
 */
export function fetchAdminRoles() {
  return request.get('/admin/roles')
}

/**
 * 分页查询审计日志
 * @param {object} [params] {page, size, role, action}
 * @returns {Promise<object>} {total, list, page, size}
 */
export function fetchAuditLogs(params = {}) {
  return request.get('/admin/audit-logs', { params })
}

/**
 * 审核一条审计日志（通过或驳回）
 * @param {number} id 审计日志 ID
 * @param {string} decision APPROVED 通过 / REJECTED 驳回（驳回自动禁用操作人账号）
 * @returns {Promise<void>}
 */
export function reviewAuditLog(id, decision) {
  return request.put(`/admin/audit-logs/${id}/review`, { decision })
}

/**
 * 获取数据质量巡检历史快照（按时间倒序）。
 * @returns {Promise<Array<{id, dataCount, labeledCount, missingRate, abnormalRate, updatedAt}>>}
 */
export function fetchDataQuality() {
  return request.get('/admin/data-quality')
}

/**
 * 手动触发一次数据质量巡检（立刻跑一遍 scheduler 逻辑，插入新快照）。
 * @returns {Promise<object>} 刚插入的快照
 */
export function runDataQualityCheck() {
  return request.post('/admin/data-quality/run')
}

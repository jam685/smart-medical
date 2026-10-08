/**
 * 认证相关接口模块
 * 封装患者/医生/管理员三端的登录请求；所有方法返回后端 Result.data 的内容。
 */
import request from './request'

/**
 * 患者账号密码登录
 * @param {string} username 患者用户名（如 zhangsan）
 * @param {string} password 明文密码（如 123456）
 * @returns {Promise<{token: string, user: object}>} JWT 令牌与用户信息（id/realName/phone/status）
 */
export function patientLogin(username, password) {
  return request.post('/patients/login', { username, password })
}

/**
 * 医生工号/姓名登录
 * @param {string} employeeNo 医生工号（如 D001）或姓名
 * @param {string} password    明文密码（如 123456）
 * @returns {Promise<{token: string, user: object}>} JWT 令牌与用户信息（id/employeeNo/realName/department/onlineStatus/title）
 */
export function doctorLogin(employeeNo, password) {
  return request.post('/doctors/login', { employeeNo, password })
}

/**
 * 管理员简化登录（Demo 模式，无需密码）
 * 后端接受 employeeNo / phone / idCard 任一身份标识匹配管理员账号
 * @param {string} identifier 工号 / 手机号 / 身份证号（如 A001）
 * @returns {Promise<{token: string, user: object}>} JWT 令牌与用户信息（id/employeeNo/realName/role）
 */
export function adminLogin(identifier) {
  return request.post('/admin/login', { employeeNo: identifier })
}

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
 * 患者注册（无需登录）——注册成功自动登录，返回与登录一致的 {token, user}
 * @param {object} data { username, password, realName, gender, phone, idCard }
 * @returns {Promise<{token: string, user: object}>}
 */
export function patientRegister(data) {
  return request.post('/patients/register', data)
}

/**
 * 患者忘记密码-发送验证码（无需登录）
 * 通过 用户名 + 身份证号 + 手机号 三要素校验身份后生成验证码。
 * 演示环境未接短信通道，验证码直接随响应返回（data.code），正式环境应走短信。
 * @param {string} username 用户名
 * @param {string} idCard   身份证号
 * @param {string} phone    手机号
 * @returns {Promise<{code: string}>} 验证码
 */
export function sendPasswordResetCode(username, idCard, phone) {
  return request.post('/patients/password-reset/code', { username, idCard, phone })
}

/**
 * 患者忘记密码-重置密码（无需登录）
 * 验证码校验通过后写入新密码。
 * @param {string} username    用户名
 * @param {string} code        验证码
 * @param {string} newPassword 新密码（6-64 位）
 */
export function resetPassword(username, code, newPassword) {
  return request.put('/patients/password-reset', { username, code, newPassword })
}

/**
 * 医生工号/姓名登录
 * @param {string} employeeNo 医生工号（如 D001）或姓名
 * @param {string} password    明文密码（如 123456）
 * @returns {Promise<{token: string, user: object}>} JWT 令牌与用户信息（id/employeeNo/realName/department/onlineStatus/title）
 */
export function doctorLogin(employeeNo, password) {
  // 同时传 username，后端支持工号精确匹配 + 姓名模糊匹配（否则姓名登录会走不通）
  return request.post('/doctors/login', { employeeNo, username: employeeNo, password })
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

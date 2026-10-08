/**
 * 患者「我的」相关接口模块
 * 对应后端 PatientController（前缀 /patients）
 */
import request from './request'

/**
 * 获取当前登录患者资料
 * @returns {Promise<object>} {id, username, realName, gender, phone, idCard(脱敏), avatar, status}
 */
export function getMyProfile() {
  return request.get('/patients/me')
}

/**
 * 更新当前患者资料（姓名、手机号、性别）
 * @param {{realName?: string, phone?: string, gender?: string}} data
 * @returns {Promise<object>} 更新后的完整资料
 */
export function updateMyProfile(data) {
  return request.put('/patients/me', data)
}

/**
 * 上传当前患者头像
 * @param {File} file 图片文件（jpg/jpeg/png/webp/gif，≤5MB）
 * @returns {Promise<{avatar: string}>} 上传成功后的头像访问路径
 */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/patients/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 修改当前患者密码
 * @param {{oldPassword: string, newPassword: string}} data
 * @returns {Promise<void>}
 */
export function changeMyPassword(data) {
  return request.put('/patients/me/password', data)
}

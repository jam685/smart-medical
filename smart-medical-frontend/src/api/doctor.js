/**
 * 医生「我的」相关接口模块
 * 对应后端 DoctorController（前缀 /doctors）
 */
import request from './request'

/**
 * 获取当前登录医生资料
 * @returns {Promise<object>} {id, employeeNo, realName, gender, title, phone, department, avatar, bio, onlineStatus, status}
 */
export function getMyProfile() {
  return request.get('/doctors/me')
}

/**
 * 更新当前医生资料（姓名、手机号、性别、职称、简介）
 * @param {{realName?: string, phone?: string, gender?: string, title?: string, bio?: string}} data
 * @returns {Promise<object>} 更新后的完整资料
 */
export function updateMyProfile(data) {
  return request.put('/doctors/me', data)
}

/**
 * 上传当前医生头像
 * @param {File} file 图片文件（jpg/jpeg/png/webp/gif，≤5MB）
 * @returns {Promise<{avatar: string}>} 上传成功后的头像访问路径
 */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/doctors/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

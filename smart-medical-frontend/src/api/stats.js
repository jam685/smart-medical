/**
 * 仪表盘统计业务接口模块
 * 对应后端 StatsController（前缀 /stats）：
 *   GET /stats/patient  患者端首页聚合数据（用户 + 5 项指标 + 即将到来挂号列表）
 *   GET /stats/doctor   医生端首页聚合数据（用户 + 7 项指标 + 今日接诊列表）
 *   GET /stats/admin    管理员端首页聚合数据（用户 + 7 项指标 + 最新审计日志）
 * 后端会显式校验角色，越权访问返回 FORBIDDEN。
 */
import request from './request'

/**
 * 获取患者端首页仪表盘数据
 * @returns {Promise<object>} 后端 PatientDashboard VO
 */
export function fetchPatientDashboard() {
  return request.get('/stats/patient')
}

/**
 * 获取医生端首页仪表盘数据
 * @returns {Promise<object>} 后端 DoctorDashboard VO
 */
export function fetchDoctorDashboard() {
  return request.get('/stats/doctor')
}

/**
 * 获取管理员端首页仪表盘数据
 * @returns {Promise<object>} 后端 AdminDashboard VO
 */
export function fetchAdminDashboard() {
  return request.get('/stats/admin')
}

/**
 * 挂号业务接口模块
 * 对应后端 AppointmentController（前缀 /appointments）：
 *   GET    /doctors          可挂号医生列表
 *   POST   /                创建挂号
 *   GET    /                 挂号分页列表
 *   GET    /{id}             挂号详情
 *   PUT    /{id}/cancel      取消挂号
 */
import request from './request'

/**
 * 查询可挂号医生（新建挂号页选医生用；仅返回在线医生）
 * @param {object} [params] 查询参数
 * @param {string} [params.department] 科室名，不传则返回全部科室医生
 * @returns {Promise<Array>} 医生数组：{id, employeeNo, realName, title, department, onlineStatus, fee}
 */
export function fetchAvailableDoctors(params = {}) {
  return request.get('/appointments/doctors', { params })
}

/**
 * 分页查询当前登录用户的挂号列表
 * @param {object} [params] 查询参数
 * @param {number} [params.page=1]   页码
 * @param {number} [params.size=100] 每页条数（列表页一次取足量数据，Tab 在前端过滤）
 * @param {string} [params.status]   状态过滤：PENDING/CONFIRMED/FINISHED/CANCELLED，不传为全部
 * @returns {Promise<object>} 分页对象 {total, list, page, size}
 */
export function fetchAppointments(params = { page: 1, size: 100 }) {
  return request.get('/appointments', { params })
}

/**
 * 查询单条挂号详情
 * @param {number|string} id 挂号单 ID
 * @returns {Promise<object>} 挂号 VO（含医生姓名、职称、单号、挂号费）
 */
export function fetchAppointmentDetail(id) {
  return request.get(`/appointments/${id}`)
}

/**
 * 创建挂号（提交后后端会同时生成就诊进度记录）
 * @param {object} payload 挂号载荷
 * @param {number} payload.doctorId        医生 ID
 * @param {string} payload.department      科室名
 * @param {string} payload.appointmentTime 就诊时间，格式 'yyyy-MM-dd HH:mm:ss'
 * @returns {Promise<object>} 新建成功的挂号实体（含自增 id）
 */
export function createAppointment(payload) {
  return request.post('/appointments', payload)
}

/**
 * 取消挂号（后端校验：仅本人、且单据非已取消/已完成状态）
 * @param {number|string} id 挂号单 ID
 * @returns {Promise<void>} 成功无返回体，失败由请求拦截器统一弹错
 */
export function cancelAppointment(id) {
  return request.put(`/appointments/${id}/cancel`)
}

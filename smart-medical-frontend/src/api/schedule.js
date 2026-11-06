/**
 * 号源与排班接口模块
 * 对应后端：
 *   GET  /schedules/slots?doctorId&date      患者选号（某医生某天的时段与余号）
 *   GET  /schedules/days?doctorId&startDate&days  患者看未来几天有没有号
 *   GET  /doctors/me/schedules               医生看自己的排班
 *   PUT  /doctors/me/schedules/{id}/status   医生停诊/恢复
 *   PUT  /doctors/me/schedules/{id}/capacity 医生加号/减号
 */
import request from './request'

/**
 * 查询某医生某天的号源
 * @param {number|string} doctorId 医生 ID
 * @param {string} date 日期 yyyy-MM-dd
 * @returns {Promise<Array>} 号源数组：{id, startTime, endTime, period, total, booked, remain, status, bookable}
 */
export function fetchScheduleSlots(doctorId, date) {
  return request.get('/schedules/slots', { params: { doctorId, date } })
}

/**
 * 查询某医生未来若干天的号源概览
 * @param {number|string} doctorId 医生 ID
 * @param {string} [startDate] 起始日期 yyyy-MM-dd，默认今天
 * @param {number} [days=7] 天数
 * @returns {Promise<Array>} 每天一条：{date, weekday, totalRemain, slotCount, hasBookable, slots}
 */
export function fetchScheduleDays(doctorId, startDate, days = 7) {
  return request.get('/schedules/days', { params: { doctorId, startDate, days } })
}

/**
 * 查询当前登录医生的排班
 * @param {string} [startDate] 起始日期 yyyy-MM-dd
 * @param {number} [days=7] 天数
 * @returns {Promise<Array>} 每天一条（含 slots 明细与已约数）
 */
export function fetchMySchedules(startDate, days = 7) {
  return request.get('/doctors/me/schedules', { params: { startDate, days } })
}

/**
 * 医生提交停诊申请（已有人预约时不能直接停诊，需管理员审批）
 * @param {number[]} slotIds 号源 ID
 * @param {string} reason 停诊原因
 */
export function submitSuspendRequest(slotIds, reason) {
  return request.post('/doctors/me/suspend-requests', { slotIds, reason })
}

/**
 * 我的停诊申请
 */
export function fetchMySuspendRequests() {
  return request.get('/doctors/me/suspend-requests')
}

/**
 * 停诊 / 恢复出诊
 * @param {number|string} slotId 号源 ID
 * @param {boolean} suspended true=停诊，false=恢复
 * @returns {Promise<object>} {id, status, total, booked}
 */
export function changeScheduleStatus(slotId, suspended) {
  return request.put(`/doctors/me/schedules/${slotId}/status`, { suspended })
}

/**
 * 加号 / 减号
 * @param {number|string} slotId 号源 ID
 * @param {number} totalCount 新的总号数
 * @returns {Promise<object>} {id, total, booked}
 */
export function changeScheduleCapacity(slotId, totalCount) {
  return request.put(`/doctors/me/schedules/${slotId}/capacity`, { totalCount })
}

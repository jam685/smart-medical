/**
 * 管理端排班接口（/admin/schedules）
 */
import request from './request'

/** 医生列表（可按科室筛，含未上线医生） */
export function fetchAdminDoctors(department) {
  return request.get('/admin/schedules/doctors', { params: { department } })
}

/** 某医生的排班（未来 N 天，含每时段已约数） */
export function fetchAdminBoard(doctorId, startDate, days = 7) {
  return request.get('/admin/schedules/board', { params: { doctorId, startDate, days } })
}

/** 停诊影响面预览 */
export function fetchSuspendImpact(slotIds) {
  return request.get('/admin/schedules/impact', { params: { slotIds: slotIds.join(',') } })
}

/** 批量停诊 / 恢复 */
export function changeAdminScheduleStatus(slotIds, suspended, force = false) {
  return request.put('/admin/schedules/status', { slotIds, suspended, force })
}

/** 管理端调整号数 */
export function changeAdminCapacity(slotId, totalCount) {
  return request.put(`/admin/schedules/${slotId}/capacity`, { totalCount })
}

/** 跨科室候选时段（改约目标） */
export function fetchAdminAlternatives(department, startDate, days = 7, excludeSlotIds = []) {
  return request.get('/admin/schedules/alternatives', {
    params: { department: department || undefined, startDate: startDate || undefined, days, excludeSlotIds: excludeSlotIds.join(',') },
  })
}

/** 停诊申请列表 */
export function fetchSuspendRequests(status) {
  return request.get('/admin/suspend-requests', { params: { status } })
}

/** 待审批数量 */
export function fetchPendingRequestCount() {
  return request.get('/admin/suspend-requests/pending-count')
}

/** 审批停诊申请（通过时会执行退号停诊） */
export function reviewSuspendRequest(id, approve, note) {
  return request.put(`/admin/suspend-requests/${id}/review`, { approve, note })
}

/** 一键改约并停诊：把受影响患者整体迁到目标时段 */
export function reschedulePatients(fromSlotIds, toSlotId, appointmentIds) {
  return request.post('/admin/schedules/reschedule', { fromSlotIds, toSlotId, appointmentIds })
}

/** 批量生成号源 */
export function generateSchedules(payload) {
  return request.post('/admin/schedules/generate', payload)
}

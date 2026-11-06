/**
 * 站内信接口：停诊/改约等业务通知
 */
import request from './request'

/** 最近 N 条通知 */
export function fetchNotifications(limit = 20) {
  return request.get('/notifications', { params: { limit } })
}

/** 未读数 */
export function fetchUnreadCount() {
  return request.get('/notifications/unread-count')
}

/** 单条已读 */
export function markNotificationRead(id) {
  return request.put(`/notifications/${id}/read`)
}

/** 全部已读 */
export function markAllNotificationsRead() {
  return request.put('/notifications/read-all')
}

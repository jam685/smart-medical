/**
 * 线上面诊业务接口模块
 * 对应后端 OnlineConsultController（前缀 /online-consults）：
 *   GET  /my-doctors                  我的挂号主治医生列表（问诊发起专用，按有效挂号派生）
 *   GET  /online-doctors              在线医生列表（脱敏视图，不含密码）
 *   POST /                            发起问诊
 *   GET  /                            问诊分页列表（按角色过滤）
 *   GET  /{id}                        问诊详情（含医生、患者脱敏信息）
 *   POST /{id}/messages               发送消息（患者补充描述）
 *   GET  /{id}/messages               消息记录（按时间正序）
 *   POST /{id}/confirm-reply          医生确认回复（写入消息流）
 *   POST /{id}/close                  关闭问诊
 *   GET  /{id}/supplement-records   补充材料记录与次数
 */
import request from './request'

/**
 * 查询患者自己的「挂号主治医生」列表（发起问诊时选择医生用）
 * 业务规则：患者只能对自己挂号成功（挂号状态非 CANCELLED）的主治医生发起在线问诊。
 * @returns {Promise<Array>} 医生数组：{id, employeeNo, realName, gender, title, department, avatar, bio, onlineStatus}
 */
export function fetchMyDoctors() {
  return request.get('/online-consults/my-doctors')
}

/**
 * 查询在线医生列表（患者发起问诊时选择医生用；仅返回 onlineStatus=ONLINE 的医生）
 * @param {object} [params] 查询参数
 * @param {string} [params.department] 科室名，不传则返回全部科室医生
 * @param {string} [params.keyword]    医生姓名关键词，不传则不按姓名过滤
 * @returns {Promise<Array>} 医生数组：{id, employeeNo, realName, gender, title, department, avatar, bio, onlineStatus}
 */
export function fetchOnlineDoctors(params = {}) {
  return request.get('/online-consults/online-doctors', { params })
}

/**
 * 患者发起线上问诊
 * @param {object} payload 问诊载荷
 * @param {number} payload.doctorId        接诊医生 ID（必须在线）
 * @param {string} payload.department      科室名
 * @param {string} payload.chiefComplaint  主诉
 * @param {string} payload.description     病情描述
 * @returns {Promise<object>} 新建的问诊实体 {id, patientId, doctorId, department, chiefComplaint, description, status, supplementCount, ...}
 */
export function createConsult(payload) {
  return request.post('/online-consults', payload)
}

/**
 * 分页查询当前登录用户的问诊列表（患者返回本人、医生返回本人接诊、管理员返回全部）
 * @param {object} [params] 查询参数
 * @param {number} [params.page=1]   页码
 * @param {number} [params.size=100] 每页条数（列表页一次取足量数据，Tab 在前端过滤）
 * @returns {Promise<object>} 分页对象 {total, list, page, size}，list 为问诊实体数组
 */
export function fetchConsults(params = { page: 1, size: 100 }) {
  return request.get('/online-consults', { params })
}

/**
 * 查询单条问诊详情（含归属校验；医生信息脱敏、患者身份证脱敏）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<object>} {consult: 问诊实体, doctor: 医生视图, patient: 患者脱敏视图}
 */
export function fetchConsultDetail(id) {
  return request.get(`/online-consults/${id}`)
}

/**
 * 患者发送消息（补充描述或上传图片）
 * @param {number|string} id 问诊 ID
 * @param {object} payload 消息载荷
 * @param {string} [payload.content]  文本内容
 * @param {string} [payload.imageUrl] 图片地址（非空即图片消息，会递增补充次数）
 * @returns {Promise<object>} 发送成功的消息实体；命中敏感词时返回 {blocked:true, message, keyword}
 */
export function sendConsultMessage(id, payload) {
  return request.post(`/online-consults/${id}/messages`, payload)
}

/**
 * 上传聊天图片（患者/医生均可，任意路径的图片文件）
 * 后端仅保存文件到 uploads/chat/，不写入报告表；返回 imageUrl 后前端以 IMAGE 消息发送。
 * @param {File} file 图片文件（jpg/jpeg/png/webp/gif）
 * @returns {Promise<object>} {imageUrl}
 */
export function uploadChatImage(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/online-consults/upload-image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 查询问诊消息记录（按时间正序，含系统消息、医患消息）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<Array>} 消息数组 {id, consultId, sender, messageType, content, imageUrl, sensitiveFlag, createdAt}
 */
export function fetchConsultMessages(id) {
  return request.get(`/online-consults/${id}/messages`)
}

/**
 * 医生确认并回复患者（写入消息流，患者端可见；仅接诊医生本人可操作）
 * @param {number|string} id 问诊 ID
 * @param {object} payload 回复载荷
 * @param {string} payload.finalReply 最终回复内容
 * @returns {Promise<void>} 成功无返回体，失败由请求拦截器统一弹错
 */
export function confirmConsultReply(id, payload) {
  return request.post(`/online-consults/${id}/confirm-reply`, payload)
}

/**
 * 关闭问诊（医生本人或管理员可操作；关闭时后端自动生成病历草稿）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<void>} 成功无返回体
 */
export function closeConsult(id) {
  return request.post(`/online-consults/${id}/close`)
}

/**
 * 查询补充材料记录与次数（仅统计图片上传）
 * @param {number|string} id 问诊 ID
 * @returns {Promise<Array>} 补充记录数组 {id, consultId, supplementNo, materialType, content, imageUrl, createdAt}
 */
export function fetchSupplementRecords(id) {
  return request.get(`/online-consults/${id}/supplement-records`)
}

/**
 * 检查报告业务接口模块
 * 对应后端 ReportController（前缀 /reports，V2.3 无 interpret 端点）：
 *   GET  /reports        报告分页列表（按角色过滤：患者看本人、医生/管理员看全部）
 *   GET  /reports/{id}   报告详情（含归属校验）
 *   POST /reports/upload 上传报告图片（multipart，字段 file）
 * 说明：V2.3 未提供报告解读/AI 解读接口，报告解读能力在医生端 AI 分析（/ai/image-analysis）中提供。
 */
import request from './request'

/**
 * 分页查询报告列表
 * @param {object} [params]
 * @param {number} [params.page=1]   页码
 * @param {number} [params.size=10]  每页条数
 * @returns {Promise<object>} 分页对象 {total, list, page, size}
 *   list 元素：{id, patientId, reportType, imageUrl, imageMeta, uploadedBy, status, createdAt}
 */
export function fetchReports(params = { page: 1, size: 10 }) {
  return request.get('/reports', { params })
}

/**
 * 查询单条报告详情（含归属校验）
 * @param {number|string} id 报告 ID
 * @returns {Promise<object>} 报告实体
 */
export function fetchReportDetail(id) {
  return request.get(`/reports/${id}`)
}

/**
 * 上传报告图片
 * @param {File} file 报告图片文件
 * @returns {Promise<number>} 新报告 ID
 */
export function uploadReport(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/reports/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
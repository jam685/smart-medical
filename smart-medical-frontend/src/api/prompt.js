/**
 * 提示词模板业务接口模块（管理员端）
 * 对应后端 PromptTemplateController（前缀 /admin/prompt-templates，PT-01~04）：
 *   GET    /admin/prompt-templates      模板列表
 *   POST   /admin/prompt-templates      新增模板
 *   PUT    /admin/prompt-templates/{id} 修改模板
 *   DELETE /admin/prompt-templates/{id} 删除模板
 */
import request from './request'

/**
 * 提示词模板列表（PT-01）
 * @returns {Promise<Array>} [{id, templateCode, templateName, scene: 'PATIENT'|'DOCTOR'|'ADMIN', variables, promptContent, enabled, createdAt, updatedAt}]
 */
export function fetchPromptTemplates() {
  return request.get('/admin/prompt-templates')
}

/**
 * 新增提示词模板（PT-02）
 * @param {object} payload {templateCode, templateName, scene, variables?, promptContent, enabled?}
 * @returns {Promise<object>} 新建的模板实体
 */
export function createPromptTemplate(payload) {
  return request.post('/admin/prompt-templates', payload)
}

/**
 * 修改提示词模板（PT-03）
 * @param {number|string} id 模板 ID
 * @param {object} payload   需要更新的字段
 * @returns {Promise<object>} 更新后的模板实体
 */
export function updatePromptTemplate(id, payload) {
  return request.put(`/admin/prompt-templates/${id}`, payload)
}

/**
 * 删除提示词模板（PT-04）
 * @param {number|string} id 模板 ID
 * @returns {Promise<void>} 成功无返回体
 */
export function deletePromptTemplate(id) {
  return request.delete(`/admin/prompt-templates/${id}`)
}
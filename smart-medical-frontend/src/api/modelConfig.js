/**
 * 模型配置接口模块（管理员端）
 * 对应后端 AdminController（前缀 /admin/model-configs，数据映射 sm_model_config 表）：
 *   GET    /admin/model-configs         配置列表（全量）
 *   GET    /admin/model-configs/active  当前生效的聊天模型（含来源：数据库/配置文件）
 *   POST   /admin/model-configs         新增配置（保存即热更新）
 *   PUT    /admin/model-configs/{id}    修改配置（保存即热更新）
 *   DELETE /admin/model-configs/{id}    删除配置（删除即热更新）
 */
import request from './request'

/**
 * 模型配置列表（字段与 sm_model_config 表一一对应）
 * @returns {Promise<Array>} [{id, configName, type, provider, modelName, apiUrl, apiKey, maxTokens, enabled, remark, updatedAt, createdAt}]
 */
export function fetchModelConfigs() {
  return request.get('/admin/model-configs')
}

/**
 * 当前生效的聊天模型信息
 * @returns {Promise<object>} {configId, configName, provider, modelName, apiUrl, maxTokens, source, apiKeyConfigured}
 */
export function fetchActiveModelConfig() {
  return request.get('/admin/model-configs/active')
}

/**
 * 新增模型配置
 * @param {object} payload {configName, type, provider, modelName, apiUrl, apiKey?, maxTokens?, enabled?, remark?}
 * @returns {Promise<object>} 新建的配置
 */
export function createModelConfig(payload) {
  return request.post('/admin/model-configs', payload)
}

/**
 * 修改模型配置
 * @param {number|string} id 配置 ID
 * @param {object} payload    待更新字段（apiKey 不传表示不修改）
 * @returns {Promise<object>} 更新后的配置
 */
export function updateModelConfig(id, payload) {
  return request.put(`/admin/model-configs/${id}`, payload)
}

/**
 * 删除模型配置
 * @param {number|string} id 配置 ID
 * @returns {Promise<void>}
 */
export function deleteModelConfig(id) {
  return request.delete(`/admin/model-configs/${id}`)
}

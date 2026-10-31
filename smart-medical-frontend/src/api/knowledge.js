/**
 * 医学知识库业务接口模块（管理员端）
 * 对应后端 KnowledgeController（前缀 /admin/knowledge，KB-01~06）：
 *   GET/POST/PUT/DELETE /admin/knowledge/bases            知识库 列表/新增/更新/删除
 *   GET/POST/PUT/DELETE /admin/knowledge/documents        知识文档 列表/新增/更新/删除
 *   GET/POST/PUT/DELETE /admin/knowledge/chunks           知识片段 列表/新增/更新/删除
 *   GET      /admin/knowledge/vector-indexes              向量化检索记录列表
 * 说明：任何增删改后端都会自动重建科室介绍库向量索引（热更新，无需重启）。
 */
import request from './request'

/**
 * 知识库列表（KB-01）
 * @returns {Promise<Array>} [{id, name, type: 'CLINICAL'|'DEPARTMENT', description, status, createdAt, updatedAt}]
 */
export function fetchKnowledgeBases() {
  return request.get('/admin/knowledge/bases')
}

/**
 * 新增医学知识库（KB-02）
 * @param {object} payload {name, type, description?, status?}
 * @returns {Promise<object>} 新建的知识库实体
 */
export function createKnowledgeBase(payload) {
  return request.post('/admin/knowledge/bases', payload)
}

/**
 * 更新医学知识库（名称/类型/说明/状态），后端自动重建向量索引（热更新）
 * @param {number} id 知识库 ID
 * @param {object} payload 待更新字段
 */
export function updateKnowledgeBase(id, payload) {
  return request.put(`/admin/knowledge/bases/${id}`, payload)
}

/**
 * 删除医学知识库（级联删除其下文档、片段与向量索引记录）
 * @param {number} id 知识库 ID
 */
export function deleteKnowledgeBase(id) {
  return request.delete(`/admin/knowledge/bases/${id}`)
}

/**
 * 知识文档列表（KB-03）
 * @param {object} [params]
 * @param {number} [params.knowledgeBaseId] 按知识库过滤
 * @returns {Promise<Array>} [{id, documentName, type, knowledgeBaseId, parseStatus, chunkCount, originalText, createdAt, updatedAt}]
 */
export function fetchKnowledgeDocuments(params = {}) {
  return request.get('/admin/knowledge/documents', { params })
}

/**
 * 新增知识文档（KB-04）
 * @param {object} payload {documentName, type, knowledgeBaseId, originalText?}
 * @returns {Promise<object>} 新建的文档实体
 */
export function createKnowledgeDocument(payload) {
  return request.post('/admin/knowledge/documents', payload)
}

/**
 * 更新知识文档，后端自动重建向量索引（热更新）
 * @param {number} id 文档 ID
 * @param {object} payload 待更新字段
 */
export function updateKnowledgeDocument(id, payload) {
  return request.put(`/admin/knowledge/documents/${id}`, payload)
}

/**
 * 删除知识文档（级联删除其下片段与向量索引记录）
 * @param {number} id 文档 ID
 */
export function deleteKnowledgeDocument(id) {
  return request.delete(`/admin/knowledge/documents/${id}`)
}

/**
 * 知识片段列表（KB-05）
 * @param {object} [params]
 * @param {number} [params.documentId] 按文档过滤
 * @returns {Promise<Array>} 片段数组
 */
export function fetchKnowledgeChunks(params = {}) {
  return request.get('/admin/knowledge/chunks', { params })
}

/**
 * 新增知识片段，后端自动重建向量索引（热更新）
 * @param {object} payload {documentId, chunkIndex, chunkTitle, content}
 * @returns {Promise<object>} 新建的片段实体
 */
export function createKnowledgeChunk(payload) {
  return request.post('/admin/knowledge/chunks', payload)
}

/**
 * 更新知识片段，后端自动重建向量索引（热更新）
 * @param {number} id 片段 ID
 * @param {object} payload 待更新字段
 */
export function updateKnowledgeChunk(id, payload) {
  return request.put(`/admin/knowledge/chunks/${id}`, payload)
}

/**
 * 删除知识片段，后端自动重建向量索引（热更新）
 * @param {number} id 片段 ID
 */
export function deleteKnowledgeChunk(id) {
  return request.delete(`/admin/knowledge/chunks/${id}`)
}

/**
 * 向量化检索记录列表（KB-06）
 * @param {object} [params]
 * @param {number} [params.sourceDocumentId] 按来源文档过滤
 * @returns {Promise<Array>} 向量索引记录数组
 */
export function fetchVectorIndexes(params = {}) {
  return request.get('/admin/knowledge/vector-indexes', { params })
}
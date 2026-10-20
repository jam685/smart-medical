/**
 * 医学知识库业务接口模块（管理员端）
 * 对应后端 KnowledgeController（前缀 /admin/knowledge，KB-01~06）：
 *   GET/POST /admin/knowledge/bases            知识库列表 / 新增
 *   GET/POST /admin/knowledge/documents        知识文档列表 / 新增
 *   GET/POST /admin/knowledge/chunks           知识片段列表 / 新增
 *   GET      /admin/knowledge/vector-indexes   向量化检索记录列表
 * 说明：bases 可为空列表用于 CRUD 展示；文档含原文字段 originalText。
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
 * 知识片段列表（KB-05）
 * @param {object} [params]
 * @param {number} [params.documentId] 按文档过滤
 * @returns {Promise<Array>} 片段数组
 */
export function fetchKnowledgeChunks(params = {}) {
  return request.get('/admin/knowledge/chunks', { params })
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
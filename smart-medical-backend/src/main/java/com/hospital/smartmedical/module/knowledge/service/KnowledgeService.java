package com.hospital.smartmedical.module.knowledge.service;

import com.hospital.smartmedical.module.knowledge.entity.KnowledgeChunk;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeDocument;
import com.hospital.smartmedical.module.knowledge.entity.MedicalKnowledgeBase;
import com.hospital.smartmedical.module.knowledge.entity.VectorIndex;

import java.util.List;

/**
 * 医学知识库服务接口（/admin/knowledge）。
 *
 * <p>管理端维护知识库、知识文档、知识片段与向量索引（KB-01~06，三层 CRUD + 列表）。
 * 任何增删改操作均会触发科室介绍库 RAG 向量索引热更新（DoctorRagService.rebuild）。</p>
 */
public interface KnowledgeService {

    /**
     * 知识库列表（KB-01）。
     */
    List<MedicalKnowledgeBase> listBases();

    /**
     * 新增医学知识库（KB-02）。
     */
    MedicalKnowledgeBase createBase(MedicalKnowledgeBase base);

    /**
     * 更新知识库（名称/类型/说明/状态），变更后触发 RAG 热更新。
     */
    void updateBase(Long id, MedicalKnowledgeBase base);

    /**
     * 删除知识库（级联删除其下文档、片段与向量索引记录），删除后触发 RAG 热更新。
     */
    void deleteBase(Long id);

    /**
     * 知识文档列表（KB-03），可按知识库过滤。
     */
    List<KnowledgeDocument> listDocuments(Long knowledgeBaseId);

    /**
     * 新增知识文档（KB-04）。
     */
    KnowledgeDocument createDocument(KnowledgeDocument document);

    /**
     * 更新知识文档（名称/类型/所属知识库/原文），变更后触发 RAG 热更新。
     */
    void updateDocument(Long id, KnowledgeDocument document);

    /**
     * 删除知识文档（级联删除其下片段与向量索引记录），删除后触发 RAG 热更新。
     */
    void deleteDocument(Long id);

    /**
     * 知识片段列表（KB-05），可按文档过滤。
     */
    List<KnowledgeChunk> listChunks(Long documentId);

    /**
     * 新增知识片段，同步刷新所属文档的片段数与解析状态，并触发 RAG 热更新。
     */
    KnowledgeChunk createChunk(KnowledgeChunk chunk);

    /**
     * 更新知识片段，同步刷新所属文档的解析状态，并触发 RAG 热更新。
     */
    void updateChunk(Long id, KnowledgeChunk chunk);

    /**
     * 删除知识片段（同步删除其向量索引记录），刷新所属文档的片段数，并触发 RAG 热更新。
     */
    void deleteChunk(Long id);

    /**
     * 向量化检索记录列表（KB-06），可按来源文档过滤。
     */
    List<VectorIndex> listVectorIndexes(Long sourceDocumentId);
}

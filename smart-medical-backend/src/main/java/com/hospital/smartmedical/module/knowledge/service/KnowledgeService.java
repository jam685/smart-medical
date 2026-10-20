package com.hospital.smartmedical.module.knowledge.service;

import com.hospital.smartmedical.module.knowledge.entity.KnowledgeChunk;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeDocument;
import com.hospital.smartmedical.module.knowledge.entity.MedicalKnowledgeBase;
import com.hospital.smartmedical.module.knowledge.entity.VectorIndex;

import java.util.List;

/**
 * 医学知识库服务接口（/admin/knowledge）。
 *
 * <p>管理端维护知识库、知识文档、知识片段与向量索引（KB-01~06，简单 CRUD + 列表）。</p>
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
     * 知识文档列表（KB-03），可按知识库过滤。
     */
    List<KnowledgeDocument> listDocuments(Long knowledgeBaseId);

    /**
     * 新增知识文档（KB-04）。
     */
    KnowledgeDocument createDocument(KnowledgeDocument document);

    /**
     * 知识片段列表（KB-05），可按文档过滤。
     */
    List<KnowledgeChunk> listChunks(Long documentId);

    /**
     * 向量化检索记录列表（KB-06），可按来源文档过滤。
     */
    List<VectorIndex> listVectorIndexes(Long sourceDocumentId);
}
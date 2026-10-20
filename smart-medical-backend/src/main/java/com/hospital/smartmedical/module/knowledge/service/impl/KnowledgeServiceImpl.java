package com.hospital.smartmedical.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeChunk;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeDocument;
import com.hospital.smartmedical.module.knowledge.entity.MedicalKnowledgeBase;
import com.hospital.smartmedical.module.knowledge.entity.VectorIndex;
import com.hospital.smartmedical.module.knowledge.mapper.KnowledgeChunkMapper;
import com.hospital.smartmedical.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.hospital.smartmedical.module.knowledge.mapper.MedicalKnowledgeBaseMapper;
import com.hospital.smartmedical.module.knowledge.mapper.VectorIndexMapper;
import com.hospital.smartmedical.module.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医学知识库服务实现（/admin/knowledge，KB-01~06）。
 *
 * <p>管理端对知识库、文档、片段与向量索引做简单 CRUD + 列表查询。</p>
 */
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final MedicalKnowledgeBaseMapper baseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorIndexMapper vectorIndexMapper;

    /** 知识库列表（KB-01）。 */
    @Override
    public List<MedicalKnowledgeBase> listBases() {
        return baseMapper.selectList(
                new LambdaQueryWrapper<MedicalKnowledgeBase>().orderByDesc(MedicalKnowledgeBase::getId));
    }

    /** 新增知识库（KB-02）。 */
    @Override
    public MedicalKnowledgeBase createBase(MedicalKnowledgeBase base) {
        if (base.getStatus() == null || base.getStatus().isBlank()) {
            base.setStatus("ACTIVE");
        }
        base.setCreatedAt(LocalDateTime.now());
        base.setUpdatedAt(LocalDateTime.now());
        baseMapper.insert(base);
        return base;
    }

    /** 知识文档列表（KB-03）。 */
    @Override
    public List<KnowledgeDocument> listDocuments(Long knowledgeBaseId) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        if (knowledgeBaseId != null) {
            wrapper.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
        }
        wrapper.orderByDesc(KnowledgeDocument::getId);
        return documentMapper.selectList(wrapper);
    }

    /** 新增知识文档（KB-04）。 */
    @Override
    public KnowledgeDocument createDocument(KnowledgeDocument document) {
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);
        return document;
    }

    /** 知识片段列表（KB-05）。 */
    @Override
    public List<KnowledgeChunk> listChunks(Long documentId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        if (documentId != null) {
            wrapper.eq(KnowledgeChunk::getDocumentId, documentId);
        }
        wrapper.orderByDesc(KnowledgeChunk::getId);
        return chunkMapper.selectList(wrapper);
    }

    /** 向量化检索记录列表（KB-06）。 */
    @Override
    public List<VectorIndex> listVectorIndexes(Long sourceDocumentId) {
        LambdaQueryWrapper<VectorIndex> wrapper = new LambdaQueryWrapper<>();
        if (sourceDocumentId != null) {
            wrapper.eq(VectorIndex::getSourceDocumentId, sourceDocumentId);
        }
        wrapper.orderByDesc(VectorIndex::getId);
        return vectorIndexMapper.selectList(wrapper);
    }
}
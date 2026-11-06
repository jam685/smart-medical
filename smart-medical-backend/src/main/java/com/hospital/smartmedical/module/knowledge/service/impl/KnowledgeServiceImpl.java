package com.hospital.smartmedical.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.module.ai.agent.rag.ClinicalRagService;
import com.hospital.smartmedical.module.ai.agent.rag.DoctorRagService;
import com.hospital.smartmedical.module.ai.agent.rag.FaqRagService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医学知识库服务实现（/admin/knowledge，KB-01~06）。
 *
 * <p>管理端对知识库、文档、片段做三层 CRUD + 列表查询；片段变更同步刷新文档
 * 片段数/解析状态；文档/片段增删改后调用 {@link DoctorRagService#rebuildDocument(Long)}
 * 按文档局部重建向量索引（仅重算变更文档，其余文档不动），知识库级变更调用
 * {@link DoctorRagService#rebuild()} 全量重建，均无需重启。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeServiceImpl implements KnowledgeService {

    private final MedicalKnowledgeBaseMapper baseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorIndexMapper vectorIndexMapper;
    /** 科室介绍库 RAG 服务（数据变更后触发热更新重建向量索引） */
    private final DoctorRagService doctorRagService;
    /** 临床指南库 RAG 服务（数据变更后触发热更新，全量重建内存索引） */
    private final ClinicalRagService clinicalRagService;
    /** FAQ 常见问题库 RAG 服务（数据变更后触发热更新） */
    private final FaqRagService faqRagService;

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
        doctorRagService.rebuild();
        clinicalRagService.rebuild();
        faqRagService.rebuild();
        return base;
    }

    /** 更新知识库（名称/类型/说明/状态），变更后触发 RAG 热更新。 */
    @Override
    public void updateBase(Long id, MedicalKnowledgeBase base) {
        base.setId(id);
        base.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(base);
        doctorRagService.rebuild();
        clinicalRagService.rebuild();
        faqRagService.rebuild();
    }

    /** 删除知识库（级联删除其下文档、片段与向量索引记录），删除后触发 RAG 热更新。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBase(Long id) {
        List<KnowledgeDocument> docs = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getKnowledgeBaseId, id));
        for (KnowledgeDocument doc : docs) {
            deleteDocumentInternal(doc.getId());
        }
        baseMapper.deleteById(id);
        doctorRagService.rebuild();
        clinicalRagService.rebuild();
        faqRagService.rebuild();
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

    /** 新增知识文档（KB-04），新文档无片段无需重建，直接返回。 */
    @Override
    public KnowledgeDocument createDocument(KnowledgeDocument document) {
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);
        return document;
    }

    /** 更新知识文档（名称/类型/所属知识库/原文），按文档局部重建向量索引。 */
    @Override
    public void updateDocument(Long id, KnowledgeDocument document) {
        // 文档可能被移动到其他知识库（DEPARTMENT→CLINICAL），向量索引由局部重建统一刷新
        document.setId(id);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        doctorRagService.rebuildDocument(id);
        clinicalRagService.rebuild();
        faqRagService.rebuild();
    }

    /** 删除知识文档（级联删除其下片段与向量索引记录），删除后移除该文档向量。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        deleteDocumentInternal(id);
        doctorRagService.removeDocument(id);
        clinicalRagService.rebuild();
        faqRagService.rebuild();
    }

    /**
     * 删除文档内部逻辑：先删其下片段与向量索引记录，再删文档本体。
     *
     * @param id 文档 ID
     */
    private void deleteDocumentInternal(Long id) {
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId, id));
        for (KnowledgeChunk chunk : chunks) {
            vectorIndexMapper.delete(new LambdaQueryWrapper<VectorIndex>()
                    .eq(VectorIndex::getChunkId, chunk.getId()));
            chunkMapper.deleteById(chunk.getId());
        }
        documentMapper.deleteById(id);
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

    /** 新增知识片段，刷新所属文档片段数与解析状态，并按所属文档局部重建向量索引。 */
    @Override
    public KnowledgeChunk createChunk(KnowledgeChunk chunk) {
        chunk.setCreatedAt(LocalDateTime.now());
        chunk.setUpdatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);
        refreshDocChunkInfo(chunk.getDocumentId());
        doctorRagService.rebuildDocument(chunk.getDocumentId());
        clinicalRagService.rebuild();
        faqRagService.rebuild();
        return chunk;
    }

    /** 更新知识片段，先清其向量索引记录，刷新文档解析状态，并按所属文档局部重建向量索引。 */
    @Override
    public void updateChunk(Long id, KnowledgeChunk chunk) {
        // 片段内容变更后旧向量记录失效，由局部重建统一处理
        chunk.setId(id);
        chunk.setUpdatedAt(LocalDateTime.now());
        chunkMapper.updateById(chunk);
        refreshDocChunkInfo(chunk.getDocumentId());
        doctorRagService.rebuildDocument(chunk.getDocumentId());
        clinicalRagService.rebuild();
        faqRagService.rebuild();
    }

    /** 删除知识片段（同步删除其向量索引记录），刷新文档片段数，并按所属文档局部重建向量索引。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChunk(Long id) {
        KnowledgeChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            return;
        }
        vectorIndexMapper.delete(new LambdaQueryWrapper<VectorIndex>()
                .eq(VectorIndex::getChunkId, id));
        chunkMapper.deleteById(id);
        refreshDocChunkInfo(chunk.getDocumentId());
        doctorRagService.rebuildDocument(chunk.getDocumentId());
        clinicalRagService.rebuild();
        faqRagService.rebuild();
    }

    /**
     * 刷新文档的片段数与解析状态（有片段=已解析，无片段=未解析）。
     *
     * @param documentId 文档 ID
     */
    private void refreshDocChunkInfo(Long documentId) {
        if (documentId == null) {
            return;
        }
        Long count = chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        doc.setChunkCount(count == null ? 0 : count.intValue());
        doc.setParseStatus(count != null && count > 0 ? "PARSED" : "FAILED");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);
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

package com.hospital.smartmedical.module.ai.agent.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeChunk;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeDocument;
import com.hospital.smartmedical.module.knowledge.entity.MedicalKnowledgeBase;
import com.hospital.smartmedical.module.knowledge.mapper.KnowledgeChunkMapper;
import com.hospital.smartmedical.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.hospital.smartmedical.module.knowledge.mapper.MedicalKnowledgeBaseMapper;
import dev.langchain4j.data.embedding.Embedding;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 临床指南知识库 RAG 服务（医生 AI 辅助分析用）。
 *
 * <p>数据源为「临床指南库」（sm_medical_knowledge_base.type=CLINICAL），结构：
 * 临床指南库 → 知识文档（单病种指南）→ 知识片段（诊断要素/鉴别诊断/治疗原则等）。
 * 医生端问诊分析时，以患者症状+对话摘要做语义检索，把最相关的指南片段注入提示词，
 * 辅助 LLM 判断信息充足度并给出病情分析/就诊建议/处方建议。</p>
 *
 * <p>实现为内存索引（CopyOnWriteArrayList 存片段向量），向量计算复用
 * {@link DoctorRagService#embedText(String)} 的同一向量模型，不重复加载模型、不接 Milvus。
 * 支持热更新：管理端对知识库/文档/片段增删改后调用 {@link #rebuild()} 全量重建
 * （临床指南数据量小、秒级），无需重启服务。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicalRagService {

    /** 临床指南库类型（V1.3 约定：CLINICAL） */
    private static final String CLINICAL_BASE_TYPE = "CLINICAL";

    /** 知识库 Mapper（数据源：临床指南库） */
    private final MedicalKnowledgeBaseMapper knowledgeBaseMapper;

    /** 知识文档 Mapper */
    private final KnowledgeDocumentMapper documentMapper;

    /** 知识片段 Mapper */
    private final KnowledgeChunkMapper chunkMapper;

    /** 科室介绍库 RAG 服务（复用其向量模型做文本向量化，避免重复加载模型） */
    private final DoctorRagService doctorRagService;

    /** 内存索引：临床指南片段向量（启动/热更新时全量替换，读多写少） */
    private final CopyOnWriteArrayList<IndexedChunk> index = new CopyOnWriteArrayList<>();

    /**
     * 启动时构建临床指南索引。失败不影响服务启动（索引置空，分析时 RAG 上下文为空）。
     */
    @PostConstruct
    public void init() {
        rebuild();
    }

    /**
     * 全量重建临床指南索引（启动与热更新共用入口）。
     *
     * <p>流程：CLINICAL 且 ACTIVE 的知识库 → 文档 → 片段 → 复用共享向量模型逐条向量化
     * → 全量替换内存索引。任一片段向量化失败仅跳过该片段，整体不中断。</p>
     */
    public synchronized void rebuild() {
        try {
            List<MedicalKnowledgeBase> bases = knowledgeBaseMapper.selectList(
                    new LambdaQueryWrapper<MedicalKnowledgeBase>()
                            .eq(MedicalKnowledgeBase::getType, CLINICAL_BASE_TYPE)
                            .eq(MedicalKnowledgeBase::getStatus, "ACTIVE"));

            List<IndexedChunk> fresh = new ArrayList<>();
            for (MedicalKnowledgeBase base : bases) {
                List<KnowledgeDocument> docs = documentMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDocument>()
                                .eq(KnowledgeDocument::getKnowledgeBaseId, base.getId()));
                for (KnowledgeDocument doc : docs) {
                    List<KnowledgeChunk> chunks = chunkMapper.selectList(
                            new LambdaQueryWrapper<KnowledgeChunk>()
                                    .eq(KnowledgeChunk::getDocumentId, doc.getId()));
                    for (KnowledgeChunk chunk : chunks) {
                        String text = joinChunkText(chunk);
                        if (text.isBlank()) {
                            continue;
                        }
                        Embedding embedding = doctorRagService.embedText(text);
                        if (embedding == null) {
                            continue;
                        }
                        fresh.add(new IndexedChunk(chunk.getId(), doc.getId(),
                                doc.getDocumentName(), chunk.getChunkTitle(), text,
                                embedding.vector()));
                    }
                }
            }

            index.clear();
            index.addAll(fresh);
            log.info("临床指南库向量化完成：{} 个片段（内存索引）", fresh.size());
        } catch (Exception e) {
            log.error("临床指南库向量化失败，RAG 临床上下文暂不可用：{}", e.getMessage(), e);
            index.clear();
        }
    }

    /**
     * 按症状/对话摘要语义检索最相关的临床指南片段。
     *
     * @param query 检索文本（患者症状摘要 + 对话内容）
     * @param topK  返回 topK 条候选
     * @return 指南片段匹配列表（按相似度降序），索引不可用或向量化失败时返回空列表
     */
    public List<ClinicalMatch> search(String query, int topK) {
        if (query == null || query.isBlank() || index.isEmpty()) {
            return List.of();
        }
        Embedding queryEmbedding = doctorRagService.embedText(query);
        if (queryEmbedding == null) {
            return List.of();
        }
        float[] qv = queryEmbedding.vector();
        List<ClinicalMatch> scored = new ArrayList<>();
        for (IndexedChunk item : index) {
            double score = cosine(qv, item.vector());
            scored.add(new ClinicalMatch(item.documentName(), item.chunkTitle(), item.text(), score));
        }
        scored.sort(Comparator.comparingDouble(ClinicalMatch::score).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    /**
     * 拼装医生分析提示词的临床指南上下文文本。
     *
     * @param query 检索文本（患者症状摘要 + 对话内容）
     * @param topK  返回候选条数
     * @return 上下文文本（如「· 《感冒发烧诊疗指南》诊断要素：…（相似度 0.83）」）；不可用时返回空串
     */
    public String buildRagContext(String query, int topK) {
        List<ClinicalMatch> matches = search(query, topK);
        if (matches.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("临床指南库检索到的相关诊疗参考：");
        for (ClinicalMatch match : matches) {
            sb.append("\n· 《").append(match.documentName()).append("》")
                    .append(match.chunkTitle() == null || match.chunkTitle().isBlank() ? "" : match.chunkTitle())
                    .append("：").append(match.text());
        }
        return sb.toString();
    }

    /**
     * 余弦相似度。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 相似度（-1~1）；长度不一致或零向量时返回 0
     */
    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /**
     * 拼装单条知识片段的检索文本（片段标题 + 片段内容）。
     *
     * @param chunk 知识片段
     * @return 检索用文本（标题与内容均空时返回空串）
     */
    private String joinChunkText(KnowledgeChunk chunk) {
        StringBuilder sb = new StringBuilder();
        if (chunk.getChunkTitle() != null && !chunk.getChunkTitle().isBlank()) {
            sb.append("标题：").append(chunk.getChunkTitle()).append("；");
        }
        if (chunk.getContent() != null && !chunk.getContent().isBlank()) {
            sb.append("内容：").append(chunk.getContent());
        }
        return sb.toString();
    }

    /**
     * 内存索引条目（片段元数据 + 向量）。
     */
    private record IndexedChunk(Long chunkId, Long documentId, String documentName,
                                String chunkTitle, String text, float[] vector) {
    }

    /**
     * 临床指南检索匹配结果。
     *
     * @param documentName 来源指南文档名
     * @param chunkTitle   片段标题
     * @param text         片段文本
     * @param score        相似度分（越高越相关）
     */
    public record ClinicalMatch(String documentName, String chunkTitle, String text, double score) {
    }
}

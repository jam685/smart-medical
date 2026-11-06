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
 * 常见问题库（FAQ）RAG 服务：为「流程/材料/费用/时间」类问题提供有依据的回答。
 *
 * <p>数据源：管理员端知识库中 {@code type=FAQ} 且 {@code status=ACTIVE} 的知识库 →
 * 知识文档 → 知识片段。与「科室介绍库」（医生推荐）和「临床指南库」（医生分析）并列，
 * 专供患者端客服对话使用。</p>
 *
 * <p>实现：内存索引（片段向量），向量化复用 {@link DoctorRagService#embedText(String)} 的同一模型，
 * 不额外加载模型。支持热更新：管理端对知识库/文档/片段增删改后调用 {@link #rebuild()}
 * （FAQ 数据量小、秒级）。</p>
 *
 * <p>与业务节点的区别：FAQ 只回答"是什么/怎么办/带什么"，不执行挂号、不查个人数据，
 * 因此可以安全地注入到客服对话提示词里。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaqRagService {

    /** FAQ 知识库类型 */
    private static final String FAQ_BASE_TYPE = "FAQ";

    /** 相似度下限：低于该值视为不相关，避免把无关片段塞进提示词 */
    private static final double MIN_SCORE = 0.45;

    private final MedicalKnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DoctorRagService doctorRagService;

    /** 内存索引：FAQ 片段（启动/热更新时全量替换，读多写少） */
    private final CopyOnWriteArrayList<IndexedChunk> index = new CopyOnWriteArrayList<>();

    /**
     * 启动时构建 FAQ 索引。失败不影响服务启动（索引置空，对话不注入 FAQ 上下文）。
     */
    @PostConstruct
    public void init() {
        rebuild();
    }

    /**
     * 全量重建 FAQ 索引（启动与热更新共用入口）。
     *
     * <p>流程：FAQ 且 ACTIVE 的知识库 → 文档 → 片段 → 逐条向量化 → 全量替换内存索引。
     * 单条片段向量化失败只跳过该片段，不中断整体。</p>
     */
    public synchronized void rebuild() {
        try {
            List<MedicalKnowledgeBase> bases = knowledgeBaseMapper.selectList(
                    new LambdaQueryWrapper<MedicalKnowledgeBase>()
                            .eq(MedicalKnowledgeBase::getType, FAQ_BASE_TYPE)
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
                        fresh.add(new IndexedChunk(doc.getDocumentName(), chunk.getChunkTitle(),
                                text, embedding.vector()));
                    }
                }
            }
            index.clear();
            index.addAll(fresh);
            log.info("FAQ 知识库索引构建完成：{} 个片段（来源 {} 个知识库）", fresh.size(), bases.size());
        } catch (Exception e) {
            log.error("FAQ 知识库索引构建失败，对话将不注入 FAQ 上下文：{}", e.getMessage(), e);
            index.clear();
        }
    }

    /**
     * 按问题语义检索最相关的 FAQ 片段（带相似度下限）。
     *
     * @param query 用户问题
     * @param topK  返回条数
     * @return 匹配列表（按相似度降序）；索引为空或全部低于阈值时返回空列表
     */
    public List<FaqMatch> search(String query, int topK) {
        if (query == null || query.isBlank() || index.isEmpty()) {
            return List.of();
        }
        Embedding queryEmbedding = doctorRagService.embedText(query);
        if (queryEmbedding == null) {
            return List.of();
        }
        float[] qv = queryEmbedding.vector();
        List<FaqMatch> scored = new ArrayList<>();
        for (IndexedChunk item : index) {
            double score = cosine(qv, item.vector());
            if (score >= MIN_SCORE) {
                scored.add(new FaqMatch(item.documentName(), item.chunkTitle(), item.text(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(FaqMatch::score).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    /**
     * 拼装注入客服提示词的 FAQ 上下文文本。
     *
     * @param query 用户问题
     * @param topK  注入条数
     * @return 上下文文本；无命中返回空串
     */
    public String buildContext(String query, int topK) {
        List<FaqMatch> matches = search(query, topK);
        if (matches.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (FaqMatch match : matches) {
            sb.append("· 《").append(match.documentName()).append("》")
                    .append(match.chunkTitle() == null || match.chunkTitle().isBlank() ? "" : match.chunkTitle())
                    .append("：").append(match.text())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 当前索引大小（管理员端观测用）。
     *
     * @return 片段数
     */
    public int size() {
        return index.size();
    }

    // ===================== 内部工具 =====================

    /**
     * 拼装单条知识片段的检索文本（标题 + 内容）。
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
     * 余弦相似度。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 相似度（-1~1）；长度不一致或零向量返回 0
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

    /** 内存索引条目。 */
    private record IndexedChunk(String documentName, String chunkTitle, String text, float[] vector) {
    }

    /**
     * FAQ 检索匹配结果。
     *
     * @param documentName 来源文档名
     * @param chunkTitle   片段标题
     * @param text         片段文本
     * @param score        相似度分
     */
    public record FaqMatch(String documentName, String chunkTitle, String text, double score) {
    }
}

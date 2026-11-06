package com.hospital.smartmedical.module.ai.agent.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.module.ai.agent.config.EmbeddingConfig;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeChunk;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeDocument;
import com.hospital.smartmedical.module.knowledge.entity.MedicalKnowledgeBase;
import com.hospital.smartmedical.module.knowledge.entity.VectorIndex;
import com.hospital.smartmedical.module.knowledge.mapper.KnowledgeChunkMapper;
import com.hospital.smartmedical.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.hospital.smartmedical.module.knowledge.mapper.MedicalKnowledgeBaseMapper;
import com.hospital.smartmedical.module.knowledge.mapper.VectorIndexMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医生科室介绍知识库 RAG 服务。
 *
 * <p>数据源为管理员端「科室介绍库」（sm_medical_knowledge_base.type=DEPARTMENT），
 * 结构：科室介绍库 → 知识文档 → 知识片段 → 向量化存储（内存 EmbeddingStore）。
 * 片段内容中出现哪位医生姓名即关联到该医生；患者带症状请求「帮我挂号」且未指定科室/医生时，
 * 按症状语义检索并推荐最匹配的可挂号医生。</p>
 *
 * <p>支持热更新：管理端对知识库/文档/片段做增删改后调用 {@link #rebuild()} 全量重建
 * （数据量小、秒级），无需重启服务。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorRagService {

    /** 科室介绍库类型（V1.3 约定：DEPARTMENT） */
    private static final String DEPT_BASE_TYPE = "DEPARTMENT";

    /** 医生 Mapper（用于片段内容按医生姓名关联医生实体） */
    private final DoctorMapper doctorMapper;

    /** 知识库 Mapper（数据源：科室介绍库） */
    private final MedicalKnowledgeBaseMapper knowledgeBaseMapper;

    /** 知识文档 Mapper */
    private final KnowledgeDocumentMapper documentMapper;

    /** 知识片段 Mapper */
    private final KnowledgeChunkMapper chunkMapper;

    /** 向量化检索记录 Mapper（sm_vector_index 元数据维护） */
    private final VectorIndexMapper vectorIndexMapper;

    /** 向量模型配置（ai.llm.embedding.*，api-key 空时降级本地 BGE） */
    private final EmbeddingConfig embeddingConfig;

    /** 内存向量库：科室介绍库片段（启动/热更新时构建，volatile 保证可见性） */
    private volatile EmbeddingStore<TextSegment> store;

    /** 当前向量模型 */
    private volatile EmbeddingModel embeddingModel;

    /** Milvus 向量库是否启用（ai.llm.embedding.milvus.enabled） */
    private boolean milvusEnabled;

    /** 当前向量模型的实际维度（由 probeModelDimension 在 rebuild 时从 embed 结果反推） */
    private volatile int modelDimension;

    /**
     * 启动时构建科室介绍库索引。
     * 任一步骤失败均不影响服务启动（store/embeddingModel 置空，推荐自动降级为原关键词流程）。
     */
    @PostConstruct
    public void init() {
        // 读取 Milvus 启用开关（构造注入的配置在 @PostConstruct 前已完成绑定）
        this.milvusEnabled = embeddingConfig.getMilvus() != null && embeddingConfig.getMilvus().isEnabled();
        rebuild();
    }

    /**
     * 重建科室介绍库向量索引（启动与全量热更新共用入口）。
     *
     * <p>流程：科室介绍库 → 文档 → 片段 → 向量化入向量库（Milvus 或内存）→ 重建 sm_vector_index 元数据。
     * 片段内容匹配医生真实姓名建立「片段→医生」关联（一个片段可关联多位医生）。
     * 任何一步失败时置空索引，RAG 推荐降级为不可用（由业务层走兜底流程）。</p>
     */
    public synchronized void rebuild() {
        try {
            // DJL 原生库（tokenizers DLL）默认解压到 {user.home}/.djl.ai，演示环境该目录可能无写权限，
            // 先切换缓存目录到可写路径再触发模型加载（仅本地 BGE 需要，切换本身无害）
            initDjlCacheDir();
            EmbeddingModel model = createEmbeddingModel();
            // 向量模型与向量库解耦：模型创建成功后立即保留，即便后续向量库连接失败，
            // 语义缓存等仅需向量化的场景仍可复用该模型（不因 Milvus 不可用而整体失效）
            this.embeddingModel = model;
            String modelName = model instanceof OpenAiEmbeddingModel
                    ? embeddingConfig.getModel() : "BGE-small-zh-v1.5";
            // 关键修复：维度从模型实际 embed 结果反推，不再依赖配置默认 1024。
            // 千问 text-embedding-v4 → 1024 维；本地 BGE-small-zh-v1.5 → 512 维；
            // 若用配置硬塞，Milvus 集合维度与模型向量不匹配会导致 insert 失败且极难定位。
            int dimension = probeModelDimension(model);
            this.modelDimension = dimension;
            log.info("向量模型维度反推：model={} → dimension={}", modelName, dimension);
            EmbeddingStore<TextSegment> newStore = createStore(dimension);
            // Milvus 集合可能存在历史数据，全量重建先清空集合内全部向量（内存库新建即为空，无需清空）
            if (milvusEnabled) {
                newStore.removeAll();
            }

            // 1) 医生姓名表：RAG 推荐以医生为粒度，片段内容中出现哪位医生姓名即关联到哪位医生
            List<Doctor> doctors = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                    .isNotNull(Doctor::getRealName)
                    .ne(Doctor::getRealName, ""));

            // 2) 科室介绍库（DEPARTMENT 且 ACTIVE）下的全部文档与片段
            List<MedicalKnowledgeBase> bases = knowledgeBaseMapper.selectList(
                    new LambdaQueryWrapper<MedicalKnowledgeBase>()
                            .eq(MedicalKnowledgeBase::getType, DEPT_BASE_TYPE)
                            .eq(MedicalKnowledgeBase::getStatus, "ACTIVE"));

            List<VectorIndex> vectorRows = new ArrayList<>();
            List<Long> deptDocIds = new ArrayList<>();
            Set<Long> doctorIdSet = new HashSet<>();
            int indexedChunkCount = 0;
            for (MedicalKnowledgeBase base : bases) {
                List<KnowledgeDocument> docs = documentMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDocument>()
                                .eq(KnowledgeDocument::getKnowledgeBaseId, base.getId()));
                for (KnowledgeDocument doc : docs) {
                    deptDocIds.add(doc.getId());
                    List<KnowledgeChunk> chunks = chunkMapper.selectList(
                            new LambdaQueryWrapper<KnowledgeChunk>()
                                    .eq(KnowledgeChunk::getDocumentId, doc.getId()));
                    for (KnowledgeChunk chunk : chunks) {
                        String text = joinChunkText(chunk);
                        if (text.isBlank()) {
                            continue;
                        }
                        // 匹配片段内容中出现的医生姓名（一个片段可关联多位医生，每位医生各生成一条索引）
                        List<Doctor> matched = matchDoctors(doctors, text);
                        if (matched.isEmpty()) {
                            continue;
                        }
                        for (Doctor d : matched) {
                            Map<String, String> meta = new LinkedHashMap<>();
                            meta.put("doctorId", String.valueOf(d.getId()));
                            meta.put("chunkId", String.valueOf(chunk.getId()));
                            meta.put("documentId", String.valueOf(doc.getId()));
                            TextSegment segment = TextSegment.from(text, new Metadata(meta));
                            Embedding embedding = model.embed(text).content();
                            newStore.add(embedding, segment);
                            doctorIdSet.add(d.getId());
                            vectorRows.add(buildVectorIndex(chunk, doc, modelName, embedding));
                        }
                        indexedChunkCount++;
                    }
                }
            }

            // 3) 维护 sm_vector_index 元数据：先清科室介绍库旧记录，再写入本次向量化结果
            if (!deptDocIds.isEmpty()) {
                vectorIndexMapper.delete(new LambdaQueryWrapper<VectorIndex>()
                        .in(VectorIndex::getSourceDocumentId, deptDocIds));
            }
            for (VectorIndex vi : vectorRows) {
                vectorIndexMapper.insert(vi);
            }

            this.embeddingModel = model;
            this.store = newStore;
            log.info("科室介绍库向量化完成：{} 个片段、{} 条向量记录、关联 {} 位医生（向量模型 {}，存储 {}）",
                    indexedChunkCount, vectorRows.size(), doctorIdSet.size(), modelName,
                    milvusEnabled ? "Milvus" : "内存");
        } catch (Exception e) {
            log.error("科室介绍库向量化失败，RAG 推荐暂不可用：{}", e.getMessage(), e);
            // 仅索引不可用；向量模型保留（供语义缓存等向量化场景复用）
            this.store = null;
        }
    }

    /**
     * 按文档局部重建向量索引（热更新：仅重算变更文档的片段，其余文档向量不动）。
     *
     * <p>管理端对某文档增删改片段/文档内容后调用：先按 documentId 过滤删除该文档在向量库中的
     * 全部旧向量，再重新向量化该文档现有片段并写入，同步刷新 sm_vector_index 元数据。
     * Milvus 支持按 metadata 过滤删除，仅局部生效；内存库无过滤删除能力，退化为全量重建。</p>
     *
     * @param documentId 变更的知识文档 ID
     */
    public synchronized void rebuildDocument(Long documentId) {
        if (documentId == null) {
            rebuild();
            return;
        }
        try {
            if (store == null || embeddingModel == null) {
                // 索引尚未构建（启动失败/未初始化），直接全量重建
                rebuild();
                return;
            }
            if (milvusEnabled) {
                // Milvus：按 documentId 过滤删除该文档旧向量（metadata.documentId 精确匹配）
                Filter docFilter = MetadataFilterBuilder.metadataKey("documentId").isEqualTo(String.valueOf(documentId));
                store.removeAll(docFilter);
            } else {
                // 内存库：无过滤删除能力，退化为全量重建（数据量小、秒级）
                rebuild();
                return;
            }
            // 重新向量化该文档现有片段
            KnowledgeDocument doc = documentMapper.selectById(documentId);
            if (doc == null) {
                // 文档已删除：仅清除元数据即可（上方已删向量）
                clearVectorRows(documentId);
                return;
            }
            List<Doctor> doctors = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                    .isNotNull(Doctor::getRealName)
                    .ne(Doctor::getRealName, ""));
            List<KnowledgeChunk> chunks = chunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunk>()
                            .eq(KnowledgeChunk::getDocumentId, documentId));
            List<VectorIndex> vectorRows = new ArrayList<>();
            int indexedChunkCount = 0;
            for (KnowledgeChunk chunk : chunks) {
                String text = joinChunkText(chunk);
                if (text.isBlank()) {
                    continue;
                }
                List<Doctor> matched = matchDoctors(doctors, text);
                if (matched.isEmpty()) {
                    continue;
                }
                for (Doctor d : matched) {
                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("doctorId", String.valueOf(d.getId()));
                    meta.put("chunkId", String.valueOf(chunk.getId()));
                    meta.put("documentId", String.valueOf(documentId));
                    TextSegment segment = TextSegment.from(text, new Metadata(meta));
                    Embedding embedding = embeddingModel.embed(text).content();
                    store.add(embedding, segment);
                    vectorRows.add(buildVectorIndex(chunk, doc, modelName(), embedding));
                }
                indexedChunkCount++;
            }
            // 刷新元数据：先删该文档旧记录再写入新记录
            clearVectorRows(documentId);
            for (VectorIndex vi : vectorRows) {
                vectorIndexMapper.insert(vi);
            }
            log.info("科室介绍库按文档局部重建完成：文档 {}、{} 个片段、{} 条向量记录", documentId, indexedChunkCount, vectorRows.size());
        } catch (Exception e) {
            log.error("科室介绍库按文档局部重建失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 删除指定文档在向量库中的全部向量及 sm_vector_index 元数据记录。
     *
     * <p>管理端删除知识文档时调用（文档删除后无需保留其向量）。Milvus 按 documentId
     * 过滤删除；内存库无过滤删除能力，退化为全量重建。文档不存在时静默返回。</p>
     *
     * @param documentId 待删除的知识文档 ID
     */
    public synchronized void removeDocument(Long documentId) {
        if (documentId == null || store == null) {
            return;
        }
        try {
            if (milvusEnabled) {
                Filter docFilter = MetadataFilterBuilder.metadataKey("documentId").isEqualTo(String.valueOf(documentId));
                store.removeAll(docFilter);
            } else {
                rebuild();
                return;
            }
            clearVectorRows(documentId);
            log.info("科室介绍库移除文档向量完成：文档 {}", documentId);
        } catch (Exception e) {
            log.error("科室介绍库移除文档向量失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 当前向量模型名（千问配置名或本地 BGE）。
     *
     * @return 模型名
     */
    private String modelName() {
        return embeddingModel instanceof OpenAiEmbeddingModel ? embeddingConfig.getModel() : "BGE-small-zh-v1.5";
    }

    /**
     * 清空指定文档在 sm_vector_index 中的元数据记录。
     *
     * @param documentId 知识文档 ID
     */
    private void clearVectorRows(Long documentId) {
        vectorIndexMapper.delete(new LambdaQueryWrapper<VectorIndex>()
                .eq(VectorIndex::getSourceDocumentId, documentId));
    }

    /**
     * 创建向量库实例（Milvus 或内存）。
     *
     * <p>Milvus 启用时连接 Milvus 并自动创建集合（dimension 由调用方从模型实际维度传入，
     * 不再从 embeddingConfig 默认 1024 硬读，避免千问 1024 / BGE 512 维度不匹配）；
     * 否则使用内存 InMemoryEmbeddingStore（数据随服务重启丢失，仅演示用）。</p>
     *
     * @param dimension 向量实际维度（由 probeModelDimension 从模型 embed 结果反推）
     * @return 可用的向量库实例
     */
    private EmbeddingStore<TextSegment> createStore(int dimension) {
        if (milvusEnabled) {
            MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                    .host(embeddingConfig.getMilvus().getHost())
                    .port(embeddingConfig.getMilvus().getPort())
                    .collectionName(embeddingConfig.getMilvus().getCollectionName())
                    .dimension(dimension);
            // 数据库名非空时指定（Milvus 默认库可不传）
            if (embeddingConfig.getMilvus().getDatabaseName() != null
                    && !embeddingConfig.getMilvus().getDatabaseName().isBlank()) {
                builder.databaseName(embeddingConfig.getMilvus().getDatabaseName());
            }
            log.info("科室介绍库使用 Milvus 向量库：{}:{}，集合 {}，维度 {}",
                    embeddingConfig.getMilvus().getHost(), embeddingConfig.getMilvus().getPort(),
                    embeddingConfig.getMilvus().getCollectionName(), dimension);
            return builder.build();
        }
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * 从模型实际 embed 结果反推向量维度。
     * 避免配置默认 1024 与本地 BGE 512 不匹配导致 Milvus insert 失败（被 catch 成"RAG 暂不可用"，极难定位）。
     *
     * @param model 向量模型实例
     * @return 实际维度（探测失败 fallback 到 embeddingConfig.getDimension() 或 1024）
     */
    private int probeModelDimension(EmbeddingModel model) {
        try {
            dev.langchain4j.data.embedding.Embedding emb = model.embed("维度探测").content();
            // LangChain4j Embedding.vector() 返回 float[]，直接用 .length 拿维度
            int dim = emb.vector().length;
            if (dim > 0) {
                return dim;
            }
        } catch (Exception e) {
            log.warn("向量维度探测失败：{}，fallback 到 embeddingConfig 默认", e.getMessage());
        }
        Integer configured = embeddingConfig.getDimension();
        return (configured != null && configured > 0) ? configured : 1024;
    }

    /**
     * 构建向量模型（千问 text-embedding-v4 或本地 BGE-small-zh-v1.5 降级）。
     *
     * <p>规则：ai.llm.embedding.api-key 非空 → 千问（DashScope OpenAI 兼容接口）；
     * 为空 → 本地 BGE-small-zh-v1.5（jar 内置 ONNX 模型，离线可用）。</p>
     *
     * @return 可用的向量模型实例
     */
    private EmbeddingModel createEmbeddingModel() {
        if (embeddingConfig.getApiKey() != null && !embeddingConfig.getApiKey().isBlank()) {
            var builder = OpenAiEmbeddingModel.builder()
                    .baseUrl(embeddingConfig.getBaseUrl())
                    .apiKey(embeddingConfig.getApiKey())
                    .modelName(embeddingConfig.getModel());
            if (embeddingConfig.getDimension() != null && embeddingConfig.getDimension() > 0) {
                builder.dimensions(embeddingConfig.getDimension());
            }
            log.info("科室介绍库使用千问向量模型：{}（维度 {}）", embeddingConfig.getModel(), embeddingConfig.getDimension());
            return builder.build();
        }
        log.info("未配置千问向量模型 API Key，科室介绍库降级使用本地 BGE-small-zh-v1.5");
        return new BgeSmallZhV15EmbeddingModel();
    }

    /**
     * 切换 DJL 原生库缓存目录到可写路径。
     *
     * <p>DJL 默认将 tokenizers 等原生库解压到 {user.home}/.djl.ai，演示环境沙箱对该目录
     * 无写权限（AccessDeniedException），导致原生库解压失败。此处若未显式配置
     * DJL_CACHE_DIR，则切换到可写目录：优先系统临时目录下 djl-cache，
     * 临时目录不可写时回退当前工作目录下的 .djl-cache。</p>
     */
    private void initDjlCacheDir() {
        String configured = System.getProperty("DJL_CACHE_DIR");
        if (configured != null && !configured.isBlank()) {
            // 用户已显式指定缓存目录，保持原配置不动
            return;
        }
        Path candidate = Paths.get(System.getProperty("java.io.tmpdir"), "djl-cache");
        if (!Files.isWritable(candidate.getParent())) {
            candidate = Paths.get("", ".djl-cache").toAbsolutePath();
        }
        System.setProperty("DJL_CACHE_DIR", candidate.toString());
        log.info("DJL 原生库缓存目录切换为：{}", candidate);
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
     * 匹配片段文本中出现的医生（按真实姓名包含匹配）。
     *
     * @param doctors 全部医生列表
     * @param text    片段检索文本
     * @return 命中医生列表（可能为空）
     */
    private List<Doctor> matchDoctors(List<Doctor> doctors, String text) {
        List<Doctor> matched = new ArrayList<>();
        for (Doctor d : doctors) {
            if (d.getRealName() != null && !d.getRealName().isBlank() && text.contains(d.getRealName())) {
                matched.add(d);
            }
        }
        return matched;
    }

    /**
     * 构建 sm_vector_index 元数据记录（不存向量数据，仅记录片段标题、来源文档、向量模型、维度与状态）。
     *
     * @param chunk     已向量化的知识片段
     * @param doc       片段所属文档
     * @param modelName 向量模型名
     * @param embedding 向量（取实际维度）
     * @return 待入库的向量索引记录
     */
    private VectorIndex buildVectorIndex(KnowledgeChunk chunk, KnowledgeDocument doc, String modelName, Embedding embedding) {
        VectorIndex vi = new VectorIndex();
        vi.setChunkId(chunk.getId());
        vi.setChunkTitle(chunk.getChunkTitle());
        vi.setSourceDocumentId(doc.getId());
        vi.setVectorModel(modelName);
        vi.setDimension(embedding.dimension());
        vi.setStatus("READY");
        LocalDateTime now = LocalDateTime.now();
        vi.setCreatedAt(now);
        vi.setUpdatedAt(now);
        return vi;
    }

    /**
     * 复用知识库同一向量模型对文本做向量化（语义缓存等复用方使用，避免重复加载模型）。
     *
     * @param text 待向量化文本
     * @return 文本向量；向量模型不可用或文本为空时返回 null
     */
    public Embedding embedText(String text) {
        EmbeddingModel model = embeddingModel;
        if (model == null || text == null || text.isBlank()) {
            return null;
        }
        return model.embed(text).content();
    }

    /**
     * 当前向量模型名（语义缓存版本命名空间等使用）。
     *
     * @return 模型名（千问配置名或本地 BGE）
     */
    public String embeddingModelName() {
        return modelName();
    }

    /**
     * 当前向量模型的实际维度（由 rebuild() 时 probeModelDimension 从 embed 结果反推，
     * 千问 text-embedding-v4 → 1024；本地 BGE-small-zh-v1.5 → 512）。
     * 语义缓存等其他 Milvus 集合应复用此维度，避免硬编码 1024 与降级 BGE 512 不匹配。
     *
     * @return 向量维度（rebuild 前返回 0，调用方应在 rebuild 完成后再取）
     */
    public int embeddingDimension() {
        return modelDimension;
    }

    /**
     * 按患者症状描述检索最匹配的医生（RAG 语义检索）。
     *
     * @param query 检索文本（症状描述 + 建议科室等）
     * @param topK  返回 topK 条候选
     * @return 医生匹配列表（含相似度分与文档摘要），索引不可用时返回空列表
     */
    public List<DoctorMatch> recommend(String query, int topK) {
        if (embeddingModel == null || store == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK)
                    .build();
            List<EmbeddingMatch<TextSegment>> matches = store.search(searchRequest).matches();
            List<DoctorMatch> result = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                Metadata metadata = segment.metadata();
                String doctorId = metadata == null ? null : metadata.getString("doctorId");
                Double score = match.score();
                if (doctorId == null || score == null) {
                    continue;
                }
                Doctor doctor = doctorMapper.selectById(Long.valueOf(doctorId));
                if (doctor == null) {
                    continue;
                }
                result.add(new DoctorMatch(doctor, score, segment.text()));
            }
            return result;
        } catch (Exception e) {
            log.error("RAG 医生推荐检索失败：{}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 按患者病情描述检索匹配的科室/医生，拼装为预问诊提示词上下文文本。
     *
     * <p>预问诊每轮调用：以最新症状描述语义检索知识库（科室介绍库→文档→片段），
     * 将最匹配的科室医生（科室/姓名/职称/擅长）格式化为提示词 {ragContext}，
     * 供 LLM 在推荐科室时参考知识库匹配结果。</p>
     *
     * @param query 检索文本（患者最新症状/摘要）
     * @param topK  返回候选条数
     * @return 上下文文本（如「· 呼吸科 张医生（主治医师）：擅长慢性咳嗽…」）；不可用时返回空串
     */
    public String buildRagContext(String query, int topK) {
        List<DoctorMatch> matches = recommend(query, topK);
        if (matches.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("知识库检索到的相关科室医生：");
        for (DoctorMatch match : matches) {
            Doctor d = match.doctor();
            sb.append("\n· ").append(nullToEmpty(d.getDepartment())).append(" ").append(nullToEmpty(d.getRealName()))
                    .append("（").append(d.getTitle() == null || d.getTitle().isBlank() ? "医生" : d.getTitle())
                    .append("）：").append(shortBio(d.getBio()));
        }
        return sb.toString();
    }

    /**
     * 截取医生擅长领域简介（超过 40 字截断展示）。
     *
     * @param bio 医生简介
     * @return 截断后的简介；为空时返回「暂未维护擅长领域介绍」
     */
    private String shortBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return "暂未维护擅长领域介绍";
        }
        return bio.length() > 40 ? bio.substring(0, 40) + "…" : bio;
    }

    /**
     * 空值安全转空串。
     *
     * @param s 原字符串
     * @return 非空原值，null 返回空串
     */
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 医生检索匹配结果（医生实体 + 相似度分 + 文档摘要）。
     *
     * @param doctor  命中的医生实体
     * @param score   相似度分（0~1，越高越匹配）
     * @param snippet 命中的知识库片段文本
     */
    public record DoctorMatch(Doctor doctor, double score, String snippet) {
    }
}

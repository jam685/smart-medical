package com.hospital.smartmedical.module.ai.agent.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hospital.smartmedical.module.ai.agent.config.EmbeddingConfig;
import com.hospital.smartmedical.module.ai.agent.rag.DoctorRagService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 语义缓存服务（P2 命中返回模式 / P1 影子模式）。
 *
 * <p>目标：当不同患者提出语义相似的通用健康问题时，复用已有回答以节省 LLM token。
 * 对患者端小林客服（cs_ 会话）的每条提问做一次向量检索：
 * <ul>
 *   <li>shadow=true（影子模式）：只记录「若开启会不会命中」的统计与日志，不返回缓存答案；</li>
 *   <li>shadow=false（P2 命中返回）：相似度 ≥ 阈值且 Redis 答案未过期时，直接回放缓存答案，
 *       跳过图流转与 LLM 调用；答案缺失（TTL 过期）则清理陈旧向量并按未命中走正常回复。</li>
 * </ul>
 * 合格问答在本轮结束后异步沉淀：向量入 Milvus（检索键），答案正文入 Redis（带 TTL）。</p>
 *
 * <p>安全边界（医疗场景硬约束）：
 * <ul>
 *   <li>只缓存两类问题：流程/指引类（FLOW，默认 TTL 30 天）与通用健康常识类（COMMON，默认 TTL 3 天）；</li>
 *   <li>涉及症状判断/用药/治疗/诊断的问题（SENSITIVE）与无法归类的问题（UNKNOWN）一律不缓存；</li>
 *   <li>含患者数据/业务意图（就诊进度、报告、病历、处方等）或手机号/身份证的提问一律跳过；</li>
 *   <li>读取时再做一次二次校验（分类白名单、个人信息、全局敏感词、可选免责声明），防止规则变更或脏数据进入回复。</li>
 * </ul></p>
 *
 * <p>存储：向量检索复用 Milvus（集合 ai.semantic-cache.collection，独立于知识库集合），
 * Milvus 不可用时降级内存向量库（此时答案虽存 Redis 但向量重启即失，等价冷启动）；
 * 答案正文存 Redis（key=sm:semcache:ans:{向量ID}，TTL 按分类取值）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticCacheService {

    /**
     * 缓存版本命名空间前缀。
     * v3：引入「分类白名单 + 按类 TTL」。
     * v4：引入「回复来源过滤（只缓存客服对话节点的回复）」，旧版本中可能混入业务节点
     *     回复（如"推荐医生列表"）的条目整体失效。
     */
    private static final String NS_VERSION = "v4";

    /** Redis 答案 key 前缀（sm:semcache:ans:{向量ID}） */
    private static final String ANSWER_KEY_PREFIX = "sm:semcache:ans:";

    /** Redis 答案 key 索引集合（clear 时批量删除用） */
    private static final String KEYS_SET = "sm:semcache:keys";

    /** 写入幂等锁前缀（同一问题并发写入只保留一条向量） */
    private static final String LOCK_PREFIX = "sm:semcache:lock:";

    /** 幂等锁有效期（分钟）：覆盖一次异步写入耗时即可，过期后允许重试 */
    private static final long LOCK_TTL_MINUTES = 10L;

    /** 问题向量本地缓存容量（Caffeine，避免每次请求都调一次向量模型） */
    private static final int QUERY_CACHE_SIZE = 5000;

    /** 单次孤儿清理最多扫描的 key 数（防止一次任务跑太久） */
    private static final int MAX_CLEAN_PER_RUN = 200;

    /** 手机号（PII，出现即不缓存） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    /** 18 位身份证号（PII，出现即不缓存） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[0-9Xx]");

    /**
     * 业务/个性化关键词：命中任一即视为不可缓存（避免缓存命中绕过权限与实时数据）。
     *
     * <p>注意：不再拦截「挂号」「预约」——它们是流程类问题的高频词，是否可以缓存
     * 交由 {@link #classify(String)} 判定（流程类可缓存，泛化的挂号动作不可缓存）。</p>
     */
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "我的", "报告", "进度", "病历", "处方", "缴费", "退款", "订单",
            "就诊卡", "叫号", "接诊", "排班", "余额", "密码", "身份证", "手机号", "电话", "地址");

    /** 低质量回答标记：出现即不写入缓存 */
    private static final Set<String> REJECT_MARKERS = Set.of(
            "抱歉", "无法回答", "暂时", "请稍后", "回复失败", "连接已断开");

    /** 医疗敏感词：涉及用药/治疗/诊断，命中即禁止缓存（医疗安全边界） */
    private static final List<String> MEDICAL_SENSITIVE_WORDS = List.of(
            "药", "剂量", "副作用", "不良反应", "治疗", "诊断", "确诊", "手术", "输液",
            "抗生素", "激素", "癌", "肿瘤", "怀孕", "哺乳", "婴儿", "新生儿", "过敏", "处方");

    /** 流程/指引类关键词：可缓存，TTL 较长 */
    private static final List<String> FLOW_WORDS = List.of(
            "流程", "怎么挂", "如何挂", "预约流程", "就诊须知", "需要带", "带什么", "几点",
            "上班时间", "怎么走", "在哪", "报销", "医保", "发票", "取号", "签到", "报告怎么",
            "体检流程", "住院", "出院", "入院", "押金", "退费", "退号", "改约", "复诊", "挂号费");

    /** 通用常识类关键词：可缓存，TTL 较短 */
    private static final List<String> COMMON_WORDS = List.of(
            "什么科", "哪个科", "挂哪科", "看什么科", "体检", "疫苗", "预防", "饮食", "运动",
            "睡眠", "作息", "喝水", "休息", "卫生", "传染", "隔离", "洗手", "口罩", "注意");

    /** 免责声明标记：开启 require-disclaimer 后，缺失这些标记的答案不予返回 */
    private static final List<String> DISCLAIMER_MARKERS = List.of(
            "仅供参考", "以医生", "医嘱", "就医", "面诊", "及时就诊", "复诊", "线下", "医院就诊");

    /**
     * 缓存类别：决定「是否可缓存」与「TTL」。
     */
    public enum Category {
        /** 流程/指引类（挂号流程、就诊须知、医保报销）：可缓存，TTL 较长 */
        FLOW,
        /** 通用健康常识（挂什么科、体检注意）：可缓存，TTL 较短 */
        COMMON,
        /** 涉及症状判断/用药/治疗：禁止缓存 */
        SENSITIVE,
        /** 无法分类：禁止缓存（保守策略） */
        UNKNOWN
    }

    /** 向量库（Milvus 或内存降级），懒加载后就绪 */
    private volatile EmbeddingStore<TextSegment> store;

    /** 问题向量本地缓存（ns + 归一化问题 → 向量），避免命中路径每次都要调向量模型 */
    private volatile Cache<String, Embedding> queryVectorCache;

    /** 知识库 RAG 服务（复用其向量模型，避免重复加载 BGE） */
    private final DoctorRagService doctorRagService;

    /** 向量/ Milvus 连接配置 */
    private final EmbeddingConfig embeddingConfig;

    /** Redis（P2：答案正文 + TTL 存储） */
    private final StringRedisTemplate redisTemplate;

    /** JSON 序列化（答案载荷） */
    private final ObjectMapper objectMapper;

    @Value("${ai.semantic-cache.enabled:true}")
    private boolean enabled;

    @Value("${ai.semantic-cache.shadow:true}")
    private boolean shadow;

    @Value("${ai.semantic-cache.threshold:0.92}")
    private double threshold;

    @Value("${ai.semantic-cache.collection:sm_semantic_cache}")
    private String collection;

    @Value("${ai.semantic-cache.top-k:3}")
    private int topK;

    /**
     * 命中返回采样率（0-100）：100=所有命中都真正返回，10=仅 10% 命中返回、其余只计入统计。
     * 用于灰度放量：先在 10% 观察缓存答案质量，再逐步升到 100。影子模式忽略此值。
     */
    @Value("${ai.semantic-cache.sample-rate:100}")
    private int sampleRate;

    /** 流程/指引类缓存 TTL（天）：流程类内容相对稳定，可以放长 */
    @Value("${ai.semantic-cache.flow-ttl-days:30}")
    private int flowTtlDays;

    /** 通用常识类缓存 TTL（天）：健康常识可能随指南更新，保守取短 */
    @Value("${ai.semantic-cache.common-ttl-days:3}")
    private int commonTtlDays;

    /** 是否强制要求缓存答案包含免责声明（默认关闭；开启后无免责声明的答案不予返回） */
    @Value("${ai.semantic-cache.require-disclaimer:false}")
    private boolean requireDisclaimer;

    /** 敏感词配置（沿用全局 sensitive.words，逗号分隔） */
    @Value("${sensitive.words:}")
    private String sensitiveWordsConfig;

    /** 解析后的敏感词集合（懒加载，避免每次请求都 split） */
    private volatile Set<String> sensitiveWordsCache;

    /** 参与检索引擎的提问数 */
    private final AtomicLong total = new AtomicLong();

    /** 影子命中数（相似度 ≥ 阈值） */
    private final AtomicLong hit = new AtomicLong();

    /** 实际返回给用户的缓存命中数（受 shadow 与 sample-rate 约束） */
    private final AtomicLong returned = new AtomicLong();

    /** 累计返回的答案字符数（用于估算节省的 token） */
    private final AtomicLong savedChars = new AtomicLong();

    /** 向量命中但答案已过期（Redis TTL 到期）的次数 */
    private final AtomicLong expiredHits = new AtomicLong();

    /** 写入失败的次数（向量库或 Redis 异常） */
    private final AtomicLong writeFailures = new AtomicLong();

    /** 写入缓存的问答数 */
    private final AtomicLong written = new AtomicLong();

    /** 被闸门/未就绪/低质量跳过的次数 */
    private final AtomicLong skipped = new AtomicLong();

    /** 问题向量本地缓存命中次数 */
    private final AtomicLong queryVectorHits = new AtomicLong();

    /** 问题向量本地缓存未命中次数（需调用向量模型） */
    private final AtomicLong queryVectorMisses = new AtomicLong();

    /** 因幂等锁跳过的重复写入次数 */
    private final AtomicLong dedupSkipped = new AtomicLong();

    /** 累计清理的孤儿向量数 */
    private final AtomicLong orphansCleaned = new AtomicLong();

    /** 分类为流程类并写入的次数 */
    private final AtomicLong writtenFlow = new AtomicLong();

    /** 分类为常识类并写入的次数 */
    private final AtomicLong writtenCommon = new AtomicLong();

    /** 因分类为敏感/未知而被拒绝写入的次数 */
    private final AtomicLong blockedByCategory = new AtomicLong();

    /** 命中但读取时二次校验未通过（分类/个人信息/敏感词）的次数 */
    private final AtomicLong rejectedOnRead = new AtomicLong();

    /**
     * 启动时异步初始化向量库（不阻塞应用启动；Milvus 不可用时降级内存库）。
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("语义缓存未启用（ai.semantic-cache.enabled=false）");
            return;
        }
        String mode = shadow ? "影子模式（只统计命中，不返回缓存）"
                : "命中返回模式（采样率 " + sampleRate + "%）";
        // 问题向量缓存：命中路径不再调用向量模型，把命中延迟从「一次网络往返」降到内存读取
        queryVectorCache = Caffeine.newBuilder()
                .maximumSize(QUERY_CACHE_SIZE)
                .expireAfterWrite(Duration.ofDays(Math.max(1, Math.max(flowTtlDays, commonTtlDays))))
                .build();
        log.info("语义缓存已启用：{}，阈值 {}，分类 TTL 流程 {} 天 / 常识 {} 天，集合 {}，问题向量缓存容量 {}，强制免责声明={}",
                mode, threshold, flowTtlDays, commonTtlDays, collection, QUERY_CACHE_SIZE, requireDisclaimer);
        CompletableFuture.runAsync(this::initStore)
                .exceptionally(ex -> {
                    log.warn("语义缓存向量库初始化异常：{}", ex.getMessage());
                    return null;
                });
    }

    /**
     * 初始化向量库：优先 Milvus（独立集合），失败或未启用时降级内存向量库。
     */
    private void initStore() {
        EmbeddingConfig.Milvus milvus = embeddingConfig.getMilvus();
        // 关键修复：复用 DoctorRagService 的实际模型维度（千问 1024 / BGE 512），
        // 不再硬编码 1024，避免 Milvus 集合维度与降级 BGE 向量不匹配。
        // DoctorRagService.rebuild() 在 @PostConstruct init() 中先跑完，所以 embeddingDimension() 能拿到正确值。
        int dimension = doctorRagService.embeddingDimension();
        if (dimension <= 0) {
            // DoctorRagService rebuild 可能还没跑完（两个 @PostConstruct 顺序有竞态），
            // 兜底：等 DoctorRagService 的 store 就绪后再重试一次 init。
            log.info("DoctorRagService 维度尚未就绪（{}），延迟重试 initStore", dimension);
            CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS).execute(this::initStore);
            return;
        }
        if (milvus != null && milvus.isEnabled()) {
            try {
                MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                        .host(milvus.getHost())
                        .port(milvus.getPort())
                        .collectionName(collection)
                        .dimension(dimension);
                if (milvus.getDatabaseName() != null && !milvus.getDatabaseName().isBlank()) {
                    builder.databaseName(milvus.getDatabaseName());
                }
                store = builder.build();
                log.info("语义缓存向量库：Milvus {}:{}，集合 {}，维度 {}",
                        milvus.getHost(), milvus.getPort(), collection, dimension);
                return;
            } catch (Exception e) {
                log.warn("语义缓存连接 Milvus 失败，降级内存向量库：{}", e.getMessage());
            }
        }
        store = new InMemoryEmbeddingStore<>();
        log.info("语义缓存使用内存向量库（重启后清空，等价冷启动）");
    }

    /**
     * 探针：对提问做一次向量检索。
     * 影子模式只记录命中统计；P2 模式命中时连带读出 Redis 缓存答案（未过期才返回）。
     *
     * @param question 用户原始提问
     * @return 探针结果（含命中答案，供命中返回与写回复用）；未启用/不可缓存时返回 null
     */
    public Probe probe(String question) {
        if (!enabled || question == null) {
            return null;
        }
        String normQuery = normalize(question);
        if (!eligible(normQuery)) {
            skipped.incrementAndGet();
            log.info("[语义缓存] 跳过（闸门拦截）：'{}'", abbreviate(normQuery));
            return null;
        }
        EmbeddingStore<TextSegment> s = store;
        if (s == null) {
            skipped.incrementAndGet();
            log.info("[语义缓存] 跳过（向量库未就绪）：'{}'", abbreviate(normQuery));
            return null;
        }
        // 命名空间在检索前确定：既作为问题向量缓存的 key 前缀，也用于命中后的 ns 过滤
        String targetNs = namespace();
        Embedding embedding = embedQuery(targetNs, normQuery);
        if (embedding == null) {
            skipped.incrementAndGet();
            log.info("[语义缓存] 跳过（向量化失败）：'{}'", abbreviate(normQuery));
            return null;
        }
        total.incrementAndGet();
        String tag = shadow ? "[语义缓存/影子]" : "[语义缓存]";
        try {
            List<EmbeddingMatch<TextSegment>> matches = s.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(Math.max(1, topK))
                    .minScore(threshold)
                    .build()).matches();
            // 按 ns 版本命名空间二次过滤（MilvusEmbeddingStore.search 不支持 metadata filter）：
            // 客户端手动过滤 metadata.ns == 当前 namespace()，避免模型版本变更后旧版本缓存误命中。
            if (matches != null && !matches.isEmpty()) {
                matches = matches.stream()
                        .filter(m -> {
                            String ns = m.embedded().metadata() != null
                                    ? m.embedded().metadata().getString("ns") : null;
                            return targetNs.equals(ns);
                        })
                        .toList();
            }
            if (matches.isEmpty()) {
                log.info("{} 未命中：'{}'", tag, abbreviate(normQuery));
                return new Probe(normQuery, embedding, false, 0.0, null);
            }
            EmbeddingMatch<TextSegment> best = matches.get(0);
            double score = best.score() == null ? 0.0 : best.score();
            // P2：命中返回模式需从 Redis 取答案正文；答案已过期（TTL）则清理陈旧向量按未命中处理
            String cachedAnswer = null;
            if (!shadow) {
                CachedAnswer stored = loadAnswer(best.embeddingId());
                if (stored == null) {
                    expiredHits.incrementAndGet();
                    removeStaleAsync(s, best.embeddingId());
                    log.info("{} 向量命中但答案已过期，清理后按未命中处理：'{}'", tag, abbreviate(normQuery));
                    return new Probe(normQuery, embedding, false, 0.0, null);
                }
                // 读取时二次校验：分类白名单、个人信息、敏感词（可选免责声明），任一不过即视为陈旧
                String invalidReason = validateCachedAnswer(stored);
                if (invalidReason != null) {
                    rejectedOnRead.incrementAndGet();
                    removeStaleAsync(s, best.embeddingId());
                    log.info("{} 命中但答案未通过二次校验（{}），清理后按未命中处理：'{}'",
                            tag, invalidReason, abbreviate(normQuery));
                    return new Probe(normQuery, embedding, false, 0.0, null);
                }
                if (sampled()) {
                    cachedAnswer = stored.answer();
                    returned.incrementAndGet();
                    savedChars.addAndGet(cachedAnswer.length());
                    log.info("{} 命中返回（similarity={}，采样率 {}%，分类 {}）：'{}'",
                            tag, String.format("%.3f", score), sampleRate, stored.category(), abbreviate(normQuery));
                } else {
                    log.info("{} 命中但未通过采样（sampleRate={}%），本轮按未命中处理：'{}'",
                            tag, sampleRate, abbreviate(normQuery));
                }
            }
            hit.incrementAndGet();
            log.info("{} 命中：similarity={}，问题='{}'，缓存问题='{}'",
                    tag, String.format("%.3f", score), abbreviate(normQuery), abbreviate(best.embedded().text()));
            return new Probe(normQuery, embedding, true, score, cachedAnswer);
        } catch (Exception e) {
            log.warn("{} 检索失败：{}", tag, e.getMessage());
            return null;
        }
    }

    /**
     * 写入缓存（同步执行；调用方负责异步包装）：向量入向量库，答案正文入 Redis（TTL 按分类）。
     *
     * <p>同步化的原因：写回滚、幂等锁、TTL 分类都是关键逻辑，异步会让单元测试变成
     * 「轮询等待」的脆弱测试。非阻塞由调用方（AiAgentService）用 CompletableFuture 保证。</p>
     *
     * <p>仅当探针通过闸门、回答质量合格且分类在白名单内才写入。</p>
     *
     * @param probe   探针结果（可为 null）
     * @param answer  本轮实际回复
     * @param intent  本轮识别意图（可为空）
     */
    public void writeAfterReply(Probe probe, String answer, String intent) {
        if (!enabled || probe == null || probe.embedding() == null || probe.hit()) {
            // 已命中相同问题的不重复写入
            return;
        }
        if (!cacheableAnswer(answer)) {
            skipped.incrementAndGet();
            return;
        }
        // 分类白名单：只有「流程/指引类」与「通用常识类」允许落缓存；症状/用药/未知一律拒绝
        Category category = classify(probe.normQuery());
        if (category != Category.FLOW && category != Category.COMMON) {
            blockedByCategory.incrementAndGet();
            log.info("[语义缓存] 不写入（分类 {} 不允许缓存）：'{}'", category, abbreviate(probe.normQuery()));
            return;
        }
        int ttl = category == Category.FLOW ? flowTtlDays : commonTtlDays;
        EmbeddingStore<TextSegment> s = store;
        if (s == null) {
            return;
        }
        String ns = namespace();
        // 写入幂等：同一问题并发未命中时只允许一个请求写入，避免向量库出现重复条目
        String lockKey = null;
        try {
            lockKey = LOCK_PREFIX + sha256(ns + "|" + probe.normQuery());
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", LOCK_TTL_MINUTES, TimeUnit.MINUTES);
            if (!Boolean.TRUE.equals(locked)) {
                dedupSkipped.incrementAndGet();
                log.info("[语义缓存] 跳过重复写入（同问题已被其他请求写入）：'{}'", abbreviate(probe.normQuery()));
                return;
            }
        } catch (Exception e) {
            // Redis 异常时不阻断缓存写入：退化为无锁，极端情况下可能重复一条，但不会丢缓存
            log.warn("[语义缓存] 幂等锁获取失败，降级为无锁写入：{}", e.getMessage());
            lockKey = null;
        }
        Metadata metadata = Metadata.from(Map.of(
                "ns", ns,
                "category", category.name(),
                "intent", intent == null ? "" : intent,
                "createdAt", LocalDateTime.now().toString()));
        TextSegment segment = TextSegment.from(probe.normQuery(), metadata);
        String vectorId = null;
        try {
            vectorId = s.add(probe.embedding(), segment);
            String payload = objectMapper.writeValueAsString(Map.of(
                    "answer", answer,
                    "category", category.name(),
                    "ttlDays", ttl,
                    "intent", intent == null ? "" : intent,
                    "question", probe.normQuery(),
                    "createdAt", LocalDateTime.now().toString()));
            String key = ANSWER_KEY_PREFIX + vectorId;
            redisTemplate.opsForValue().set(key, payload, Math.max(1, ttl), TimeUnit.DAYS);
            redisTemplate.opsForSet().add(KEYS_SET, key);
            written.incrementAndGet();
            if (category == Category.FLOW) {
                writtenFlow.incrementAndGet();
            } else {
                writtenCommon.incrementAndGet();
            }
            log.info("[语义缓存] 已沉淀问答：'{}'（ns={}，分类={}，TTL={}天）",
                    abbreviate(probe.normQuery()), ns, category, ttl);
        } catch (Exception e) {
            writeFailures.incrementAndGet();
            // 回滚半成品：向量已写入但答案落库失败时删除该向量，避免留下「有索引无答案」的孤儿条目
            if (vectorId != null) {
                try {
                    s.removeAll(List.of(vectorId));
                } catch (Exception re) {
                    log.warn("[语义缓存] 回滚向量失败（vectorId={}）：{}", vectorId, re.getMessage());
                }
            }
            releaseLock(lockKey);
            log.warn("[语义缓存] 写入失败：{}", e.getMessage());
        }
    }

    /**
     * 读取缓存答案正文与分类（Redis）；缺失/解析失败返回 null。
     *
     * @param vectorId 向量库记录 ID
     * @return 答案载荷（含分类），未命中返回 null
     */
    private CachedAnswer loadAnswer(String vectorId) {
        if (vectorId == null || vectorId.isBlank()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(ANSWER_KEY_PREFIX + vectorId);
            if (json == null || json.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);
            String answer = node.path("answer").asText(null);
            if (answer == null || answer.isBlank()) {
                return null;
            }
            String category = node.path("category").asText(null);
            return new CachedAnswer(answer, category);
        } catch (Exception e) {
            log.warn("[语义缓存] 答案读取失败（vectorId={}）：{}", vectorId, e.getMessage());
            return null;
        }
    }

    /**
     * 读取时二次校验：即使写入时校验过，也要防止「规则变更 / 历史脏数据 / 被人为改写」进入用户回复。
     *
     * <p>四道校验：分类必须在白名单内 → 答案不得含手机号/身份证 → 不得含全局敏感词
     * →（可选）必须带免责声明。任一不过则返回原因，由调用方清理该条缓存并走正常回复。</p>
     *
     * @param stored 缓存的答案载荷
     * @return null=校验通过；否则返回不通过原因（用于日志）
     */
    private String validateCachedAnswer(CachedAnswer stored) {
        Category category;
        try {
            category = Category.valueOf(stored.category() == null ? "UNKNOWN" : stored.category());
        } catch (Exception e) {
            return "分类非法：" + stored.category();
        }
        if (category != Category.FLOW && category != Category.COMMON) {
            return "分类不可返回：" + category;
        }
        String answer = stored.answer();
        if (PHONE_PATTERN.matcher(answer).find() || ID_CARD_PATTERN.matcher(answer).find()) {
            return "答案含个人信息";
        }
        if (containsAny(answer, sensitiveWords())) {
            return "答案含敏感词";
        }
        if (requireDisclaimer && !containsAny(answer, DISCLAIMER_MARKERS)) {
            return "缺少免责声明";
        }
        return null;
    }

    /**
     * 异步清理陈旧向量（答案 TTL 过期但向量仍在的情况），并删除孤儿 key 索引。
     *
     * @param s        向量库
     * @param vectorId 向量库记录 ID
     */
    private void removeStaleAsync(EmbeddingStore<TextSegment> s, String vectorId) {
        CompletableFuture.runAsync(() -> {
            try {
                s.removeAll(List.of(vectorId));
                redisTemplate.opsForSet().remove(KEYS_SET, ANSWER_KEY_PREFIX + vectorId);
            } catch (Exception e) {
                log.warn("[语义缓存] 陈旧向量清理失败（vectorId={}）：{}", vectorId, e.getMessage());
            }
        });
    }

    /**
     * 当前缓存统计（管理员端观察命中率用）。
     *
     * @return 统计 Map
     */
    public Map<String, Object> stats() {
        long t = total.get();
        long h = hit.get();
        long r = returned.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("shadow", shadow);
        m.put("mode", shadow ? "shadow（影子统计）" : "hit-return（命中返回）");
        m.put("sampleRate", sampleRate);
        m.put("threshold", threshold);
        m.put("flowTtlDays", flowTtlDays);
        m.put("commonTtlDays", commonTtlDays);
        m.put("requireDisclaimer", requireDisclaimer);
        m.put("collection", collection);
        m.put("storeReady", store != null);
        m.put("total", t);
        m.put("hit", h);
        m.put("hitRate", t == 0 ? 0.0 : Math.round(h * 10000.0 / t) / 10000.0);
        // 实际返回给用户的次数与占比（hit 包含采样未通过的部分，returned 才是真实省钱的次数）
        m.put("returned", r);
        m.put("returnedRate", t == 0 ? 0.0 : Math.round(r * 10000.0 / t) / 10000.0);
        // 节省估算：中文按 1 字 ≈ 1 token 粗估，仅用于趋势观察，不作为计费依据
        m.put("savedChars", savedChars.get());
        m.put("estimatedSavedTokens", savedChars.get());
        m.put("expiredHits", expiredHits.get());
        m.put("writeFailures", writeFailures.get());
        // 幂等与维护：dedupSkipped=因并发锁跳过的重复写入；orphansCleaned=已清理的过期向量
        m.put("dedupSkipped", dedupSkipped.get());
        m.put("orphansCleaned", orphansCleaned.get());
        // 分类治理：写入按类别拆分统计，被拒绝写入与读取二次校验不通过都要可见
        m.put("writtenFlow", writtenFlow.get());
        m.put("writtenCommon", writtenCommon.get());
        m.put("blockedByCategory", blockedByCategory.get());
        m.put("rejectedOnRead", rejectedOnRead.get());
        // 问题向量缓存：命中次数越高，命中路径越接近毫秒级
        m.put("queryVectorCacheSize", queryVectorCache == null ? 0L : queryVectorCache.estimatedSize());
        m.put("queryVectorHits", queryVectorHits.get());
        m.put("queryVectorMisses", queryVectorMisses.get());
        m.put("written", written.get());
        m.put("skipped", skipped.get());
        return m;
    }

    /**
     * 清空缓存向量、Redis 答案与统计（内存库直接重建，Milvus 集合清空）。
     */
    public void clear() {
        EmbeddingStore<TextSegment> s = store;
        if (s != null) {
            try {
                s.removeAll();
            } catch (Exception e) {
                log.warn("语义缓存清空失败：{}", e.getMessage());
            }
        }
        if (embeddingConfig.getMilvus() == null || !embeddingConfig.getMilvus().isEnabled()) {
            store = new InMemoryEmbeddingStore<>();
        }
        try {
            Set<String> keys = redisTemplate.opsForSet().members(KEYS_SET);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(KEYS_SET);
        } catch (Exception e) {
            log.warn("语义缓存 Redis 答案清空失败：{}", e.getMessage());
        }
        total.set(0);
        hit.set(0);
        returned.set(0);
        savedChars.set(0);
        expiredHits.set(0);
        writeFailures.set(0);
        dedupSkipped.set(0);
        orphansCleaned.set(0);
        queryVectorHits.set(0);
        queryVectorMisses.set(0);
        writtenFlow.set(0);
        writtenCommon.set(0);
        blockedByCategory.set(0);
        rejectedOnRead.set(0);
        written.set(0);
        skipped.set(0);
        if (queryVectorCache != null) {
            queryVectorCache.invalidateAll();
        }
        log.info("语义缓存已清空（向量 + Redis 答案 + 统计）");
    }

    // ===================== 维护任务 =====================

    /**
     * 定时清理孤儿向量：Redis 答案已按 TTL 过期消失、但向量仍留在向量库中的条目。
     *
     * <p>闭环方式：写入时把答案 key 记入 KEYS_SET；Redis 到期后 key 自动消失而集合成员还在，
     * 因此「集合里有、Redis 里没有」就是孤儿。每小时扫描一批（上限 {@link #MAX_CLEAN_PER_RUN} 条），
     * 删向量并从集合中移除成员，避免向量库无限增长。</p>
     *
     * <p>容错：Redis 或向量库任一异常只记录日志，不影响主流程。</p>
     */
    @Scheduled(cron = "0 7 * * * *")
    public int cleanOrphanVectors() {
        if (!enabled) {
            return 0;
        }
        EmbeddingStore<TextSegment> s = store;
        if (s == null) {
            return 0;
        }
        try {
            Set<String> keys = redisTemplate.opsForSet().members(KEYS_SET);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            int checked = 0;
            int cleaned = 0;
            for (String key : keys) {
                if (checked >= MAX_CLEAN_PER_RUN) {
                    break;
                }
                checked++;
                Boolean exists = redisTemplate.hasKey(key);
                if (Boolean.TRUE.equals(exists)) {
                    continue;
                }
                String vectorId = key.startsWith(ANSWER_KEY_PREFIX)
                        ? key.substring(ANSWER_KEY_PREFIX.length()) : null;
                if (vectorId != null && !vectorId.isBlank()) {
                    try {
                        s.removeAll(List.of(vectorId));
                    } catch (Exception e) {
                        // 向量删除失败则保留成员，下轮重试
                        log.warn("[语义缓存] 孤儿向量删除失败（vectorId={}）：{}", vectorId, e.getMessage());
                        continue;
                    }
                }
                redisTemplate.opsForSet().remove(KEYS_SET, key);
                cleaned++;
            }
            orphansCleaned.addAndGet(cleaned);
            if (cleaned > 0) {
                log.info("[语义缓存] 孤儿向量清理完成：删除 {} 条（本次扫描 {} 条）", cleaned, checked);
            }
            return cleaned;
        } catch (Exception e) {
            log.warn("[语义缓存] 孤儿向量清理任务失败：{}", e.getMessage());
            return 0;
        }
    }

    // ===================== 内部工具 =====================

    /**
     * 问题向量化（带本地缓存）：同一问题在 TTL 内只调用一次向量模型。
     *
     * <p>价值：命中路径原本每次都要付一次 embedding 网络往返（约 1-2 秒），
     * 加缓存后命中只需内存查找，能把命中延迟压到毫秒级。</p>
     *
     * @param ns        当前命名空间（模型/版本变化时自然失效）
     * @param normQuery 归一化后的问题
     * @return 问题向量；向量模型不可用时返回 null
     */
    private Embedding embedQuery(String ns, String normQuery) {
        Cache<String, Embedding> cache = queryVectorCache;
        if (cache == null) {
            return doctorRagService.embedText(normQuery);
        }
        String key = ns + "|" + normQuery;
        Embedding cached = cache.getIfPresent(key);
        if (cached != null) {
            queryVectorHits.incrementAndGet();
            return cached;
        }
        queryVectorMisses.incrementAndGet();
        Embedding embedding = doctorRagService.embedText(normQuery);
        if (embedding != null) {
            cache.put(key, embedding);
        }
        return embedding;
    }

    /**
     * SHA-256 摘要（十六进制），用于幂等锁 key，避免问题原文作为 key 过长/含特殊字符。
     *
     * @param text 原文
     * @return 64 位十六进制摘要
     */
    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 理论上不会失败；退化用 hashCode 保证功能可用
            return Integer.toHexString(text.hashCode());
        }
    }

    /**
     * 释放幂等锁（写入失败时调用，允许下次重试）。
     *
     * @param lockKey 锁 key（可为 null）
     */
    private void releaseLock(String lockKey) {
        if (lockKey == null) {
            return;
        }
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("[语义缓存] 释放幂等锁失败（{}）：{}", lockKey, e.getMessage());
        }
    }

    /**
     * 缓存分类：先判医疗敏感（用药/治疗/诊断），再判流程类，最后判通用常识类。
     *
     * <p>顺序很关键：「头疼吃什么药」同时含症状与用药词，必须先命中 SENSITIVE；
     * 「感冒发烧怎么挂号」含症状但也属流程咨询，只要不含用药/治疗词就归 FLOW。</p>
     *
     * @param question 归一化后的问题
     * @return 分类；无法归类返回 UNKNOWN（保守策略：不缓存）
     */
    static Category classify(String question) {
        if (question == null || question.isBlank()) {
            return Category.UNKNOWN;
        }
        if (containsAny(question, MEDICAL_SENSITIVE_WORDS)) {
            return Category.SENSITIVE;
        }
        if (containsAny(question, FLOW_WORDS)) {
            return Category.FLOW;
        }
        if (containsAny(question, COMMON_WORDS)) {
            return Category.COMMON;
        }
        return Category.UNKNOWN;
    }

    /**
     * 判断文本是否包含词表中的任意一个词。
     *
     * @param text  文本
     * @param words 词表
     * @return true=命中
     */
    private static boolean containsAny(String text, Collection<String> words) {
        if (text == null || words == null || words.isEmpty()) {
            return false;
        }
        for (String word : words) {
            if (word != null && !word.isEmpty() && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 全局敏感词集合（懒加载）：沿用 application.yml 的 sensitive.words，逗号或中文逗号分隔。
     *
     * @return 不可缓存的敏感词集合（配置为空时返回空集合）
     */
    private Set<String> sensitiveWords() {
        Set<String> cached = sensitiveWordsCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (sensitiveWordsCache == null) {
                sensitiveWordsCache = sensitiveWordsConfig == null || sensitiveWordsConfig.isBlank()
                        ? Set.of()
                        : Arrays.stream(sensitiveWordsConfig.split("[,，]"))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());
            }
            return sensitiveWordsCache;
        }
    }

    /**
     * 可缓存闸门：长度、PII、业务/个性化关键词、分类白名单、全局敏感词，任一不过即拦截。
     *
     * @param question 归一化后的问题
     * @return true=允许参与语义缓存
     */
    boolean eligible(String question) {
        if (question == null || question.length() < 4 || question.length() > 200) {
            return false;
        }
        if (PHONE_PATTERN.matcher(question).find() || ID_CARD_PATTERN.matcher(question).find()) {
            return false;
        }
        for (String kw : BLOCKED_KEYWORDS) {
            if (question.contains(kw)) {
                return false;
            }
        }
        // 分类白名单：只有流程类与常识类可以进缓存，敏感/未知一律拒绝
        Category category = classify(question);
        if (category != Category.FLOW && category != Category.COMMON) {
            return false;
        }
        // 全局敏感词：命中也拒绝
        return !containsAny(question, sensitiveWords());
    }

    /**
     * 回答质量校验：过短、拒答类、含 PII 的回答不写入缓存。
     *
     * @param answer 本轮回复
     * @return true=可写入缓存
     */
    private boolean cacheableAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String a = answer.trim();
        if (a.length() < 60) {
            return false;
        }
        if (PHONE_PATTERN.matcher(a).find() || ID_CARD_PATTERN.matcher(a).find()) {
            return false;
        }
        for (String marker : REJECT_MARKERS) {
            if (a.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 版本命名空间：向量模型变化即整体失效（P2 再加入模板版本与知识库版本）。
     *
     * @return 命名空间字符串
     */
    String namespace() {
        return NS_VERSION + ":" + doctorRagService.embeddingModelName();
    }

    /**
     * 本轮是否允许返回缓存答案（灰度采样）。
     *
     * <p>规则：影子模式永不返回；采样率 ≥ 100 全量返回；≤ 0 全部只统计；
     * 其余按百分比随机决定。采样未通过时仍计入命中统计，但不返回答案，
     * 这样放量前后命中率口径一致，只有「实际返回率」会变。</p>
     *
     * @return true=本轮可返回缓存答案
     */
    boolean sampled() {
        if (shadow) {
            return false;
        }
        if (sampleRate >= 100) {
            return true;
        }
        if (sampleRate <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < sampleRate;
    }

    /**
     * 问题归一化：全角转半角、合并空白、去掉首尾标点与语气符号。
     *
     * @param text 原始文本
     * @return 归一化文本
     */
    String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.trim().toCharArray()) {
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else if (c == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString()
                .replaceAll("\\s+", " ")
                .replaceAll("[。！？!?~～\\s]+$", "")
                .trim();
    }

    /**
     * 日志截断，避免超长问题刷屏。
     *
     * @param text 文本
     * @return 最长 60 字
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 60 ? text : text.substring(0, 60) + "…";
    }

    /**
     * 探针结果。
     *
     * @param normQuery    归一化问题
     * @param embedding    问题向量
     * @param hit          是否达到命中阈值
     * @param score        最高相似度（未命中为 0）
     * @param cachedAnswer 缓存答案正文（影子模式或未命中为 null；P2 命中时有值）
     */
    public record Probe(String normQuery, Embedding embedding, boolean hit, double score, String cachedAnswer) {
    }

    /**
     * 缓存答案载荷。
     *
     * @param answer   答案正文
     * @param category 写入时的分类（FLOW / COMMON），读取时用于白名单校验
     */
    public record CachedAnswer(String answer, String category) {
    }
}

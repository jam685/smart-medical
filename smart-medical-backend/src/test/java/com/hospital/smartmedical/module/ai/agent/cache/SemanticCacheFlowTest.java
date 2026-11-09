package com.hospital.smartmedical.module.ai.agent.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.module.ai.agent.rag.DoctorRagService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 语义缓存链路测试：内存向量库 + 手写假 Redis，覆盖「写入 → 命中 → 校验 → 回滚 → 清理」。
 *
 * <p>不启动 Spring、不连外部服务；writeAfterReply 已同步化，断言无需轮询等待。</p>
 */
class SemanticCacheFlowTest {

    private static final Embedding EMB = Embedding.from(new float[]{0.12f, 0.23f, 0.34f, 0.45f});

    /** 必须明显超过 60 字，否则会被答案质量闸门拦下（这是本测试第一次跑挂的原因） */
    private static final String LONG_ANSWER =
            "这是用于自动化测试的缓存答案正文，长度必须明显超过六十个字符才能通过质量闸门。"
                    + "这里再补充一些说明文字，确保长度足够，避免因为答案过短而被缓存拒绝。"
                    + "本回答仅用于测试，实际诊疗请以医生判断为准。";

    private static final String KEYS_SET = "sm:semcache:keys";
    private static final String ANSWER_PREFIX = "sm:semcache:ans:";

    private FakeRedis redis;
    private InMemoryEmbeddingStore<TextSegment> store;
    private SemanticCacheService service;

    @BeforeEach
    void setUp() {
        redis = new FakeRedis();
        store = new InMemoryEmbeddingStore<>();

        DoctorRagService rag = mock(DoctorRagService.class);
        when(rag.embeddingModelName()).thenReturn("test-model");
        when(rag.embedText(anyString())).thenReturn(EMB);

        service = new SemanticCacheService(rag, null, redis.template, new ObjectMapper(), (Runnable r) -> r.run());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "shadow", false);
        ReflectionTestUtils.setField(service, "threshold", 0.92);
        ReflectionTestUtils.setField(service, "topK", 3);
        ReflectionTestUtils.setField(service, "sampleRate", 100);
        ReflectionTestUtils.setField(service, "flowTtlDays", 30);
        ReflectionTestUtils.setField(service, "commonTtlDays", 3);
        ReflectionTestUtils.setField(service, "requireDisclaimer", false);
        ReflectionTestUtils.setField(service, "sensitiveWordsConfig", "政治,暴力,自杀");
        ReflectionTestUtils.setField(service, "collection", "test_collection");
        ReflectionTestUtils.setField(service, "store", store);
    }

    @Test
    @DisplayName("流程类写入后：TTL=30 天，再次提问命中并返回同一答案")
    void writeFlowThenHit() {
        String question = "挂号流程是什么";
        service.writeAfterReply(probe(question), LONG_ANSWER, null);

        assertEquals(1, redis.ttlLog.size(), "应写入一条答案");
        assertEquals(30L, redis.ttlLog.get(0)[0], "流程类 TTL 应为 30 天");
        assertEquals(TimeUnit.DAYS.toSeconds(30), redis.ttlLog.get(0)[1]);

        SemanticCacheService.Probe hit = service.probe(question);
        assertTrue(hit.hit(), "同问题应命中");
        assertNotNull(hit.cachedAnswer());
        assertEquals(LONG_ANSWER, hit.cachedAnswer());
    }

    @Test
    @DisplayName("常识类写入：TTL=3 天")
    void writeCommonTtl() {
        service.writeAfterReply(probe("体检前需要注意什么"), LONG_ANSWER, null);
        assertEquals(1, redis.ttlLog.size());
        assertEquals(3L, redis.ttlLog.get(0)[0], "常识类 TTL 应为 3 天");
    }

    @Test
    @DisplayName("用药类问题：不写入缓存，计入 blockedByCategory")
    void sensitiveNotWritten() {
        service.writeAfterReply(probe("头疼吃什么药"), LONG_ANSWER, null);

        assertTrue(redis.ttlLog.isEmpty(), "不应写 Redis");
        assertEquals(0, vectorCount(), "不应写向量");
        assertEquals(1L, stat("blockedByCategory"));
    }

    @Test
    @DisplayName("读取二次校验：答案含手机号时拒绝返回并计数")
    void rejectAnswerWithPii() {
        String id = store.add(EMB, TextSegment.from("体检注意", Metadata.from(Map.of(
                "ns", service.namespace(), "category", "COMMON"))));
        redis.values.put(ANSWER_PREFIX + id,
                "{\"answer\":\"请拨打13800138000咨询，本回答仅供参考。\",\"category\":\"COMMON\"}");

        SemanticCacheService.Probe probe = service.probe("体检前需要注意什么");

        assertFalse(probe.hit(), "含个人信息的答案不得返回");
        assertNull(probe.cachedAnswer());
        assertEquals(1L, stat("rejectedOnRead"));
    }

    @Test
    @DisplayName("答案过期：向量命中但 Redis 无答案，按未命中处理")
    void expiredAnswer() {
        store.add(EMB, TextSegment.from("体检注意", Metadata.from(Map.of(
                "ns", service.namespace(), "category", "COMMON"))));

        SemanticCacheService.Probe probe = service.probe("体检前需要注意什么");

        assertFalse(probe.hit());
        assertEquals(1L, stat("expiredHits"));
    }

    @Test
    @DisplayName("写入失败：回滚已写入的向量，避免孤儿条目")
    void rollbackOnRedisFailure() {
        redis.failOnSet = true;
        service.writeAfterReply(probe("挂号流程是什么"), LONG_ANSWER, null);

        assertEquals(0, vectorCount(), "写入失败应回滚向量");
        assertEquals(1L, stat("writeFailures"));
    }

    @Test
    @DisplayName("幂等锁：同一问题重复写入只保留一条向量")
    void dedupeByLock() {
        String question = "挂号流程是什么";
        service.writeAfterReply(probe(question), LONG_ANSWER, null);
        service.writeAfterReply(probe(question), LONG_ANSWER, null);

        assertEquals(1, vectorCount());
        assertEquals(1L, stat("dedupSkipped"));
    }

    @Test
    @DisplayName("孤儿清理：答案已过期但向量仍在时清理成功")
    void cleanOrphanVectors() {
        String id = store.add(EMB, TextSegment.from("体检注意", Metadata.from(Map.of(
                "ns", service.namespace(), "category", "COMMON"))));
        redis.sets.put(KEYS_SET, new HashSet<>(Set.of(ANSWER_PREFIX + id)));

        int cleaned = service.cleanOrphanVectors();

        assertEquals(1, cleaned);
        assertEquals(0, vectorCount(), "孤儿向量应被删除");
        assertTrue(redis.sets.getOrDefault(KEYS_SET, Set.of()).isEmpty(), "索引成员应被移除");
    }

    // ===================== 工具 =====================

    private SemanticCacheService.Probe probe(String question) {
        return new SemanticCacheService.Probe(question, EMB, false, 0.0, null);
    }

    private long stat(String key) {
        Object value = service.stats().get(key);
        return value == null ? -1 : ((Number) value).longValue();
    }

    private int vectorCount() {
        return store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMB)
                .maxResults(100)
                .minScore(0.0)
                .build()).matches().size();
    }

    /**
     * 内存版 Redis：用动态代理实现 ValueOperations / SetOperations，
     * 避免 Mockito 对变长参数（add/remove）的匹配与强转问题。
     */
    static class FakeRedis {

        final Map<String, String> values = new ConcurrentHashMap<>();
        final Map<String, Set<String>> sets = new ConcurrentHashMap<>();
        final List<long[]> ttlLog = new ArrayList<>();
        final StringRedisTemplate template;
        boolean failOnSet = false;

        FakeRedis() {
            ValueOperations<String, String> valueOps =
                    (ValueOperations<String, String>) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{ValueOperations.class},
                            (proxy, method, args) -> {
                                switch (method.getName()) {
                                    case "get":
                                        return values.get((String) args[0]);
                                    case "set":
                                        if (failOnSet) {
                                            throw new IllegalStateException("redis unavailable");
                                        }
                                        values.put((String) args[0], (String) args[1]);
                                        if (args.length == 4) {
                                            long amount = ((Number) args[2]).longValue();
                                            TimeUnit unit = (TimeUnit) args[3];
                                            ttlLog.add(new long[]{amount, unit.toSeconds(amount)});
                                        }
                                        return null;
                                    case "setIfAbsent":
                                        if (failOnSet) {
                                            throw new IllegalStateException("redis unavailable");
                                        }
                                        String key = (String) args[0];
                                        if (values.containsKey(key)) {
                                            return Boolean.FALSE;
                                        }
                                        values.put(key, (String) args[1]);
                                        return Boolean.TRUE;
                                    case "toString":
                                        return "fakeValueOps";
                                    case "hashCode":
                                        return System.identityHashCode(proxy);
                                    case "equals":
                                        return proxy == args[0];
                                    default:
                                        return null;
                                }
                            });

            SetOperations<String, String> setOps =
                    (SetOperations<String, String>) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{SetOperations.class},
                            (proxy, method, args) -> {
                                switch (method.getName()) {
                                    case "add": {
                                        String key = (String) args[0];
                                        String[] members = (String[]) args[1];
                                        Set<String> target = sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
                                        for (String member : members) {
                                            target.add(member);
                                        }
                                        return (long) members.length;
                                    }
                                    case "remove": {
                                        String key = (String) args[0];
                                        Object[] members = (Object[]) args[1];
                                        Set<String> target = sets.get(key);
                                        if (target == null) {
                                            return 0L;
                                        }
                                        long removed = 0;
                                        for (Object member : members) {
                                            if (target.remove(member)) {
                                                removed++;
                                            }
                                        }
                                        return removed;
                                    }
                                    case "members": {
                                        Set<String> target = sets.get((String) args[0]);
                                        return target == null ? Set.of() : new HashSet<>(target);
                                    }
                                    case "toString":
                                        return "fakeSetOps";
                                    case "hashCode":
                                        return System.identityHashCode(proxy);
                                    case "equals":
                                        return proxy == args[0];
                                    default:
                                        return null;
                                }
                            });

            template = new StringRedisTemplate() {
                @Override
                public ValueOperations<String, String> opsForValue() {
                    return valueOps;
                }

                @Override
                public SetOperations<String, String> opsForSet() {
                    return setOps;
                }

                @Override
                public Boolean hasKey(String key) {
                    return values.containsKey(key);
                }

                @Override
                public Boolean delete(String key) {
                    values.remove(key);
                    return Boolean.TRUE;
                }
            };
        }
    }
}

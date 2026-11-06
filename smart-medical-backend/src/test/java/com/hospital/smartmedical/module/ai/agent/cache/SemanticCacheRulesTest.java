package com.hospital.smartmedical.module.ai.agent.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义缓存纯规则单测：分类、归一化、闸门、采样、幂等 key。
 *
 * <p>不启动 Spring、不连 Redis/向量库，保证毫秒级且稳定。</p>
 */
class SemanticCacheRulesTest {

    private SemanticCacheService service;

    @BeforeEach
    void setUp() {
        // 依赖只在链路测试里使用，纯规则测试传 null 即可
        service = new SemanticCacheService(null, null, null, new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "shadow", false);
        ReflectionTestUtils.setField(service, "sampleRate", 100);
        ReflectionTestUtils.setField(service, "flowTtlDays", 30);
        ReflectionTestUtils.setField(service, "commonTtlDays", 3);
        ReflectionTestUtils.setField(service, "requireDisclaimer", false);
        ReflectionTestUtils.setField(service, "sensitiveWordsConfig", "政治,暴力,自杀");
    }

    @Test
    @DisplayName("流程类问题应归为 FLOW")
    void classifyFlowQuestions() {
        assertEquals(SemanticCacheService.Category.FLOW,
                SemanticCacheService.classify("挂号流程是什么"));
        assertEquals(SemanticCacheService.Category.FLOW,
                SemanticCacheService.classify("医保怎么报销"));
        // 含症状词但本质是流程咨询，且不含用药/治疗词 → 仍归 FLOW
        assertEquals(SemanticCacheService.Category.FLOW,
                SemanticCacheService.classify("感冒发烧怎么挂号"));
    }

    @Test
    @DisplayName("常识类问题应归为 COMMON")
    void classifyCommonQuestions() {
        assertEquals(SemanticCacheService.Category.COMMON,
                SemanticCacheService.classify("体检前需要注意什么"));
        assertEquals(SemanticCacheService.Category.COMMON,
                SemanticCacheService.classify("应该挂什么科"));
    }

    @Test
    @DisplayName("用药/治疗类问题应归为 SENSITIVE")
    void classifySensitiveQuestions() {
        assertEquals(SemanticCacheService.Category.SENSITIVE,
                SemanticCacheService.classify("头疼吃什么药"));
        assertEquals(SemanticCacheService.Category.SENSITIVE,
                SemanticCacheService.classify("肺癌怎么治疗"));
        assertEquals(SemanticCacheService.Category.SENSITIVE,
                SemanticCacheService.classify("这个副作用大吗"));
    }

    @Test
    @DisplayName("无法归类的问题应归为 UNKNOWN（保守不缓存）")
    void classifyUnknownQuestions() {
        assertEquals(SemanticCacheService.Category.UNKNOWN,
                SemanticCacheService.classify("今天天气怎么样"));
        assertEquals(SemanticCacheService.Category.UNKNOWN,
                SemanticCacheService.classify(""));
    }

    @Test
    @DisplayName("归一化：全角转半角、压缩空白、去掉尾部标点")
    void normalizeText() {
        assertEquals("感冒了", service.normalize("感冒了！！！   "));
        assertEquals("挂 号 流程", service.normalize("　挂　号　流程　"));
        assertEquals("hello,world", service.normalize("ｈｅｌｌｏ，ｗｏｒｌｄ。"));
    }

    @Test
    @DisplayName("闸门：敏感/业务/PII/过短一律不可缓存，流程类放行")
    void eligibleGate() {
        assertTrue(service.eligible("挂号流程是什么"));
        assertFalse(service.eligible("头疼吃什么药"), "用药类必须拒绝");
        assertFalse(service.eligible("我的报告在哪里"), "个人业务问题必须拒绝");
        assertFalse(service.eligible("13800138000"), "手机号必须拒绝");
        assertFalse(service.eligible("短"), "过短必须拒绝");
        assertFalse(service.eligible("含有自杀倾向的描述"), "全局敏感词必须拒绝");
        assertFalse(service.eligible("今天天气真好"), "无法归类必须拒绝");
    }

    @Test
    @DisplayName("采样：影子模式不返回；100 全量；0 全不返回")
    void sampling() {
        assertTrue(service.sampled());
        ReflectionTestUtils.setField(service, "sampleRate", 0);
        assertFalse(service.sampled());
        ReflectionTestUtils.setField(service, "sampleRate", 100);
        ReflectionTestUtils.setField(service, "shadow", true);
        assertFalse(service.sampled(), "影子模式永不返回");
    }

    @Test
    @DisplayName("幂等 key：64 位十六进制、同输入稳定、不同输入不同")
    void sha256Key() {
        String a = SemanticCacheService.sha256("挂号流程是什么");
        String b = SemanticCacheService.sha256("挂号流程是什么");
        String c = SemanticCacheService.sha256("医保怎么报销");
        assertEquals(64, a.length());
        assertTrue(a.matches("[0-9a-f]{64}"));
        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}

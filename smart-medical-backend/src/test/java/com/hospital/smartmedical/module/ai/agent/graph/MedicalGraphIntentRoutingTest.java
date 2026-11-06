package com.hospital.smartmedical.module.ai.agent.graph;

import com.hospital.smartmedical.module.ai.agent.enums.IntentKeywords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由意图判定测试：锁定「FAQ / UNKNOWN 不进业务节点」。
 *
 * <p>回归点：模型判 UNKNOWN 后曾回退关键词，把「挂号流程是什么」改判成 APPOINT，
 * 最终由业务节点答出一屏医生推荐。</p>
 */
class MedicalGraphIntentRoutingTest {

    @Test
    @DisplayName("FAQ 与 UNKNOWN 都不算业务意图")
    void faqAndUnknownAreNotBusiness() {
        assertFalse(MedicalGraphBuilder.isBusinessIntentName("FAQ"), "FAQ 应走对话节点");
        assertFalse(MedicalGraphBuilder.isBusinessIntentName("UNKNOWN"), "UNKNOWN 应走对话节点");
        assertFalse(MedicalGraphBuilder.isBusinessIntentName(null), "空意图不是业务");
        assertFalse(MedicalGraphBuilder.isBusinessIntentName(""), "空串不是业务");
    }

    @Test
    @DisplayName("业务意图仍正确识别")
    void businessIntentsStillRecognized() {
        assertTrue(MedicalGraphBuilder.isBusinessIntentName("APPOINT"));
        assertTrue(MedicalGraphBuilder.isBusinessIntentName("PROGRESS"));
        assertTrue(MedicalGraphBuilder.isBusinessIntentName("PRESCRIPTION_REPORT"));
        assertTrue(MedicalGraphBuilder.isBusinessIntentName("DOCTOR_PATIENTS"));
        assertTrue(MedicalGraphBuilder.isBusinessIntentName("DOCTOR_PROGRESS"));
    }

    @Test
    @DisplayName("合法枚举集合包含 FAQ，且路由与业务节点共用同一份")
    void validIntentsContainFaq() {
        assertTrue(IntentKeywords.VALID_INTENTS.contains("FAQ"));
        assertTrue(IntentKeywords.VALID_INTENTS.contains("UNKNOWN"));
        assertTrue(IntentKeywords.VALID_INTENTS.contains("APPOINT"));
    }
}

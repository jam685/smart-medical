package com.hospital.smartmedical.module.ai.agent.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挂号意图关键词测试：锁定「流程/材料/费用类问题不算挂号」这条规则。
 *
 * <p>回归点：用户问「挂号流程是什么」，因命中关键词「挂号」被送进业务节点，
 * 最终答出"为您推荐可挂号医生"（精神科/妇科/耳鼻喉科列表）。</p>
 */
class IntentKeywordsTest {

    @Test
    @DisplayName("流程类问题必须命中排除词，不得判为挂号意图")
    void flowQuestionsAreExcluded() {
        String[] flowQuestions = {
                "挂号流程是什么",
                "线上挂号怎么挂",
                "如何预约挂号",
                "挂号需要带什么材料",
                "挂号费多少钱",
                "挂号几点开始",
                "挂号窗口在哪",
                "预约流程麻烦吗"
        };
        for (String q : flowQuestions) {
            assertTrue(containsAny(q, IntentKeywords.APPOINT_EXCLUDED),
                    "应命中排除词：" + q);
        }
    }

    @Test
    @DisplayName("明确的挂号动作仍应识别为挂号意图")
    void appointmentActionsStillDetected() {
        String[] actions = {
                "帮我挂呼吸科的号",
                "我要挂张医生的号",
                "预约明天上午",
                "挂个号"
        };
        for (String q : actions) {
            assertFalse(containsAny(q, IntentKeywords.APPOINT_EXCLUDED),
                    "不应命中排除词：" + q);
            assertTrue(containsAny(q, IntentKeywords.APPOINT_POSITIVE),
                    "应命中挂号正向词：" + q);
        }
    }

    @Test
    @DisplayName("科室推荐类问题同样被排除")
    void departmentRecommendationExcluded() {
        assertTrue(containsAny("我该挂什么科", IntentKeywords.APPOINT_EXCLUDED));
        assertTrue(containsAny("推荐挂哪个科", IntentKeywords.APPOINT_EXCLUDED));
    }

    private static boolean containsAny(String text, String[] keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }
}

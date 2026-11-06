package com.hospital.smartmedical.module.ai.agent.service;

import com.hospital.smartmedical.module.ai.agent.enums.ReplySource;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义缓存「回复来源过滤」测试。
 *
 * <p>回归点：图内多个节点都会写 {@code g.reply}，业务节点的回复（推荐医生列表等）
 * 曾被误当成通用问答写入缓存并存活 30 天。此测试锁定「只允许客服对话节点进缓存」。</p>
 */
class SemanticCacheSourceGateTest {

    @Test
    @DisplayName("客服对话节点的通用回复允许进缓存")
    void assistantDialogAllowed() {
        MedicalGraphState g = new MedicalGraphState();
        g.setReplySource(ReplySource.ASSISTANT_DIALOG);
        assertTrue(AiAgentService.cacheableReplySource(g));
    }

    @Test
    @DisplayName("业务节点回复一律拒绝（含实时号源/医生列表）")
    void businessDenied() {
        MedicalGraphState g = new MedicalGraphState();
        g.setReplySource(ReplySource.BUSINESS);
        g.setLastBusinessResult(Map.of("intent", "APPOINT"));
        assertFalse(AiAgentService.cacheableReplySource(g));
    }

    @Test
    @DisplayName("预问诊 / 医生草稿 / 缓存回放 / 来源为空，全部拒绝")
    void otherSourcesDenied() {
        ReplySource[] denied = {ReplySource.PATIENT_DIALOG, ReplySource.DOCTOR_ASSIST,
                ReplySource.CACHE, null};
        for (ReplySource source : denied) {
            MedicalGraphState g = new MedicalGraphState();
            g.setReplySource(source);
            assertFalse(AiAgentService.cacheableReplySource(g), "source 不应允许缓存：" + source);
        }
    }

    @Test
    @DisplayName("客服对话节点但产生了业务卡片时同样拒绝")
    void cardDenied() {
        MedicalGraphState g = new MedicalGraphState();
        g.setReplySource(ReplySource.ASSISTANT_DIALOG);
        g.setCurrentRoundCard(Map.of("type", "APPOINTMENT"));
        assertFalse(AiAgentService.cacheableReplySource(g));
    }
}

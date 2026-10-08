package com.hospital.smartmedical.module.ai.service;

import com.hospital.smartmedical.module.ai.entity.AiChatMessage;

import java.util.List;
import java.util.Map;

public interface AiService {

    /**
     * AI 客服对话（Demo 模式）
     */
    Map<String, Object> chat(Long userId, String role, String sessionKey, String message);

    /**
     * 转人工
     */
    Map<String, Object> transferToHuman(String sessionKey);

    /**
     * 获取会话历史
     */
    List<AiChatMessage> getChatHistory(String sessionKey);

    /**
     * 医生端：病情分析（Demo）
     */
    Map<String, Object> clinicalAnalysis(String chiefComplaint, String description,
                                         Map<String, Object> patientInfo);

    /**
     * 医生端：检查建议（Demo）
     */
    Map<String, Object> examinationSuggestions(String chiefComplaint, String description);

    /**
     * 医生端：回复草稿（Demo）
     */
    Map<String, Object> replyDrafts(String chiefComplaint, String description);

    /**
     * 医生端：图片/报告分析
     */
    Map<String, Object> imageAnalysis(Long reportId);
}

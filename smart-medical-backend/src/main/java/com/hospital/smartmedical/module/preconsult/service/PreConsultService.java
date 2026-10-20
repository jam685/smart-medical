package com.hospital.smartmedical.module.preconsult.service;

import com.hospital.smartmedical.module.preconsult.entity.PreConsult;

import java.util.List;
import java.util.Map;

/**
 * 智能预问诊服务接口（/pre-consults）。
 *
 * <p>患者挂号前发送症状描述，AI 关键词模拟引导话术完成症状采集与挂号引导，
 * 全程不诊断、不开方（PRE-01~04）。</p>
 */
public interface PreConsultService {

    /**
     * 发送预问诊消息（PRE-01）：保存一条 PATIENT 消息 -> 生成并保存一条 AI 回复消息 -> 返回 AI 回复。
     *
     * @param patientId 患者 ID
     * @param sessionId 会话标识（为空则按语义由服务端维护）
     * @param content   文本内容（可空，图片消息时为空）
     * @param imageUrl  图片地址（可空，非空即图片消息）
     * @return 本次的 AI 回复（含回复文本）
     */
    Map<String, Object> sendMessage(Long patientId, String sessionId, String content, String imageUrl);

    /**
     * 获取预问诊会话历史（PRE-02），按时间升序返回。
     */
    List<PreConsult> getHistory(Long patientId, String sessionId);

    /**
     * 患者查看自己的预问诊记录列表（PRE-03）——按会话去重后返回最近会话。
     */
    List<Map<String, Object>> listSessions(Long patientId);

    /**
     * 结束预问诊会话（PRE-04）。V1.3 表无状态列，此接口做合法性校验后返回。
     */
    void closeSession(Long patientId, String sessionId);
}
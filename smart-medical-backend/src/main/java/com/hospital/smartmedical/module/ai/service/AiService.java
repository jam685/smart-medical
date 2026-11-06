package com.hospital.smartmedical.module.ai.service;

import com.hospital.smartmedical.common.enums.Role;

import java.util.Map;

/**
 * AI 辅助分析服务接口。
 * V2.3 移除原 AI 客服（会话/消息）能力，仅保留 4 个医生端 AI 辅助接口，
 * 当前为 Demo 模拟实现（ai.mock-mode），待接入真实 FastAPI + 大模型。
 */
public interface AiService {

    /**
     * 病情分析：根据主诉、详细描述与患者信息，返回病情分析、诊断/鉴别/用药建议与风险等级。
     * 返回结构：conditionAnalysis / diagnosisSuggestion / differentialDiagnosis /
     * medicationSuggestion / riskLevel / confidence / references / disclaimer
     *
     * @param chiefComplaint 主诉
     * @param description    详细描述
     * @param patientInfo    患者基本信息（年龄/性别/既往史等）
     */
    Map<String, Object> clinicalAnalysis(String chiefComplaint, String description,
                                         Map<String, Object> patientInfo);

    /**
     * 检查建议：根据主诉与描述，生成建议的检查项目清单。
     * 返回结构：examinationList / urgency / confidence / note
     */
    Map<String, Object> examinationSuggestions(String chiefComplaint, String description);

    /**
     * 回复草稿：为医生生成给患者的礼貌、专业回复草稿。
     * 返回结构：draft / confidence / tone / suggestedEdit
     */
    Map<String, Object> replyDrafts(String chiefComplaint, String description);

    /**
     * 图片/报告分析：根据报告 id 读取报告信息，返回 AI 分析结果（Demo 返回通用占位）。
     * 返回结构：reportId / reportTitle / recognizedContent / analysis /
     * abnormalItems / referenceRanges / confidence / needHuman / disclaimer
     *
     * <p>归属校验说明：
     * <ul>
     *   <li>DOCTOR / ADMIN 角色：直接放行（医生可查看任意患者报告做诊疗参考）</li>
     *   <li>PATIENT 角色：report.patient_id 必须等于当前 userId，否则抛 FORBIDDEN</li>
     * </ul>
     * </p>
     *
     * @param reportId 报告 ID（sm_report.id）
     * @param userId   当前登录用户 ID
     * @param role     当前登录角色
     */
    Map<String, Object> imageAnalysis(Long reportId, Long userId, Role role);
}
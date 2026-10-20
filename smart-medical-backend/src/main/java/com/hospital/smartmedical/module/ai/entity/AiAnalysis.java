package com.hospital.smartmedical.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 分析记录实体，对应 sm_ai_analysis 表。
 * 承载一次医生端 AI 辅助分析（病情分析/诊断建议/鉴别提示/用药建议/风险等级），
 * 仅作辅助参考，须经医生审核后采用（V1.3 的字段精简方案）。
 */
@Data
@TableName("sm_ai_analysis")
public class AiAnalysis implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的线上面诊记录 id（sm_online_consult_record） */
    private Long consultId;

    /** 患者 id（sm_patient） */
    private Long patientId;

    /** 医生 id（sm_doctor） */
    private Long doctorId;

    /** 病情分析文本 */
    private String conditionAnalysis;

    /** 诊断建议文本 */
    private String diagnosisSuggestion;

    /** 鉴别提示文本 */
    private String differentialDiagnosis;

    /** 用药建议文本 */
    private String medicationSuggestion;

    /** 风险等级：LOW/MEDIUM/HIGH */
    private String riskLevel;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
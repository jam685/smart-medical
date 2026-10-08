package com.hospital.smartmedical.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sm_ai_analysis")
public class AiAnalysis implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long consultId;

    private Long patientId;

    private String analysisType;

    private String possibleCauses;

    private String differential;

    private String diagnosisSuggestions;

    private String medicationSuggestions;

    private String requiredMaterials;

    private String riskLevel;

    private BigDecimal confidence;

    private String references;

    private String disclaimer;

    private LocalDateTime createdAt;
}

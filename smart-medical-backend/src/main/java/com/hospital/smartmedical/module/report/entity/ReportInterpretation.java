package com.hospital.smartmedical.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sm_report_interpretation")
public class ReportInterpretation implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;

    private String recognizedContent;

    private String analysis;

    private String abnormalItems;

    private String referenceRanges;

    private BigDecimal confidence;

    private String references;

    private Integer needHuman;

    private String disclaimer;

    private LocalDateTime createdAt;
}

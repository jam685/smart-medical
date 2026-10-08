package com.hospital.smartmedical.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_report")
public class Report implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long patientId;

    private String reportType;

    private String imageUrl;

    private String imageMeta;

    private Long uploadedBy;

    private String status;

    private LocalDateTime createdAt;
}

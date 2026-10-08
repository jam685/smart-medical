package com.hospital.smartmedical.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_sensitive_content")
public class SensitiveContent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String role;

    private String content;

    private String sensitiveType;

    private Integer sensitiveLevel;

    private String actionTaken;

    private String reviewStatus;

    private Long reviewedBy;

    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
}

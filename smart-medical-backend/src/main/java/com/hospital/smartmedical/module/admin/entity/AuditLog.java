package com.hospital.smartmedical.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_audit_log")
public class AuditLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String role;

    private String action;

    private String targetType;

    private Long targetId;

    private String detail;

    private String ip;

    private String userAgent;

    private String sensitiveType;

    private Integer sensitiveLevel;

    private String actionTaken;

    private String reviewStatus;

    private LocalDateTime createdAt;
}

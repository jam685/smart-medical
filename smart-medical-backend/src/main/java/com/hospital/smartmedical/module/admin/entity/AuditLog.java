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

    private Long targetId;

    private String detail;

    /** 敏感等级 */
    private String sensitiveLevel;

    /** 审核状态 */
    private String reviewStatus;

    private LocalDateTime createdAt;
}

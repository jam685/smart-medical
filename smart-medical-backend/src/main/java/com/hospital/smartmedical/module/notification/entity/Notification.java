package com.hospital.smartmedical.module.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信（sm_notification）：停诊、改约等业务事件写给患者/医生的通知。
 */
@Data
@TableName("sm_notification")
public class Notification implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 ID（患者 ID 或医生 ID） */
    private Long userId;

    /** 接收人角色：PATIENT / DOCTOR */
    private String role;

    /** 事件类型：SUSPEND_REFUND / SUSPEND_RESCHEDULE / SUSPEND_RESULT */
    private String type;

    private String title;

    private String content;

    /** 关联业务类型：APPOINTMENT / SCHEDULE */
    private String bizType;

    /** 关联业务 ID */
    private Long bizId;

    /** 0=未读 1=已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;
}

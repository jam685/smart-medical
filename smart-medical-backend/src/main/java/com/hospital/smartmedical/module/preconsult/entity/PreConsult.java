package com.hospital.smartmedical.module.preconsult.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能预问诊实体（sm_pre_consult，V1.3 新增）。
 *
 * <p>记录挂号前患者与 AI 助手的对话（文字/图片）：sender 为 PATIENT / AI / SYSTEM。
 * AI 只做症状采集与挂号引导，不诊断、不开方。</p>
 */
@Data
@TableName("sm_pre_consult")
public class PreConsult implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID（sm_patient） */
    private Long patientId;

    /** 会话标识（一次预问诊一个 sessionId） */
    private String sessionId;

    /** 发送方：PATIENT / AI / SYSTEM */
    private String sender;

    /** 消息类型：TEXT / IMAGE */
    private String messageType;

    /** 文字内容 */
    private String content;

    /** 图片地址（图片消息时使用） */
    private String imageUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
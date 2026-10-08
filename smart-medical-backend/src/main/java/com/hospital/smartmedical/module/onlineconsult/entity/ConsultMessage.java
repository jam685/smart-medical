package com.hospital.smartmedical.module.onlineconsult.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问诊消息表（sm_consult_message）。
 *
 * <p>线上面诊中的文本消息与图片消息，sender 为 PATIENT / DOCTOR / SYSTEM。
 * 医生回复经确认后也会落一条 DOCTOR 消息，保证患者端消息流完整可见。
 * 医生下线时患者仍可发送，消息保存，医生上线后可见。</p>
 */
@Data
@TableName("sm_consult_message")
public class ConsultMessage implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问诊 ID（sm_online_consult.id） */
    private Long consultId;

    /** 发送方：PATIENT / DOCTOR / SYSTEM */
    private String sender;

    /** 消息类型：TEXT / IMAGE / SYSTEM */
    private String messageType;

    /** 文本内容 */
    private String content;

    /** 图片地址（图片消息时使用） */
    private String imageUrl;

    /** 敏感标记：0 正常 / 1 命中敏感词 */
    private Integer sensitiveFlag;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

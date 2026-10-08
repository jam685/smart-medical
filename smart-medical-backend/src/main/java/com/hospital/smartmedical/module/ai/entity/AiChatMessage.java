package com.hospital.smartmedical.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sm_ai_chat_message")
public class AiChatMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String sender;

    private String content;

    private BigDecimal confidence;

    private Integer needHuman;

    private LocalDateTime createdAt;
}

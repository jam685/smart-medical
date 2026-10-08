package com.hospital.smartmedical.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_ai_chat_session")
public class AiChatSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userRole;

    private String sessionKey;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

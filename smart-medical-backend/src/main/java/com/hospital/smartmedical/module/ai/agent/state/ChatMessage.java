package com.hospital.smartmedical.module.ai.agent.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * AI 图内对话消息（支持文本 + 图片多模态）。
 * 与 MedicalGraphState.chatHistory 配合，用于会话隔离与上下文传递。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    /** 发送方：PATIENT / DOCTOR / AI */
    private String sender;

    /** 消息类型：TEXT / IMAGE */
    private String messageType;

    /** 文本内容 */
    private String content;

    /** 图片地址（图片消息时使用） */
    private String imageUrl;

    /** 消息时间（yyyy-MM-dd HH:mm:ss） */
    private String createdAt;

    /** 结构化卡片数据（如挂号成功卡片 APPOINTMENT，非卡片消息为 null；历史回放时前端据此渲染） */
    private Map<String, Object> card;

    /**
     * 兼容无卡片的 5 参构造（文本/图片消息），card 默认 null。
     *
     * @param sender      发送方
     * @param messageType 消息类型
     * @param content     文本内容
     * @param imageUrl    图片地址
     * @param createdAt   消息时间
     */
    public ChatMessage(String sender, String messageType, String content, String imageUrl, String createdAt) {
        this.sender = sender;
        this.messageType = messageType;
        this.content = content;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
    }
}

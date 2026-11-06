package com.hospital.smartmedical.module.notification.vo;

import com.hospital.smartmedical.module.notification.entity.Notification;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 站内信展示对象。
 */
@Data
public class NotificationVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Boolean read;
    private String createdAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static NotificationVO of(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setBizType(n.getBizType());
        vo.setBizId(n.getBizId());
        vo.setRead(n.getReadFlag() != null && n.getReadFlag() == 1);
        vo.setCreatedAt(n.getCreatedAt() == null ? null : n.getCreatedAt().format(FMT));
        return vo;
    }
}

package com.hospital.smartmedical.module.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.module.notification.entity.Notification;
import com.hospital.smartmedical.module.notification.mapper.NotificationMapper;
import com.hospital.smartmedical.module.notification.service.NotificationService;
import com.hospital.smartmedical.module.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 站内信服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void push(Long userId, String role, String type, String title, String content,
                     String bizType, Long bizId) {
        if (userId == null || role == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setRole(role);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setBizType(bizType);
            n.setBizId(bizId);
            n.setReadFlag(0);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            // 通知失败不能影响主业务（退号/改约已经成功）
            log.warn("站内信写入失败：userId={}, role={}, type={}, err={}", userId, role, type, e.getMessage());
        }
    }

    @Override
    public List<NotificationVO> latest(Long userId, String role, int limit) {
        int size = Math.min(50, Math.max(1, limit));
        List<Notification> list = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getRole, role)
                .orderByDesc(Notification::getId)
                .last("LIMIT " + size));
        List<NotificationVO> result = new ArrayList<>(list.size());
        for (Notification n : list) {
            result.add(NotificationVO.of(n));
        }
        return result;
    }

    @Override
    public long unread(Long userId, String role) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getRole, role)
                .eq(Notification::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    @Override
    public void markRead(Long userId, String role, Long id) {
        notificationMapper.markRead(id, userId, role);
    }

    @Override
    public void markAllRead(Long userId, String role) {
        notificationMapper.markAllRead(userId, role);
    }
}

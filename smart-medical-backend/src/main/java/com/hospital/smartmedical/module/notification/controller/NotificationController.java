package com.hospital.smartmedical.module.notification.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.notification.service.NotificationService;
import com.hospital.smartmedical.module.notification.vo.NotificationVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内信接口（患者端、医生端共用，按登录角色隔离）。
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 最近 N 条通知 */
    @GetMapping
    public Result<List<NotificationVO>> list(@RequestParam(defaultValue = "20") int limit,
                                             HttpServletRequest request) {
        return Result.ok(notificationService.latest(userId(request), role(request), limit));
    }

    /** 未读数（头部铃铛红点） */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unread(HttpServletRequest request) {
        return Result.ok(Map.of("count", notificationService.unread(userId(request), role(request))));
    }

    /** 单条已读 */
    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id, HttpServletRequest request) {
        notificationService.markRead(userId(request), role(request), id);
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Result<Void> readAll(HttpServletRequest request) {
        notificationService.markAllRead(userId(request), role(request));
        return Result.ok();
    }

    private Long userId(HttpServletRequest request) {
        Long id = (Long) request.getAttribute("currentUserId");
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    private String role(HttpServletRequest request) {
        Role role = (Role) request.getAttribute("currentUserRole");
        return role == null ? "PATIENT" : role.name();
    }
}

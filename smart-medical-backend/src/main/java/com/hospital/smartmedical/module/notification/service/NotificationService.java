package com.hospital.smartmedical.module.notification.service;

import com.hospital.smartmedical.module.notification.vo.NotificationVO;

import java.util.List;

/**
 * 站内信服务：业务事件 → 患者/医生通知。
 */
public interface NotificationService {

    /**
     * 推送一条站内信（失败只记日志，不影响主业务事务）。
     *
     * @param userId  接收人 ID
     * @param role    接收人角色 PATIENT/DOCTOR
     * @param type    事件类型
     * @param title   标题
     * @param content 正文
     * @param bizType 业务类型（可空）
     * @param bizId   业务 ID（可空）
     */
    void push(Long userId, String role, String type, String title, String content, String bizType, Long bizId);

    /**
     * 最近 N 条通知（按时间倒序）。
     */
    List<NotificationVO> latest(Long userId, String role, int limit);

    /** 未读数 */
    long unread(Long userId, String role);

    /** 单条已读 */
    void markRead(Long userId, String role, Long id);

    /** 全部已读 */
    void markAllRead(Long userId, String role);
}

package com.hospital.smartmedical.module.admin.service;

import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.admin.dto.AdminUserRequest;
import com.hospital.smartmedical.module.admin.entity.Admin;

import java.util.List;
import java.util.Map;

public interface AdminService {

    /**
     * 管理员登录（Demo简化：employeeNo / phone / idCard 任一匹配即可）
     */
    Map<String, Object> login(String employeeNo, String phone, String idCard);

    void logout();

    Admin getById(Long id);

    /**
     * 分页查询患者或医生
     * @param keyword 可选关键词：患者匹配 username / realName / phone / idCard；医生匹配 employeeNo / realName / phone / department
     */
    PageResult<?> getUsersByRole(String role, int page, int size, String keyword);

    /**
     * 更新用户状态（PATIENT / DOCTOR）
     */
    void updateUserStatus(String role, Long userId, String status);

    /**
     * 新增用户（PATIENT / DOCTOR）
     */
    Map<String, Object> createUser(String role, AdminUserRequest req, Long operatorId);

    /**
     * 修改用户（PATIENT / DOCTOR）
     */
    Map<String, Object> updateUser(String role, Long userId, AdminUserRequest req, Long operatorId);

    /**
     * 返回固定角色列表
     */
    List<String> getRoles();

    /**
     * 分页查询审计日志
     */
    PageResult<?> getAuditLogs(int page, int size, String role, String action);

    /**
     * 审核一条审计日志（通过或驳回）
     * @param id       审计日志ID
     * @param decision 审核结论：APPROVED 通过 / REJECTED 驳回（驳回自动禁用操作人账号）
     */
    void reviewAuditLog(Long id, String decision);

    /**
     * 返回所有模型配置
     */
    List<?> getAllModelConfigs();

    /**
     * 返回数据质量记录
     */
    List<?> getDataQuality();
}

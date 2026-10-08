package com.hospital.smartmedical.module.admin.dto;

import lombok.Data;

/**
 * 管理员新增 / 修改用户请求体
 * <p>role 决定操作哪张表：PATIENT 使用 username / idCard；DOCTOR 使用 employeeNo / title / department / bio / onlineStatus。
 * 修改时字段为 null 表示不修改该字段（部分更新）。
 */
@Data
public class AdminUserRequest {

    // ===== 通用字段 =====
    /** 真实姓名 */
    private String realName;
    /** 性别：男 / 女 */
    private String gender;
    /** 手机号（11 位数字） */
    private String phone;
    /** 账号状态：ACTIVE / DISABLED */
    private String status;
    /** 密码（新增时缺省默认 123456；修改时提供非空值则重置密码） */
    private String password;

    // ===== 患者专属 =====
    /** 登录用户名（新增患者时必填） */
    private String username;
    /** 身份证号 */
    private String idCard;

    // ===== 医生专属 =====
    /** 工号（新增医生时必填） */
    private String employeeNo;
    /** 职称 */
    private String title;
    /** 科室 */
    private String department;
    /** 医生简介 / 擅长领域 */
    private String bio;
    /** 在线状态：ONLINE / OFFLINE */
    private String onlineStatus;
}

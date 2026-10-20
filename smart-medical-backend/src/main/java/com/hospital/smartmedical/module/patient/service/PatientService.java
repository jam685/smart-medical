package com.hospital.smartmedical.module.patient.service;

import com.hospital.smartmedical.module.patient.entity.Patient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface PatientService {

    Map<String, Object> login(String username, String password);

    /**
     * 患者注册：校验用户名/手机号/身份证唯一性后 BCrypt 加密入库，注册成功自动登录。
     *
     * @param username 用户名（2-20 位）
     * @param password 密码（6-64 位）
     * @param realName 真实姓名
     * @param gender   性别：男/女（可空）
     * @param phone    手机号（11 位）
     * @param idCard   身份证号（18 位）
     * @return 同登录返回结构（token + user）
     */
    Map<String, Object> register(String username, String password, String realName,
                                 String gender, String phone, String idCard);

    void logout();

    Patient getById(Long id);

    /**
     * 更新当前患者资料（姓名、手机号、性别）
     *
     * @param id       患者ID
     * @param realName 真实姓名（传 null 表示不更新）
     * @param phone    手机号（传 null 表示不更新）
     * @param gender   性别：男/女（传 null 或空串表示不更新/清空）
     */
    Patient update(Long id, String realName, String phone, String gender);

    /**
     * 上传头像并更新当前患者 avatar 字段
     *
     * @param id   患者ID
     * @param file 图片文件（jpg/jpeg/png/webp/gif）
     * @return 头像可访问的完整相对路径（含 context-path，前端可直接用于 img src）
     */
    String uploadAvatar(Long id, MultipartFile file);

    /**
     * 修改当前患者密码：校验原密码 → BCrypt 加密存新密码
     *
     * @param id          患者ID
     * @param oldPassword 原密码
     * @param newPassword 新密码（6-64 位）
     */
    void changePassword(Long id, String oldPassword, String newPassword);

    /**
     * 忘记密码-发送验证码：按用户名定位患者，校验身份证号与手机号匹配后
     * 生成 6 位验证码（有效期 5 分钟），演示环境验证码直接随响应返回。
     *
     * @param username 用户名
     * @param idCard   身份证号
     * @param phone    手机号
     * @return 验证码（演示环境透出，正式环境应走短信通道）
     */
    String sendPasswordResetCode(String username, String idCard, String phone);

    /**
     * 忘记密码-重置密码：验证码校验通过后，用 BCrypt 加密写入新密码。
     *
     * @param username    用户名
     * @param code        验证码
     * @param newPassword 新密码（6-64 位）
     */
    void resetPassword(String username, String code, String newPassword);
}

package com.hospital.smartmedical.module.patient.service;

import com.hospital.smartmedical.module.patient.entity.Patient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface PatientService {

    Map<String, Object> login(String username, String password);

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
}

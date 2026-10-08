package com.hospital.smartmedical.module.doctor.service;

import com.hospital.smartmedical.module.doctor.entity.Doctor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DoctorService {

    Map<String, Object> login(String employeeNo, String username, String password);

    void logout();

    Doctor getById(Long id);

    Doctor update(Long id, Doctor updates);

    /**
     * 上传头像并更新当前医生 avatar 字段
     *
     * @param id   医生ID
     * @param file 图片文件（jpg/jpeg/png/webp/gif）
     * @return 头像可访问的完整相对路径（含 context-path）
     */
    String uploadAvatar(Long id, MultipartFile file);

    /**
     * 获取该医生所有就诊过的患者（从 Visit 关联 sm_patient）
     * 返回 [{patientId, realName, phone, lastVisitTime}]
     */
    List<Map<String, Object>> getMyPatients(Long doctorId);

    /**
     * 获取该医生的挂号列表
     */
    List<Object> getMyAppointments(Long doctorId);
}

package com.hospital.smartmedical.module.patient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.security.JwtUtil;
import com.hospital.smartmedical.common.storage.FileStorageService;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.patient.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientMapper patientMapper;
    private final JwtUtil jwtUtil;
    private final FileStorageService fileStorageService;

    // Demo 密码 hash: 123456
    private static final String DEMO_PASSWORD_HASH =
            "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, Object> login(String username, String password) {
        Patient patient = patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>().eq(Patient::getUsername, username)
        );
        if (patient == null) {
            throw new BizException("用户名或密码错误");
        }

        // 密码校验：支持 Demo 简化和 BCrypt
        boolean matched = passwordEncoder.matches(password, DEMO_PASSWORD_HASH);
        if (!matched && patient.getPassword() != null) {
            matched = passwordEncoder.matches(password, patient.getPassword());
        }
        if (!matched) {
            throw new BizException("用户名或密码错误");
        }

        // 检查账号状态
        if ("DISABLED".equalsIgnoreCase(patient.getStatus())) {
            throw new BizException("账号已被禁用");
        }

        String token = jwtUtil.generate(patient.getId(), Role.PATIENT, patient.getUsername());

        Map<String, Object> user = buildUserDto(patient);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }

    @Override
    public void logout() {
        // Demo 简化：前端清 token 即可，服务端不做黑名单
    }

    @Override
    public Patient getById(Long id) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BizException("患者不存在");
        }
        return patient;
    }

    @Override
    public Patient update(Long id, String realName, String phone, String gender) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BizException("患者不存在");
        }
        if (realName != null) patient.setRealName(realName);
        if (phone != null) patient.setPhone(phone);
        // 性别：空串视为清空；非空只允许 男/女
        if (gender != null) {
            String g = gender.trim();
            if (g.isEmpty()) {
                patient.setGender(null);
            } else if ("男".equals(g) || "女".equals(g)) {
                patient.setGender(g);
            } else {
                throw new BizException("性别只能为 男 或 女");
            }
        }
        patient.setUpdatedAt(LocalDateTime.now());
        patientMapper.updateById(patient);
        return patient;
    }

    @Override
    public String uploadAvatar(Long id, MultipartFile file) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BizException("患者不存在");
        }
        String avatarUrl = fileStorageService.saveAvatar(file, "patient_" + id);

        patient.setAvatar(avatarUrl);
        patient.setUpdatedAt(LocalDateTime.now());
        patientMapper.updateById(patient);
        return avatarUrl;
    }

    @Override
    public void changePassword(Long id, String oldPassword, String newPassword) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BizException("患者不存在");
        }
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new BizException("请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 64) {
            throw new BizException("新密码长度需为 6-64 位");
        }
        // 原密码校验口径与登录一致：Demo 密码或库中密码任一匹配即可
        boolean matched = passwordEncoder.matches(oldPassword, DEMO_PASSWORD_HASH)
                || (patient.getPassword() != null && passwordEncoder.matches(oldPassword, patient.getPassword()));
        if (!matched) {
            throw new BizException("原密码不正确");
        }
        patient.setPassword(passwordEncoder.encode(newPassword));
        patient.setUpdatedAt(LocalDateTime.now());
        patientMapper.updateById(patient);
    }

    private Map<String, Object> buildUserDto(Patient p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("realName", p.getRealName());
        map.put("gender", p.getGender());
        map.put("phone", p.getPhone());
        map.put("avatar", p.getAvatar());
        map.put("status", p.getStatus());
        return map;
    }
}

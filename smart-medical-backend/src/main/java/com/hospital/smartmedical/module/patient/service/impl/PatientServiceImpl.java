package com.hospital.smartmedical.module.patient.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.security.DemoMode;
import com.hospital.smartmedical.common.security.JwtUtil;
import com.hospital.smartmedical.common.security.VerificationCodeService;
import com.hospital.smartmedical.common.storage.FileStorageService;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.patient.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * 演示模式（仅 demo profile 下存在）；默认环境为空，只认数据库中的真实密码。
     */
    private final ObjectProvider<DemoMode> demoModeProvider;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, Object> login(String username, String password) {
        // 支持用户名或真实姓名登录：先精确匹配用户名，再按姓名兜底
        String account = username == null ? "" : username.trim();
        Patient patient = patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>().eq(Patient::getUsername, account)
        );
        if (patient == null) {
            patient = patientMapper.selectOne(
                    new LambdaQueryWrapper<Patient>().like(Patient::getRealName, account)
            );
        }
        if (patient == null) {
            throw new BizException("用户名或密码错误");
        }

        // 密码校验：默认只认数据库中的真实 BCrypt 密码；
        // 仅当以 demo profile 启动（DemoMode Bean 存在）时才额外接受演示口令
        boolean matched = false;
        DemoMode demoMode = demoModeProvider.getIfAvailable();
        if (demoMode != null && demoMode.matches(password)) {
            matched = true;
        }
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
    public Map<String, Object> register(String username, String password, String realName,
                                        String gender, String phone, String idCard) {
        // ===== 基础校验 =====
        if (username == null || username.isBlank()) {
            throw new BizException("请输入用户名");
        }
        String name = username.trim();
        if (name.length() < 2 || name.length() > 20) {
            throw new BizException("用户名长度需为 2-20 位");
        }
        if (password == null || password.length() < 6 || password.length() > 64) {
            throw new BizException("密码长度需为 6-64 位");
        }
        if (realName == null || realName.isBlank()) {
            throw new BizException("请输入真实姓名");
        }
        if (phone == null || !phone.trim().matches("^1[3-9]\\d{9}$")) {
            throw new BizException("请输入正确的手机号");
        }
        if (idCard == null || !idCard.trim().matches("^\\d{17}[\\dXx]$")) {
            throw new BizException("请输入正确的身份证号");
        }
        if (gender != null && !gender.isBlank() && !"男".equals(gender.trim()) && !"女".equals(gender.trim())) {
            throw new BizException("性别只能为 男 或 女");
        }

        // ===== 唯一性校验 =====
        Long nameCount = patientMapper.selectCount(
                new LambdaQueryWrapper<Patient>().eq(Patient::getUsername, name));
        if (nameCount != null && nameCount > 0) {
            throw new BizException("用户名已被注册");
        }
        String phoneTrim = phone.trim();
        Long phoneCount = patientMapper.selectCount(
                new LambdaQueryWrapper<Patient>().eq(Patient::getPhone, phoneTrim));
        if (phoneCount != null && phoneCount > 0) {
            throw new BizException("该手机号已被注册");
        }
        String idCardTrim = idCard.trim();
        Long idCardCount = patientMapper.selectCount(
                new LambdaQueryWrapper<Patient>().eq(Patient::getIdCard, idCardTrim));
        if (idCardCount != null && idCardCount > 0) {
            throw new BizException("该身份证号已被注册");
        }

        // ===== 入库 =====
        Patient patient = new Patient();
        patient.setUsername(name);
        patient.setPassword(passwordEncoder.encode(password));
        patient.setRealName(realName.trim());
        patient.setGender(gender == null || gender.isBlank() ? null : gender.trim());
        patient.setPhone(phoneTrim);
        patient.setIdCard(idCardTrim);
        patient.setStatus("ACTIVE");
        patient.setCreatedAt(LocalDateTime.now());
        patient.setUpdatedAt(LocalDateTime.now());
        patientMapper.insert(patient);

        // 注册成功自动登录，与登录返回结构一致
        String token = jwtUtil.generate(patient.getId(), Role.PATIENT, patient.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildUserDto(patient));
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
        String avatarUrl = fileStorageService.saveAvatar(file);

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
        // 原密码只认真实密码：演示口令不能作为"原密码"使用（否则等于任意账号可被改密）
        boolean matched = patient.getPassword() != null
                && passwordEncoder.matches(oldPassword, patient.getPassword());
        if (!matched) {
            throw new BizException("原密码不正确");
        }
        patient.setPassword(passwordEncoder.encode(newPassword));
        patient.setUpdatedAt(LocalDateTime.now());
        patientMapper.updateById(patient);
    }

    @Override
    public String sendPasswordResetCode(String username, String idCard, String phone) {
        if (username == null || username.isBlank()) {
            throw new BizException("请输入用户名");
        }
        if (idCard == null || idCard.isBlank()) {
            throw new BizException("请输入身份证号");
        }
        if (phone == null || phone.isBlank()) {
            throw new BizException("请输入手机号");
        }
        Patient patient = patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>().eq(Patient::getUsername, username.trim())
        );
        if (patient == null) {
            throw new BizException("该用户名不存在");
        }
        if (!idCard.trim().equals(patient.getIdCard())) {
            throw new BizException("身份证号与注册信息不符");
        }
        if (!phone.trim().equals(patient.getPhone())) {
            throw new BizException("手机号与注册信息不符");
        }
        if ("DISABLED".equalsIgnoreCase(patient.getStatus())) {
            throw new BizException("账号已被禁用，无法重置密码");
        }
        String code = VerificationCodeService.generate(patient.getUsername());
        // 无短信通道：验证码只通过服务端日志/演示回显交付，响应体默认不返回（见 PatientController 开关）
        return code;
    }

    @Override
    public void resetPassword(String username, String code, String newPassword) {
        if (username == null || username.isBlank()) {
            throw new BizException("请输入用户名");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 64) {
            throw new BizException("新密码长度需为 6-64 位");
        }
        VerificationCodeService.verify(username.trim(), code);

        Patient patient = patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>().eq(Patient::getUsername, username.trim())
        );
        if (patient == null) {
            throw new BizException("该用户名不存在");
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

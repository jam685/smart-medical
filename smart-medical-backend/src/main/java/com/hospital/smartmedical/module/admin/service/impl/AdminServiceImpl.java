package com.hospital.smartmedical.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.security.JwtUtil;
import com.hospital.smartmedical.module.admin.dto.AdminUserRequest;
import com.hospital.smartmedical.module.admin.entity.*;
import com.hospital.smartmedical.module.admin.mapper.*;
import com.hospital.smartmedical.module.admin.service.AdminService;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final AuditLogMapper auditLogMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final DataQualityMapper dataQualityMapper;
    private final SensitiveContentMapper sensitiveContentMapper;
    private final JwtUtil jwtUtil;

    /** 新增用户缺省密码 123456 */
    private static final String DEMO_PASSWORD = "123456";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, Object> login(String employeeNo, String phone, String idCard) {
        Admin admin = null;

        // 依次尝试匹配
        if (employeeNo != null && !employeeNo.trim().isEmpty()) {
            admin = adminMapper.selectOne(
                    new LambdaQueryWrapper<Admin>().eq(Admin::getEmployeeNo, employeeNo.trim())
            );
        }
        if (admin == null && phone != null && !phone.trim().isEmpty()) {
            admin = adminMapper.selectOne(
                    new LambdaQueryWrapper<Admin>().eq(Admin::getPhone, phone.trim())
            );
        }
        if (admin == null && idCard != null && !idCard.trim().isEmpty()) {
            admin = adminMapper.selectOne(
                    new LambdaQueryWrapper<Admin>().eq(Admin::getIdCard, idCard.trim())
            );
        }

        // Demo：如果数据库里没有，允许固定管理员登录
        if (admin == null) {
            admin = adminMapper.selectOne(
                    new LambdaQueryWrapper<Admin>().like(Admin::getRealName, "admin")
                            .or().like(Admin::getEmployeeNo, "admin")
                            .last("LIMIT 1")
            );
        }

        if (admin == null) {
            throw new BizException("管理员不存在");
        }

        if ("DISABLED".equalsIgnoreCase(admin.getStatus())) {
            throw new BizException("账号已被禁用");
        }

        String token = jwtUtil.generate(admin.getId(), Role.ADMIN, admin.getEmployeeNo());

        Map<String, Object> user = new HashMap<>();
        user.put("id", admin.getId());
        user.put("employeeNo", admin.getEmployeeNo());
        user.put("realName", admin.getRealName());
        user.put("role", Role.ADMIN.name());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }

    @Override
    public void logout() {
        // Demo 简化
    }

    @Override
    public Admin getById(Long id) {
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BizException("管理员不存在");
        }
        return admin;
    }

    @Override
    public PageResult<?> getUsersByRole(String role, int page, int size) {
        if (role == null) {
            throw new BizException("role 参数不能为空");
        }
        PageResult<?> pr;
        if ("PATIENT".equalsIgnoreCase(role)) {
            Page<Patient> pageParam = new Page<>(page, size);
            Page<Patient> result = patientMapper.selectPage(pageParam,
                    new LambdaQueryWrapper<Patient>().orderByDesc(Patient::getId));
            List<Map<String, Object>> list = new ArrayList<>();
            for (Patient p : result.getRecords()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("username", p.getUsername());
                m.put("realName", p.getRealName());
                m.put("phone", p.getPhone());
                m.put("idCard", p.getIdCard());
                m.put("status", p.getStatus());
                m.put("role", "PATIENT");
                m.put("createdAt", p.getCreatedAt());
                list.add(m);
            }
            pr = PageResult.of(result.getTotal(), list, page, size);
        } else if ("DOCTOR".equalsIgnoreCase(role)) {
            Page<Doctor> pageParam = new Page<>(page, size);
            Page<Doctor> result = doctorMapper.selectPage(pageParam,
                    new LambdaQueryWrapper<Doctor>().orderByDesc(Doctor::getId));
            List<Map<String, Object>> list = new ArrayList<>();
            for (Doctor d : result.getRecords()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", d.getId());
                m.put("employeeNo", d.getEmployeeNo());
                m.put("realName", d.getRealName());
                m.put("phone", d.getPhone());
                m.put("department", d.getDepartment());
                m.put("onlineStatus", d.getOnlineStatus());
                m.put("status", d.getStatus());
                m.put("role", "DOCTOR");
                m.put("createdAt", d.getCreatedAt());
                list.add(m);
            }
            pr = PageResult.of(result.getTotal(), list, page, size);
        } else {
            throw new BizException("不支持的 role: " + role);
        }
        return pr;
    }

    @Override
    public void updateUserStatus(String role, Long userId, String status) {
        if (!"ACTIVE".equalsIgnoreCase(status) && !"DISABLED".equalsIgnoreCase(status)) {
            throw new BizException("status 只允许 ACTIVE 或 DISABLED");
        }
        String finalStatus = status.toUpperCase();
        if ("PATIENT".equalsIgnoreCase(role)) {
            Patient p = patientMapper.selectById(userId);
            if (p == null) throw new BizException("患者不存在");
            p.setStatus(finalStatus);
            p.setUpdatedAt(LocalDateTime.now());
            patientMapper.updateById(p);
        } else if ("DOCTOR".equalsIgnoreCase(role)) {
            Doctor d = doctorMapper.selectById(userId);
            if (d == null) throw new BizException("医生不存在");
            d.setStatus(finalStatus);
            d.setUpdatedAt(LocalDateTime.now());
            doctorMapper.updateById(d);
        } else {
            throw new BizException("不支持的 role: " + role);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> createUser(String role, AdminUserRequest req, Long operatorId) {
        if (role == null || role.isBlank()) {
            throw new BizException("role 参数不能为空");
        }
        if ("PATIENT".equalsIgnoreCase(role)) {
            return createPatient(req, operatorId);
        }
        if ("DOCTOR".equalsIgnoreCase(role)) {
            return createDoctor(req, operatorId);
        }
        throw new BizException("不支持的 role: " + role);
    }

    @Override
    @Transactional
    public Map<String, Object> updateUser(String role, Long userId, AdminUserRequest req, Long operatorId) {
        if (role == null || role.isBlank()) {
            throw new BizException("role 参数不能为空");
        }
        if ("PATIENT".equalsIgnoreCase(role)) {
            return updatePatient(userId, req, operatorId);
        }
        if ("DOCTOR".equalsIgnoreCase(role)) {
            return updateDoctor(userId, req, operatorId);
        }
        throw new BizException("不支持的 role: " + role);
    }

    // ===================== 新增患者 =====================

    private Map<String, Object> createPatient(AdminUserRequest req, Long operatorId) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new BizException("患者用户名 username 不能为空");
        }
        String username = req.getUsername().trim();
        Patient exist = patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>().eq(Patient::getUsername, username));
        if (exist != null) {
            throw new BizException("用户名已存在: " + username);
        }

        Patient p = new Patient();
        p.setUsername(username);
        p.setPassword(encodePassword(req.getPassword()));
        p.setRealName(req.getRealName());
        p.setGender(validateGender(req.getGender()));
        p.setPhone(validatePhone(req.getPhone()));
        p.setIdCard(req.getIdCard());
        p.setStatus(normalizeStatus(req.getStatus(), "ACTIVE"));
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        patientMapper.insert(p);

        writeAudit(operatorId, "CREATE_USER", "PATIENT", p.getId(),
                "新增患者：" + (p.getRealName() != null ? p.getRealName() : username));
        return patientMap(p);
    }

    // ===================== 新增医生 =====================

    private Map<String, Object> createDoctor(AdminUserRequest req, Long operatorId) {
        if (req.getEmployeeNo() == null || req.getEmployeeNo().isBlank()) {
            throw new BizException("医生工号 employeeNo 不能为空");
        }
        String employeeNo = req.getEmployeeNo().trim();
        Doctor exist = doctorMapper.selectOne(
                new LambdaQueryWrapper<Doctor>().eq(Doctor::getEmployeeNo, employeeNo));
        if (exist != null) {
            throw new BizException("工号已存在: " + employeeNo);
        }

        Doctor d = new Doctor();
        d.setEmployeeNo(employeeNo);
        d.setPassword(encodePassword(req.getPassword()));
        d.setRealName(req.getRealName());
        d.setGender(validateGender(req.getGender()));
        d.setTitle(req.getTitle());
        d.setPhone(validatePhone(req.getPhone()));
        d.setDepartment(req.getDepartment());
        d.setBio(req.getBio());
        d.setOnlineStatus(req.getOnlineStatus() == null || req.getOnlineStatus().isBlank()
                ? "OFFLINE" : validateOnlineStatus(req.getOnlineStatus()));
        d.setStatus(normalizeStatus(req.getStatus(), "ACTIVE"));
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        doctorMapper.insert(d);

        writeAudit(operatorId, "CREATE_USER", "DOCTOR", d.getId(),
                "新增医生：" + (d.getRealName() != null ? d.getRealName() : employeeNo));
        return doctorMap(d);
    }

    // ===================== 修改患者 =====================

    private Map<String, Object> updatePatient(Long id, AdminUserRequest req, Long operatorId) {
        Patient p = patientMapper.selectById(id);
        if (p == null) {
            throw new BizException("患者不存在");
        }

        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            String newUsername = req.getUsername().trim();
            Patient exist = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getUsername, newUsername)
                    .ne(Patient::getId, id));
            if (exist != null) {
                throw new BizException("用户名已存在: " + newUsername);
            }
            p.setUsername(newUsername);
        }
        if (req.getRealName() != null) p.setRealName(req.getRealName());
        if (req.getGender() != null) p.setGender(validateGender(req.getGender()));
        if (req.getPhone() != null) p.setPhone(validatePhone(req.getPhone()));
        if (req.getIdCard() != null) p.setIdCard(req.getIdCard());
        if (req.getStatus() != null) p.setStatus(normalizeStatus(req.getStatus(), p.getStatus()));
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            p.setPassword(encodePassword(req.getPassword()));
        }
        p.setUpdatedAt(LocalDateTime.now());
        patientMapper.updateById(p);

        writeAudit(operatorId, "UPDATE_USER", "PATIENT", id, "修改患者：" + p.getUsername());
        return patientMap(p);
    }

    // ===================== 修改医生 =====================

    private Map<String, Object> updateDoctor(Long id, AdminUserRequest req, Long operatorId) {
        Doctor d = doctorMapper.selectById(id);
        if (d == null) {
            throw new BizException("医生不存在");
        }

        if (req.getEmployeeNo() != null && !req.getEmployeeNo().isBlank()) {
            String newEmployeeNo = req.getEmployeeNo().trim();
            Doctor exist = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>()
                    .eq(Doctor::getEmployeeNo, newEmployeeNo)
                    .ne(Doctor::getId, id));
            if (exist != null) {
                throw new BizException("工号已存在: " + newEmployeeNo);
            }
            d.setEmployeeNo(newEmployeeNo);
        }
        if (req.getRealName() != null) d.setRealName(req.getRealName());
        if (req.getGender() != null) d.setGender(validateGender(req.getGender()));
        if (req.getTitle() != null) d.setTitle(req.getTitle());
        if (req.getPhone() != null) d.setPhone(validatePhone(req.getPhone()));
        if (req.getDepartment() != null) d.setDepartment(req.getDepartment());
        if (req.getBio() != null) d.setBio(req.getBio());
        if (req.getOnlineStatus() != null) d.setOnlineStatus(validateOnlineStatus(req.getOnlineStatus()));
        if (req.getStatus() != null) d.setStatus(normalizeStatus(req.getStatus(), d.getStatus()));
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            d.setPassword(encodePassword(req.getPassword()));
        }
        d.setUpdatedAt(LocalDateTime.now());
        doctorMapper.updateById(d);

        writeAudit(operatorId, "UPDATE_USER", "DOCTOR", id, "修改医生：" + d.getEmployeeNo());
        return doctorMap(d);
    }

    // ===================== 工具方法 =====================

    private String encodePassword(String raw) {
        if (raw == null || raw.isBlank()) {
            return passwordEncoder.encode(DEMO_PASSWORD);
        }
        if (raw.length() < 6 || raw.length() > 64) {
            throw new BizException("密码长度需为 6-64 位");
        }
        return passwordEncoder.encode(raw);
    }

    private String validateGender(String gender) {
        if (gender == null || gender.isBlank()) return null;
        String g = gender.trim();
        if ("男".equals(g) || "女".equals(g)) return g;
        throw new BizException("性别只能为 男 或 女");
    }

    private String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String p = phone.trim();
        if (!p.matches("\\d{11}")) {
            throw new BizException("手机号需为 11 位数字");
        }
        return p;
    }

    private String normalizeStatus(String status, String defaultStatus) {
        if (status == null || status.isBlank()) return defaultStatus;
        String s = status.trim().toUpperCase();
        if (!"ACTIVE".equals(s) && !"DISABLED".equals(s)) {
            throw new BizException("status 只允许 ACTIVE 或 DISABLED");
        }
        return s;
    }

    private String validateOnlineStatus(String onlineStatus) {
        if (onlineStatus == null || onlineStatus.isBlank()) return null;
        String s = onlineStatus.trim().toUpperCase();
        if (!"ONLINE".equals(s) && !"OFFLINE".equals(s)) {
            throw new BizException("onlineStatus 只允许 ONLINE 或 OFFLINE");
        }
        return s;
    }

    private void writeAudit(Long operatorId, String action, String targetType, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(operatorId);
        log.setRole("ADMIN");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setActionTaken("ALLOW");
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private Map<String, Object> patientMap(Patient p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("username", p.getUsername());
        m.put("realName", p.getRealName());
        m.put("gender", p.getGender());
        m.put("phone", p.getPhone());
        m.put("idCard", p.getIdCard());
        m.put("status", p.getStatus());
        m.put("role", "PATIENT");
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private Map<String, Object> doctorMap(Doctor d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("employeeNo", d.getEmployeeNo());
        m.put("realName", d.getRealName());
        m.put("gender", d.getGender());
        m.put("title", d.getTitle());
        m.put("phone", d.getPhone());
        m.put("department", d.getDepartment());
        m.put("onlineStatus", d.getOnlineStatus());
        m.put("status", d.getStatus());
        m.put("role", "DOCTOR");
        m.put("createdAt", d.getCreatedAt());
        return m;
    }

    @Override
    public List<String> getRoles() {
        return Arrays.asList("PATIENT", "DOCTOR", "ADMIN");
    }

    @Override
    public PageResult<?> getAuditLogs(int page, int size, String role, String action) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (role != null && !role.trim().isEmpty()) {
            wrapper.eq(AuditLog::getRole, role);
        }
        if (action != null && !action.trim().isEmpty()) {
            wrapper.eq(AuditLog::getAction, action);
        }
        wrapper.orderByDesc(AuditLog::getId);

        Page<AuditLog> pageParam = new Page<>(page, size);
        Page<AuditLog> result = auditLogMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public List<?> getAllKnowledgeBases() {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>().orderByDesc(KnowledgeBase::getId)
        );
    }

    @Override
    public List<?> getAllModelConfigs() {
        return modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>().orderByDesc(ModelConfig::getId)
        );
    }

    @Override
    public List<?> getDataQuality() {
        return dataQualityMapper.selectList(
                new LambdaQueryWrapper<DataQuality>().orderByDesc(DataQuality::getId)
        );
    }

    @Override
    public List<?> getSensitiveContents(String status) {
        LambdaQueryWrapper<SensitiveContent> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(SensitiveContent::getReviewStatus, status.trim());
        }
        wrapper.orderByDesc(SensitiveContent::getId);
        return sensitiveContentMapper.selectList(wrapper);
    }
}

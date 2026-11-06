package com.hospital.smartmedical.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.security.DemoMode;
import com.hospital.smartmedical.common.security.JwtUtil;
import com.hospital.smartmedical.module.admin.dto.AdminUserRequest;
import com.hospital.smartmedical.module.ai.agent.config.AiModelConfigProvider;
import com.hospital.smartmedical.module.admin.entity.*;
import com.hospital.smartmedical.module.admin.mapper.*;
import com.hospital.smartmedical.module.admin.service.AdminService;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ModelConfigMapper modelConfigMapper;
    private final DataQualityMapper dataQualityMapper;
    private final com.hospital.smartmedical.module.admin.scheduler.DataQualityScheduler dataQualityScheduler;
    private final JwtUtil jwtUtil;

    /** 模型配置提供者：管理端修改配置后使其缓存失效，实现热更新 */
    private final AiModelConfigProvider modelConfigProvider;

    /** 演示模式（仅 demo profile 下存在）：存在时允许新建账号使用默认初始密码 */
    private final ObjectProvider<DemoMode> demoModeProvider;

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
    public PageResult<?> getUsersByRole(String role, int page, int size, String keyword) {
        if (role == null) {
            throw new BizException("role 参数不能为空");
        }
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        PageResult<?> pr;
        if ("PATIENT".equalsIgnoreCase(role)) {
            Page<Patient> pageParam = new Page<>(page, size);
            LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<>();
            if (kw != null) {
                wrapper.and(w -> w.like(Patient::getUsername, kw)
                        .or().like(Patient::getRealName, kw)
                        .or().like(Patient::getPhone, kw)
                        .or().like(Patient::getIdCard, kw));
            }
            wrapper.orderByDesc(Patient::getId);
            Page<Patient> result = patientMapper.selectPage(pageParam, wrapper);
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
            LambdaQueryWrapper<Doctor> wrapper = new LambdaQueryWrapper<>();
            if (kw != null) {
                wrapper.and(w -> w.like(Doctor::getEmployeeNo, kw)
                        .or().like(Doctor::getRealName, kw)
                        .or().like(Doctor::getPhone, kw)
                        .or().like(Doctor::getDepartment, kw));
            }
            wrapper.orderByDesc(Doctor::getId);
            Page<Doctor> result = doctorMapper.selectPage(pageParam, wrapper);
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
        // 身份证号：新增患者必填，且必须为 18 位（17 位数字 + 1 位数字或 X）
        if (req.getIdCard() == null || req.getIdCard().isBlank()) {
            throw new BizException("患者身份证号不能为空");
        }
        p.setIdCard(validateIdCard(req.getIdCard()));
        p.setStatus(normalizeStatus(req.getStatus(), "ACTIVE"));
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        patientMapper.insert(p);

        writeAudit(operatorId, "CREATE_USER", p.getId(),
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

        writeAudit(operatorId, "CREATE_USER", d.getId(),
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
        // 身份证号：修改时提供非空值则校验必须为 18 位，避免不足 18 位也能保存
        if (req.getIdCard() != null) p.setIdCard(validateIdCard(req.getIdCard()));
        if (req.getStatus() != null) p.setStatus(normalizeStatus(req.getStatus(), p.getStatus()));
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            p.setPassword(encodePassword(req.getPassword()));
        }
        p.setUpdatedAt(LocalDateTime.now());
        patientMapper.updateById(p);

        writeAudit(operatorId, "UPDATE_USER", id, "修改患者：" + p.getUsername());
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

        writeAudit(operatorId, "UPDATE_USER", id, "修改医生：" + d.getEmployeeNo());
        return doctorMap(d);
    }

    // ===================== 工具方法 =====================

    private String encodePassword(String raw) {
        if (raw == null || raw.isBlank()) {
            // 默认密码只在 demo profile 下允许；生产环境必须由管理员显式设置初始密码
            DemoMode demoMode = demoModeProvider.getIfAvailable();
            if (demoMode == null) {
                throw new BizException("请设置初始密码（生产环境不提供默认密码）");
            }
            return passwordEncoder.encode(demoMode.defaultPassword());
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

    /**
     * 校验身份证号格式：必须为 18 位（17 位数字 + 1 位数字或 X/x）。
     * 空值返回 null（修改场景表示不修改该字段）；格式不合法抛出业务异常。
     */
    private String validateIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) return null;
        String c = idCard.trim();
        if (!c.matches("\\d{17}[\\dXx]")) {
            throw new BizException("身份证号需为 18 位（17 位数字 + 1 位数字或 X）");
        }
        return c;
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

    /**
     * 写入一条管理员操作审计日志
     * 自动打标敏感等级（按操作类型）并将审核状态置为待审核（PENDING）
     * @param operatorId 操作人ID（管理员ID）
     * @param action     操作类型（CREATE_USER/UPDATE_USER 等）
     * @param targetId   操作目标ID（如患者/医生ID）
     * @param detail     操作详情描述
     */
    private void writeAudit(Long operatorId, String action, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(operatorId);
        log.setRole("ADMIN");
        log.setAction(action);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setSensitiveLevel(determineSensitiveLevel(action));
        log.setReviewStatus("PENDING");
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    /**
     * 根据操作类型判定敏感等级
     * 规则：新增类操作（CREATE 开头）→ LOW；修改类操作（UPDATE 开头）→ MEDIUM；
     *      删除类操作（DELETE/DEL 开头）→ HIGH；其余未知类型保守取 MEDIUM
     * @param action 操作类型枚举（如 CREATE_USER / UPDATE_USER）
     * @return 敏感等级 LOW / MEDIUM / HIGH
     */
    private String determineSensitiveLevel(String action) {
        if (action == null) {
            return "MEDIUM";
        }
        if (action.startsWith("CREATE")) {
            return "LOW";
        }
        if (action.startsWith("UPDATE")) {
            return "MEDIUM";
        }
        if (action.startsWith("DELETE") || action.startsWith("DEL")) {
            return "HIGH";
        }
        return "MEDIUM";
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

    /**
     * 分页查询审计日志
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param role   按角色过滤（PATIENT/DOCTOR/ADMIN，可空）
     * @param action 按操作类型过滤（CREATE_USER/UPDATE_USER 等，可空）
     * @return 分页结果，含总条数与当前页记录
     */
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

    /**
     * 审核一条审计日志（通过或驳回）
     * 规则：仅待审核（PENDING）日志可审核，已审核的不允许重复审核；
     *      驳回（REJECTED）时自动禁用「发起该操作的管理员」账号（sm_admin.status=DISABLED），
     *      使其无法再登录进行后续业务操作（登录校验在 login 中已处理）；通过（APPROVED）不禁用。
     * @param id       审计日志ID
     * @param decision 审核结论：APPROVED 通过 / REJECTED 驳回
     */
    @Override
    @Transactional
    public void reviewAuditLog(Long id, String decision) {
        if (id == null) {
            throw new BizException("审计日志ID不能为空");
        }
        if (decision == null || decision.trim().isEmpty()) {
            throw new BizException("审核结论 decision 不能为空");
        }
        String finalDecision = decision.trim().toUpperCase();
        if (!"APPROVED".equals(finalDecision) && !"REJECTED".equals(finalDecision)) {
            throw new BizException("审核结论只允许 APPROVED 或 REJECTED");
        }
        AuditLog log = auditLogMapper.selectById(id);
        if (log == null) {
            throw new BizException("审计日志不存在");
        }
        if (!"PENDING".equalsIgnoreCase(log.getReviewStatus())) {
            throw new BizException("该日志已审核，不可重复审核");
        }
        // 驳回时禁用发起该操作的管理员账号，实现「不可行则禁止该用户修改/删除业务操作」
        if ("REJECTED".equals(finalDecision)) {
            Admin operator = adminMapper.selectById(log.getUserId());
            if (operator != null) {
                operator.setStatus("DISABLED");
                adminMapper.updateById(operator);
            }
        }
        log.setReviewStatus(finalDecision);
        auditLogMapper.updateById(log);
    }

    @Override
    public List<?> getAllModelConfigs() {
        return modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>().orderByDesc(ModelConfig::getId)
        );
    }

    @Override
    public ModelConfig createModelConfig(ModelConfig config) {
        validateModelConfig(config);
        config.setId(null);
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        // 同类型仅允许一条启用，保证「当前生效配置」唯一
        if (config.getEnabled() == 1) {
            disableOtherEnabled(config.getType(), null);
        }
        modelConfigMapper.insert(config);
        // 热更新：清空提供者缓存，下一次 AI 调用使用新配置
        modelConfigProvider.invalidate();
        return config;
    }

    @Override
    public ModelConfig updateModelConfig(Long id, ModelConfig config) {
        ModelConfig old = modelConfigMapper.selectById(id);
        if (old == null) {
            throw new BizException("模型配置不存在");
        }
        validateModelConfig(config);
        ModelConfig update = new ModelConfig();
        update.setId(id);
        update.setConfigName(config.getConfigName());
        update.setType(config.getType());
        update.setProvider(config.getProvider());
        update.setModelName(config.getModelName());
        update.setApiUrl(config.getApiUrl());
        // apiKey 为 null 表示不修改，避免前端未回填时误清空密钥
        update.setApiKey(config.getApiKey() != null ? config.getApiKey() : old.getApiKey());
        update.setMaxTokens(config.getMaxTokens());
        update.setRemark(config.getRemark());
        update.setEnabled(config.getEnabled() != null ? config.getEnabled() : old.getEnabled());
        if (update.getEnabled() != null && update.getEnabled() == 1) {
            disableOtherEnabled(update.getType(), id);
        }
        modelConfigMapper.updateById(update);
        modelConfigProvider.invalidate();
        return modelConfigMapper.selectById(id);
    }

    @Override
    public void deleteModelConfig(Long id) {
        if (modelConfigMapper.selectById(id) == null) {
            throw new BizException("模型配置不存在");
        }
        modelConfigMapper.deleteById(id);
        modelConfigProvider.invalidate();
    }

    @Override
    public Map<String, Object> getActiveModelInfo() {
        AiModelConfigProvider.ChatModelSpec spec = modelConfigProvider.chatModel();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("configId", spec.getConfigId());
        info.put("configName", spec.getConfigName());
        info.put("provider", spec.getProvider());
        info.put("modelName", spec.getModelName());
        info.put("apiUrl", spec.getApiUrl());
        info.put("maxTokens", spec.getMaxTokens());
        info.put("source", spec.getSource());
        info.put("apiKeyConfigured", spec.getApiKey() != null && !spec.getApiKey().isBlank());
        return info;
    }

    /**
     * 校验模型配置必填项与类型枚举。
     *
     * @param config 模型配置
     */
    private void validateModelConfig(ModelConfig config) {
        if (config == null) {
            throw new BizException("模型配置不能为空");
        }
        if (config.getConfigName() == null || config.getConfigName().isBlank()) {
            throw new BizException("配置名称不能为空");
        }
        if (config.getType() == null || config.getType().isBlank()) {
            throw new BizException("配置类型不能为空");
        }
        if (!"聊天模型".equals(config.getType()) && !"向量模型".equals(config.getType())) {
            throw new BizException("配置类型只允许：聊天模型 / 向量模型");
        }
        if (config.getModelName() == null || config.getModelName().isBlank()) {
            throw new BizException("模型名称不能为空");
        }
        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            throw new BizException("接口地址不能为空");
        }
    }

    /**
     * 停用同类型的其他配置（保证同一类型只有一条启用，即「唯一生效」）。
     *
     * @param type      配置类型
     * @param excludeId 排除的配置 ID（修改时为自身）
     */
    private void disableOtherEnabled(String type, Long excludeId) {
        List<ModelConfig> others = modelConfigMapper.selectList(new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getType, type)
                .eq(ModelConfig::getEnabled, 1));
        for (ModelConfig other : others) {
            if (excludeId != null && excludeId.equals(other.getId())) {
                continue;
            }
            other.setEnabled(0);
            modelConfigMapper.updateById(other);
        }
    }

    @Override
    public List<?> getDataQuality() {
        return dataQualityMapper.selectList(
                new LambdaQueryWrapper<DataQuality>().orderByDesc(DataQuality::getId)
        );
    }

    @Override
    public DataQuality runDataQualityCheck() {
        dataQualityScheduler.runQualityCheck();
        return dataQualityMapper.selectOne(
                new LambdaQueryWrapper<DataQuality>().orderByDesc(DataQuality::getId).last("LIMIT 1")
        );
    }
}

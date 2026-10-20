package com.hospital.smartmedical.module.doctor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.security.JwtUtil;
import com.hospital.smartmedical.common.storage.FileStorageService;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.doctor.service.DoctorService;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.visit.entity.Visit;
import com.hospital.smartmedical.module.visit.mapper.VisitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    private final VisitMapper visitMapper;
    private final AppointmentMapper appointmentMapper;
    private final JwtUtil jwtUtil;
    private final FileStorageService fileStorageService;

    // Demo 密码 hash: 123456
    private static final String DEMO_PASSWORD_HASH =
            "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, Object> login(String employeeNo, String username, String password) {
        Doctor doctor = null;

        // 1. 先按 employeeNo 精确查
        if (employeeNo != null && !employeeNo.trim().isEmpty()) {
            doctor = doctorMapper.selectOne(
                    new LambdaQueryWrapper<Doctor>().eq(Doctor::getEmployeeNo, employeeNo.trim())
            );
        }

        // 2. 再按 username / realName 模糊查（兼容 Demo）
        if (doctor == null && username != null && !username.trim().isEmpty()) {
            doctor = doctorMapper.selectOne(
                    new LambdaQueryWrapper<Doctor>().eq(Doctor::getEmployeeNo, username.trim())
            );
            if (doctor == null) {
                doctor = doctorMapper.selectOne(
                        new LambdaQueryWrapper<Doctor>().like(Doctor::getRealName, username.trim())
                                .last("LIMIT 1")
                );
            }
        }

        if (doctor == null) {
            throw new BizException("医生不存在");
        }

        // 密码校验
        boolean matched = passwordEncoder.matches(password, DEMO_PASSWORD_HASH);
        if (!matched && doctor.getPassword() != null) {
            matched = passwordEncoder.matches(password, doctor.getPassword());
        }
        if (!matched) {
            throw new BizException("密码错误");
        }

        if ("DISABLED".equalsIgnoreCase(doctor.getStatus())) {
            throw new BizException("账号已被禁用");
        }

        String token = jwtUtil.generate(doctor.getId(), Role.DOCTOR, doctor.getEmployeeNo());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildUserDto(doctor));
        return result;
    }

    @Override
    public void logout() {
        // Demo 简化
    }

    @Override
    public Doctor getById(Long id) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BizException("医生不存在");
        }
        return doctor;
    }

    @Override
    public Doctor update(Long id, Doctor updates) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BizException("医生不存在");
        }
        if (updates.getRealName() != null) doctor.setRealName(updates.getRealName());
        if (updates.getPhone() != null) doctor.setPhone(updates.getPhone());
        if (updates.getDepartment() != null) doctor.setDepartment(updates.getDepartment());
        if (updates.getOnlineStatus() != null) doctor.setOnlineStatus(updates.getOnlineStatus());
        // 性别：空串视为清空；非空只允许 男/女
        if (updates.getGender() != null) {
            String g = updates.getGender().trim();
            if (g.isEmpty()) {
                doctor.setGender(null);
            } else if ("男".equals(g) || "女".equals(g)) {
                doctor.setGender(g);
            } else {
                throw new BizException("性别只能为 男 或 女");
            }
        }
        if (updates.getTitle() != null) doctor.setTitle(updates.getTitle());
        if (updates.getBio() != null) doctor.setBio(updates.getBio());
        doctor.setUpdatedAt(LocalDateTime.now());
        doctorMapper.updateById(doctor);
        return doctor;
    }

    @Override
    public String uploadAvatar(Long id, MultipartFile file) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BizException("医生不存在");
        }
        String avatarUrl = fileStorageService.saveAvatar(file, "doctor_" + id);
        doctor.setAvatar(avatarUrl);
        doctor.setUpdatedAt(LocalDateTime.now());
        doctorMapper.updateById(doctor);
        return avatarUrl;
    }

    @Override
    public List<Map<String, Object>> getMyPatients(Long doctorId) {
        // 从 Visit 表查出该医生所有 distinct patient_id
        List<Visit> visits = visitMapper.selectList(
                new LambdaQueryWrapper<Visit>().eq(Visit::getDoctorId, doctorId)
                        .orderByDesc(Visit::getCreatedAt)
        );

        if (visits.isEmpty()) {
            return Collections.emptyList();
        }

        // 过滤：关联挂号单已被取消的就诊不纳入"我的患者"（患者已取消挂号）
        List<Visit> activeVisits = new ArrayList<>();
        for (Visit v : visits) {
            if (v.getAppointmentId() != null) {
                Appointment appt = appointmentMapper.selectById(v.getAppointmentId());
                if (appt != null && "CANCELLED".equals(appt.getStatus())) {
                    continue; // 跳过
                }
            }
            activeVisits.add(v);
        }
        visits = activeVisits;

        if (visits.isEmpty()) {
            return Collections.emptyList();
        }

        // 每个 patientId 找一次最近 visitTime
        Map<Long, LocalDateTime> lastVisitMap = visits.stream()
                .collect(Collectors.toMap(
                        Visit::getPatientId,
                        Visit::getCreatedAt,
                        (a, b) -> a.isAfter(b) ? a : b
                ));

        Set<Long> patientIds = lastVisitMap.keySet();
        List<Patient> patients = patientMapper.selectBatchIds(patientIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Patient p : patients) {
            Map<String, Object> item = new HashMap<>();
            item.put("patientId", p.getId());
            item.put("realName", p.getRealName());
            item.put("phone", p.getPhone());
            item.put("lastVisitTime", lastVisitMap.get(p.getId()));
            result.add(item);
        }

        // 按 lastVisitTime 倒序
        result.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("lastVisitTime");
            LocalDateTime tb = (LocalDateTime) b.get("lastVisitTime");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        return result;
    }

    @Override
    public List<Object> getMyAppointments(Long doctorId) {
        List<Appointment> appointments = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>().eq(Appointment::getDoctorId, doctorId)
                        .orderByDesc(Appointment::getAppointmentTime)
        );

        List<Object> result = new ArrayList<>();
        for (Appointment a : appointments) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("patientId", a.getPatientId());
            item.put("department", a.getDepartment());
            item.put("appointmentTime", a.getAppointmentTime());
            item.put("status", a.getStatus());
            item.put("createdAt", a.getCreatedAt());

            // 关联患者信息
            Patient p = patientMapper.selectById(a.getPatientId());
            if (p != null) {
                item.put("patientName", p.getRealName());
                item.put("patientPhone", p.getPhone());
            }
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildUserDto(Doctor d) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", d.getId());
        map.put("employeeNo", d.getEmployeeNo());
        map.put("realName", d.getRealName());
        map.put("gender", d.getGender());
        map.put("title", d.getTitle());
        map.put("department", d.getDepartment());
        map.put("avatar", d.getAvatar());
        map.put("bio", d.getBio());
        map.put("onlineStatus", d.getOnlineStatus());
        map.put("phone", d.getPhone());
        return map;
    }
}

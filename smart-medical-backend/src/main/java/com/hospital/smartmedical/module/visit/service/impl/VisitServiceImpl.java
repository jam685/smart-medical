package com.hospital.smartmedical.module.visit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.visit.entity.Visit;
import com.hospital.smartmedical.module.visit.mapper.VisitMapper;
import com.hospital.smartmedical.module.visit.service.VisitService;
import com.hospital.smartmedical.module.visit.vo.VisitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 就诊进度业务服务实现
 * 数据来源：sm_visit（主表）+ sm_doctor + sm_patient + sm_appointment（联表展示）。
 * 状态机：WAITING 候诊 → CALLING 叫号 → IN_PROGRESS 就诊中 → FINISHED 已完成。
 * progress_nodes：JSON 数组，每项 {name,time}；状态推进时按目标状态追加对应节点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    /** 就诊进度 Mapper：sm_visit 增删改查 */
    private final VisitMapper visitMapper;
    /** 医生 Mapper：联查医生姓名与职称 */
    private final DoctorMapper doctorMapper;
    /** 患者 Mapper：联查患者姓名 */
    private final PatientMapper patientMapper;
    /** 挂号 Mapper：联查挂号时间与单号 */
    private final AppointmentMapper appointmentMapper;
    /** JSON 序列化器：解析与写入 progress_nodes */
    private final ObjectMapper objectMapper;

    /** 合法的就诊状态码集合（用于 updateStatus 入参校验） */
    private static final Set<String> VALID_STATUSES =
            new HashSet<>(Arrays.asList("WAITING", "CALLING", "IN_PROGRESS", "FINISHED"));

    /** 状态 → 推进时追加的节点名称（null 表示该状态不需要追加节点，如 WAITING 是初始态） */
    private static final Map<String, String> STATUS_TO_NODE;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CALLING", "叫号");
        m.put("IN_PROGRESS", "就诊");
        m.put("FINISHED", "完成");
        // WAITING 不追加节点（挂号时由创建逻辑初始化"挂号"节点）
        STATUS_TO_NODE = Collections.unmodifiableMap(m);
    }

    /** 时间格式化模板（progress_nodes 节点时间使用此格式） */
    private static final DateTimeFormatter NODE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询就诊进度（按角色隔离 + 可选状态过滤），返回联表 VO
     */
    @Override
    public PageResult<VisitVO> listVisits(Long userId, Role role, int page, int size, String status) {
        LambdaQueryWrapper<Visit> qw = new LambdaQueryWrapper<>();
        // 数据隔离：患者只看本人，医生只看本人接诊，管理员看全部
        if (role == Role.PATIENT) {
            qw.eq(Visit::getPatientId, userId);
        } else if (role == Role.DOCTOR) {
            qw.eq(Visit::getDoctorId, userId);
        }
        // 可选状态过滤
        if (StringUtils.hasText(status)) {
            qw.eq(Visit::getStatus, status);
        }
        // 业务规则：关联挂号单已被取消（sm_appointment.status=CANCELLED）的就诊进度不再展示；
        // appointment_id 为 NULL 的就诊（如 AI 问诊发起）不受此规则影响，正常展示。
        qw.apply("(appointment_id IS NULL OR appointment_id NOT IN " +
                "(SELECT id FROM sm_appointment WHERE status = 'CANCELLED'))");
        qw.orderByDesc(Visit::getCreatedAt);

        Page<Visit> pageParam = Page.of(Math.max(1, page), Math.max(1, size));
        Page<Visit> result = visitMapper.selectPage(pageParam, qw);

        // 批量取出本页涉及的医生/患者/挂号单，避免 N+1 查询
        List<Visit> records = result.getRecords();
        Set<Long> doctorIds = records.stream().map(Visit::getDoctorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> patientIds = records.stream().map(Visit::getPatientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> apptIds = records.stream().map(Visit::getAppointmentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Doctor> doctorMap = doctorIds.isEmpty() ? Collections.emptyMap()
                : doctorMapper.selectBatchIds(doctorIds).stream()
                    .collect(Collectors.toMap(Doctor::getId, d -> d));
        Map<Long, Patient> patientMap = patientIds.isEmpty() ? Collections.emptyMap()
                : patientMapper.selectBatchIds(patientIds).stream()
                    .collect(Collectors.toMap(Patient::getId, p -> p));
        Map<Long, Appointment> apptMap = apptIds.isEmpty() ? Collections.emptyMap()
                : appointmentMapper.selectBatchIds(apptIds).stream()
                    .collect(Collectors.toMap(Appointment::getId, a -> a));

        // 逐条解析 progress_nodes JSON 并组装 VO
        List<VisitVO> voList = records.stream()
                .map(v -> VisitVO.of(v, doctorMap.get(v.getDoctorId()), patientMap.get(v.getPatientId()),
                        v.getAppointmentId() == null ? null : apptMap.get(v.getAppointmentId()),
                        parseProgressNodes(v.getProgressNodes())))
                .collect(Collectors.toList());

        return PageResult.of(result.getTotal(), voList, (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 查询单条就诊详情：校验存在性 + 归属权限，再联表组装 VO
     */
    @Override
    public VisitVO getVisitDetail(Long id, Long userId, Role role) {
        Visit visit = visitMapper.selectById(id);
        if (visit == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 归属校验：患者只能看本人，医生只能看本人接诊，管理员不限
        if (role == Role.PATIENT && !visit.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (role == Role.DOCTOR && !visit.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Doctor doctor = visit.getDoctorId() == null ? null : doctorMapper.selectById(visit.getDoctorId());
        Patient patient = visit.getPatientId() == null ? null : patientMapper.selectById(visit.getPatientId());
        Appointment appt = visit.getAppointmentId() == null ? null
                : appointmentMapper.selectById(visit.getAppointmentId());
        // 业务规则：若该就诊关联的挂号单已被取消，则不展示该就诊记录（与列表口径一致）
        if (appt != null && "CANCELLED".equals(appt.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return VisitVO.of(visit, doctor, patient, appt, parseProgressNodes(visit.getProgressNodes()));
    }

    /**
     * 推进就诊状态：
     * 1) 权限：仅医生/管理员可操作（患者无权推进流程）
     * 2) 入参：状态码必须合法
     * 3) 归属：医生只能推进本人接诊的就诊记录
     * 4) 副作用：按目标状态追加 progress_nodes 节点（带当前时间戳），并更新 status/updated_at
     */
    @Override
    public void updateStatus(Long id, String status, Long userId, Role role) {
        if (role != Role.DOCTOR && role != Role.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!VALID_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Visit visit = visitMapper.selectById(id);
        if (visit == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (role == Role.DOCTOR && !visit.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        // 状态机校验：不允许从已完成回退，也不允许原地跳转（同状态直接返回成功，幂等）
        String oldStatus = visit.getStatus();
        if ("FINISHED".equals(oldStatus) && !"FINISHED".equals(status)) {
            throw new BizException("已完成的就诊不能回退状态");
        }
        if (Objects.equals(oldStatus, status)) {
            return; // 幂等：状态未变直接返回
        }

        // 追加进度节点（CALLING/IN_PROGRESS/FINISHED 各自追加；WAITING 不追加）
        String nodeName = STATUS_TO_NODE.get(status);
        if (nodeName != null) {
            List<Map<String, Object>> nodes = parseProgressNodes(visit.getProgressNodes());
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", nodeName);
            node.put("time", LocalDateTime.now().format(NODE_TIME_FMT));
            nodes.add(node);
            try {
                visit.setProgressNodes(objectMapper.writeValueAsString(nodes));
            } catch (JsonProcessingException e) {
                log.warn("progress_nodes 序列化失败 visit id={}: {}", id, e.getMessage());
                throw new BizException("进度节点写入失败");
            }
        }
        visit.setStatus(status);
        visit.setUpdatedAt(LocalDateTime.now());
        visitMapper.updateById(visit);
        log.info("就诊状态推进: visitId={}, {} -> {}, operator={}", id, oldStatus, status, userId);
    }

    /**
     * 解析 progress_nodes JSON 字符串为有序节点列表
     * 业务规则：非法 JSON 一律降级为空数组，避免阻塞列表/详情查询
     *
     * @param json progress_nodes 字段原值（可空）
     * @return 节点列表（每项 {name,time}），永不返回 null
     */
    private List<Map<String, Object>> parseProgressNodes(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> nodes = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return nodes == null ? Collections.emptyList() : nodes;
        } catch (JsonProcessingException e) {
            log.warn("progress_nodes JSON 解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}

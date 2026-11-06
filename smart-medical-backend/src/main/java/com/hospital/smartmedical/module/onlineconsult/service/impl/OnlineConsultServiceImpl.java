package com.hospital.smartmedical.module.onlineconsult.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.onlineconsult.entity.ConsultMessage;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import com.hospital.smartmedical.module.onlineconsult.mapper.ConsultMessageMapper;
import com.hospital.smartmedical.module.onlineconsult.mapper.OnlineConsultRecordMapper;
import com.hospital.smartmedical.module.onlineconsult.service.OnlineConsultService;
import com.hospital.smartmedical.module.onlineconsult.vo.OnlineDoctorVO;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.visit.entity.Visit;
import com.hospital.smartmedical.module.visit.mapper.VisitMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 线上面诊模块服务实现。
 *
 * <p>核心流程：患者发起问诊（主诉写入首条 PATIENT 消息）→ 医患消息往来 → 医生确认回复
 * （写入 DOCTOR 消息）→ 关闭问诊。V1.3 已删除敏感内容表，消息发送不再做敏感词阻断落库，
 * 仅保留归属校验与脱敏，避免越权与敏感信息泄露。</p>
 */
@Service
@RequiredArgsConstructor
public class OnlineConsultServiceImpl implements OnlineConsultService {

    private final OnlineConsultRecordMapper onlineConsultMapper;
    private final ConsultMessageMapper consultMessageMapper;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    /** 挂号单 Mapper：用于校验患者的主治医生归属（发起问诊前校验挂号记录） */
    private final AppointmentMapper appointmentMapper;
    /** 就诊记录 Mapper：用于"医生叫号"门禁校验（未叫号不能对话） */
    private final VisitMapper visitMapper;

    /** 问诊状态常量 */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REPLIED = "REPLIED";
    private static final String STATUS_CLOSED = "CLOSED";

    /** 消息发送方常量 */
    private static final String SENDER_PATIENT = "PATIENT";
    private static final String SENDER_DOCTOR = "DOCTOR";

    /** 消息类型常量 */
    private static final String MSG_TYPE_TEXT = "TEXT";
    private static final String MSG_TYPE_IMAGE = "IMAGE";

    // ===================== 归属校验 =====================

    /**
     * 加载问诊并做归属校验：管理员可见全部；患者仅本人；医生仅本人接诊的问诊。越权抛 403。
     */
    private OnlineConsultRecord getOwnedConsult(Long consultId, Long userId, Role role) {
        OnlineConsultRecord c = onlineConsultMapper.selectById(consultId);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (role == Role.ADMIN) return c;
        if (role == Role.PATIENT && !c.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (role == Role.DOCTOR && !c.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return c;
    }

    // ===================== 叫号门禁 =====================

    /**
     * 查询患者在该医生名下最近一次就诊记录的状态。
     * @return WAITING 候诊 / CALLING 叫号 / IN_PROGRESS 就诊中 / FINISHED 已完成；无记录返回 null
     */
    private String latestVisitStatus(Long patientId, Long doctorId) {
        LambdaQueryWrapper<Visit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Visit::getPatientId, patientId);
        wrapper.eq(Visit::getDoctorId, doctorId);
        wrapper.orderByDesc(Visit::getCreatedAt);
        wrapper.last("LIMIT 1");
        Visit visit = visitMapper.selectOne(wrapper);
        return visit == null ? null : visit.getStatus();
    }

    /**
     * 接诊门禁：医生点击「接诊」（就诊状态进入 IN_PROGRESS）后才能进行在线问诊对话。
     * 未接诊（无就诊记录、WAITING 候诊或 CALLING 已叫号）时，医生与患者均不能开启对话。
     */
    private void ensureDoctorAccepted(Long patientId, Long doctorId) {
        if (!isDoctorAccepted(patientId, doctorId)) {
            throw new BizException("医生尚未接诊，暂时无法开始对话");
        }
    }

    /**
     * 医生是否已接诊（接诊门禁与「我的医生」列表共用的唯一判据）。
     *
     * @param patientId 患者 ID
     * @param doctorId  医生 ID
     * @return true=已接诊（IN_PROGRESS / FINISHED），false=未接诊
     */
    private boolean isDoctorAccepted(Long patientId, Long doctorId) {
        String status = latestVisitStatus(patientId, doctorId);
        return "IN_PROGRESS".equalsIgnoreCase(status)
                || "FINISHED".equalsIgnoreCase(status);
    }

    /**
     * 身份证号脱敏：保留前 3 位与后 4 位，中间以 * 填充；长度不足 8 位原样返回。
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        int len = idCard.length();
        return idCard.substring(0, 3) + "*".repeat(len - 7) + idCard.substring(len - 4);
    }

    /**
     * 患者信息脱敏视图：剔除密码，身份证号脱敏，仅返回问诊详情所需公开字段。
     */
    private Map<String, Object> toPatientSafe(Patient p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("username", p.getUsername());
        map.put("realName", p.getRealName());
        map.put("gender", p.getGender());
        map.put("phone", p.getPhone());
        map.put("idCard", maskIdCard(p.getIdCard()));
        map.put("avatar", p.getAvatar());
        map.put("status", p.getStatus());
        return map;
    }

    // ===================== 1. 在线医生列表（OC-12） =====================

    @Override
    public List<OnlineDoctorVO> getOnlineDoctors(String department, String keyword) {
        LambdaQueryWrapper<Doctor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Doctor::getOnlineStatus, "ONLINE");
        if (department != null && !department.isBlank()) {
            wrapper.eq(Doctor::getDepartment, department);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Doctor::getRealName, keyword);
        }
        wrapper.eq(Doctor::getStatus, "ACTIVE");
        wrapper.orderByDesc(Doctor::getUpdatedAt);
        List<Doctor> doctors = doctorMapper.selectList(wrapper);

        // 返回脱敏视图，剔除 password 等敏感字段
        return doctors.stream().map(OnlineDoctorVO::from).collect(Collectors.toList());
    }

    // ===================== 1.1 我的挂号主治医生列表（问诊发起专用） =====================

    /**
     * 查询患者自己的「挂号主治医生」列表。
     *
     * <p>业务规则：患者只能对自己挂号成功（挂号状态非 CANCELLED）的主治医生发起在线问诊。
     * 实现：先查 sm_appointment 中该患者所有有效挂号（排除 CANCELLED），按 doctorId 去重，
     * 再批量查医生并返回脱敏视图。</p>
     *
     * <p>每条记录额外返回 {@code accepted} 标记，取值与 {@link #ensureDoctorAccepted} 的接诊门禁
     * 保持一致：false 表示医生尚未接诊，前端应置灰并提示「医生接诊后可发起问诊」，
     * 避免出现「列表里能选、点进去却报错」的割裂体验。</p>
     *
     * @param patientId 患者 ID
     * @return 该患者的挂号主治医生列表（不含密码，含接诊标记）
     */
    @Override
    public List<OnlineDoctorVO> getMyDoctors(Long patientId) {
        // 1) 查该患者的有效挂号记录（排除已取消），按医生去重
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Appointment::getPatientId, patientId);
        wrapper.ne(Appointment::getStatus, "CANCELLED");
        List<Appointment> appointments = appointmentMapper.selectList(wrapper);
        List<Long> doctorIds = appointments.stream()
                .map(Appointment::getDoctorId)
                .distinct()
                .collect(Collectors.toList());

        // 2) 无有效挂号则返回空列表
        if (doctorIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 3) 批量查询医生并按挂号顺序返回脱敏视图（剔除 password 等敏感字段）
        List<Doctor> doctors = doctorMapper.selectBatchIds(doctorIds);
        Map<Long, Doctor> doctorMap = doctors.stream()
                .collect(Collectors.toMap(Doctor::getId, d -> d));
        List<OnlineDoctorVO> result = new ArrayList<>();
        for (Long doctorId : doctorIds) {
            Doctor doctor = doctorMap.get(doctorId);
            if (doctor != null) {
                OnlineDoctorVO vo = OnlineDoctorVO.from(doctor);
                // 接诊标记：与 createConsult 的接诊门禁同一判据，保证「能选」与「能发起」一致
                vo.setAccepted(isDoctorAccepted(patientId, doctor.getId()));
                result.add(vo);
            }
        }
        return result;
    }

    // ===================== 2. 发起问诊（OC-01） =====================

    @Override
    @Transactional
    public OnlineConsultRecord createConsult(Long patientId, Long doctorId, String department, String content,
                                             String preConsultSessionId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BizException("医生不存在");
        }

        // 业务规则：患者只能对自己挂号成功（挂号状态非 CANCELLED）的主治医生发起问诊
        LambdaQueryWrapper<Appointment> apptWrapper = new LambdaQueryWrapper<>();
        apptWrapper.eq(Appointment::getPatientId, patientId);
        apptWrapper.eq(Appointment::getDoctorId, doctorId);
        apptWrapper.ne(Appointment::getStatus, "CANCELLED");
        Long validAppointments = appointmentMapper.selectCount(apptWrapper);
        if (validAppointments == null || validAppointments == 0) {
            throw new BizException("仅能对您挂号成功的主治医生发起问诊，请先挂号对应医生");
        }

        // 接诊门禁：医生接诊后患者才能发起问诊对话
        ensureDoctorAccepted(patientId, doctorId);

        if (!"ONLINE".equalsIgnoreCase(doctor.getOnlineStatus())) {
            throw new BizException("该医生当前不在线，无法发起问诊");
        }

        OnlineConsultRecord consult = new OnlineConsultRecord();
        consult.setPatientId(patientId);
        consult.setDoctorId(doctorId);
        consult.setDepartment(department != null && !department.isBlank()
                ? department : doctor.getDepartment());
        consult.setStatus(STATUS_PENDING);
        consult.setStartTime(LocalDateTime.now());
        consult.setCreatedAt(LocalDateTime.now());
        consult.setUpdatedAt(LocalDateTime.now());
        // 记录关联的 AI 预问诊 sessionId（可为 null：患者没走过 AI 预问诊）
        if (preConsultSessionId != null && !preConsultSessionId.isBlank()) {
            consult.setPreConsultSessionId(preConsultSessionId);
        }
        onlineConsultMapper.insert(consult);

        // 主诉/描述作为首条 PATIENT 消息写入消息表
        if (content != null && !content.isBlank()) {
            ConsultMessage msg = new ConsultMessage();
            msg.setConsultId(consult.getId());
            msg.setSender(SENDER_PATIENT);
            msg.setMessageType(MSG_TYPE_TEXT);
            msg.setContent(content);
            msg.setCreatedAt(LocalDateTime.now());
            consultMessageMapper.insert(msg);
        }

        return consult;
    }

    // ===================== 3. 问诊列表（OC-02） =====================

    @Override
    public PageResult<Object> listConsults(Long userId, String role, int page, int size) {
        LambdaQueryWrapper<OnlineConsultRecord> wrapper = new LambdaQueryWrapper<>();

        Role r = Role.of(role);
        if (r == Role.PATIENT) {
            wrapper.eq(OnlineConsultRecord::getPatientId, userId);
        } else if (r == Role.DOCTOR) {
            wrapper.eq(OnlineConsultRecord::getDoctorId, userId);
        }
        // ADMIN 返回全部
        wrapper.orderByDesc(OnlineConsultRecord::getCreatedAt);

        if (r == Role.DOCTOR) {
            // 医生视角：附加患者姓名 / 叫号时间 / 医生最新回复时间，
            // 排序规则：医生发送消息的最新时间最先；无回复的沉底。分页在内存中完成，保证全局排序正确
            List<OnlineConsultRecord> records = onlineConsultMapper.selectList(wrapper);
            List<Map<String, Object>> vos = buildDoctorConsultVos(records);
            vos.sort((a, b) -> {
                LocalDateTime ta = (LocalDateTime) a.get("lastDoctorMessageAt");
                LocalDateTime tb = (LocalDateTime) b.get("lastDoctorMessageAt");
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });
            int from = Math.min((page - 1) * size, vos.size());
            int to = Math.min(from + size, vos.size());
            return PageResult.of((long) vos.size(), new ArrayList<>(vos.subList(from, to)), page, size);
        }

        Page<OnlineConsultRecord> result = onlineConsultMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(result.getTotal(), new ArrayList<Object>(result.getRecords()), page, size);
    }

    /**
     * 医生视角问诊列表 VO：患者姓名、叫号时间（就诊 CALLING 节点时间）、医生最新回复时间。
     */
    private List<Map<String, Object>> buildDoctorConsultVos(List<OnlineConsultRecord> records) {
        List<Map<String, Object>> vos = new ArrayList<>();
        for (OnlineConsultRecord c : records) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", c.getId());
            vo.put("department", c.getDepartment());
            vo.put("status", c.getStatus());
            vo.put("createdAt", c.getCreatedAt());
            Patient p = patientMapper.selectById(c.getPatientId());
            vo.put("patientId", c.getPatientId());
            vo.put("patientName", p != null ? p.getRealName() : "未知患者");
            vo.put("callTime", latestVisitCallTime(c.getPatientId(), c.getDoctorId()));
            vo.put("lastDoctorMessageAt", lastDoctorMessageAt(c.getId()));
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 最近一次就诊的「叫号」时间：解析该医生与患者最近就诊的 progressNodes，取 CALLING 节点时间。
     */
    private LocalDateTime latestVisitCallTime(Long patientId, Long doctorId) {
        LambdaQueryWrapper<Visit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Visit::getPatientId, patientId);
        wrapper.eq(Visit::getDoctorId, doctorId);
        wrapper.orderByDesc(Visit::getCreatedAt);
        wrapper.last("LIMIT 1");
        Visit visit = visitMapper.selectOne(wrapper);
        if (visit == null || visit.getProgressNodes() == null) {
            return null;
        }
        try {
            JsonNode arr = new ObjectMapper().readTree(visit.getProgressNodes());
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String name = n.path("name").asText("");
                    // 节点名为中文"叫号"（VisitService 追加时使用 STATUS_TO_NODE 映射），兼容英文 CALLING
                    if ("叫号".equals(name) || "CALLING".equalsIgnoreCase(n.path("status").asText(""))) {
                        String t = n.path("time").asText("");
                        return (t != null && !t.isEmpty()) ? LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null;
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败视为无叫号时间
        }
        return null;
    }

    /**
     * 问诊中医生（DOCTOR 发送方）最新一条消息的发送时间；无回复返回 null。
     */
    private LocalDateTime lastDoctorMessageAt(Long consultId) {
        ConsultMessage last = consultMessageMapper.selectOne(
                new LambdaQueryWrapper<ConsultMessage>()
                        .eq(ConsultMessage::getConsultId, consultId)
                        .eq(ConsultMessage::getSender, SENDER_DOCTOR)
                        .orderByDesc(ConsultMessage::getCreatedAt)
                        .last("LIMIT 1"));
        return last == null ? null : last.getCreatedAt();
    }

    // ===================== 4. 问诊详情（OC-03） =====================

    @Override
    public Object getConsultDetail(Long id, Long userId, Role role) {
        OnlineConsultRecord consult = getOwnedConsult(id, userId, role);

        Doctor doctor = doctorMapper.selectById(consult.getDoctorId());
        Patient patient = patientMapper.selectById(consult.getPatientId());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("consult", consult);
        detail.put("doctor", doctor != null ? OnlineDoctorVO.from(doctor) : null);
        detail.put("patient", patient != null ? toPatientSafe(patient) : null);
        // 接诊状态：前端据此展示等待提示并禁用输入（WAITING 未叫号 / CALLING 已叫号未接诊）
        String visitStatus = latestVisitStatus(consult.getPatientId(), consult.getDoctorId());
        detail.put("visitStatus", visitStatus);
        detail.put("chatEnabled", "IN_PROGRESS".equalsIgnoreCase(visitStatus)
                || "FINISHED".equalsIgnoreCase(visitStatus));
        return detail;
    }

    // ===================== 5. 发送消息（OC-04） =====================

    @Override
    @Transactional
    public ConsultMessage sendMessage(Long userId, Role role, Long consultId,
                                      String content, String imageUrl) {
        OnlineConsultRecord consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 仅问诊双方（患者本人 / 主治医生本人）可发送消息
        String sender;
        if (role == Role.PATIENT) {
            if (!consult.getPatientId().equals(userId)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            // 接诊门禁：医生未接诊时患者不能发送消息
            ensureDoctorAccepted(consult.getPatientId(), consult.getDoctorId());
            sender = SENDER_PATIENT;
        } else if (role == Role.DOCTOR) {
            if (!consult.getDoctorId().equals(userId)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            sender = SENDER_DOCTOR;
        } else {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        boolean isImage = imageUrl != null && !imageUrl.isBlank();

        ConsultMessage msg = new ConsultMessage();
        msg.setConsultId(consultId);
        msg.setSender(sender);
        msg.setMessageType(isImage ? MSG_TYPE_IMAGE : MSG_TYPE_TEXT);
        msg.setContent(content);
        msg.setImageUrl(imageUrl);
        msg.setCreatedAt(LocalDateTime.now());
        consultMessageMapper.insert(msg);
        return msg;
    }

    // ===================== 6. 消息列表（OC-11） =====================

    @Override
    public List<ConsultMessage> listMessages(Long consultId, Long userId, Role role) {
        getOwnedConsult(consultId, userId, role);
        LambdaQueryWrapper<ConsultMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultMessage::getConsultId, consultId);
        wrapper.orderByAsc(ConsultMessage::getCreatedAt);
        return consultMessageMapper.selectList(wrapper);
    }

    // ===================== 7. 确认最终回复（OC-09） =====================

    @Override
    @Transactional
    public void confirmReply(Long consultId, Long doctorId, String finalReply) {
        OnlineConsultRecord consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 仅接诊医生本人可回复
        if (!consult.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        // 接诊门禁：医生接诊后才能回复患者（与患者侧规则一致）
        ensureDoctorAccepted(consult.getPatientId(), doctorId);

        if (finalReply == null || finalReply.isBlank()) {
            throw new BizException("回复内容不能为空");
        }

        // 将医生回复写入消息流，患者端列表可见（核心：保证医患在线交流闭环）
        ConsultMessage msg = new ConsultMessage();
        msg.setConsultId(consultId);
        msg.setSender(SENDER_DOCTOR);
        msg.setMessageType(MSG_TYPE_TEXT);
        msg.setContent(finalReply);
        msg.setCreatedAt(LocalDateTime.now());
        consultMessageMapper.insert(msg);

        // 更新问诊状态
        LambdaUpdateWrapper<OnlineConsultRecord> upd = new LambdaUpdateWrapper<>();
        upd.eq(OnlineConsultRecord::getId, consultId);
        upd.set(OnlineConsultRecord::getStatus, STATUS_REPLIED);
        upd.set(OnlineConsultRecord::getUpdatedAt, LocalDateTime.now());
        onlineConsultMapper.update(null, upd);
    }

    // ===================== 8. 关闭问诊（OC-10） =====================

    @Override
    @Transactional
    public void closeConsult(Long consultId, Long userId, Role role) {
        OnlineConsultRecord consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 仅接诊医生本人或管理员可关闭
        boolean allowed = role == Role.ADMIN
                || (role == Role.DOCTOR && consult.getDoctorId().equals(userId));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        LambdaUpdateWrapper<OnlineConsultRecord> upd = new LambdaUpdateWrapper<>();
        upd.eq(OnlineConsultRecord::getId, consultId);
        upd.set(OnlineConsultRecord::getStatus, STATUS_CLOSED);
        upd.set(OnlineConsultRecord::getEndTime, LocalDateTime.now());
        upd.set(OnlineConsultRecord::getUpdatedAt, LocalDateTime.now());
        onlineConsultMapper.update(null, upd);
    }
}
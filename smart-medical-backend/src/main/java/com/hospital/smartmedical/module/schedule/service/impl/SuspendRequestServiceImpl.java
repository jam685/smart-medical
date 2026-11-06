package com.hospital.smartmedical.module.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.notification.service.NotificationService;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.entity.SuspendRequest;
import com.hospital.smartmedical.module.schedule.mapper.ScheduleSlotMapper;
import com.hospital.smartmedical.module.schedule.mapper.SuspendRequestMapper;
import com.hospital.smartmedical.module.schedule.service.ScheduleService;
import com.hospital.smartmedical.module.schedule.service.SuspendRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 停诊申请服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspendRequestServiceImpl implements SuspendRequestService {

    private final SuspendRequestMapper requestMapper;
    private final ScheduleSlotMapper slotMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleService scheduleService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HM_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long doctorId, List<Long> slotIds, String reason) {
        if (slotIds == null || slotIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择要停诊的时段");
        }
        List<ScheduleSlot> slots = slotMapper.selectBatchIds(slotIds);
        if (slots == null || slots.size() != slotIds.size()) {
            throw new BizException("部分时段不存在，请刷新后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        for (ScheduleSlot s : slots) {
            if (!Objects.equals(s.getDoctorId(), doctorId)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            if (!"OPEN".equals(s.getStatus())) {
                throw new BizException("时段 " + HM_FMT.format(s.getStartTime()) + " 已停诊，请勿重复申请");
            }
            if (!LocalDateTime.of(s.getWorkDate(), s.getStartTime()).isAfter(now)) {
                throw new BizException("时段 " + HM_FMT.format(s.getStartTime()) + " 已开始，无法申请停诊");
            }
        }
        SuspendRequest req = new SuspendRequest();
        req.setDoctorId(doctorId);
        try {
            req.setSlotIds(objectMapper.writeValueAsString(slotIds));
        } catch (Exception e) {
            throw new BizException("时段序列化失败");
        }
        req.setReason(StringUtils.hasText(reason) ? reason.trim() : "临时停诊");
        req.setStatus("PENDING");
        req.setCreatedAt(now);
        requestMapper.insert(req);
        log.info("医生提交停诊申请：requestId={}, doctorId={}, slots={}", req.getId(), doctorId, slotIds);
        return toMap(req);
    }

    @Override
    public List<Map<String, Object>> listByDoctor(Long doctorId) {
        List<SuspendRequest> list = requestMapper.selectList(new LambdaQueryWrapper<SuspendRequest>()
                .eq(SuspendRequest::getDoctorId, doctorId)
                .orderByDesc(SuspendRequest::getId)
                .last("LIMIT 20"));
        return list.stream().map(this::toMap).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listByStatus(String status) {
        LambdaQueryWrapper<SuspendRequest> qw = new LambdaQueryWrapper<SuspendRequest>()
                .orderByDesc(SuspendRequest::getId)
                .last("LIMIT 50");
        if (StringUtils.hasText(status)) {
            qw.eq(SuspendRequest::getStatus, status);
        }
        return requestMapper.selectList(qw).stream().map(this::toMap).collect(Collectors.toList());
    }

    @Override
    public long pendingCount() {
        Long count = requestMapper.selectCount(new LambdaQueryWrapper<SuspendRequest>()
                .eq(SuspendRequest::getStatus, "PENDING"));
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> review(Long id, Long adminId, boolean approve, String note) {
        SuspendRequest req = requestMapper.selectById(id);
        if (req == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new BizException("该申请已处理，请勿重复审批");
        }
        List<Long> slotIds = parseSlotIds(req.getSlotIds());
        if (approve) {
            List<ScheduleSlot> slots = slotMapper.selectBatchIds(slotIds);
            boolean allSuspended = slots != null && !slots.isEmpty()
                    && slots.stream().allMatch(s -> "SUSPENDED".equals(s.getStatus()));
            if (!allSuspended) {
                // 执行退号停诊（内部会给患者和医生发通知）
                scheduleService.changeStatusByAdmin(slotIds, true, true);
            }
        }
        req.setStatus(approve ? "APPROVED" : "REJECTED");
        req.setAdminId(adminId);
        req.setReviewNote(StringUtils.hasText(note) ? note.trim() : null);
        req.setReviewedAt(LocalDateTime.now());
        requestMapper.updateById(req);

        Doctor doctor = doctorMapper.selectById(req.getDoctorId());
        if (doctor != null) {
            notificationService.push(doctor.getId(), "DOCTOR", "SUSPEND_RESULT",
                    approve ? "停诊申请已通过" : "停诊申请被驳回",
                    "您提交的停诊申请（" + summarize(slotIds) + "）"
                            + (approve ? "已通过，相关患者已安排退号。" : "被驳回：" + (StringUtils.hasText(note) ? note : "请与医务科联系")),
                    "SCHEDULE", req.getId());
        }
        log.info("停诊申请审批：requestId={}, approve={}, adminId={}", id, approve, adminId);
        return toMap(req);
    }

    /** 实体 → 展示 Map（补医生姓名与时段摘要） */
    private Map<String, Object> toMap(SuspendRequest req) {
        List<Long> slotIds = parseSlotIds(req.getSlotIds());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", req.getId());
        m.put("doctorId", req.getDoctorId());
        Doctor doctor = req.getDoctorId() == null ? null : doctorMapper.selectById(req.getDoctorId());
        m.put("doctorName", doctor == null ? "未知医生" : doctor.getRealName());
        m.put("department", doctor == null ? null : doctor.getDepartment());
        m.put("slotIds", slotIds);
        m.put("slotSummary", summarize(slotIds));
        m.put("reason", req.getReason());
        m.put("status", req.getStatus());
        m.put("reviewNote", req.getReviewNote());
        m.put("createdAt", req.getCreatedAt() == null ? null : req.getCreatedAt().format(TIME_FMT));
        m.put("reviewedAt", req.getReviewedAt() == null ? null : req.getReviewedAt().format(TIME_FMT));
        return m;
    }

    /** 时段摘要：11-06 08:00-08:30（共 N 个时段） */
    private String summarize(List<Long> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            return "";
        }
        List<ScheduleSlot> slots = slotMapper.selectBatchIds(slotIds);
        if (slots == null || slots.isEmpty()) {
            return "未知时段";
        }
        ScheduleSlot first = slots.get(0);
        String head = first.getWorkDate().toString().substring(5) + " "
                + HM_FMT.format(first.getStartTime()) + "-" + HM_FMT.format(first.getEndTime());
        return slots.size() == 1 ? head : head + " 等 " + slots.size() + " 个时段";
    }

    private List<Long> parseSlotIds(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}

package com.hospital.smartmedical.module.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.appointment.service.AppointmentService;
import com.hospital.smartmedical.module.appointment.vo.AppointmentVO;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.notification.service.NotificationService;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.mapper.ScheduleSlotMapper;
import com.hospital.smartmedical.module.schedule.service.ScheduleService;
import com.hospital.smartmedical.module.schedule.vo.ImpactedAppointmentVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleDayVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleImpactVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleSlotVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 号源与排班服务实现。
 *
 * <p>号源模板固定为「上午 6 段 + 下午 6 段、每段 30 分钟」，与患者端选号页一致；
 * 生成时只补缺失时段（已存在跳过），避免把医生手工停诊/加号的结果覆盖掉。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    /** 号源模板：上午 08:00-11:00、下午 14:00-17:00，每 30 分钟一段 */
    private static final String[][] TEMPLATE = {
            {"AM", "08:00", "08:30"}, {"AM", "08:30", "09:00"}, {"AM", "09:00", "09:30"},
            {"AM", "09:30", "10:00"}, {"AM", "10:00", "10:30"}, {"AM", "10:30", "11:00"},
            {"PM", "14:00", "14:30"}, {"PM", "14:30", "15:00"}, {"PM", "15:00", "15:30"},
            {"PM", "15:30", "16:00"}, {"PM", "16:00", "16:30"}, {"PM", "16:30", "17:00"}
    };

    /** 星期中文标签（DayOfWeek.getValue() 从 1=周一 开始） */
    private static final String[] WEEK_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /** 单时段号数上限（防误操作一次放出过多号） */
    private static final int MAX_CAPACITY = 50;

    /** 默认号数 */
    private static final int DEFAULT_CAPACITY = 5;

    /** 排班最多向后生成的天数 */
    private static final int MAX_DAYS = 30;

    /** 号源 Mapper */
    private final ScheduleSlotMapper slotMapper;
    /** 医生 Mapper（排班生成、医生列表） */
    private final DoctorMapper doctorMapper;
    /** 挂号 Mapper（停诊影响面：查受影响的患者挂号） */
    private final AppointmentMapper appointmentMapper;
    /** 患者 Mapper（影响面里补患者姓名与手机号） */
    private final PatientMapper patientMapper;
    /** 挂号服务：改约时按号源重新下单（复用同一套扣号/事务逻辑） */
    private final AppointmentService appointmentService;
    /** 站内信：停诊/改约结果通知患者与医生 */
    private final NotificationService notificationService;

    /** 影响面时间格式 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 时段格式 HH:mm */
    private static final DateTimeFormatter HM_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public List<ScheduleSlotVO> listSlots(Long doctorId, LocalDate date) {
        if (doctorId == null || date == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ScheduleSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<ScheduleSlot>()
                .eq(ScheduleSlot::getDoctorId, doctorId)
                .eq(ScheduleSlot::getWorkDate, date)
                .orderByAsc(ScheduleSlot::getStartTime));
        List<ScheduleSlotVO> result = new ArrayList<>(slots.size());
        for (ScheduleSlot s : slots) {
            result.add(ScheduleSlotVO.of(s, now));
        }
        return result;
    }

    @Override
    public List<ScheduleDayVO> listDays(Long doctorId, LocalDate startDate, int days) {
        if (doctorId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        LocalDate start = startDate == null ? LocalDate.now() : startDate;
        int span = Math.min(MAX_DAYS, Math.max(1, days));
        List<ScheduleSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<ScheduleSlot>()
                .eq(ScheduleSlot::getDoctorId, doctorId)
                .ge(ScheduleSlot::getWorkDate, start)
                .lt(ScheduleSlot::getWorkDate, start.plusDays(span))
                .orderByAsc(ScheduleSlot::getWorkDate)
                .orderByAsc(ScheduleSlot::getStartTime));

        Map<LocalDate, List<ScheduleSlot>> grouped = new LinkedHashMap<>();
        for (ScheduleSlot s : slots) {
            grouped.computeIfAbsent(s.getWorkDate(), k -> new ArrayList<>()).add(s);
        }

        LocalDateTime now = LocalDateTime.now();
        List<ScheduleDayVO> result = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            LocalDate d = start.plusDays(i);
            List<ScheduleSlot> daySlots = grouped.getOrDefault(d, List.of());
            ScheduleDayVO day = new ScheduleDayVO();
            day.setDate(d.toString());
            day.setWeekday(WEEK_LABELS[d.getDayOfWeek().getValue() - 1]);
            int remain = 0;
            int booked = 0;
            boolean hasBookable = false;
            List<ScheduleSlotVO> vos = new ArrayList<>(daySlots.size());
            for (ScheduleSlot s : daySlots) {
                ScheduleSlotVO vo = ScheduleSlotVO.of(s, now);
                remain += vo.getRemain();
                booked += vo.getBooked();
                hasBookable = hasBookable || Boolean.TRUE.equals(vo.getBookable());
                vos.add(vo);
            }
            day.setTotalRemain(remain);
            day.setBookedCount(booked);
            day.setSlotCount(vos.size());
            day.setHasBookable(hasBookable);
            day.setSlots(vos);
            result.add(day);
        }
        return result;
    }

    @Override
    public int generate(Long doctorId, LocalDate startDate, int days, int capacity) {
        Doctor doctor = doctorId == null ? null : doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "医生不存在");
        }
        LocalDate start = startDate == null ? LocalDate.now() : startDate;
        int span = Math.min(MAX_DAYS, Math.max(1, days));
        int cap = capacity <= 0 ? DEFAULT_CAPACITY : Math.min(MAX_CAPACITY, capacity);
        int created = 0;
        for (int i = 0; i < span; i++) {
            LocalDate d = start.plusDays(i);
            for (String[] t : TEMPLATE) {
                LocalTime startTime = LocalTime.parse(t[1]);
                Long exists = slotMapper.selectCount(new LambdaQueryWrapper<ScheduleSlot>()
                        .eq(ScheduleSlot::getDoctorId, doctorId)
                        .eq(ScheduleSlot::getWorkDate, d)
                        .eq(ScheduleSlot::getStartTime, startTime));
                if (exists != null && exists > 0) {
                    continue;
                }
                ScheduleSlot s = new ScheduleSlot();
                s.setDoctorId(doctorId);
                s.setDepartment(doctor.getDepartment());
                s.setWorkDate(d);
                s.setPeriod(t[0]);
                s.setStartTime(startTime);
                s.setEndTime(LocalTime.parse(t[2]));
                s.setTotalCount(cap);
                s.setBookedCount(0);
                s.setStatus("OPEN");
                s.setCreatedAt(LocalDateTime.now());
                s.setUpdatedAt(LocalDateTime.now());
                slotMapper.insert(s);
                created++;
            }
        }
        log.info("号源生成完成：doctorId={}, 日期 {}~{}，每段 {} 个号，新建 {} 行",
                doctorId, start, start.plusDays(span - 1), cap, created);
        return created;
    }

    @Override
    public int generateByDepartment(String department, LocalDate startDate, int days, int capacity) {
        if (department == null || department.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请指定科室");
        }
        LambdaQueryWrapper<Doctor> qw = new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDepartment, department);
        List<Doctor> doctors = doctorMapper.selectList(qw);
        int total = 0;
        for (Doctor d : doctors) {
            total += generate(d.getId(), startDate, days, capacity);
        }
        return total;
    }

    @Override
    public ScheduleSlot suspend(Long slotId, Long doctorId, boolean suspended) {
        ScheduleSlot slot = slotId == null ? null : slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!slot.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (suspended) {
            int booked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
            if (booked > 0) {
                throw new BizException("该时段已有 " + booked + " 人预约，请先联系患者改约并退号后再停诊");
            }
        }
        slot.setStatus(suspended ? "SUSPENDED" : "OPEN");
        slot.setUpdatedAt(LocalDateTime.now());
        slotMapper.updateById(slot);
        log.info("排班状态变更：slotId={}, doctorId={}, status={}", slotId, doctorId, slot.getStatus());
        return slot;
    }

    @Override
    public ScheduleSlot updateCapacity(Long slotId, Long doctorId, int totalCount) {
        ScheduleSlot slot = slotId == null ? null : slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!slot.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int booked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
        if (totalCount < booked) {
            throw new BizException("号数不能小于已预约人数（当前已约 " + booked + " 人）");
        }
        if (totalCount > MAX_CAPACITY) {
            throw new BizException("单个时段号数不能超过 " + MAX_CAPACITY + " 个");
        }
        slot.setTotalCount(totalCount);
        slot.setUpdatedAt(LocalDateTime.now());
        slotMapper.updateById(slot);
        log.info("号数调整：slotId={}, doctorId={}, total={}", slotId, doctorId, totalCount);
        return slot;
    }

    @Override
    public int seedIfEmpty(int days, int capacity) {
        Long existing = slotMapper.selectCount(new LambdaQueryWrapper<ScheduleSlot>()
                .ge(ScheduleSlot::getWorkDate, LocalDate.now()));
        if (existing != null && existing > 0) {
            return 0;
        }
        List<Doctor> doctors = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getStatus, "ACTIVE"));
        int total = 0;
        for (Doctor d : doctors) {
            total += generate(d.getId(), LocalDate.now(), days, capacity);
        }
        if (total > 0) {
            log.info("号源为空，已为 {} 位医生自动生成近 {} 天号源（每时段 {} 个）", doctors.size(), days, capacity);
        }
        return total;
    }

    @Override
    public List<Map<String, Object>> listDoctors(String department) {
        LambdaQueryWrapper<Doctor> qw = new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getStatus, "ACTIVE");
        if (StringUtils.hasText(department)) {
            qw.eq(Doctor::getDepartment, department);
        }
        qw.orderByAsc(Doctor::getDepartment).orderByAsc(Doctor::getRealName);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Doctor d : doctorMapper.selectList(qw)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("realName", d.getRealName());
            m.put("department", d.getDepartment());
            m.put("title", d.getTitle());
            m.put("onlineStatus", d.getOnlineStatus());
            result.add(m);
        }
        return result;
    }

    @Override
    public ScheduleImpactVO impact(List<Long> slotIds) {
        List<ScheduleSlot> slots = loadSlots(slotIds);
        LocalDateTime now = LocalDateTime.now();
        Map<Long, ScheduleSlot> slotMap = slots.stream()
                .collect(Collectors.toMap(ScheduleSlot::getId, s -> s));

        ScheduleImpactVO vo = new ScheduleImpactVO();
        vo.setSlotCount(slots.size());
        vo.setBookedCount(slots.stream()
                .mapToInt(s -> s.getBookedCount() == null ? 0 : s.getBookedCount()).sum());
        vo.setHasStarted(slots.stream().anyMatch(s -> !slotStart(s).isAfter(now)));

        // 受影响患者：只统计还能退的（PENDING/CONFIRMED）
        List<Appointment> appts = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .in(Appointment::getScheduleId, slotIds)
                .in(Appointment::getStatus, List.of("PENDING", "CONFIRMED"))
                .orderByAsc(Appointment::getAppointmentTime));
        Set<Long> patientIds = appts.stream()
                .map(Appointment::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Patient> patientMap = patientIds.isEmpty() ? Map.of()
                : patientMapper.selectBatchIds(patientIds).stream()
                    .collect(Collectors.toMap(Patient::getId, p -> p));

        List<ImpactedAppointmentVO> affected = new ArrayList<>(appts.size());
        for (Appointment a : appts) {
            ImpactedAppointmentVO item = new ImpactedAppointmentVO();
            item.setAppointmentId(a.getId());
            item.setOrderNo(AppointmentVO.buildOrderNoPublic(a));
            item.setPatientId(a.getPatientId());
            Patient patient = patientMap.get(a.getPatientId());
            item.setPatientName(patient == null || !StringUtils.hasText(patient.getRealName())
                    ? "未知患者" : patient.getRealName());
            item.setPatientPhone(patient == null ? null : patient.getPhone());
            item.setAppointmentTime(a.getAppointmentTime() == null ? null
                    : a.getAppointmentTime().format(TIME_FMT));
            ScheduleSlot slot = slotMap.get(a.getScheduleId());
            item.setSlotId(a.getScheduleId());
            item.setSlotTime(slot == null ? null
                    : slot.getStartTime().format(HM_FMT) + "-" + slot.getEndTime().format(HM_FMT));
            item.setStatus(a.getStatus());
            affected.add(item);
        }
        vo.setAffected(affected);
        vo.setAffectedCount(affected.size());
        vo.setAlternatives(findAlternatives(slots, now));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> changeStatusByAdmin(List<Long> slotIds, boolean suspended, boolean force) {
        List<ScheduleSlot> slots = loadSlots(slotIds);
        LocalDateTime now = LocalDateTime.now();
        // 先锁号源行：避免"查询受影响患者"与"置为停诊"之间被并发下单挤进来
        slotMapper.lockByIds(slots.stream().map(ScheduleSlot::getId).collect(Collectors.toList()));

        int changed = 0;
        int cancelled = 0;
        if (suspended) {
            for (ScheduleSlot s : slots) {
                if (!slotStart(s).isAfter(now)) {
                    throw new BizException("时段 " + HM_FMT.format(s.getStartTime()) + " 已开始，不能停诊");
                }
            }
            List<Appointment> active = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                    .in(Appointment::getScheduleId, slotIds)
                    .in(Appointment::getStatus, List.of("PENDING", "CONFIRMED")));
            if (!active.isEmpty() && !force) {
                throw new BizException("该批次已有 " + active.size()
                        + " 位患者预约，停诊将为他们退号，请先确认影响面");
            }
            for (Appointment a : active) {
                if (appointmentMapper.cancelIfActive(a.getId(), "SUSPEND") == 1) {
                    cancelled++;
                    notifySuspendCancelled(a);
                }
            }
            for (ScheduleSlot s : slots) {
                s.setStatus("SUSPENDED");
                s.setBookedCount(0);
                s.setUpdatedAt(now);
                slotMapper.updateById(s);
                changed++;
            }
            notifyDoctorSuspended(slots.get(0), cancelled, "退号");
        } else {
            for (ScheduleSlot s : slots) {
                if (!slotStart(s).isAfter(now)) {
                    throw new BizException("时段 " + HM_FMT.format(s.getStartTime()) + " 已开始，不能恢复出诊");
                }
                s.setStatus("OPEN");
                s.setUpdatedAt(now);
                slotMapper.updateById(s);
                changed++;
            }
        }
        log.info("管理端停诊变更：slotIds={}, suspended={}, changed={}, cancelled={}",
                slotIds, suspended, changed, cancelled);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changed", changed);
        result.put("cancelled", cancelled);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reschedule(List<Long> fromSlotIds, List<Long> toSlotIds, List<Long> appointmentIds) {
        if (toSlotIds == null || toSlotIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择改约目标时段");
        }
        List<ScheduleSlot> fromSlots = loadSlots(fromSlotIds);
        LocalDateTime now = LocalDateTime.now();
        // 目标时段去重 + 校验：允许跨医生/跨科室，也允许多个时段分摊患者
        List<Long> distinctToIds = toSlotIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<ScheduleSlot> targets = new ArrayList<>();
        for (Long id : distinctToIds) {
            if (fromSlotIds.contains(id)) {
                throw new BizException("目标时段不能是待停诊的时段");
            }
            ScheduleSlot t = slotMapper.selectById(id);
            if (t == null) {
                throw new BizException(ErrorCode.NOT_FOUND.getCode(), "目标时段不存在");
            }
            if (!"OPEN".equals(t.getStatus())) {
                throw new BizException("目标时段已停诊，请重新选择");
            }
            if (!slotStart(t).isAfter(now.plusMinutes(ScheduleSlotVO.BOOK_LEAD_MINUTES))) {
                throw new BizException("目标时段已停止预约，请重新选择");
            }
            targets.add(t);
        }

        // 待迁移的挂号单：默认取源时段下全部有效单，也支持只迁指定的几张
        List<Appointment> candidates = new ArrayList<>(appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .in(Appointment::getScheduleId, fromSlotIds)
                        .in(Appointment::getStatus, List.of("PENDING", "CONFIRMED"))
                        .orderByAsc(Appointment::getAppointmentTime)));
        if (appointmentIds != null && !appointmentIds.isEmpty()) {
            Set<Long> wanted = new LinkedHashSet<>(appointmentIds);
            candidates.removeIf(a -> !wanted.contains(a.getId()));
            if (candidates.size() != wanted.size()) {
                throw new BizException("部分挂号单已变更，请刷新影响面后重试");
            }
        }
        if (candidates.isEmpty()) {
            throw new BizException("没有可改约的挂号单");
        }

        int[] remain = new int[targets.size()];
        int capacity = 0;
        for (int i = 0; i < targets.size(); i++) {
            ScheduleSlot t = targets.get(i);
            int total = t.getTotalCount() == null ? 0 : t.getTotalCount();
            int booked = t.getBookedCount() == null ? 0 : t.getBookedCount();
            remain[i] = Math.max(0, total - booked);
            capacity += remain[i];
        }
        if (capacity < candidates.size()) {
            throw new BizException("目标时段余号不足（需要 " + candidates.size() + " 个，共剩余 " + capacity + " 个）");
        }

        int moved = 0;
        int targetIndex = 0;
        List<Map<String, Object>> assignments = new ArrayList<>();
        for (Appointment a : candidates) {
            while (targetIndex < targets.size() && remain[targetIndex] <= 0) {
                targetIndex++;
            }
            if (targetIndex >= targets.size()) {
                throw new BizException("目标时段余号不足，请重新选择");
            }
            ScheduleSlot target = targets.get(targetIndex);
            // 先取消原单再下新单：同一患者同日同医生的重复挂号校验才不会误伤自己
            if (appointmentMapper.cancelIfActive(a.getId(), "RESCHEDULE") == 0) {
                throw new BizException("挂号单 " + a.getId() + " 状态已变更，请刷新后重试");
            }
            if (a.getScheduleId() != null) {
                slotMapper.release(a.getScheduleId());
            }
            Appointment created = appointmentService.createAppointmentBySlot(
                    a.getPatientId(), Role.PATIENT, target.getId());
            remain[targetIndex]--;
            moved++;
            notifyRescheduled(a, target, created);

            Map<String, Object> assignment = new LinkedHashMap<>();
            assignment.put("appointmentId", a.getId());
            assignment.put("newAppointmentId", created == null ? null : created.getId());
            assignment.put("toSlotId", target.getId());
            assignment.put("toDate", target.getWorkDate().toString());
            assignment.put("toStartTime", target.getStartTime().format(HM_FMT));
            assignments.add(assignment);
        }

        // 源时段此时应已无预约；若期间有人抢号，条件更新失败 → 整批回滚
        for (ScheduleSlot s : fromSlots) {
            if (slotMapper.suspendIfEmpty(s.getId()) == 0) {
                throw new BizException("时段 " + HM_FMT.format(s.getStartTime())
                        + " 出现新的预约，请刷新影响面后重试");
            }
        }
        notifyDoctorRescheduled(fromSlots.get(0), moved);

        log.info("一键改约完成：from={}, to={}, moved={}", fromSlotIds, distinctToIds, moved);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rescheduled", moved);
        result.put("suspended", fromSlots.size());
        result.put("toSlotIds", distinctToIds);
        result.put("assignments", assignments);
        return result;
    }

    /** 停诊退号：通知患者 */
    private void notifySuspendCancelled(Appointment a) {
        Doctor doctor = a.getDoctorId() == null ? null : doctorMapper.selectById(a.getDoctorId());
        String doctorName = doctor == null || !StringUtils.hasText(doctor.getRealName())
                ? "医生" : doctor.getRealName();
        String when = a.getAppointmentTime() == null ? "" : a.getAppointmentTime().format(TIME_FMT);
        notificationService.push(a.getPatientId(), "PATIENT", "SUSPEND_REFUND",
                "预约已取消（医生停诊）",
                "您预约的【" + a.getDepartment() + " " + doctorName + "】" + when
                        + " 的号因医生临时停诊已取消，请重新选号或联系客服改约。",
                "APPOINTMENT", a.getId());
    }

    /** 改约成功：通知患者 */
    private void notifyRescheduled(Appointment old, ScheduleSlot target, Appointment created) {
        Doctor doctor = target.getDoctorId() == null ? null : doctorMapper.selectById(target.getDoctorId());
        String doctorName = doctor == null || !StringUtils.hasText(doctor.getRealName())
                ? "医生" : doctor.getRealName();
        String oldWhen = old.getAppointmentTime() == null ? "" : old.getAppointmentTime().format(TIME_FMT);
        String newWhen = target.getWorkDate() + " " + target.getStartTime().format(HM_FMT);
        notificationService.push(old.getPatientId(), "PATIENT", "SUSPEND_RESCHEDULE",
                "预约已改约",
                "您原定 " + oldWhen + " 的号因医生停诊已改约至【" + target.getDepartment() + " " + doctorName
                        + "】" + newWhen + (created == null ? "" : "，挂号单号 " + created.getId()) + "，请按时到院。",
                "APPOINTMENT", created == null ? old.getId() : created.getId());
    }

    /** 停诊结果：通知医生 */
    private void notifyDoctorSuspended(ScheduleSlot slot, int cancelled, String action) {
        Doctor doctor = slot == null || slot.getDoctorId() == null ? null : doctorMapper.selectById(slot.getDoctorId());
        if (doctor == null) {
            return;
        }
        notificationService.push(doctor.getId(), "DOCTOR", "SUSPEND_RESULT",
                "您的号源被停诊",
                "管理员停诊了您 " + slot.getWorkDate() + " " + HM_FMT.format(slot.getStartTime()) + "-"
                        + HM_FMT.format(slot.getEndTime()) + " 的号源，涉及 " + cancelled + " 位患者已" + action + "。",
                "SCHEDULE", slot.getId());
    }

    /** 改约结果：通知医生 */
    private void notifyDoctorRescheduled(ScheduleSlot slot, int moved) {
        Doctor doctor = slot == null || slot.getDoctorId() == null ? null : doctorMapper.selectById(slot.getDoctorId());
        if (doctor == null) {
            return;
        }
        notificationService.push(doctor.getId(), "DOCTOR", "SUSPEND_RESULT",
                "您的号源已停诊（患者已改约）",
                "您 " + slot.getWorkDate() + " " + HM_FMT.format(slot.getStartTime()) + "-"
                        + HM_FMT.format(slot.getEndTime()) + " 的号源已停诊，涉及 " + moved + " 位患者已改约。",
                "SCHEDULE", slot.getId());
    }

    @Override
    public ScheduleSlot updateCapacityByAdmin(Long slotId, int totalCount) {
        ScheduleSlot slot = slotId == null ? null : slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        int booked = slot.getBookedCount() == null ? 0 : slot.getBookedCount();
        if (totalCount < booked) {
            throw new BizException("号数不能小于已预约人数（当前已约 " + booked + " 人）");
        }
        if (totalCount > MAX_CAPACITY) {
            throw new BizException("单个时段号数不能超过 " + MAX_CAPACITY + " 个");
        }
        slot.setTotalCount(totalCount);
        slot.setUpdatedAt(LocalDateTime.now());
        slotMapper.updateById(slot);
        log.info("管理端号数调整：slotId={}, total={}", slotId, totalCount);
        return slot;
    }

    /** 批量加载号源并校验：空列表/不存在的 ID 直接报参数错误 */
    private List<ScheduleSlot> loadSlots(List<Long> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择要操作的号源");
        }
        List<ScheduleSlot> slots = slotMapper.selectBatchIds(slotIds);
        if (slots == null || slots.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return slots;
    }

    /** 号源开始时间 */
    private LocalDateTime slotStart(ScheduleSlot s) {
        return LocalDateTime.of(s.getWorkDate(), s.getStartTime());
    }

    /**
     * 改约建议：同医生、不早于停诊日、仍可约的最近 5 个时段。
     */
    private List<ScheduleSlotVO> findAlternatives(List<ScheduleSlot> slots, LocalDateTime now) {
        ScheduleSlot first = slots.get(0);
        Set<Long> excluded = slots.stream().map(ScheduleSlot::getId).collect(Collectors.toSet());
        List<ScheduleSlot> candidates = slotMapper.selectList(new LambdaQueryWrapper<ScheduleSlot>()
                .eq(ScheduleSlot::getDoctorId, first.getDoctorId())
                .eq(ScheduleSlot::getStatus, "OPEN")
                .ge(ScheduleSlot::getWorkDate, first.getWorkDate())
                .apply("booked_count < total_count")
                .orderByAsc(ScheduleSlot::getWorkDate)
                .orderByAsc(ScheduleSlot::getStartTime)
                .last("LIMIT 30"));
        Doctor doctor = first.getDoctorId() == null ? null : doctorMapper.selectById(first.getDoctorId());
        List<ScheduleSlotVO> result = new ArrayList<>();
        for (ScheduleSlot s : candidates) {
            if (excluded.contains(s.getId())) {
                continue;
            }
            ScheduleSlotVO vo = ScheduleSlotVO.of(s, now);
            if (Boolean.TRUE.equals(vo.getBookable())) {
                if (doctor != null) {
                    vo.setDoctorName(doctor.getRealName());
                }
                result.add(vo);
                if (result.size() >= 5) {
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<ScheduleSlotVO> listAlternatives(String department, LocalDate startDate, int days, List<Long> exclude) {
        LocalDate start = startDate == null ? LocalDate.now() : startDate;
        int span = Math.min(MAX_DAYS, Math.max(1, days));
        Set<Long> excluded = exclude == null ? Set.of() : new LinkedHashSet<>(exclude);
        LambdaQueryWrapper<ScheduleSlot> qw = new LambdaQueryWrapper<ScheduleSlot>()
                .eq(ScheduleSlot::getStatus, "OPEN")
                .ge(ScheduleSlot::getWorkDate, start)
                .lt(ScheduleSlot::getWorkDate, start.plusDays(span))
                .apply("booked_count < total_count")
                .orderByAsc(ScheduleSlot::getWorkDate)
                .orderByAsc(ScheduleSlot::getStartTime)
                .last("LIMIT 200");
        if (StringUtils.hasText(department)) {
            qw.eq(ScheduleSlot::getDepartment, department);
        }
        List<ScheduleSlot> slots = slotMapper.selectList(qw);
        LocalDateTime now = LocalDateTime.now();
        Set<Long> doctorIds = slots.stream().map(ScheduleSlot::getDoctorId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Doctor> doctorMap = doctorIds.isEmpty() ? Map.of()
                : doctorMapper.selectBatchIds(doctorIds).stream()
                    .collect(Collectors.toMap(Doctor::getId, d -> d));

        List<ScheduleSlotVO> result = new ArrayList<>();
        for (ScheduleSlot s : slots) {
            if (excluded.contains(s.getId())) {
                continue;
            }
            ScheduleSlotVO vo = ScheduleSlotVO.of(s, now);
            if (!Boolean.TRUE.equals(vo.getBookable())) {
                continue;
            }
            Doctor d = doctorMap.get(s.getDoctorId());
            vo.setDoctorName(d == null ? null : d.getRealName());
            result.add(vo);
            if (result.size() >= 50) {
                break;
            }
        }
        return result;
    }

    @Override
    public ScheduleSlot getById(Long slotId) {
        return slotId == null ? null : slotMapper.selectById(slotId);
    }

    @Override
    public ScheduleSlot resolveForAi(Long doctorId, LocalDateTime desired) {
        if (doctorId == null) {
            return null;
        }
        LocalDate from = desired == null ? LocalDate.now() : desired.toLocalDate();
        List<ScheduleSlot> candidates = slotMapper.selectList(new LambdaQueryWrapper<ScheduleSlot>()
                .eq(ScheduleSlot::getDoctorId, doctorId)
                .eq(ScheduleSlot::getStatus, "OPEN")
                .ge(ScheduleSlot::getWorkDate, from)
                .le(ScheduleSlot::getWorkDate, from.plusDays(7))
                .apply("booked_count < total_count")
                .orderByAsc(ScheduleSlot::getWorkDate)
                .orderByAsc(ScheduleSlot::getStartTime));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = now.plusMinutes(ScheduleSlotVO.BOOK_LEAD_MINUTES);
        candidates.removeIf(s -> s.getWorkDate() == null || s.getStartTime() == null
                || !LocalDateTime.of(s.getWorkDate(), s.getStartTime()).isAfter(earliest));
        if (candidates.isEmpty()) {
            return null;
        }
        if (desired == null) {
            return candidates.get(0);
        }
        // 优先：同一天且不早于患者期望时间
        for (ScheduleSlot s : candidates) {
            if (s.getWorkDate().equals(desired.toLocalDate())
                    && !s.getStartTime().isBefore(desired.toLocalTime())) {
                return s;
            }
        }
        // 其次：同一天任意可约时段
        for (ScheduleSlot s : candidates) {
            if (s.getWorkDate().equals(desired.toLocalDate())) {
                return s;
            }
        }
        // 最后：最近一个有号的日期
        return candidates.get(0);
    }
}

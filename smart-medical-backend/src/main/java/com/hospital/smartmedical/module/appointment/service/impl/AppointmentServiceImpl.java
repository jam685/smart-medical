package com.hospital.smartmedical.module.appointment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.appointment.service.AppointmentService;
import com.hospital.smartmedical.module.appointment.vo.AppointmentVO;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.mapper.ScheduleSlotMapper;
import com.hospital.smartmedical.module.schedule.vo.ScheduleSlotVO;
import com.hospital.smartmedical.module.visit.entity.Visit;
import com.hospital.smartmedical.module.visit.mapper.VisitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 挂号业务服务实现
 * 数据来源：sm_appointment（挂号）+ sm_doctor（医生信息）+ sm_schedule_slot（号源）+ sm_visit（就诊进度）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    /** 挂号 Mapper：挂号单的增删改查 */
    private final AppointmentMapper appointmentMapper;
    /** 医生 Mapper：列表/详情联查医生姓名与职称 */
    private final DoctorMapper doctorMapper;
    /** 就诊进度 Mapper：创建挂号成功后联动创建 WAITING 状态的就诊记录 */
    private final VisitMapper visitMapper;
    /** 号源 Mapper：扣号/回补（带条件的单条 UPDATE） */
    private final ScheduleSlotMapper scheduleSlotMapper;
    /** 患者 Mapper：仅用于下单时锁患者行，串行化同一患者的重复提交 */
    private final PatientMapper patientMapper;

    /** 进度节点时间格式 */
    private static final DateTimeFormatter NODE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 查询可挂号医生：仅返回在线（ONLINE）医生，支持科室与姓名/工号过滤
     */
    @Override
    public List<Map<String, Object>> listAvailableDoctors(String department, String keyword) {
        LambdaQueryWrapper<Doctor> qw = new LambdaQueryWrapper<>();
        qw.eq(Doctor::getOnlineStatus, "ONLINE");
        if (StringUtils.hasText(department)) {
            qw.eq(Doctor::getDepartment, department);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Doctor::getRealName, keyword)
                    .or().like(Doctor::getEmployeeNo, keyword));
        }
        qw.orderByAsc(Doctor::getDepartment).orderByAsc(Doctor::getRealName);

        List<Doctor> doctors = doctorMapper.selectList(qw);
        return toDoctorMapList(doctors);
    }

    /**
     * 批量查询可挂号医生：按科室集合一次查出全部在线医生，避免 RAG 推荐时逐个科室循环查询的 N+1
     */
    @Override
    public List<Map<String, Object>> listAvailableDoctorsByDepts(Collection<String> departments) {
        LambdaQueryWrapper<Doctor> qw = new LambdaQueryWrapper<>();
        qw.eq(Doctor::getOnlineStatus, "ONLINE");
        if (departments != null && !departments.isEmpty()) {
            qw.in(Doctor::getDepartment, departments);
        }
        qw.orderByAsc(Doctor::getDepartment).orderByAsc(Doctor::getRealName);

        List<Doctor> doctors = doctorMapper.selectList(qw);
        return toDoctorMapList(doctors);
    }

    /**
     * 校验同一患者同一医生同一天是否已存在有效挂号（状态非 CANCELLED），用于 AI 挂号前置拦截重复挂号
     */
    @Override
    public boolean hasAppointmentOnDate(Long patientId, Long doctorId, LocalDate date) {
        if (patientId == null || doctorId == null || date == null) {
            return false;
        }
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Long count = appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getPatientId, patientId)
                        .eq(Appointment::getDoctorId, doctorId)
                        .ge(Appointment::getAppointmentTime, start)
                        .lt(Appointment::getAppointmentTime, end)
                        .ne(Appointment::getStatus, "CANCELLED"));
        return count != null && count > 0;
    }

    /**
     * 医生实体列表 → 前端/AI 可用的医生信息 Map 列表（统一组装字段，避免重复代码）
     *
     * @param doctors 医生实体列表
     * @return 医生信息 Map 列表（含 id、姓名、科室、职称、在线状态、挂号费）
     */
    private List<Map<String, Object>> toDoctorMapList(List<Doctor> doctors) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Doctor d : doctors) {
            // 逐项组装前端医生选择卡片所需字段（挂号费由职称档位统一计算）
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("employeeNo", d.getEmployeeNo());
            item.put("realName", d.getRealName());
            item.put("gender", d.getGender());
            item.put("title", d.getTitle());
            item.put("department", d.getDepartment());
            item.put("avatar", d.getAvatar());
            item.put("bio", d.getBio());
            item.put("onlineStatus", d.getOnlineStatus());
            item.put("fee", AppointmentVO.feeOfTitle(d.getTitle()));
            result.add(item);
        }
        return result;
    }

    /**
     * 创建挂号（兼容旧调用）：先按「医生 + 日期 + 时段」匹配号源，命中则走号源链路；
     * 未建号源的历史数据仍可走无号源链路，保证旧接口不中断。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Appointment createAppointment(Long userId, Role role, Long doctorId, String department,
                                         LocalDateTime appointmentTime) {
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (doctorId == null || appointmentTime == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ScheduleSlot slot = findSlotByTime(doctorId, appointmentTime);
        if (slot != null) {
            return createAppointmentBySlot(userId, role, slot.getId());
        }
        return createLegacyAppointment(userId, doctorId, department, appointmentTime);
    }

    /**
     * 按号源下单：锁患者行 → 校验号源 → 原子扣号 → 落挂号单 → 建就诊进度。
     *
     * <p>并发控制只有一处：{@code UPDATE ... WHERE booked_count < total_count}。
     * 返回 0 就是号满，不做重试；扣号与落单同事务，落单失败自动回滚号源。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Appointment createAppointmentBySlot(Long userId, Role role, Long slotId) {
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (slotId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        // 同一患者的并发下单串行化：双击/重放只会产生一张单（不同患者互不阻塞）
        patientMapper.lockById(userId);

        ScheduleSlot slot = scheduleSlotMapper.selectById(slotId);
        if (slot == null || !"OPEN".equals(slot.getStatus())) {
            throw new BizException("该号源不可预约，请刷新后重新选择");
        }
        LocalDateTime start = LocalDateTime.of(slot.getWorkDate(), slot.getStartTime());
        if (!start.isAfter(LocalDateTime.now().plusMinutes(ScheduleSlotVO.BOOK_LEAD_MINUTES))) {
            throw new BizException("该时段已停止预约，请选择其他时段");
        }
        if (hasAppointmentOnDate(userId, slot.getDoctorId(), slot.getWorkDate())) {
            throw new BizException("您当天已挂过该医生的号，请勿重复预约");
        }
        // ★ 唯一的并发控制点：条件更新，0 行即号满
        if (scheduleSlotMapper.deduct(slotId) == 0) {
            throw new BizException("该时段号源已约满，请选择其他时段");
        }
        // 更新已清空 MyBatis 一级缓存，这里读到的是扣减后的新值，直接作为时段内序号
        ScheduleSlot after = scheduleSlotMapper.selectById(slotId);
        int queueNo = after == null || after.getBookedCount() == null ? 1 : after.getBookedCount();

        Doctor doctor = doctorMapper.selectById(slot.getDoctorId());
        Appointment appt = new Appointment();
        appt.setPatientId(userId);
        appt.setDoctorId(slot.getDoctorId());
        appt.setDepartment(StringUtils.hasText(slot.getDepartment())
                ? slot.getDepartment()
                : (doctor == null ? null : doctor.getDepartment()));
        appt.setScheduleId(slotId);
        appt.setQueueNo(queueNo);
        appt.setSlotStart(start);
        appt.setAppointmentTime(start);
        appt.setStatus("PENDING");
        appt.setCreatedAt(LocalDateTime.now());
        appt.setUpdatedAt(LocalDateTime.now());
        appointmentMapper.insert(appt);

        createWaitingVisit(appt);
        log.info("号源挂号成功: appointmentId={}, slotId={}, patientId={}, doctorId={}, queueNo={}, start={}",
                appt.getId(), slotId, userId, slot.getDoctorId(), queueNo, start);
        return appt;
    }

    /**
     * 无号源兼容链路（历史数据/脚本直接指定时间）：不扣号，仅在预约时间合法时落单。
     */
    private Appointment createLegacyAppointment(Long userId, Long doctorId, String department,
                                               LocalDateTime appointmentTime) {
        if (appointmentTime.isBefore(LocalDateTime.now())) {
            throw new BizException("就诊时间不能早于当前时间");
        }
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Appointment appt = new Appointment();
        appt.setPatientId(userId);
        appt.setDoctorId(doctorId);
        appt.setDepartment(StringUtils.hasText(department) ? department : doctor.getDepartment());
        appt.setAppointmentTime(appointmentTime);
        appt.setStatus("PENDING");
        appt.setCreatedAt(LocalDateTime.now());
        appt.setUpdatedAt(LocalDateTime.now());
        appointmentMapper.insert(appt);
        createWaitingVisit(appt);
        log.info("挂号创建成功（无号源兼容链路）: appointmentId={}, patientId={}, doctorId={}, time={}",
                appt.getId(), userId, doctorId, appointmentTime);
        return appt;
    }

    /**
     * 按「医生 + 日期 + 时段起点」定位号源（旧接口兼容用）。
     *
     * @return 命中的号源；无号源返回 null
     */
    private ScheduleSlot findSlotByTime(Long doctorId, LocalDateTime time) {
        return scheduleSlotMapper.selectOne(new LambdaQueryWrapper<ScheduleSlot>()
                .eq(ScheduleSlot::getDoctorId, doctorId)
                .eq(ScheduleSlot::getWorkDate, time.toLocalDate())
                .le(ScheduleSlot::getStartTime, time.toLocalTime())
                .gt(ScheduleSlot::getEndTime, time.toLocalTime())
                .last("LIMIT 1"));
    }

    /**
     * 建就诊进度（WAITING，初始节点"挂号"）
     */
    private void createWaitingVisit(Appointment appt) {
        Visit visit = new Visit();
        visit.setAppointmentId(appt.getId());
        visit.setPatientId(appt.getPatientId());
        visit.setDoctorId(appt.getDoctorId());
        visit.setDepartment(appt.getDepartment());
        visit.setStatus("WAITING");
        String nowFmt = LocalDateTime.now().format(NODE_TIME_FMT);
        visit.setProgressNodes("[{\"name\":\"挂号\",\"time\":\"" + nowFmt + "\"}]");
        visit.setCreatedAt(LocalDateTime.now());
        visit.setUpdatedAt(LocalDateTime.now());
        visitMapper.insert(visit);
    }

    /**
     * 分页查询挂号并组装 VO（批量查询医生，避免 N+1 查询）
     */
    @Override
    public PageResult<AppointmentVO> listAppointments(Long userId, Role role, int page, int size, String status) {
        LambdaQueryWrapper<Appointment> qw = new LambdaQueryWrapper<>();
        // 数据隔离：患者只看本人，医生只看本人接诊，管理员不加过滤
        if (role == Role.PATIENT) {
            qw.eq(Appointment::getPatientId, userId);
        } else if (role == Role.DOCTOR) {
            qw.eq(Appointment::getDoctorId, userId);
        }
        // 可选状态过滤
        if (StringUtils.hasText(status)) {
            qw.eq(Appointment::getStatus, status);
        }
        qw.orderByDesc(Appointment::getAppointmentTime);

        Page<Appointment> pageParam = Page.of(Math.max(1, page), Math.max(1, size));
        Page<Appointment> result = appointmentMapper.selectPage(pageParam, qw);

        // 批量取出本页涉及的医生，构建 id -> Doctor 映射
        Set<Long> doctorIds = result.getRecords().stream()
                .map(Appointment::getDoctorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Doctor> doctorMap = doctorIds.isEmpty()
                ? Collections.emptyMap()
                : doctorMapper.selectBatchIds(doctorIds).stream()
                    .collect(Collectors.toMap(Doctor::getId, d -> d));

        List<AppointmentVO> voList = result.getRecords().stream()
                .map(a -> AppointmentVO.of(a, doctorMap.get(a.getDoctorId())))
                .collect(Collectors.toList());

        // 排序规则（业务展示顺序，由后端统一控制，前端不写死）：
        //   1) 已取消（CANCELLED）的挂号单排在列表末尾；
        //   2) 其余状态（待确认/待就诊/已完成）排在前面；
        //   3) 同组内按就诊时间 appointmentTime 倒序（由近到远），时间相同时按 id 倒序稳定排序。
        voList.sort((a, b) -> {
            int groupA = "CANCELLED".equals(a.getStatus()) ? 1 : 0;
            int groupB = "CANCELLED".equals(b.getStatus()) ? 1 : 0;
            if (groupA != groupB) {
                return Integer.compare(groupA, groupB);
            }
            int cmp = compareTimeDesc(a.getAppointmentTime(), b.getAppointmentTime());
            return cmp != 0 ? cmp : Long.compare(b.getId(), a.getId());
        });

        return PageResult.of(result.getTotal(), voList, (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 按 yyyy-MM-dd HH:mm:ss 字符串倒序比较（近的在前），空值视为最小（排末尾）
     *
     * @param a 时间字符串 a
     * @param b 时间字符串 b
     * @return 负值表示 a 排在 b 前，正值表示 a 排在 b 后
     */
    private static int compareTimeDesc(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    /**
     * 查询挂号详情：先校验存在性与归属权限，再组装 VO
     */
    @Override
    public AppointmentVO getAppointmentDetail(Long id, Long userId, Role role) {
        Appointment appt = appointmentMapper.selectById(id);
        if (appt == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (role == Role.PATIENT && !appt.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (role == Role.DOCTOR && !appt.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Doctor doctor = doctorMapper.selectById(appt.getDoctorId());
        return AppointmentVO.of(appt, doctor);
    }

    /**
     * 患者自助改约：同医生换时段，原子完成"退旧号 → 扣新号 → 建新单"。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Appointment rescheduleAppointment(Long id, Long userId, Role role, Long toSlotId) {
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (id == null || toSlotId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Appointment old = appointmentMapper.selectById(id);
        if (old == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!old.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!"PENDING".equals(old.getStatus()) && !"CONFIRMED".equals(old.getStatus())) {
            throw new BizException("当前挂号状态不支持改约");
        }
        if (old.getSlotStart() != null && !old.getSlotStart().isAfter(LocalDateTime.now())) {
            throw new BizException("就诊时间已开始，无法改约");
        }
        if (toSlotId.equals(old.getScheduleId())) {
            throw new BizException("目标时段与当前时段相同，请选择其他时段");
        }
        ScheduleSlot target = scheduleSlotMapper.selectById(toSlotId);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "目标时段不存在");
        }
        if (!target.getDoctorId().equals(old.getDoctorId())) {
            throw new BizException("改约仅支持同一医生，更换医生请先取消再重新挂号");
        }
        if (!"OPEN".equals(target.getStatus())) {
            throw new BizException("目标时段不可预约，请重新选择");
        }
        LocalDateTime start = LocalDateTime.of(target.getWorkDate(), target.getStartTime());
        if (!start.isAfter(LocalDateTime.now().plusMinutes(ScheduleSlotVO.BOOK_LEAD_MINUTES))) {
            throw new BizException("目标时段已停止预约，请选择其他时段");
        }
        Visit visit = visitMapper.selectOne(new LambdaQueryWrapper<Visit>()
                .eq(Visit::getAppointmentId, id).last("LIMIT 1"));
        if (visit != null && !"WAITING".equals(visit.getStatus())) {
            throw new BizException("医生已叫号，无法改约");
        }

        // 先退旧号再下新单：同一患者同日同医生的重复挂号校验才不会误伤自己
        if (appointmentMapper.cancelIfActive(id, "RESCHEDULE") == 0) {
            throw new BizException("挂号单状态已变更，请刷新后重试");
        }
        if (old.getScheduleId() != null) {
            scheduleSlotMapper.release(old.getScheduleId());
        }
        Appointment created = createAppointmentBySlot(userId, Role.PATIENT, toSlotId);
        // 记录改约来源，形成"原单 → 新单"的可追溯链路
        created.setRescheduledFrom(id);
        appointmentMapper.updateById(created);
        log.info("自助改约成功：oldId={}, newId={}, patientId={}, slotId={}",
                id, created.getId(), userId, toSlotId);
        return created;
    }

    /**
     * 取消挂号：条件更新单据状态 + 回补号源（同一事务）。
     *
     * <p>先改单据再回补：单据状态是幂等闸门，只有把状态从 PENDING/CONFIRMED 改成 CANCELLED
     * 成功的那一次才会执行回补，重复点击/并发取消不会把号源多减一次。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAppointment(Long id, Long userId, Role role) {
        Appointment appt = appointmentMapper.selectById(id);
        if (appt == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (role != Role.PATIENT || !appt.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if ("CANCELLED".equals(appt.getStatus())) {
            throw new BizException("该挂号已取消，请勿重复操作");
        }
        if ("FINISHED".equals(appt.getStatus())) {
            throw new BizException("已完成的挂号不能取消");
        }
        if (appt.getSlotStart() != null && !appt.getSlotStart().isAfter(LocalDateTime.now())) {
            throw new BizException("该号就诊时间已开始，无法取消");
        }
        // 业务规则：医生未叫号前患者可取消挂号；一旦医生已叫号（visit 进入 CALLING 及之后），不允许取消
        Visit visit = visitMapper.selectOne(
                new LambdaQueryWrapper<Visit>().eq(Visit::getAppointmentId, id).last("LIMIT 1"));
        if (visit != null && !"WAITING".equals(visit.getStatus())) {
            throw new BizException("医生已叫号，无法取消挂号");
        }
        if (appointmentMapper.cancelIfActive(id, "CANCEL") == 0) {
            throw new BizException("挂号单状态已变更，请刷新后重试");
        }
        if (appt.getScheduleId() != null) {
            scheduleSlotMapper.release(appt.getScheduleId());
        }
        log.info("挂号取消成功: appointmentId={}, patientId={}, slotId={}", id, userId, appt.getScheduleId());
    }
}

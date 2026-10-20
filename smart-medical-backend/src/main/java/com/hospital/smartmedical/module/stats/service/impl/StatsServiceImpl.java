package com.hospital.smartmedical.module.stats.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.admin.entity.AuditLog;
import com.hospital.smartmedical.module.admin.mapper.AuditLogMapper;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.appointment.vo.AppointmentVO;
import com.hospital.smartmedical.module.appointment.service.AppointmentService;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import com.hospital.smartmedical.module.onlineconsult.mapper.OnlineConsultRecordMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.admin.entity.Admin;
import com.hospital.smartmedical.module.admin.mapper.AdminMapper;
import com.hospital.smartmedical.module.prescription.entity.Prescription;
import com.hospital.smartmedical.module.prescription.mapper.PrescriptionMapper;
import com.hospital.smartmedical.module.report.entity.Report;
import com.hospital.smartmedical.module.report.mapper.ReportMapper;
import com.hospital.smartmedical.module.stats.service.StatsService;
import com.hospital.smartmedical.module.stats.vo.DashboardVOs;
import com.hospital.smartmedical.module.visit.entity.Visit;
import com.hospital.smartmedical.module.visit.mapper.VisitMapper;
import com.hospital.smartmedical.module.visit.service.VisitService;
import com.hospital.smartmedical.module.visit.vo.VisitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表盘统计服务实现
 * 实现要点：
 *   1. 全部用 MyBatis-Plus 的 LambdaQueryWrapper + selectCount，不写新 SQL；
 *   2. 列表数据复用 AppointmentService / VisitService 既有方法，保证口径与列表页一致；
 *   3. "今日" 时间范围用 [今日 00:00, 明日 00:00) 半开区间，避免数据库时区差异。
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    /** 患者表 Mapper（统计总患者数 + 取登录用户信息） */
    private final PatientMapper patientMapper;
    /** 医生表 Mapper（统计总医生数 + 取登录用户信息） */
    private final DoctorMapper doctorMapper;
    /** 管理员表 Mapper（取登录用户信息） */
    private final AdminMapper adminMapper;
    /** 挂号表 Mapper（统计今日新增、患者历史挂号） */
    private final AppointmentMapper appointmentMapper;
    /** 就诊进度表 Mapper（统计今日就诊、医生接诊） */
    private final VisitMapper visitMapper;
    /** 处方表 Mapper（统计医生累计处方） */
    private final PrescriptionMapper prescriptionMapper;
    /** 报告表 Mapper（统计患者报告总数） */
    private final ReportMapper reportMapper;
    /** 在线问诊记录表 Mapper（统计待处理问诊） */
    private final OnlineConsultRecordMapper onlineConsultMapper;
    /** 审计日志 Mapper（统计今日审计 + 取最新 5 条） */
    private final AuditLogMapper auditLogMapper;
    /** 挂号业务服务（复用 listAppointments 取即将到来列表） */
    private final AppointmentService appointmentService;
    /** 就诊业务服务（复用 listVisits 取今日接诊列表） */
    private final VisitService visitService;

    /**
     * 患者端仪表盘：聚合当前登录患者的统计指标与即将到来挂号
     */
    @Override
    public DashboardVOs.PatientDashboard getPatientDashboard(Long userId) {
        Patient p = patientMapper.selectById(userId);

        DashboardVOs.PatientDashboard dash = new DashboardVOs.PatientDashboard();
        // 用户信息
        DashboardVOs.UserInfo u = new DashboardVOs.UserInfo();
        u.setId(p.getId());
        u.setRealName(p.getRealName());
        u.setUsername(p.getUsername());
        u.setRole(Role.PATIENT.name());
        u.setGender(p.getGender());
        u.setAvatar(p.getAvatar());
        dash.setUser(u);

        // 统计指标
        DashboardVOs.PatientStats s = new DashboardVOs.PatientStats();
        s.setAppointments(appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>().eq(Appointment::getPatientId, userId)));
        s.setPending(appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getPatientId, userId)
                        .in(Appointment::getStatus, "PENDING", "CONFIRMED")));
        s.setReports(reportMapper.selectCount(
                new LambdaQueryWrapper<Report>().eq(Report::getPatientId, userId)));
        s.setConsults(onlineConsultMapper.selectCount(
                new LambdaQueryWrapper<OnlineConsultRecord>().eq(OnlineConsultRecord::getPatientId, userId)));
        s.setVisits(visitMapper.selectCount(
                new LambdaQueryWrapper<Visit>().eq(Visit::getPatientId, userId)));
        dash.setStats(s);

        // 即将到来挂号：复用既有分页接口取前 5 条
        PageResult<AppointmentVO> page = appointmentService.listAppointments(userId, Role.PATIENT, 1, 5, null);
        dash.setUpcoming(page.getList());
        return dash;
    }

    /**
     * 医生端仪表盘：聚合当前登录医生的今日接诊、累计患者/处方、待回复问诊
     */
    @Override
    public DashboardVOs.DoctorDashboard getDoctorDashboard(Long userId) {
        Doctor d = doctorMapper.selectById(userId);

        DashboardVOs.DoctorDashboard dash = new DashboardVOs.DoctorDashboard();
        // 用户信息
        DashboardVOs.UserInfo u = new DashboardVOs.UserInfo();
        u.setId(d.getId());
        u.setRealName(d.getRealName());
        u.setUsername(d.getEmployeeNo());
        u.setRole(Role.DOCTOR.name());
        u.setGender(d.getGender());
        u.setAvatar(d.getAvatar());
        u.setDepartment(d.getDepartment());
        dash.setUser(u);

        // 今日时间范围 [00:00, 次日 00:00)
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        // 统计指标
        DashboardVOs.DoctorStats s = new DashboardVOs.DoctorStats();
        s.setTodayVisits(visitMapper.selectCount(
                new LambdaQueryWrapper<Visit>()
                        .eq(Visit::getDoctorId, userId)
                        .ge(Visit::getCreatedAt, dayStart)
                        .lt(Visit::getCreatedAt, dayEnd)));
        s.setWaiting(visitMapper.selectCount(
                new LambdaQueryWrapper<Visit>()
                        .eq(Visit::getDoctorId, userId)
                        .eq(Visit::getStatus, "WAITING")));
        s.setInProgress(visitMapper.selectCount(
                new LambdaQueryWrapper<Visit>()
                        .eq(Visit::getDoctorId, userId)
                        .eq(Visit::getStatus, "IN_PROGRESS")));
        s.setFinishedToday(visitMapper.selectCount(
                new LambdaQueryWrapper<Visit>()
                        .eq(Visit::getDoctorId, userId)
                        .eq(Visit::getStatus, "FINISHED")
                        .ge(Visit::getUpdatedAt, dayStart)
                        .lt(Visit::getUpdatedAt, dayEnd)));
        s.setTotalPatients(appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>().eq(Appointment::getDoctorId, userId)));
        s.setTotalPrescriptions(prescriptionMapper.selectCount(
                new LambdaQueryWrapper<Prescription>().eq(Prescription::getDoctorId, userId)));
        s.setPendingConsults(onlineConsultMapper.selectCount(
                new LambdaQueryWrapper<OnlineConsultRecord>()
                        .eq(OnlineConsultRecord::getDoctorId, userId)
                        .eq(OnlineConsultRecord::getStatus, "PENDING")));
        dash.setStats(s);

        // 今日接诊列表：复用既有分页接口取前 5 条
        PageResult<VisitVO> vp = visitService.listVisits(userId, Role.DOCTOR, 1, 5, null);
        dash.setTodayVisitList(vp.getList());
        return dash;
    }

    /**
     * 管理员端仪表盘：聚合系统全局统计与最新审计日志
     */
    @Override
    public DashboardVOs.AdminDashboard getAdminDashboard(Long userId) {
        Admin a = adminMapper.selectById(userId);

        DashboardVOs.AdminDashboard dash = new DashboardVOs.AdminDashboard();
        // 用户信息
        DashboardVOs.UserInfo u = new DashboardVOs.UserInfo();
        u.setId(a.getId());
        u.setRealName(a.getRealName());
        u.setUsername(a.getEmployeeNo());
        u.setRole(Role.ADMIN.name());
        dash.setUser(u);

        // 今日时间范围
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        // 统计指标
        DashboardVOs.AdminStats s = new DashboardVOs.AdminStats();
        s.setTotalPatients(patientMapper.selectCount(null));
        s.setTotalDoctors(doctorMapper.selectCount(null));
        s.setTodayAppointments(appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>()
                        .ge(Appointment::getCreatedAt, dayStart)
                        .lt(Appointment::getCreatedAt, dayEnd)));
        s.setTodayVisits(visitMapper.selectCount(
                new LambdaQueryWrapper<Visit>()
                        .ge(Visit::getCreatedAt, dayStart)
                        .lt(Visit::getCreatedAt, dayEnd)));
        s.setPendingConsults(onlineConsultMapper.selectCount(
                new LambdaQueryWrapper<OnlineConsultRecord>().eq(OnlineConsultRecord::getStatus, "PENDING")));
        // 敏感内容表已随 V1.3 删除，待审核敏感内容数置 0
        s.setSensitivePending(0L);
        s.setTodayAuditLogs(auditLogMapper.selectCount(
                new LambdaQueryWrapper<AuditLog>()
                        .ge(AuditLog::getCreatedAt, dayStart)
                        .lt(AuditLog::getCreatedAt, dayEnd)));
        dash.setStats(s);

        // 最新 5 条审计日志
        List<AuditLog> recent = auditLogMapper.selectList(
                new LambdaQueryWrapper<AuditLog>()
                        .orderByDesc(AuditLog::getId)
                        .last("LIMIT 5"));
        dash.setRecentAuditLogs(recent);
        return dash;
    }
}

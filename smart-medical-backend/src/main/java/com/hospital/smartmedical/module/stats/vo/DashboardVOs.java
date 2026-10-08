package com.hospital.smartmedical.module.stats.vo;

import com.hospital.smartmedical.module.admin.entity.AuditLog;
import com.hospital.smartmedical.module.appointment.vo.AppointmentVO;
import com.hospital.smartmedical.module.visit.vo.VisitVO;
import lombok.Data;

import java.util.List;

/**
 * 仪表盘 VO 集合（首页统计聚合响应）
 * 作用：将三端首页所需的"用户信息 + 统计指标 + 关联列表"统一聚合，单次响应给前端，
 *       避免前端发起多次请求并自行拼接；字段含义见各内部静态类的 Javadoc。
 */
public class DashboardVOs {

    /**
     * 患者端首页仪表盘 VO
     * 字段：用户基本信息 + 四项统计指标 + 即将到来挂号列表
     */
    @Data
    public static class PatientDashboard {
        /** 当前登录患者基本信息（来自 sm_patient） */
        private UserInfo user;
        /** 四项核心统计指标 */
        private PatientStats stats;
        /** 即将到来的挂号列表（最多 5 条，按就诊时间升序） */
        private List<AppointmentVO> upcoming;
    }

    /**
     * 患者端统计指标
     */
    @Data
    public static class PatientStats {
        /** 历史挂号总数 */
        private long appointments;
        /** 待就诊数（PENDING + CONFIRMED） */
        private long pending;
        /** 检查报告总数 */
        private long reports;
        /** 在线问诊总数 */
        private long consults;
        /** 就诊记录总数 */
        private long visits;
    }

    /**
     * 医生端首页仪表盘 VO
     * 字段：用户基本信息 + 七项统计指标 + 今日接诊列表
     */
    @Data
    public static class DoctorDashboard {
        /** 当前登录医生基本信息（来自 sm_doctor） */
        private UserInfo user;
        /** 七项核心统计指标 */
        private DoctorStats stats;
        /** 今日待接诊/在诊列表（最多 5 条 VisitVO） */
        private List<VisitVO> todayVisitList;
    }

    /**
     * 医生端统计指标
     */
    @Data
    public static class DoctorStats {
        /** 今日接诊总数（今日创建的就诊记录数） */
        private long todayVisits;
        /** 候诊中数量（status=WAITING） */
        private long waiting;
        /** 就诊中数量（status=IN_PROGRESS） */
        private long inProgress;
        /** 今日已完成数（status=FINISHED 且今日更新） */
        private long finishedToday;
        /** 累计接诊患者数（按挂号单计，不去重） */
        private long totalPatients;
        /** 累计开具处方数 */
        private long totalPrescriptions;
        /** 待回复在线问诊数（status=PENDING） */
        private long pendingConsults;
    }

    /**
     * 管理员端首页仪表盘 VO
     * 字段：用户基本信息 + 七项系统级指标 + 最新审计日志
     */
    @Data
    public static class AdminDashboard {
        /** 当前登录管理员基本信息（来自 sm_admin） */
        private UserInfo user;
        /** 七项系统级统计指标 */
        private AdminStats stats;
        /** 最新 5 条审计日志 */
        private List<AuditLog> recentAuditLogs;
    }

    /**
     * 管理员端统计指标
     */
    @Data
    public static class AdminStats {
        /** 注册患者总数 */
        private long totalPatients;
        /** 在职医生总数 */
        private long totalDoctors;
        /** 今日新增挂号数 */
        private long todayAppointments;
        /** 今日就诊记录数 */
        private long todayVisits;
        /** 待处理在线问诊数 */
        private long pendingConsults;
        /** 待审核敏感内容数 */
        private long sensitivePending;
        /** 今日审计日志数 */
        private long todayAuditLogs;
    }

    /**
     * 通用用户信息（三端共用）
     */
    @Data
    public static class UserInfo {
        /** 用户主键 */
        private Long id;
        /** 真实姓名 */
        private String realName;
        /** 用户名/工号 */
        private String username;
        /** 角色（PATIENT/DOCTOR/ADMIN） */
        private String role;
        /** 性别：男/女（管理员可空） */
        private String gender;
        /** 头像URL（空则前端用姓名首字母占位） */
        private String avatar;
        /** 所属科室（仅医生有值） */
        private String department;
    }
}

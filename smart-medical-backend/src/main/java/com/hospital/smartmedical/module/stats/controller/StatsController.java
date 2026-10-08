package com.hospital.smartmedical.module.stats.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.stats.service.StatsService;
import com.hospital.smartmedical.module.stats.vo.DashboardVOs;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘统计控制器
 * 路由前缀：/stats（完整路径含 context-path：/api/smart-medical/v1/stats）
 * 职责：为三端首页提供聚合统计接口，单次请求返回首页所需全部数据。
 * 鉴权：所有端点都需要登录；并在方法内部显式校验角色，避免越权访问其他角色的数据。
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    /** 仪表盘统计服务 */
    private final StatsService statsService;

    /**
     * 患者端首页仪表盘
     * 权限：仅 PATIENT 可访问
     *
     * @param request HTTP 请求（拦截器注入 currentUserId / currentUserRole）
     * @return 患者端仪表盘 VO（用户 + 5 项统计 + 即将到来挂号列表）
     */
    @GetMapping("/patient")
    public Result<DashboardVOs.PatientDashboard> patientDashboard(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(statsService.getPatientDashboard(userId));
    }

    /**
     * 医生端首页仪表盘
     * 权限：仅 DOCTOR 可访问
     *
     * @param request HTTP 请求
     * @return 医生端仪表盘 VO（用户 + 7 项统计 + 今日接诊列表）
     */
    @GetMapping("/doctor")
    public Result<DashboardVOs.DoctorDashboard> doctorDashboard(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.DOCTOR) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(statsService.getDoctorDashboard(userId));
    }

    /**
     * 管理员端首页仪表盘
     * 权限：仅 ADMIN 可访问
     *
     * @param request HTTP 请求
     * @return 管理员端仪表盘 VO（用户 + 7 项统计 + 最新审计日志）
     */
    @GetMapping("/admin")
    public Result<DashboardVOs.AdminDashboard> adminDashboard(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(statsService.getAdminDashboard(userId));
    }
}

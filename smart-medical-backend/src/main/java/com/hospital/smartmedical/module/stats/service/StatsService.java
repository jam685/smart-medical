package com.hospital.smartmedical.module.stats.service;

import com.hospital.smartmedical.module.stats.vo.DashboardVOs;

/**
 * 仪表盘统计业务服务
 * 职责：为三端首页聚合后端真实统计数据，避免前端发起多次请求；全部走既有 Mapper，不引入新 SQL。
 */
public interface StatsService {

    /**
     * 获取患者端首页仪表盘数据
     * 统计口径：当前登录患者本人的挂号/报告/问诊/就诊总数 + 即将到来的挂号列表
     *
     * @param userId 当前登录患者 ID（拦截器注入）
     * @return 患者端仪表盘 VO（用户信息 + 5 项指标 + 即将到来列表）
     */
    DashboardVOs.PatientDashboard getPatientDashboard(Long userId);

    /**
     * 获取医生端首页仪表盘数据
     * 统计口径：当前登录医生今日接诊/候诊/在诊/已完成 + 累计患者/处方 + 待回复问诊
     *
     * @param userId 当前登录医生 ID（拦截器注入）
     * @return 医生端仪表盘 VO（用户信息 + 7 项指标 + 今日接诊列表）
     */
    DashboardVOs.DoctorDashboard getDoctorDashboard(Long userId);

    /**
     * 获取管理员端首页仪表盘数据
     * 统计口径：系统总患者/医生 + 今日新增挂号/就诊 + 待处理问诊/敏感内容 + 今日审计日志
     *
     * @return 管理员端仪表盘 VO（用户信息 + 7 项指标 + 最新审计日志）
     */
    DashboardVOs.AdminDashboard getAdminDashboard(Long userId);
}

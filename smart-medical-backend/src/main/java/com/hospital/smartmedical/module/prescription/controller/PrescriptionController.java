package com.hospital.smartmedical.module.prescription.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.prescription.service.PrescriptionService;
import com.hospital.smartmedical.module.prescription.vo.PrescriptionVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 处方业务控制器
 * 路由前缀：/prescriptions（完整路径含 context-path：/api/smart-medical/v1/prescriptions）
 * 提供：开具处方、按就诊查询处方、医生处方列表 3 个接口。
 * 权限口径：仅医生/管理员可开方；患者只能查看本人处方。
 */
@RestController
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    /** 处方业务服务 */
    private final PrescriptionService prescriptionService;

    /**
     * 开具处方（医生接诊时调用）
     *
     * @param body    请求体：visitId、diagnosis、advice、items（药品明细数组）
     * @param request HTTP 请求（拦截器注入当前用户 ID 与角色）
     * @return 新建处方 VO（含明细）
     */
    @PostMapping
    public Result<PrescriptionVO> create(@RequestBody CreatePrescriptionRequest body,
                                         HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(prescriptionService.createPrescription(
                body.getVisitId(), body.getDiagnosis(), body.getAdvice(), body.getItems(), userId, role));
    }

    /**
     * 按就诊 ID 查询处方详情（含药品明细）
     *
     * @param visitId 就诊进度 ID
     * @param request HTTP 请求（注入当前用户，用于归属校验）
     * @return 处方 VO，无处方时 data 为 null
     */
    @GetMapping("/by-visit/{visitId}")
    public Result<PrescriptionVO> getByVisit(@PathVariable Long visitId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(prescriptionService.getByVisitId(visitId, userId, role));
    }

    /**
     * 查询当前医生开具的处方列表
     *
     * @param request HTTP 请求（注入当前医生 ID）
     * @return 处方 VO 列表
     */
    @GetMapping("/my")
    public Result<List<PrescriptionVO>> myPrescriptions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(prescriptionService.listByDoctor(userId));
    }

    /**
     * 查询当前患者本人的处方列表（患者端"我的报告-处方报告"使用）
     *
     * @param request HTTP 请求（注入当前患者 ID）
     * @return 处方 VO 列表（按处方编号分组，含药品明细）
     */
    @GetMapping("/patient/me")
    public Result<List<PrescriptionVO>> myPatientPrescriptions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(prescriptionService.listByPatient(userId));
    }

    /**
     * 开具处方请求体
     */
    @Data
    public static class CreatePrescriptionRequest {
        /** 就诊进度 ID（sm_visit.id，必填） */
        private Long visitId;
        /** 诊断 */
        private String diagnosis;
        /** 医嘱 */
        private String advice;
        /** 药品明细列表（至少一项） */
        private List<PrescriptionVO.Item> items;
    }
}

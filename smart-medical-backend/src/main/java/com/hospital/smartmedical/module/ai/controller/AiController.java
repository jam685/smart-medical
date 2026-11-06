package com.hospital.smartmedical.module.ai.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.ai.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 辅助分析接口（V2.3）。
 * 移除原 AI 客服对话（/ai/chat）能力，仅保留医生端 4 个 AI 辅助接口。
 * 当前为 Demo 模拟实现，接入真实 FastAPI 后本层不变。
 *
 * <p>角色校验说明：本 Controller 所有接口只允许 DOCTOR 角色访问。AuthInterceptor 已按
 * 医生专属路径规则（/ai/clinical-analysis 等）做了一层拦截，这里再做一次显式校验兜底，
 * 防止后续调整路径规则时被绕过。</p>
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // ===================== 1. 医生端：病情分析 =====================

    @PostMapping("/clinical-analysis")
    public Result<Map<String, Object>> clinicalAnalysis(@RequestBody ClinicalAnalysisReq req, HttpServletRequest request) {
        requireDoctor(request);
        return Result.ok(aiService.clinicalAnalysis(
                req.getChiefComplaint(), req.getDescription(), req.getPatientInfo()));
    }

    // ===================== 2. 医生端：检查建议 =====================

    @PostMapping("/examination-suggestions")
    public Result<Map<String, Object>> examinationSuggestions(@RequestBody ExaminationReq req, HttpServletRequest request) {
        requireDoctor(request);
        return Result.ok(aiService.examinationSuggestions(
                req.getChiefComplaint(), req.getDescription()));
    }

    // ===================== 3. 医生端：回复草稿 =====================

    @PostMapping("/reply-drafts")
    public Result<Map<String, Object>> replyDrafts(@RequestBody ReplyDraftReq req, HttpServletRequest request) {
        requireDoctor(request);
        return Result.ok(aiService.replyDrafts(
                req.getChiefComplaint(), req.getDescription()));
    }

    // ===================== 4. 医生端：图片/报告分析 =====================

    @PostMapping("/image-analysis")
    public Result<Map<String, Object>> imageAnalysis(@RequestBody ImageAnalysisReq req, HttpServletRequest request) {
        requireDoctor(request);
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(aiService.imageAnalysis(req.getReportId(), userId, role));
    }

    // ===================== 角色校验工具 =====================

    /** 断言当前登录角色为 DOCTOR；否则抛 403 FORBIDDEN。 */
    private static void requireDoctor(HttpServletRequest request) {
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role == null || role != Role.DOCTOR) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅医生可访问 AI 辅助分析接口");
        }
    }

    // ===================== DTOs =====================

    /** 病情分析入参 */
    @Data
    public static class ClinicalAnalysisReq {
        private String chiefComplaint;
        private String description;
        private Map<String, Object> patientInfo;
    }

    /** 检查建议入参 */
    @Data
    public static class ExaminationReq {
        private String chiefComplaint;
        private String description;
    }

    /** 回复草稿入参 */
    @Data
    public static class ReplyDraftReq {
        private String chiefComplaint;
        private String description;
    }

    /** 图片分析入参 */
    @Data
    public static class ImageAnalysisReq {
        private Long reportId;
    }
}

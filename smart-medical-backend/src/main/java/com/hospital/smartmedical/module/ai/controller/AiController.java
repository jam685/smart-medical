package com.hospital.smartmedical.module.ai.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.ai.service.AiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 辅助分析接口（V2.3）。
 * 移除原 AI 客服对话（/ai/chat）能力，仅保留医生端 4 个 AI 辅助接口。
 * 当前为 Demo 模拟实现，接入真实 FastAPI 后本层不变。
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // ===================== 1. 医生端：病情分析 =====================

    @PostMapping("/clinical-analysis")
    public Result<Map<String, Object>> clinicalAnalysis(@RequestBody ClinicalAnalysisReq req) {
        return Result.ok(aiService.clinicalAnalysis(
                req.getChiefComplaint(), req.getDescription(), req.getPatientInfo()));
    }

    // ===================== 2. 医生端：检查建议 =====================

    @PostMapping("/examination-suggestions")
    public Result<Map<String, Object>> examinationSuggestions(@RequestBody ExaminationReq req) {
        return Result.ok(aiService.examinationSuggestions(
                req.getChiefComplaint(), req.getDescription()));
    }

    // ===================== 3. 医生端：回复草稿 =====================

    @PostMapping("/reply-drafts")
    public Result<Map<String, Object>> replyDrafts(@RequestBody ReplyDraftReq req) {
        return Result.ok(aiService.replyDrafts(
                req.getChiefComplaint(), req.getDescription()));
    }

    // ===================== 4. 医生端：图片/报告分析 =====================

    @PostMapping("/image-analysis")
    public Result<Map<String, Object>> imageAnalysis(@RequestBody ImageAnalysisReq req) {
        return Result.ok(aiService.imageAnalysis(req.getReportId()));
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
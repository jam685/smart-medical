package com.hospital.smartmedical.module.ai.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.ai.entity.AiChatMessage;
import com.hospital.smartmedical.module.ai.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // ===================== 1. AI 客服对话 =====================

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(
            @RequestBody ChatReq req,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String role = (String) request.getAttribute("currentUserRole");
        return Result.ok(aiService.chat(userId, role, req.getSessionKey(), req.getMessage()));
    }

    // ===================== 2. 转人工 =====================

    @PostMapping("/chat/transfer")
    public Result<Map<String, Object>> transfer(@RequestBody TransferReq req) {
        return Result.ok(aiService.transferToHuman(req.getSessionKey()));
    }

    // ===================== 3. 会话历史 =====================

    @GetMapping("/chat/history")
    public Result<List<AiChatMessage>> history(@RequestParam String sessionKey) {
        return Result.ok(aiService.getChatHistory(sessionKey));
    }

    // ===================== 4. 医生端：病情分析 =====================

    @PostMapping("/clinical-analysis")
    public Result<Map<String, Object>> clinicalAnalysis(@RequestBody ClinicalAnalysisReq req) {
        return Result.ok(aiService.clinicalAnalysis(
                req.getChiefComplaint(), req.getDescription(), req.getPatientInfo()));
    }

    // ===================== 5. 医生端：检查建议 =====================

    @PostMapping("/examination-suggestions")
    public Result<Map<String, Object>> examinationSuggestions(
            @RequestBody ExaminationReq req) {
        return Result.ok(aiService.examinationSuggestions(
                req.getChiefComplaint(), req.getDescription()));
    }

    // ===================== 6. 医生端：回复草稿 =====================

    @PostMapping("/reply-drafts")
    public Result<Map<String, Object>> replyDrafts(@RequestBody ReplyDraftReq req) {
        return Result.ok(aiService.replyDrafts(
                req.getChiefComplaint(), req.getDescription()));
    }

    // ===================== 7. 医生端：图片/报告分析 =====================

    @PostMapping("/image-analysis")
    public Result<Map<String, Object>> imageAnalysis(@RequestBody ImageAnalysisReq req) {
        return Result.ok(aiService.imageAnalysis(req.getReportId()));
    }

    // ===================== DTOs =====================

    @Data
    public static class ChatReq {
        private String sessionKey;
        private String message;
    }

    @Data
    public static class TransferReq {
        private String sessionKey;
    }

    @Data
    public static class ClinicalAnalysisReq {
        private String chiefComplaint;
        private String description;
        private Map<String, Object> patientInfo;
    }

    @Data
    public static class ExaminationReq {
        private String chiefComplaint;
        private String description;
    }

    @Data
    public static class ReplyDraftReq {
        private String chiefComplaint;
        private String description;
    }

    @Data
    public static class ImageAnalysisReq {
        private Long reportId;
    }
}

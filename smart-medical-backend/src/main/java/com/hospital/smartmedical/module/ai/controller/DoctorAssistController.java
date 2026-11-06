package com.hospital.smartmedical.module.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.ai.agent.service.DoctorAssistAnalyzeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * 医生 AI 辅助接口（ConsultHandle 问诊处理页）。
 *
 * <p>身份来自登录态（AuthInterceptor 注入 currentUserId/currentUserRole），
 * 仅医生角色可访问；问诊归属校验在 service 层（仅接诊医生可分析自己的问诊）。</p>
 */
@RestController
@RequestMapping("/ai/doctor-assist")
@RequiredArgsConstructor
@Slf4j
public class DoctorAssistController {

    private final DoctorAssistAnalyzeService doctorAssistAnalyzeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 读取某问诊最新 AI 分析草稿（缓存命中场景）。
     *
     * <p>医生打开问诊处理页时先调此接口：若患者未发新消息、已有完整分析草稿，
     * 直接返回缓存，前端零等待渲染。返回 JSON 带 _cached/_version/_cachedAt/_draftId
     * 元数据，前端可展示「v3 · 5 分钟前 · 患者未发新消息」提示。</p>
     *
     * <p>无草稿（首次分析）或归属不匹配 → 返回 null，前端改走 analyze-stream。</p>
     */
    @GetMapping("/draft/{consultId}")
    public Result<Map<String, Object>> getLatestDraft(@PathVariable Long consultId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (userId == null || role != Role.DOCTOR) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Map<String, Object> draft = doctorAssistAnalyzeService.getLatestDraft(userId, consultId);
        return Result.ok(draft);
    }

    /**
     * 问诊 AI 分析（非流式，结构化 JSON 结果）。
     *
     * <p>患者每发新消息，前端轮询检测后自动调用：返回信息充足度、缺失项、
     * 引导追问草稿 / 病情分析 + 就诊建议 + 处方建议（含「本院开过」标注），
     * 以及右侧边栏档案区数据（患者档案/预问诊摘要/历史病历）。</p>
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyze(@RequestBody AnalyzeReq req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (userId == null || role != Role.DOCTOR) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (req.getConsultId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return Result.ok(doctorAssistAnalyzeService.analyze(userId, req.getConsultId()));
    }

    /**
     * 问诊 AI 分析流式入口（SSE，text/event-stream）。
     *
     * <p>事件序列：profile（患者档案/预问诊/病史，模型调用前秒出）→
     * section*（顶层字段完成：sufficiency/missingItems/draftReply/diagnosis/advice/prescription）→
     * token*（draftReply 增量文本，打字机效果）→ done（完整结果，含 inHospital 标注与分析时间）。</p>
     */
    @PostMapping(value = "/analyze-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@RequestBody AnalyzeReq req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (userId == null || role != Role.DOCTOR) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (req.getConsultId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long doctorId = userId;
        Long consultId = req.getConsultId();

        SseEmitter emitter = new SseEmitter(180_000L);
        // 异步线程执行：先推档案区（不依赖模型），再做流式分析
        Thread worker = new Thread(() -> {
            try {
                DoctorAssistAnalyzeService.AnalysisSession session =
                        doctorAssistAnalyzeService.prepare(doctorId, consultId);
                sendSse(emitter, "profile", doctorAssistAnalyzeService.profilePayload(session));
                Map<String, Object> result = doctorAssistAnalyzeService.streamAnalyze(session,
                        (key, value) -> sendSseQuietly(emitter, "section", Map.of("key", key, "value", value)),
                        delta -> sendSseQuietly(emitter, "token", Map.of("delta", delta)));
                sendSse(emitter, "done", result);
            } catch (Exception e) {
                log.error("问诊 AI 流式分析失败：consultId={}, {}", consultId, e.getMessage());
                sendSseQuietly(emitter, "error", Map.of("message", "AI 分析失败：" + e.getMessage()));
            } finally {
                emitter.complete();
            }
        }, "doctor-assist-" + consultId);
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    /**
     * 发送 SSE 事件（event 名 + JSON 数据）。
     */
    private void sendSse(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(event)
                .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
    }

    /**
     * 推送 SSE 事件，前端断开等异常仅记录不中断分析。
     */
    private void sendSseQuietly(SseEmitter emitter, String event, Object data) {
        try {
            sendSse(emitter, event, data);
        } catch (Exception e) {
            log.warn("SSE 事件推送失败（{}）：{}", event, e.getMessage());
        }
    }

    /** 问诊分析入参 */
    @Data
    public static class AnalyzeReq {
        /** 问诊记录 ID（sm_online_consult_record.id） */
        private Long consultId;
    }
}

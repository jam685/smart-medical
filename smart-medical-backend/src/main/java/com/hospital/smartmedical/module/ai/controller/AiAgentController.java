package com.hospital.smartmedical.module.ai.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.ai.agent.service.AiAgentService;
import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

/**
 * AI 多智能体对话接口（患者客服 + 医生辅助统一入口，走 LangGraph 图流转）。
 *
 * <p>身份来自登录态（AuthInterceptor 注入 currentUserId/currentUserRole）。
 * 患者：预问诊症状采集、科室推荐、挂号/进度/处方报告查询；
 * 医生：回复草稿、处方草稿（后续医生端入口接入）。</p>
 */
@RestController
@RequestMapping("/ai/patient-chat")
@RequiredArgsConstructor
@Slf4j
public class AiAgentController {

    private final AiAgentService aiAgentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===================== 1. 发送消息（走图） =====================

    /**
     * AI 对话入口：接收用户消息（文本/图片），返回图流转结果（同步，一次性返回）。
     */
    @PostMapping
    public Result<Map<String, Object>> chat(@RequestBody AiChatReq req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Map<String, Object> result = aiAgentService.process(userId, role,
                req.getSessionId(), req.getContent(), req.getImageUrls(), null, req.getSource());
        return Result.ok(result);
    }

    /**
     * AI 对话流式入口（SSE，text/event-stream）：
     * 事件序列 start → token*（模型增量文本，逐字/逐句实时推送）→ done（含图流转全量结果）。
     * 模型生成期间前端即可实时展示回复，消除加载等待阶段。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AiChatReq req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        // 先推 start 事件，前端立即进入流式接收态（无加载空窗）
        try {
            sendSse(emitter, "start", Map.of("status", "ok"));
        } catch (Exception e) {
            emitter.complete();
            return emitter;
        }
        // 异步线程执行图流转：onToken 回调实时推送模型增量文本 token 事件（显式传参穿透节点线程，不走 ThreadLocal）
        new Thread(() -> {
            try {
                Map<String, Object> result = aiAgentService.process(userId, role,
                        req.getSessionId(), req.getContent(), req.getImageUrls(),
                        delta -> {
                            try {
                                sendSse(emitter, "token", Map.of("delta", delta));
                            } catch (IOException e) {
                                // 前端断开等异常：仅记录，不影响图流转收尾
                                log.warn("SSE token 推送失败：{}", e.getMessage());
                            }
                        }, req.getSource());
                sendSse(emitter, "done", result);
            } catch (Exception e) {
                log.error("AI 流式对话执行失败：{}", e.getMessage());
                try {
                    sendSse(emitter, "error", Map.of("message", "AI 回复失败：" + e.getMessage()));
                } catch (IOException ex) {
                    log.warn("SSE error 推送失败：{}", ex.getMessage());
                }
            } finally {
                emitter.complete();
            }
        }, "ai-stream-" + (req.getSessionId() == null ? "new" : req.getSessionId())).start();
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

    // ===================== 2. 会话历史 =====================

    /**
     * 查询会话历史消息。
     */
    @GetMapping("/{sessionId}/history")
    public Result<List<ChatMessage>> history(@PathVariable String sessionId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(aiAgentService.history(userId, role, sessionId));
    }

    // ===================== 3. 历史会话列表 =====================

    /**
     * 患者历史会话列表。
     */
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.PATIENT) {
            return Result.ok(List.of());
        }
        return Result.ok(aiAgentService.listSessions(userId));
    }

    // ===================== DTOs =====================

    /** AI 对话入参 */
    @Data
    public static class AiChatReq {
        /** 会话 ID（为空则后端新建） */
        private String sessionId;
        /** 用户消息文本 */
        private String content;
        /** 本次上传图片地址集合（可空） */
        private List<String> imageUrls;
        /** 会话来源：assistant=患者端数字医生小林（cs_ 会话）；doctor_assistant=医生端数字医生小林（ds_ 会话）；为空=预问诊 */
        private String source;
    }
}

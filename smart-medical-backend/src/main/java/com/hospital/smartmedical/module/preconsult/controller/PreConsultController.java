package com.hospital.smartmedical.module.preconsult.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.preconsult.entity.PreConsult;
import com.hospital.smartmedical.module.preconsult.service.PreConsultService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能预问诊控制器（/pre-consults）。
 *
 * <p>患者挂号前的 AI 预问诊：发送消息（含图片）、查看会话历史、记录列表、结束会话。
 * AI 只做症状采集与挂号引导（PRE-01~04）。</p>
 */
@RestController
@RequestMapping("/pre-consults")
@RequiredArgsConstructor
public class PreConsultController {

    private final PreConsultService preConsultService;

    /**
     * 患者发送预问诊消息（PRE-01），返回患者侧 AI 回复。
     */
    @PostMapping
    public Result<Map<String, Object>> sendMessage(@RequestBody SendMessageReq req,
                                                    HttpServletRequest request) {
        Long patientId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(preConsultService.sendMessage(patientId, req.getSessionId(),
                req.getContent(), req.getImageUrl()));
    }

    /**
     * 获取预问诊会话历史（PRE-02）。
     */
    @GetMapping("/{sessionId}")
    public Result<List<PreConsult>> getHistory(@PathVariable String sessionId,
                                               HttpServletRequest request) {
        Long patientId = (Long) request.getAttribute("currentUserId");
        return Result.ok(preConsultService.getHistory(patientId, sessionId));
    }

    /**
     * 患者查看自己的预问诊记录列表（PRE-03）。
     */
    @GetMapping
    public Result<List<Map<String, Object>>> listSessions(HttpServletRequest request) {
        Long patientId = (Long) request.getAttribute("currentUserId");
        return Result.ok(preConsultService.listSessions(patientId));
    }

    /**
     * 结束预问诊会话（PRE-04）。
     */
    @PostMapping("/{sessionId}/close")
    public Result<Void> closeSession(@PathVariable String sessionId,
                                     HttpServletRequest request) {
        Long patientId = (Long) request.getAttribute("currentUserId");
        preConsultService.closeSession(patientId, sessionId);
        return Result.ok();
    }

    /** 发送预问诊消息请求体 */
    @Data
    public static class SendMessageReq {
        /** 会话标识；为空则由服务端生成新会话 */
        private String sessionId;
        /** 文本内容（可空，图片消息时为空） */
        private String content;
        /** 图片地址（可空，非空即图片消息） */
        private String imageUrl;
    }
}
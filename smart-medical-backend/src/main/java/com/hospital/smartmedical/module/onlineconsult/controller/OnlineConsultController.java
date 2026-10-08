package com.hospital.smartmedical.module.onlineconsult.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.ai.entity.AiAnalysis;
import com.hospital.smartmedical.module.onlineconsult.entity.*;
import com.hospital.smartmedical.module.onlineconsult.service.OnlineConsultService;
import com.hospital.smartmedical.module.onlineconsult.vo.OnlineDoctorVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 线上面诊模块控制器（/online-consults）。
 *
 * <p>鉴权与角色在 AuthInterceptor 中统一完成，本控制器负责读取当前用户并转发到服务层。
 * 涉及隐私的接口均透传当前用户 ID 与角色，由服务层做归属校验。</p>
 */
@RestController
@RequestMapping("/online-consults")
@RequiredArgsConstructor
public class OnlineConsultController {

    private final OnlineConsultService onlineConsultService;

    // ===================== 1. 在线医生列表（OC-12） =====================

    @GetMapping("/online-doctors")
    public Result<List<OnlineDoctorVO>> getOnlineDoctors(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword) {
        List<OnlineDoctorVO> doctors = onlineConsultService.getOnlineDoctors(department, keyword);
        return Result.ok(doctors);
    }

    // ===================== 2. 发起问诊（OC-01） =====================

    @PostMapping
    public Result<OnlineConsult> createConsult(
            @RequestBody CreateConsultReq req,
            HttpServletRequest request) {
        Long patientId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        OnlineConsult consult = onlineConsultService.createConsult(
                patientId, req.getDoctorId(), req.getDepartment(),
                req.getChiefComplaint(), req.getDescription());
        return Result.ok(consult);
    }

    // ===================== 3. 问诊列表（分页）（OC-02） =====================

    @GetMapping
    public Result<PageResult<OnlineConsult>> listConsults(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        PageResult<OnlineConsult> result = onlineConsultService.listConsults(
                userId, role.name(), page, size);
        return Result.ok(result);
    }

    // ===================== 4. 问诊详情（OC-03） =====================

    @GetMapping("/{id}")
    public Result<Object> getConsultDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(onlineConsultService.getConsultDetail(id, userId, role));
    }

    // ===================== 5. 发送消息（OC-04） =====================

    @PostMapping("/{id}/messages")
    public Result<Object> sendMessage(
            @PathVariable Long id,
            @RequestBody SendMessageReq req,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        Object res = onlineConsultService.sendMessage(
                userId, role, id, req.getContent(), req.getImageUrl());
        return Result.ok(res);
    }

    // ===================== 6. 消息列表（OC-11） =====================

    @GetMapping("/{id}/messages")
    public Result<List<ConsultMessage>> listMessages(@PathVariable Long id,
                                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(onlineConsultService.listMessages(id, userId, role));
    }

    // ===================== 7. 生成 AI 分析（OC-05，本期不关注） =====================

    @PostMapping("/{id}/ai-analysis")
    public Result<AiAnalysis> generateAiAnalysis(@PathVariable Long id) {
        return Result.ok(onlineConsultService.generateAiAnalysis(id));
    }

    // ===================== 8. 获取已生成的 AI 分析（OC-06，本期不关注） =====================

    @GetMapping("/{id}/ai-analysis")
    public Result<AiAnalysis> getAiAnalysis(@PathVariable Long id) {
        return Result.ok(onlineConsultService.getAiAnalysis(id));
    }

    // ===================== 9. 医生请求 AI 生成回复草稿（OC-07，本期不关注） =====================

    @PostMapping("/{id}/reply-draft")
    public Result<ConsultReply> generateReplyDraft(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long doctorId = (Long) request.getAttribute("currentUserId");
        return Result.ok(onlineConsultService.generateReplyDraft(id, doctorId));
    }

    // ===================== 10. 医生修改草稿（OC-08，本期不关注） =====================

    @PutMapping("/{id}/reply-draft")
    public Result<ConsultReply> updateReplyDraft(
            @PathVariable Long id,
            @RequestBody UpdateDraftReq req) {
        return Result.ok(onlineConsultService.updateReplyDraft(id, req.getAiDraft()));
    }

    // ===================== 11. 确认最终回复（OC-09） =====================

    @PostMapping("/{id}/confirm-reply")
    public Result<Void> confirmReply(
            @PathVariable Long id,
            @RequestBody ConfirmReplyReq req,
            HttpServletRequest request) {
        Long doctorId = (Long) request.getAttribute("currentUserId");
        onlineConsultService.confirmReply(id, doctorId, req.getFinalReply());
        return Result.ok();
    }

    // ===================== 12. 关闭问诊（OC-10） =====================

    @PostMapping("/{id}/close")
    public Result<Void> closeConsult(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        onlineConsultService.closeConsult(id, userId, role);
        return Result.ok();
    }

    // ===================== 13. 补充材料记录（OC-13） =====================

    @GetMapping("/{id}/supplement-records")
    public Result<List<ConsultSupplement>> listSupplementRecords(@PathVariable Long id,
                                                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(onlineConsultService.listSupplementRecords(id, userId, role));
    }

    // ===================== DTOs =====================

    /** 发起问诊请求体 */
    @Data
    public static class CreateConsultReq {
        private Long doctorId;
        private String department;
        private String chiefComplaint;
        private String description;
    }

    /** 发送消息请求体 */
    @Data
    public static class SendMessageReq {
        private String content;
        private String imageUrl;
    }

    /** 修改回复草稿请求体 */
    @Data
    public static class UpdateDraftReq {
        private String aiDraft;
    }

    /** 确认回复请求体 */
    @Data
    public static class ConfirmReplyReq {
        private String finalReply;
    }
}

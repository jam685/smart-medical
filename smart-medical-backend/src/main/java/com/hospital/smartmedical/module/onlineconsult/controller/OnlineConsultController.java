package com.hospital.smartmedical.module.onlineconsult.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.common.storage.FileStorageService;
import com.hospital.smartmedical.module.onlineconsult.entity.ConsultMessage;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import com.hospital.smartmedical.module.onlineconsult.service.OnlineConsultService;
import com.hospital.smartmedical.module.onlineconsult.vo.OnlineDoctorVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 线上面诊模块控制器（/online-consults）。
 *
 * <p>鉴权与角色在 AuthInterceptor 中统一完成，本控制器负责读取当前用户并转发到服务层。
 * V2.3 移除 OC-05~08（AI 分析/回复草稿，已迁往 /ai/*）与 OC-13（补充材料记录）。</p>
 */
@RestController
@RequestMapping("/online-consults")
@RequiredArgsConstructor
public class OnlineConsultController {

    private final OnlineConsultService onlineConsultService;
    private final FileStorageService fileStorageService;

    // ===================== 0. 通用聊天图片上传 =====================

    /**
     * 上传聊天图片（患者/医生均可）：仅保存文件，不写入 sm_report 等业务表。
     * 上传成功后前端以 IMAGE 消息发送（POST /online-consults/{id}/messages）。
     *
     * @param file 任意路径的图片文件（jpg/jpeg/png/webp/gif）
     * @return {imageUrl} 可访问的图片地址（含 context-path）
     */
    @PostMapping("/upload-image")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = fileStorageService.saveImage(file, "chat", "msg");
        Map<String, Object> data = new HashMap<>();
        data.put("imageUrl", imageUrl);
        return Result.ok(data);
    }

    // ===================== 1. 在线医生列表（OC-12） =====================

    /**
     * 查看在线医生列表（OC-12）。
     *
     * @param department 科室（可空）
     * @param keyword    医生姓名关键词（可空）
     * @return 在线医生脱敏列表
     */
    @GetMapping("/online-doctors")
    public Result<List<OnlineDoctorVO>> getOnlineDoctors(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword) {
        List<OnlineDoctorVO> doctors = onlineConsultService.getOnlineDoctors(department, keyword);
        return Result.ok(doctors);
    }

    // ===================== 1.1 我的挂号主治医生列表（问诊发起专用） =====================

    /**
     * 查看患者自己的「挂号主治医生」列表。
     *
     * <p>业务规则：患者只能对自己挂号成功（挂号状态非 CANCELLED）的主治医生发起在线问诊，
     * 本接口返回该患者所有有效挂号对应的医生（按医生去重），用于发起问诊时的医生选择。</p>
     *
     * @return 该患者的挂号主治医生列表（脱敏视图）
     */
    @GetMapping("/my-doctors")
    public Result<List<OnlineDoctorVO>> getMyDoctors(HttpServletRequest request) {
        Long patientId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(onlineConsultService.getMyDoctors(patientId));
    }

    // ===================== 2. 发起问诊（OC-01） =====================

    /**
     * 患者发起线上问诊（OC-01），主诉/描述作为首条 PATIENT 消息写入。
     */
    @PostMapping
    public Result<OnlineConsultRecord> createConsult(
            @RequestBody CreateConsultReq req,
            HttpServletRequest request) {
        Long patientId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.PATIENT) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        OnlineConsultRecord consult = onlineConsultService.createConsult(
                patientId, req.getDoctorId(), req.getDepartment(), req.getChiefComplaint());
        return Result.ok(consult);
    }

    // ===================== 3. 问诊列表（分页）（OC-02） =====================

    /**
     * 查看问诊列表（OC-02，按角色过滤分页）。
     */
    @GetMapping
    public Result<PageResult<Object>> listConsults(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        PageResult<Object> result = onlineConsultService.listConsults(
                userId, role.name(), page, size);
        return Result.ok(result);
    }

    // ===================== 4. 问诊详情（OC-03） =====================

    /**
     * 查看问诊详情（OC-03，合并医生/患者脱敏视图）。
     */
    @GetMapping("/{id}")
    public Result<Object> getConsultDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(onlineConsultService.getConsultDetail(id, userId, role));
    }

    // ===================== 5. 发送消息（OC-04） =====================

    /**
     * 患者发送消息（OC-04，补充描述或上传图片）。
     */
    @PostMapping("/{id}/messages")
    public Result<ConsultMessage> sendMessage(
            @PathVariable Long id,
            @RequestBody SendMessageReq req,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(onlineConsultService.sendMessage(
                userId, role, id, req.getContent(), req.getImageUrl()));
    }

    // ===================== 6. 消息列表（OC-11） =====================

    /**
     * 查看消息记录（OC-11，按时间升序）。
     */
    @GetMapping("/{id}/messages")
    public Result<List<ConsultMessage>> listMessages(@PathVariable Long id,
                                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(onlineConsultService.listMessages(id, userId, role));
    }

    // ===================== 7. 确认最终回复（OC-09） =====================

    /**
     * 医生确认并回复患者（OC-09），回复写入一条 DOCTOR 消息。
     */
    @PostMapping("/{id}/confirm-reply")
    public Result<Void> confirmReply(
            @PathVariable Long id,
            @RequestBody ConfirmReplyReq req,
            HttpServletRequest request) {
        Long doctorId = (Long) request.getAttribute("currentUserId");
        onlineConsultService.confirmReply(id, doctorId, req.getFinalReply());
        return Result.ok();
    }

    // ===================== 8. 关闭问诊（OC-10） =====================

    /**
     * 关闭问诊（OC-10）。
     */
    @PostMapping("/{id}/close")
    public Result<Void> closeConsult(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        onlineConsultService.closeConsult(id, userId, role);
        return Result.ok();
    }

    // ===================== DTOs =====================

    /** 发起问诊请求体 */
    @Data
    public static class CreateConsultReq {
        private Long doctorId;
        private String department;
        private String chiefComplaint;
    }

    /** 发送消息请求体 */
    @Data
    public static class SendMessageReq {
        private String content;
        private String imageUrl;
    }

    /** 确认回复请求体 */
    @Data
    public static class ConfirmReplyReq {
        private String finalReply;
    }
}
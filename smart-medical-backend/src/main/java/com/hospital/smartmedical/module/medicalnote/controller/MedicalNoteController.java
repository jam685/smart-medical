package com.hospital.smartmedical.module.medicalnote.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNote;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNoteVersion;
import com.hospital.smartmedical.module.medicalnote.service.MedicalNoteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/medical-notes")
@RequiredArgsConstructor
public class MedicalNoteController {

    private final MedicalNoteService medicalNoteService;

    // ===================== 1. 预览病历（AI 生成 NOTE-00） =====================

    @PostMapping("/preview")
    public Result<MedicalNote> previewNote(
            @RequestBody PreviewReq req,
            HttpServletRequest request) {
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.DOCTOR) {
            throw new BizException(com.hospital.smartmedical.common.result.ErrorCode.FORBIDDEN);
        }
        Long doctorId = (Long) request.getAttribute("currentUserId");
        return Result.ok(medicalNoteService.previewNote(req.getConsultId(), doctorId));
    }

    // ===================== 2. 病历列表（分页） =====================

    @GetMapping
    public Result<PageResult<MedicalNote>> listNotes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(medicalNoteService.listNotes(userId, role.name(), page, size));
    }

    // ===================== 3. 病历详情 =====================

    @GetMapping("/{id}")
    public Result<MedicalNote> getNote(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(medicalNoteService.getNote(id, userId, role.name()));
    }

    // ===================== 4. 修改病历 =====================

    @PutMapping("/{id}")
    public Result<MedicalNote> updateNote(
            @PathVariable Long id,
            @RequestBody UpdateReq req,
            HttpServletRequest request) {
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.DOCTOR) {
            throw new BizException(com.hospital.smartmedical.common.result.ErrorCode.FORBIDDEN);
        }
        Long doctorId = (Long) request.getAttribute("currentUserId");
        return Result.ok(medicalNoteService.updateNote(id, doctorId, req.getDraft()));
    }

    // ===================== 5. 签名 =====================

    @PostMapping("/{id}/sign")
    public Result<Void> signNote(
            @PathVariable Long id,
            HttpServletRequest request) {
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.DOCTOR) {
            throw new BizException(com.hospital.smartmedical.common.result.ErrorCode.FORBIDDEN);
        }
        Long doctorId = (Long) request.getAttribute("currentUserId");
        medicalNoteService.signNote(id, doctorId);
        return Result.ok();
    }

    // ===================== 6. 版本历史 =====================

    @GetMapping("/{id}/versions")
    public Result<List<MedicalNoteVersion>> listVersions(@PathVariable Long id) {
        return Result.ok(medicalNoteService.listVersions(id));
    }

    // ===================== DTOs =====================

    @Data
    public static class PreviewReq {
        private Long consultId;
    }

    @Data
    public static class UpdateReq {
        private String draft;
    }
}

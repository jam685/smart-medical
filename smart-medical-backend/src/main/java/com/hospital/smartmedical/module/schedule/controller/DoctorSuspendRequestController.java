package com.hospital.smartmedical.module.schedule.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.schedule.service.SuspendRequestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 医生端停诊申请接口。
 */
@RestController
@RequestMapping("/doctors/me/suspend-requests")
@RequiredArgsConstructor
public class DoctorSuspendRequestController {

    private final SuspendRequestService suspendRequestService;

    /** 我的停诊申请（最近 20 条） */
    @GetMapping
    public Result<List<Map<String, Object>>> mine(HttpServletRequest request) {
        return Result.ok(suspendRequestService.listByDoctor(requireDoctor(request)));
    }

    /** 提交停诊申请：{slotIds:[...], reason} */
    @PostMapping
    public Result<Map<String, Object>> submit(@RequestBody SubmitReq body, HttpServletRequest request) {
        Long doctorId = requireDoctor(request);
        if (body.getSlotIds() == null || body.getSlotIds().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择要停诊的时段");
        }
        return Result.ok(suspendRequestService.submit(doctorId, body.getSlotIds(), body.getReason()));
    }

    private Long requireDoctor(HttpServletRequest request) {
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.DOCTOR) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return (Long) request.getAttribute("currentUserId");
    }

    @Data
    public static class SubmitReq {
        private List<Long> slotIds;
        private String reason;
    }
}

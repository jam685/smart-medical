package com.hospital.smartmedical.module.schedule.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.schedule.service.SuspendRequestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端停诊审批接口（/admin 前缀已被拦截器限定为管理员）。
 */
@RestController
@RequestMapping("/admin/suspend-requests")
@RequiredArgsConstructor
public class AdminSuspendRequestController {

    private final SuspendRequestService suspendRequestService;

    /** 申请列表（status 可空=全部，pending=PENDING） */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        return Result.ok(suspendRequestService.listByStatus(status));
    }

    /** 待审批数量（管理端角标） */
    @GetMapping("/pending-count")
    public Result<Map<String, Object>> pendingCount() {
        return Result.ok(Map.of("count", suspendRequestService.pendingCount()));
    }

    /** 审批：{approve:true/false, note}；通过时会执行退号停诊 */
    @PutMapping("/{id}/review")
    public Result<Map<String, Object>> review(@PathVariable Long id,
                                              @RequestBody ReviewReq body,
                                              HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("currentUserId");
        return Result.ok(suspendRequestService.review(id, adminId,
                body.getApprove() == null || body.getApprove(), body.getNote()));
    }

    @Data
    public static class ReviewReq {
        private Boolean approve;
        private String note;
    }
}

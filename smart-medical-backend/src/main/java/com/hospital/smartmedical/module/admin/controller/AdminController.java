package com.hospital.smartmedical.module.admin.controller;

import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.admin.dto.AdminUserRequest;
import com.hospital.smartmedical.module.admin.entity.Admin;
import com.hospital.smartmedical.module.admin.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String employeeNo = body.get("employeeNo");
        String phone = body.get("phone");
        String idCard = body.get("idCard");
        if (employeeNo == null && phone == null && idCard == null) {
            return Result.fail(400, "请提供 employeeNo / phone / idCard 之一");
        }
        return Result.ok(adminService.login(employeeNo, phone, idCard));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        adminService.logout();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Admin a = adminService.getById(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", a.getId());
        result.put("employeeNo", a.getEmployeeNo());
        result.put("realName", a.getRealName());
        result.put("phone", a.getPhone());
        result.put("idCard", a.getIdCard());
        result.put("status", a.getStatus());
        result.put("role", "ADMIN");
        return Result.ok(result);
    }

    @GetMapping("/users")
    public Result<PageResult<?>> users(@RequestParam String role,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.getUsersByRole(role, page, size, keyword));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id,
                                          @RequestParam String role,
                                          @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) {
            return Result.fail(400, "缺少 status 参数");
        }
        adminService.updateUserStatus(role, id, status);
        return Result.ok();
    }

    @PostMapping("/users")
    public Result<Map<String, Object>> createUser(@RequestParam String role,
                                                  @RequestBody AdminUserRequest req,
                                                  HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("currentUserId");
        return Result.ok(adminService.createUser(role, req, operatorId));
    }

    @PutMapping("/users/{id}")
    public Result<Map<String, Object>> updateUser(@PathVariable Long id,
                                                  @RequestParam String role,
                                                  @RequestBody AdminUserRequest req,
                                                  HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("currentUserId");
        return Result.ok(adminService.updateUser(role, id, req, operatorId));
    }

    @GetMapping("/roles")
    public Result<List<String>> roles() {
        return Result.ok(adminService.getRoles());
    }

    @GetMapping("/audit-logs")
    public Result<PageResult<?>> auditLogs(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(required = false) String action) {
        return Result.ok(adminService.getAuditLogs(page, size, role, action));
    }

    /**
     * 审核一条审计日志（通过/驳回）
     * @param id   审计日志ID
     * @param body 请求体，含 decision（APPROVED 通过 / REJECTED 驳回，驳回自动禁用操作人账号）
     * @return 空结果
     */
    @PutMapping("/audit-logs/{id}/review")
    public Result<Void> reviewAuditLog(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String decision = body.get("decision");
        if (decision == null || decision.trim().isEmpty()) {
            return Result.fail(400, "缺少 decision 参数");
        }
        adminService.reviewAuditLog(id, decision);
        return Result.ok();
    }

    @GetMapping("/model-configs")
    public Result<Object> modelConfigs() {
        return Result.ok(adminService.getAllModelConfigs());
    }

    @GetMapping("/data-quality")
    public Result<Object> dataQuality() {
        return Result.ok(adminService.getDataQuality());
    }
}

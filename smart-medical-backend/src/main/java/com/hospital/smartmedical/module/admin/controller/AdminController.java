package com.hospital.smartmedical.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.admin.dto.AdminUserRequest;
import com.hospital.smartmedical.module.admin.entity.Admin;
import com.hospital.smartmedical.module.admin.entity.ModelConfig;
import com.hospital.smartmedical.module.admin.service.AdminService;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import com.hospital.smartmedical.module.onlineconsult.mapper.ConsultMessageMapper;
import com.hospital.smartmedical.module.onlineconsult.mapper.OnlineConsultRecordMapper;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final OnlineConsultRecordMapper consultMapper;
    private final ConsultMessageMapper consultMessageMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;

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

    /**
     * 模型配置列表（sm_model_config 表全量，前端表格直接映射字段）
     */
    @GetMapping("/model-configs")
    public Result<Object> modelConfigs() {
        return Result.ok(adminService.getAllModelConfigs());
    }

    /**
     * 当前生效的聊天模型（含配置来源：数据库 / 配置文件），用于页面顶部「生效中」展示
     */
    @GetMapping("/model-configs/active")
    public Result<Map<String, Object>> activeModelConfig() {
        return Result.ok(adminService.getActiveModelInfo());
    }

    /**
     * 新增模型配置（保存后热更新生效）
     */
    @PostMapping("/model-configs")
    public Result<ModelConfig> createModelConfig(@RequestBody ModelConfig config) {
        return Result.ok(adminService.createModelConfig(config));
    }

    /**
     * 修改模型配置（保存后热更新生效）
     */
    @PutMapping("/model-configs/{id}")
    public Result<ModelConfig> updateModelConfig(@PathVariable Long id, @RequestBody ModelConfig config) {
        return Result.ok(adminService.updateModelConfig(id, config));
    }

    /**
     * 删除模型配置（删除后热更新生效）
     */
    @DeleteMapping("/model-configs/{id}")
    public Result<Void> deleteModelConfig(@PathVariable Long id) {
        adminService.deleteModelConfig(id);
        return Result.ok();
    }

    @GetMapping("/data-quality")
    public Result<Object> dataQuality() {
        return Result.ok(adminService.getDataQuality());
    }

    @PostMapping("/data-quality/run")
    public Result<Object> runDataQualityCheck() {
        return Result.ok(adminService.runDataQualityCheck());
    }

    // ===================== 问诊监控（管理员视角） =====================

    /**
     * 管理员查看问诊列表：全局视角，按状态/科室筛选 + 分页。
     * 返回 patientName / doctorName 已 join，前端直接展示。
     */
    @GetMapping("/online-consults")
    public Result<PageResult<Map<String, Object>>> listAdminConsults(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword) {

        QueryWrapper<OnlineConsultRecord> qw = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            qw.eq("status", status);
        }
        if (department != null && !department.isEmpty()) {
            qw.eq("department", department);
        }
        qw.orderByDesc("id");

        Page<OnlineConsultRecord> pageParam = new Page<>(page, size);
        Page<OnlineConsultRecord> result = consultMapper.selectPage(pageParam, qw);

        List<Map<String, Object>> records = result.getRecords().stream().map(rec -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rec.getId());
            row.put("patientId", rec.getPatientId());
            row.put("doctorId", rec.getDoctorId());
            row.put("department", rec.getDepartment());
            row.put("status", rec.getStatus());
            row.put("startTime", rec.getStartTime());
            row.put("endTime", rec.getEndTime());

            // join 患者名
            var p = patientMapper.selectById(rec.getPatientId());
            row.put("patientName", p != null ? p.getRealName() : "?");
            // join 医生名
            var d = doctorMapper.selectById(rec.getDoctorId());
            row.put("doctorName", d != null ? d.getRealName() : "?");

            // 消息数（快速统计）
            Long msgCount = consultMessageMapper.selectCount(
                    new QueryWrapper<com.hospital.smartmedical.module.onlineconsult.entity.ConsultMessage>()
                            .eq("consult_id", rec.getId()));
            row.put("messageCount", msgCount);

            // 问诊时长：REPLIED 取 endTime，其他状态取 startTime→现在
            if (rec.getStartTime() != null) {
                var end = rec.getEndTime();
                if (end == null && "REPLIED".equals(rec.getStatus())) {
                    // 已回复但没写 endTime，用 created_at 兜底
                    end = rec.getCreatedAt();
                }
                if (end == null) {
                    end = java.time.LocalDateTime.now();
                }
                long minutes = Duration.between(rec.getStartTime(), end).toMinutes();
                row.put("durationMinutes", minutes);
            } else {
                row.put("durationMinutes", 0);
            }
            return row;
        }).collect(Collectors.toList());

        PageResult<Map<String, Object>> pr = new PageResult<>(
                result.getTotal(), records, page, size);
        return Result.ok(pr);
    }

    /**
     * 管理员查看问诊统计卡片：总数 / 进行中 / 已回复 / 已关闭 / 今日数 / 慢问诊数。
     */
    @GetMapping("/online-consults/stats")
    public Result<Map<String, Object>> adminConsultStats() {
        Map<String, Object> stats = new HashMap<>();

        Long total = consultMapper.selectCount(null);
        Long pending = consultMapper.selectCount(new QueryWrapper<OnlineConsultRecord>().eq("status", "PENDING"));
        Long replied = consultMapper.selectCount(new QueryWrapper<OnlineConsultRecord>().eq("status", "REPLIED"));
        Long closed = consultMapper.selectCount(new QueryWrapper<OnlineConsultRecord>().eq("status", "CLOSED"));

        // 今日问诊数
        var today = java.time.LocalDate.now();
        Long todayCount = consultMapper.selectCount(
                new QueryWrapper<OnlineConsultRecord>().ge("DATE(start_time)", today.toString()));

        // 慢问诊：REPLIED 但 durationMinutes > 15 分钟
        List<OnlineConsultRecord> allReplied = consultMapper.selectList(
                new QueryWrapper<OnlineConsultRecord>().eq("status", "REPLIED"));
        long slowCount = allReplied.stream()
                .filter(r -> r.getStartTime() != null)
                .filter(r -> {
                    var end = r.getEndTime() != null ? r.getEndTime() : java.time.LocalDateTime.now();
                    return Duration.between(r.getStartTime(), end).toMinutes() > 15;
                }).count();

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("replied", replied);
        stats.put("closed", closed);
        stats.put("todayCount", todayCount);
        stats.put("slowCount", slowCount);

        return Result.ok(stats);
    }
}

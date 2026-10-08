package com.hospital.smartmedical.module.visit.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.visit.service.VisitService;
import com.hospital.smartmedical.module.visit.vo.VisitVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 就诊进度业务控制器
 * 路由前缀：/visits（完整路径含 context-path：/api/smart-medical/v1/visits）
 * 提供：就诊进度分页列表、详情、状态推进 3 个接口。
 * 权限口径：患者只能查本人就诊记录、不能推进状态；医生/管理员可推进状态。
 */
@RestController
@RequestMapping("/visits")
@RequiredArgsConstructor
public class VisitController {

    /** 就诊进度业务服务 */
    private final VisitService visitService;

    /**
     * 分页查询就诊进度列表
     *
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10（前端列表页一次取足量）
     * @param status  状态过滤（可选）：WAITING 候诊 / CALLING 叫号 / IN_PROGRESS 就诊中 / FINISHED 已完成
     * @param request HTTP 请求（拦截器注入当前用户 ID 与角色，用于数据隔离）
     * @return VisitVO 分页结果（含医生/患者姓名、进度节点、挂号时间）
     */
    @GetMapping
    public Result<PageResult<VisitVO>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String status,
                                           HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(visitService.listVisits(userId, role, page, size, status));
    }

    /**
     * 查询就诊进度详情
     *
     * @param id      就诊进度 ID
     * @param request HTTP 请求（注入当前用户，用于归属校验）
     * @return VisitVO（含完整进度节点）
     */
    @GetMapping("/{id}")
    public Result<VisitVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(visitService.getVisitDetail(id, userId, role));
    }

    /**
     * 推进就诊状态（仅医生/管理员可操作；患者无权推进流程）
     * 业务规则：WAITING→CALLING 叫号 / CALLING→IN_PROGRESS 接诊 / IN_PROGRESS→FINISHED 完成；
     *           每次推进后端自动追加 progress_nodes 节点。
     *
     * @param id      就诊进度 ID
     * @param body    请求体：{status: 目标状态码}
     * @param request HTTP 请求
     * @return 空结果，HTTP 成功即表示推进完成
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestBody UpdateStatusRequest body,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        visitService.updateStatus(id, body.getStatus(), userId, role);
        return Result.ok();
    }

    /**
     * 状态推进请求体
     */
    @Data
    public static class UpdateStatusRequest {
        /** 目标状态码：WAITING/CALLING/IN_PROGRESS/FINISHED */
        private String status;
    }
}

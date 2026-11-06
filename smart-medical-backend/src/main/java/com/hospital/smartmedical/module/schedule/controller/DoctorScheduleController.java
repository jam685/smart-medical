package com.hospital.smartmedical.module.schedule.controller;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.service.ScheduleService;
import com.hospital.smartmedical.module.schedule.vo.ScheduleDayVO;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 医生端排班接口：查看我的排班、停诊/恢复、加号/减号。
 *
 * <p>路径挂在 /doctors/me 下，但 AuthInterceptor 对 /doctors 不设角色规则（患者也要访问 /doctors/login），
 * 因此角色校验必须在本控制器内显式完成。</p>
 */
@RestController
@RequestMapping("/doctors/me/schedules")
@RequiredArgsConstructor
public class DoctorScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 我的排班（本周/未来 N 天）。
     *
     * @param startDate 起始日期 yyyy-MM-dd（可空，默认今天）
     * @param days      天数（默认 7，最大 30）
     * @param request   HTTP 请求（取当前医生身份）
     * @return 每天一条排班（含每个时段的已约/总号数）
     */
    @GetMapping
    public Result<List<ScheduleDayVO>> mySchedules(@RequestParam(required = false) String startDate,
                                                   @RequestParam(defaultValue = "7") int days,
                                                   HttpServletRequest request) {
        Long doctorId = requireDoctor(request);
        LocalDate start = null;
        if (startDate != null && !startDate.isBlank()) {
            try {
                start = LocalDate.parse(startDate);
            } catch (DateTimeParseException e) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "日期格式应为 yyyy-MM-dd");
            }
        }
        return Result.ok(scheduleService.listDays(doctorId, start, days));
    }

    /**
     * 停诊 / 恢复出诊。
     *
     * @param id      号源 ID
     * @param body    {suspended:true/false}
     * @param request HTTP 请求
     * @return 变更后的号源（total/booked/status）
     */
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> changeStatus(@PathVariable Long id,
                                                    @RequestBody StatusReq body,
                                                    HttpServletRequest request) {
        Long doctorId = requireDoctor(request);
        if (body.getSuspended() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少 suspended 参数");
        }
        ScheduleSlot slot = scheduleService.suspend(id, doctorId, body.getSuspended());
        return Result.ok(Map.of(
                "id", slot.getId(),
                "status", slot.getStatus(),
                "total", slot.getTotalCount(),
                "booked", slot.getBookedCount()));
    }

    /**
     * 加号 / 减号（调整该时段总号数）。
     *
     * @param id      号源 ID
     * @param body    {totalCount:n}
     * @param request HTTP 请求
     * @return 变更后的号源
     */
    @PutMapping("/{id}/capacity")
    public Result<Map<String, Object>> changeCapacity(@PathVariable Long id,
                                                      @RequestBody CapacityReq body,
                                                      HttpServletRequest request) {
        Long doctorId = requireDoctor(request);
        if (body.getTotalCount() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少 totalCount 参数");
        }
        ScheduleSlot slot = scheduleService.updateCapacity(id, doctorId, body.getTotalCount());
        return Result.ok(Map.of(
                "id", slot.getId(),
                "total", slot.getTotalCount(),
                "booked", slot.getBookedCount()));
    }

    /** 角色守卫：仅医生可访问自己的排班 */
    private Long requireDoctor(HttpServletRequest request) {
        Role role = (Role) request.getAttribute("currentUserRole");
        if (role != Role.DOCTOR) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return (Long) request.getAttribute("currentUserId");
    }

    /** 停诊请求体 */
    @Data
    public static class StatusReq {
        private Boolean suspended;
    }

    /** 号数调整请求体 */
    @Data
    public static class CapacityReq {
        private Integer totalCount;
    }
}

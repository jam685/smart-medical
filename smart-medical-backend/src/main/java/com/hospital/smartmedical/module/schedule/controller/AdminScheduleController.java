package com.hospital.smartmedical.module.schedule.controller;

import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.service.ScheduleService;
import com.hospital.smartmedical.module.schedule.vo.ScheduleDayVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleImpactVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleSlotVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * 管理端排班与号源接口（/admin 前缀已被 AuthInterceptor 限定为管理员）。
 *
 * <p>提供：科室/医生筛选、按医生看排班、批量生成、停诊影响面预览、批量停诊退号、调整号数。</p>
 */
@RestController
@RequestMapping("/admin/schedules")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 医生列表（含未上线医生，排班不要求在线）。
     *
     * @param department 科室名（可空）
     * @return [{id, realName, department, title, onlineStatus}]
     */
    @GetMapping("/doctors")
    public Result<List<Map<String, Object>>> doctors(@RequestParam(required = false) String department) {
        return Result.ok(scheduleService.listDoctors(department));
    }

    /**
     * 管理端排班视图（某医生未来 N 天，含每个时段的已约数）。
     *
     * @param doctorId  医生 ID
     * @param startDate 起始日期 yyyy-MM-dd（可空，默认今天）
     * @param days      天数（默认 7）
     * @return 每天一条排班
     */
    @GetMapping("/board")
    public Result<List<ScheduleDayVO>> board(@RequestParam Long doctorId,
                                             @RequestParam(required = false) String startDate,
                                             @RequestParam(defaultValue = "7") int days) {
        return Result.ok(scheduleService.listDays(doctorId, parseDate(startDate), days));
    }

    /**
     * 停诊影响面预览：停这些时段会影响谁、可改约到哪里。
     *
     * @param slotIds 号源 ID（逗号分隔，如 1,2,3；也支持重复参数）
     * @return 影响面（患者清单 + 改约建议）
     */
    @GetMapping("/impact")
    public Result<ScheduleImpactVO> impact(@RequestParam List<Long> slotIds) {
        return Result.ok(scheduleService.impact(slotIds));
    }

    /**
     * 批量停诊 / 恢复出诊。
     *
     * @param body {slotIds:[...], suspended:true/false, force:true/false}
     * @return {changed, cancelled}
     */
    @PutMapping("/status")
    public Result<Map<String, Object>> changeStatus(@RequestBody StatusReq body) {
        boolean suspended = body.getSuspended() == null || body.getSuspended();
        boolean force = Boolean.TRUE.equals(body.getForce());
        return Result.ok(scheduleService.changeStatusByAdmin(body.getSlotIds(), suspended, force));
    }

    /**
     * 一键改约并停诊：把受影响患者整体迁到目标时段，再把源时段停掉（同一事务）。
     *
     * @param body {fromSlotIds:[...], toSlotId, appointmentIds:[...]（可空=全部）}
     * @return {rescheduled, suspended, toSlotId, toDate, toStartTime, toEndTime}
     */
    @PostMapping("/reschedule")
    public Result<Map<String, Object>> reschedule(@RequestBody RescheduleReq body) {
        List<Long> targets = body.getToSlotIds();
        if ((targets == null || targets.isEmpty()) && body.getToSlotId() != null) {
            targets = List.of(body.getToSlotId());
        }
        return Result.ok(scheduleService.reschedule(
                body.getFromSlotIds(), targets, body.getAppointmentIds()));
    }

    /**
     * 跨科室候选时段（改约目标选择器）。
     *
     * @param department    科室（可空=全院）
     * @param startDate     起始日期
     * @param days          天数
     * @param excludeSlotIds 排除的号源（待停诊时段）
     * @return 可约时段（含医生姓名）
     */
    @GetMapping("/alternatives")
    public Result<List<ScheduleSlotVO>> alternatives(@RequestParam(required = false) String department,
                                                     @RequestParam(required = false) String startDate,
                                                     @RequestParam(defaultValue = "7") int days,
                                                     @RequestParam(required = false) List<Long> excludeSlotIds) {
        return Result.ok(scheduleService.listAlternatives(
                department, parseDate(startDate), days, excludeSlotIds));
    }

    /**
     * 调整某时段号数。
     *
     * @param id   号源 ID
     * @param body {totalCount:n}
     * @return 变更后的号源
     */
    @PutMapping("/{id}/capacity")
    public Result<Map<String, Object>> capacity(@PathVariable Long id, @RequestBody CapacityReq body) {
        if (body.getTotalCount() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少 totalCount 参数");
        }
        ScheduleSlot slot = scheduleService.updateCapacityByAdmin(id, body.getTotalCount());
        return Result.ok(Map.of("id", slot.getId(), "total", slot.getTotalCount(), "booked", slot.getBookedCount()));
    }

    /**
     * 批量生成号源：按医生或按科室，默认从今天起 7 天、每时段 5 个号。
     *
     * @param body 生成参数
     * @return {created: 新建行数}
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody GenerateReq body) {
        LocalDate start = parseDate(body.getStartDate());
        if (start == null) {
            start = LocalDate.now();
        }
        int days = body.getDays() == null ? 7 : body.getDays();
        int capacity = body.getCapacity() == null ? 5 : body.getCapacity();
        int created;
        if (body.getDoctorId() != null) {
            created = scheduleService.generate(body.getDoctorId(), start, days, capacity);
        } else if (body.getDepartment() != null && !body.getDepartment().isBlank()) {
            created = scheduleService.generateByDepartment(body.getDepartment(), start, days, capacity);
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请指定 doctorId 或 department");
        }
        return Result.ok(Map.of("created", created, "startDate", start.toString(), "days", days, "capacity", capacity));
    }

    /** 解析 yyyy-MM-dd；空返回 null，格式错误转参数错误 */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "日期格式应为 yyyy-MM-dd");
        }
    }

    /** 停诊/恢复请求体 */
    @Data
    public static class StatusReq {
        private List<Long> slotIds;
        private Boolean suspended;
        private Boolean force;
    }

    /** 号数调整请求体 */
    @Data
    public static class CapacityReq {
        private Integer totalCount;
    }

    /** 一键改约请求体 */
    @Data
    public static class RescheduleReq {
        private List<Long> fromSlotIds;
        /** 单个目标（兼容旧调用） */
        private Long toSlotId;
        /** 多个目标（按顺序装人） */
        private List<Long> toSlotIds;
        private List<Long> appointmentIds;
    }

    /** 号源生成请求体 */
    @Data
    public static class GenerateReq {
        private Long doctorId;
        private String department;
        private String startDate;
        private Integer days;
        private Integer capacity;
    }
}

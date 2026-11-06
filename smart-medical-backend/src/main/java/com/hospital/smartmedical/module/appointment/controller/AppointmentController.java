package com.hospital.smartmedical.module.appointment.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.service.AppointmentService;
import com.hospital.smartmedical.module.appointment.vo.AppointmentVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 挂号业务控制器
 * 路由前缀：/appointments（完整路径含 context-path：/api/smart-medical/v1/appointments）
 * 提供：可约医生列表、创建挂号、挂号分页列表、挂号详情、取消挂号 5 个接口。
 */
@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    /** 挂号业务服务 */
    private final AppointmentService appointmentService;

    /**
     * 查询可挂号医生（供新建挂号页第二步使用）
     *
     * @param department 科室名（可选）
     * @param keyword    姓名/工号关键词（可选）
     * @return 在线医生列表（含职称、挂号费）
     */
    @GetMapping("/doctors")
    public Result<List<Map<String, Object>>> listDoctors(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword) {
        return Result.ok(appointmentService.listAvailableDoctors(department, keyword));
    }

    /**
     * 创建挂号
     *
     * @param body    请求体：医生 ID、科室、就诊时间
     * @param request HTTP 请求（拦截器注入当前用户 ID 与角色）
     * @return 新建的挂号实体（含自增主键）
     */
    @PostMapping
    public Result<Appointment> create(@RequestBody CreateAppointmentRequest body,
                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        // 选号页/AI 挂号统一传 scheduleId 走号源扣减；未传时兼容旧的自由时间下单
        Appointment appt = body.getScheduleId() != null
                ? appointmentService.createAppointmentBySlot(userId, role, body.getScheduleId())
                : appointmentService.createAppointment(userId, role,
                        body.getDoctorId(), body.getDepartment(), body.getAppointmentTime());
        return Result.ok(appt);
    }

    /**
     * 分页查询挂号列表
     *
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10（前端列表页传大值一次取全量）
     * @param status  状态过滤（可选）：PENDING/CONFIRMED/FINISHED/CANCELLED
     * @param request HTTP 请求（拦截器注入当前用户 ID 与角色，用于数据隔离）
     * @return 挂号 VO 分页结果
     */
    @GetMapping
    public Result<PageResult<AppointmentVO>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String status,
                                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(appointmentService.listAppointments(userId, role, page, size, status));
    }

    /**
     * 查询挂号详情
     *
     * @param id      挂号单 ID
     * @param request HTTP 请求（注入当前用户，用于归属校验）
     * @return 挂号 VO
     */
    @GetMapping("/{id}")
    public Result<AppointmentVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(appointmentService.getAppointmentDetail(id, userId, role));
    }

    /**
     * 患者自助改约（同医生换时段，原子换号）
     *
     * @param id      原挂号单 ID
     * @param body    {toSlotId}
     * @param request HTTP 请求（注入当前患者）
     * @return 新挂号单
     */
    @PostMapping("/{id}/reschedule")
    public Result<Appointment> reschedule(@PathVariable Long id,
                                          @RequestBody RescheduleRequest body,
                                          HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        return Result.ok(appointmentService.rescheduleAppointment(id, userId, role, body.getToSlotId()));
    }

    /**
     * 取消挂号（状态置为 CANCELLED，仅患者本人可操作）
     *
     * @param id      挂号单 ID
     * @param request HTTP 请求（注入当前用户）
     * @return 空结果，HTTP 成功即表示取消完成
     */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Role role = (Role) request.getAttribute("currentUserRole");
        appointmentService.cancelAppointment(id, userId, role);
        return Result.ok();
    }

    /**
     * 改约请求体
     */
    @Data
    public static class RescheduleRequest {
        /** 目标号源 ID（必须与原单同一医生） */
        private Long toSlotId;
    }

    /**
     * 创建挂号请求体
     */
    @Data
    public static class CreateAppointmentRequest {
        /** 号源 ID（sm_schedule_slot.id；传了它就走号源扣减，优先于 appointmentTime） */
        private Long scheduleId;
        /** 选择的医生 ID（sm_doctor.id，未传 scheduleId 时必填） */
        private Long doctorId;
        /** 就诊科室名（可空，空则后端取医生所属科室） */
        private String department;
        /** 就诊时间，格式 yyyy-MM-dd HH:mm:ss（未传 scheduleId 时必填） */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime appointmentTime;
    }
}

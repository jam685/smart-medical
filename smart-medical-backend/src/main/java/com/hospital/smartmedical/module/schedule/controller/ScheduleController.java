package com.hospital.smartmedical.module.schedule.controller;

import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.schedule.service.ScheduleService;
import com.hospital.smartmedical.module.schedule.vo.ScheduleDayVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleSlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 患者端号源查询接口。
 *
 * <p>路由前缀 /schedules；任意已登录角色可读（医生端排班另走 /doctors/me/schedules）。</p>
 */
@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 查询某医生某天的号源时段（选号页第三步）。
     *
     * @param doctorId 医生 ID
     * @param date     日期 yyyy-MM-dd
     * @return 号源列表（含剩余号数、是否可约）
     */
    @GetMapping("/slots")
    public Result<List<ScheduleSlotVO>> slots(@RequestParam Long doctorId,
                                              @RequestParam String date) {
        return Result.ok(scheduleService.listSlots(doctorId, parseDate(date)));
    }

    /**
     * 查询某医生未来若干天的排班概览（日期横条上标"有号/约满"）。
     *
     * @param doctorId  医生 ID
     * @param startDate 起始日期 yyyy-MM-dd（可空，默认今天）
     * @param days      天数（默认 7，最大 30）
     * @return 按日期升序的排班
     */
    @GetMapping("/days")
    public Result<List<ScheduleDayVO>> days(@RequestParam Long doctorId,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(scheduleService.listDays(doctorId, startDate == null ? null : parseDate(startDate), days));
    }

    /** 解析 yyyy-MM-dd，格式错误统一转成参数错误 */
    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "日期格式应为 yyyy-MM-dd");
        }
    }
}

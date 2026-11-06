package com.hospital.smartmedical.module.schedule.vo;

import lombok.Data;

import java.util.List;

/**
 * 按天聚合的排班视图（患者选日期 / 医生看本周排班共用）。
 */
@Data
public class ScheduleDayVO {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 星期：周一…周日 */
    private String weekday;

    /** 当天总剩余号数 */
    private Integer totalRemain;

    /** 当天号源时段数 */
    private Integer slotCount;

    /** 当天已挂总数 */
    private Integer bookedCount;

    /** 是否还有可约号 */
    private Boolean hasBookable;

    /** 时段明细（医生端必用；患者端可只用汇总字段） */
    private List<ScheduleSlotVO> slots;
}

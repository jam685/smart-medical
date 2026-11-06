package com.hospital.smartmedical.module.schedule.vo;

import lombok.Data;

import java.util.List;

/**
 * 停诊影响面预览：停这几个时段会影响到谁、可以改约到哪里。
 */
@Data
public class ScheduleImpactVO {

    /** 本次待停诊的时段数 */
    private Integer slotCount;

    /** 这些时段当前已约总数 */
    private Integer bookedCount;

    /** 会被退号的有效挂号数 */
    private Integer affectedCount;

    /** 是否包含已开始的时段（已开始不允许停诊） */
    private Boolean hasStarted;

    /** 受影响患者清单 */
    private List<ImpactedAppointmentVO> affected;

    /** 改约建议：同医生后续仍可约的时段（最多 5 个） */
    private List<ScheduleSlotVO> alternatives;
}

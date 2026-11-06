package com.hospital.smartmedical.module.schedule.vo;

import lombok.Data;

/**
 * 停诊影响面中的单个患者（一条有效挂号）。
 */
@Data
public class ImpactedAppointmentVO {

    /** 挂号单 ID */
    private Long appointmentId;

    /** 挂号单号（GH 开头，与患者端一致） */
    private String orderNo;

    private Long patientId;

    /** 患者姓名 */
    private String patientName;

    /** 患者手机号（用于电话通知） */
    private String patientPhone;

    /** 就诊时间 yyyy-MM-dd HH:mm */
    private String appointmentTime;

    /** 所属号源时段 08:00-08:30 */
    private String slotTime;

    private Long slotId;

    /** PENDING / CONFIRMED */
    private String status;
}

package com.hospital.smartmedical.module.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_appointment")
public class Appointment implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long patientId;

    private Long doctorId;

    private String department;

    /** 号源 ID（sm_schedule_slot.id；历史数据为 null） */
    private Long scheduleId;

    /** 时段内序号，即叫号顺序（1..N；历史数据为 null） */
    private Integer queueNo;

    /** 就诊时段开始时间（与 appointmentTime 一致，冗余便于统计；历史数据为 null） */
    private LocalDateTime slotStart;

    private LocalDateTime appointmentTime;

    private String status;

    /** 取消原因：CANCEL 患者取消 / SUSPEND 医生停诊退号 / RESCHEDULE 改约（仅 CANCELLED 时有值） */
    private String cancelReason;

    /** 改约来源：本单由哪张挂号单改约而来（仅改约生成的新单有值） */
    private Long rescheduledFrom;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

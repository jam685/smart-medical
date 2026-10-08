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

    private LocalDateTime appointmentTime;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

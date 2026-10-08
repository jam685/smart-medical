package com.hospital.smartmedical.module.visit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_visit")
public class Visit implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;

    private Long patientId;

    private Long doctorId;

    private String department;

    private String status;

    private String progressNodes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

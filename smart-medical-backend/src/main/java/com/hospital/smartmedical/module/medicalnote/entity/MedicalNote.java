package com.hospital.smartmedical.module.medicalnote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_medical_note")
public class MedicalNote implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long consultId;

    private Long patientId;

    private Long doctorId;

    private String draft;

    private String status;

    private Integer modifiedCount;

    private LocalDateTime signedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

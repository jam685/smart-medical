package com.hospital.smartmedical.module.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 处方主表实体（sm_prescription）
 * 一张处方对应一次就诊，包含诊断、医嘱与若干药品明细。
 */
@Data
@TableName("sm_prescription")
public class Prescription implements Serializable {

    /** 处方主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联就诊进度 ID（sm_visit.id） */
    private Long visitId;

    /** 关联挂号单 ID（sm_appointment.id） */
    private Long appointmentId;

    /** 患者 ID */
    private Long patientId;

    /** 开方医生 ID */
    private Long doctorId;

    /** 开方科室 */
    private String department;

    /** 诊断 */
    private String diagnosis;

    /** 医嘱 */
    private String advice;

    /** 状态：DRAFT 草稿 / ISSUED 已开具 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

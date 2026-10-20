package com.hospital.smartmedical.module.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 处方实体（sm_prescription，V1.3 与明细表合并）。
 *
 * <p>一份处方通过 prescription_no 分组：一次开具的多个药品明细对应多行记录，它们共享
 * patient/doctor/diagnosis/advice 等表头字段，仅药品明细字段（drugName/specification/dosage
 * 等）不同。本实体一个实例即一行记录（一个药品明细）。</p>
 */
@Data
@TableName("sm_prescription")
public class Prescription implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 处方编号（分组标识，一份处方多条明细共用同一编号） */
    private String prescriptionNo;

    /** 关联就诊进度 ID（sm_visit.id） */
    private Long visitId;

    /** 关联挂号单 ID（sm_appointment.id，可空） */
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

    /** 药品名称 */
    private String drugName;

    /** 规格 */
    private String specification;

    /** 单次剂量 */
    private String dosage;

    /** 频次（如每日3次） */
    private String frequency;

    /** 用药天数 */
    private Integer days;

    /** 数量 */
    private String quantity;

    /** 用法（口服/外用/静滴等） */
    private String usageMethod;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
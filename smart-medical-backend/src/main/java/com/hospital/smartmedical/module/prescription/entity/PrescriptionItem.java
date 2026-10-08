package com.hospital.smartmedical.module.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 处方药品明细实体（sm_prescription_item）
 * 一条明细对应处方中的一种药品及其用法用量。
 */
@Data
@TableName("sm_prescription_item")
public class PrescriptionItem implements Serializable {

    /** 明细主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属处方 ID（sm_prescription.id） */
    private Long prescriptionId;

    /** 药品名称 */
    private String drugName;

    /** 规格 */
    private String specification;

    /** 单次剂量 */
    private String dosage;

    /** 频次（如每日 3 次） */
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
}

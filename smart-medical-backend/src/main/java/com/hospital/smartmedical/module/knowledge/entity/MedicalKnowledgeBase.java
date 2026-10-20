package com.hospital.smartmedical.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 医学知识库实体（sm_medical_knowledge_base，V1.3 新增）。
 *
 * <p>type 区分 CLINICAL（临床指南库，供面诊 agent 使用）与 DEPARTMENT（科室介绍库，
 * 供预问诊 agent 使用）。</p>
 */
@Data
@TableName("sm_medical_knowledge_base")
public class MedicalKnowledgeBase implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库名称 */
    private String name;

    /** 类型：CLINICAL / DEPARTMENT */
    private String type;

    /** 知识库说明 */
    private String description;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
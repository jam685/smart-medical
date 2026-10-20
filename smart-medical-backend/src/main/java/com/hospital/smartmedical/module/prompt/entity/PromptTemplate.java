package com.hospital.smartmedical.module.prompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提示词模板实体（sm_prompt_template，V1.3 新增）。
 *
 * <p>管理端维护的 AI prompt 模板：模板编码（唯一）、名称、使用场景（PATIENT/DOCTOR/ADMIN）、
 * 变量、prompt 内容与启用状态。</p>
 */
@Data
@TableName("sm_prompt_template")
public class PromptTemplate implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码（唯一） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 使用场景：PATIENT / DOCTOR / ADMIN */
    private String scene;

    /** 变量名称，如 {doctorId}{patientId} */
    private String variables;

    /** prompt 内容 */
    private String promptContent;

    /** 启用状态：1 启用 / 0 停用 */
    private Integer enabled;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
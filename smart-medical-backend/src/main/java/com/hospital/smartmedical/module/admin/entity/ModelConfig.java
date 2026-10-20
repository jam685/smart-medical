package com.hospital.smartmedical.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型配置实体（sm_model_config，V1.3 重新设计）。
 *
 * <p>管理端维护 AI 模型接入参数：配置名称、类型（向量模型/聊天模型）、服务商、模型名、
 * 接口地址、API Key、最大 token 量、启用状态与备注。</p>
 */
@Data
@TableName("sm_model_config")
public class ModelConfig implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置名称 */
    private String configName;

    /** 类型：向量模型 / 聊天模型 */
    private String type;

    /** 服务商 */
    private String provider;

    /** 模型名称 */
    private String modelName;

    /** 接口地址 */
    private String apiUrl;

    /** API Key */
    private String apiKey;

    /** 最大 token 量 */
    private Integer maxTokens;

    /** 启用状态：1 启用 / 0 停用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
package com.hospital.smartmedical.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_model_config")
public class ModelConfig implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelName;

    private String version;

    private String deployType;

    private String status;

    private LocalDateTime updatedAt;
}

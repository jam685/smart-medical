package com.hospital.smartmedical.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sm_data_quality")
public class DataQuality implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dataCount;

    private Long labeledCount;

    private BigDecimal missingRate;

    private BigDecimal abnormalRate;

    private LocalDateTime updatedAt;
}

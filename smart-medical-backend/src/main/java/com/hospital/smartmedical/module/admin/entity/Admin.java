package com.hospital.smartmedical.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_admin")
public class Admin implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String employeeNo;

    private String phone;

    private String idCard;

    private String realName;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

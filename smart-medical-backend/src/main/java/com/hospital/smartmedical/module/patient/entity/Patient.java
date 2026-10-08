package com.hospital.smartmedical.module.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_patient")
public class Patient implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String realName;

    private String gender;

    private String phone;

    private String idCard;

    private String avatar;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

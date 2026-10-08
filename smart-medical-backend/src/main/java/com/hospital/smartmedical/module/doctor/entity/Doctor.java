package com.hospital.smartmedical.module.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_doctor")
public class Doctor implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String employeeNo;

    private String password;

    private String realName;

    /** 性别：男 / 女 */
    private String gender;

    /** 职称：主任医师 / 副主任医师 / 主治医师（决定挂号费档位与页面展示） */
    private String title;

    private String phone;

    private String department;

    /** 头像 URL（可空，空时前端用首字母/性别占位图） */
    private String avatar;

    /** 医生简介 / 擅长领域 / 学术成就（患者选医生时展示） */
    private String bio;

    private String onlineStatus;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

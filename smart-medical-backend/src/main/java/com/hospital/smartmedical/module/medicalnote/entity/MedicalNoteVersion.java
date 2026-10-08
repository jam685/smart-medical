package com.hospital.smartmedical.module.medicalnote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_medical_note_version")
public class MedicalNoteVersion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private Integer version;

    private String content;

    private Long modifiedBy;

    private LocalDateTime createdAt;
}

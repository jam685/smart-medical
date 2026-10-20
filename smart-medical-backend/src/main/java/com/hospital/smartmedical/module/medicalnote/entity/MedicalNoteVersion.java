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

    /** 病历主键 ID（sm_medical_note.id） */
    private Long noteId;

    /** 版本号 */
    private Integer version;

    /** 修改人 ID */
    private Long modifiedBy;

    /** 修改时间 */
    private LocalDateTime createdAt;
}

package com.hospital.smartmedical.module.onlineconsult.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 医生回复表（sm_consult_reply）。
 *
 * <p>记录医生确认后的最终回复。ai_draft 为 AI 生成的草稿、final_reply 为医生确认的最终回复，
 * AI 草稿、医生修改、最终回复均在此留痕。只有医生确认后患者才能看到 final_reply。</p>
 */
@Data
@TableName("sm_consult_reply")
public class ConsultReply implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问诊 ID（sm_online_consult.id） */
    private Long consultId;

    /** 医生 ID（sm_doctor.id） */
    private Long doctorId;

    /** AI 生成草稿 */
    private String aiDraft;

    /** 医生确认的最终回复 */
    private String finalReply;

    /** 回复时间 */
    private LocalDateTime repliedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

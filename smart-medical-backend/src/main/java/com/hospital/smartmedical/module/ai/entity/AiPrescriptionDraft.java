package com.hospital.smartmedical.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 草稿版本实体（sm_prescription_draft，AI 模块新增表，不在 V1.3 的 21 张表内）。
 *
 * <p>承载 AI 多智能体生成的草稿（症状摘要 / 回复草稿 / 处方草稿），版本自增可追溯。
 * AI 只写本草稿表，禁止写入正式处方表 sm_prescription；
 * 正式处方入库唯一入口为医生确认按钮触发的后端业务逻辑。</p>
 */
@Data
@TableName("sm_prescription_draft")
public class AiPrescriptionDraft implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联问诊 ID（可空） */
    private Long consultId;

    /** 会话 ID */
    private String sessionId;

    /** 患者 ID */
    private Long patientId;

    /** 医生 ID（可空） */
    private Long doctorId;

    /** 草稿类型：SYMPTOM_SUMMARY 症状摘要 / REPLY_DRAFT 回复草稿 / PRESCRIPTION_DRAFT 处方草稿 */
    private String type;

    /** 草稿内容 */
    private String content;

    /** 草稿版本号（每次生成自增） */
    private Integer version;

    /** 状态：PENDING 待医生确认 / FINAL 已确认（默认 PENDING） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

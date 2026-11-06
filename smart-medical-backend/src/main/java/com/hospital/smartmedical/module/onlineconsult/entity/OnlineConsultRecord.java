package com.hospital.smartmedical.module.onlineconsult.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 线上面诊记录实体（sm_online_consult_record）。
 *
 * <p>V1.3 将原 sm_online_consult 改名并精简：无主诉/描述字段（主诉与描述作为首条
 * PATIENT 消息写入 sm_consult_message），仅保留患者、医生、科室、状态与起止时间，
 * 供患者/医生及管理员查看问诊流水。</p>
 */
@Data
@TableName("sm_online_consult_record")
public class OnlineConsultRecord implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID（sm_patient） */
    private Long patientId;

    /** 接诊医生 ID（sm_doctor） */
    private Long doctorId;

    /** 科室 */
    private String department;

    /** 面诊状态：PENDING / REPLIED / CLOSED */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 关联的 AI 预问诊 sessionId（sm_pre_consult.session_id，由患者在 AI 助手走完预问诊后发起问诊时带上） */
    private String preConsultSessionId;
}
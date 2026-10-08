package com.hospital.smartmedical.module.onlineconsult.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 线上面诊主表（sm_online_consult）。
 *
 * <p>记录患者发起的一次线上问诊：主诉、病情描述、所属科室、状态与补充次数（仅统计图片）。
 * 患者端发起，医生端接诊回复。</p>
 */
@Data
@TableName("sm_online_consult")
public class OnlineConsult implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID（sm_patient.id） */
    private Long patientId;

    /** 接诊医生 ID（sm_doctor.id） */
    private Long doctorId;

    /** 科室 */
    private String department;

    /** 主诉 */
    private String chiefComplaint;

    /** 病情描述 */
    private String description;

    /** 状态：PENDING / AI_ANALYZED / DOCTOR_REVIEWING / REPLIED / CLOSED */
    private String status;

    /** 补充次数（仅统计图片） */
    private Integer supplementCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;
}

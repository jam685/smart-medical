package com.hospital.smartmedical.module.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 医生停诊申请（sm_suspend_request）。
 *
 * <p>医生停不掉"已有人预约"的时段，只能发起申请；管理员审批时执行退号或改约。
 * slot_ids 存 JSON 数组字符串，如 [12,13,14]。</p>
 */
@Data
@TableName("sm_suspend_request")
public class SuspendRequest implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long doctorId;

    /** JSON 数组：待停诊的号源 ID */
    private String slotIds;

    private String reason;

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private Long adminId;

    private String reviewNote;

    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
}

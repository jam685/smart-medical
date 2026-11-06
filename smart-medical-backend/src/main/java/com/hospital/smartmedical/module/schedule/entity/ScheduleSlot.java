package com.hospital.smartmedical.module.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 号源实体（sm_schedule_slot）。
 *
 * <p>一行 = 某医生 × 某日期 × 某时段（半小时）。这是挂号并发控制的唯一真相：
 * bookedCount 只能通过 {@code ScheduleSlotMapper#deduct} 的条件更新自增，
 * 任何"先查余量再更新"的写法都会超卖。</p>
 */
@Data
@TableName("sm_schedule_slot")
public class ScheduleSlot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 医生 ID（sm_doctor.id） */
    private Long doctorId;

    /** 科室名（冗余，便于按科室筛号） */
    private String department;

    /** 出诊日期 */
    private LocalDate workDate;

    /** 上下午：AM / PM */
    private String period;

    /** 时段开始时间（如 08:00） */
    private LocalTime startTime;

    /** 时段结束时间（如 08:30） */
    private LocalTime endTime;

    /** 总号数 */
    private Integer totalCount;

    /** 已挂数量（并发扣减字段） */
    private Integer bookedCount;

    /** 状态：OPEN 可约 / SUSPENDED 停诊 / CLOSED 关闭 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

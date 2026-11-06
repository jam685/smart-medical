package com.hospital.smartmedical.module.schedule.vo;

import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 号源展示 VO（患者选号 / 医生排班共用）。
 */
@Data
public class ScheduleSlotVO {

    /** 号源 ID：下单时传给 POST /appointments 的 scheduleId */
    private Long id;

    private Long doctorId;

    /** 医生姓名（跨科室候选列表用，同医生场景可空） */
    private String doctorName;

    private String department;

    /** 出诊日期 yyyy-MM-dd */
    private String date;

    /** AM / PM */
    private String period;

    /** 时段开始 HH:mm */
    private String startTime;

    /** 时段结束 HH:mm */
    private String endTime;

    /** 总号数 */
    private Integer total;

    /** 已挂数 */
    private Integer booked;

    /** 剩余号数 */
    private Integer remain;

    /** OPEN / SUSPENDED / CLOSED */
    private String status;

    /** 当前是否可下单（状态可约 + 有余号 + 未过截单时间） */
    private Boolean bookable;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    /** 截单提前量：距开诊不足 10 分钟的号不再放号 */
    public static final int BOOK_LEAD_MINUTES = 10;

    /**
     * 实体 → VO。
     *
     * @param s   号源实体
     * @param now 当前时间（由调用方传入，便于测试）
     * @return VO
     */
    public static ScheduleSlotVO of(ScheduleSlot s, LocalDateTime now) {
        ScheduleSlotVO vo = new ScheduleSlotVO();
        vo.setId(s.getId());
        vo.setDoctorId(s.getDoctorId());
        vo.setDepartment(s.getDepartment());
        vo.setDate(s.getWorkDate() == null ? null : s.getWorkDate().toString());
        vo.setPeriod(s.getPeriod());
        vo.setStartTime(s.getStartTime() == null ? null : s.getStartTime().format(HM));
        vo.setEndTime(s.getEndTime() == null ? null : s.getEndTime().format(HM));
        int total = s.getTotalCount() == null ? 0 : s.getTotalCount();
        int booked = s.getBookedCount() == null ? 0 : s.getBookedCount();
        vo.setTotal(total);
        vo.setBooked(booked);
        vo.setRemain(Math.max(0, total - booked));
        vo.setStatus(s.getStatus());
        vo.setBookable(isBookable(s, now));
        return vo;
    }

    /**
     * 是否可下单：状态 OPEN + 有余号 + 距开诊超过截单提前量。
     *
     * @param s   号源
     * @param now 当前时间
     * @return true=可约
     */
    public static boolean isBookable(ScheduleSlot s, LocalDateTime now) {
        if (s == null || !"OPEN".equals(s.getStatus())) {
            return false;
        }
        int total = s.getTotalCount() == null ? 0 : s.getTotalCount();
        int booked = s.getBookedCount() == null ? 0 : s.getBookedCount();
        if (booked >= total) {
            return false;
        }
        if (s.getWorkDate() == null || s.getStartTime() == null) {
            return false;
        }
        LocalDateTime start = LocalDateTime.of(s.getWorkDate(), s.getStartTime());
        return start.isAfter(now.plusMinutes(BOOK_LEAD_MINUTES));
    }
}

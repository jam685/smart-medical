package com.hospital.smartmedical.module.schedule.service;

import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.vo.ScheduleDayVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleImpactVO;
import com.hospital.smartmedical.module.schedule.vo.ScheduleSlotVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 号源与排班服务。
 *
 * <p>职责边界：只管"号源怎么放、怎么查、怎么停"，不管挂号单本身。
 * 扣号/回补由 {@code ScheduleSlotMapper} 的条件更新完成，挂号事务在 AppointmentService 中编排。</p>
 */
public interface ScheduleService {

    /**
     * 查询某医生某天的号源（患者选号页）。
     *
     * @param doctorId 医生 ID
     * @param date     日期
     * @return 按开始时间升序的号源列表；当天无排班返回空列表
     */
    List<ScheduleSlotVO> listSlots(Long doctorId, LocalDate date);

    /**
     * 查询某医生未来若干天的排班（患者看哪天有号 / 医生看本周排班）。
     *
     * @param doctorId  医生 ID
     * @param startDate 起始日期（空则今天）
     * @param days      天数（1~30，越界自动收敛）
     * @return 每天一条，按日期升序；无排班的日期也会返回（slots 为空）
     */
    List<ScheduleDayVO> listDays(Long doctorId, LocalDate startDate, int days);

    /**
     * 为单个医生批量生成号源（已存在的时段跳过，不覆盖人工调整）。
     *
     * @param doctorId  医生 ID
     * @param startDate 起始日期
     * @param days      天数
     * @param capacity  每个时段的号数
     * @return 实际新建的号源行数
     */
    int generate(Long doctorId, LocalDate startDate, int days, int capacity);

    /**
     * 为整个科室批量生成号源（管理员排班用）。
     *
     * @param department 科室名
     * @param startDate  起始日期
     * @param days       天数
     * @param capacity   每个时段的号数
     * @return 实际新建的号源行数
     */
    int generateByDepartment(String department, LocalDate startDate, int days, int capacity);

    /**
     * 停诊 / 恢复出诊（医生本人操作）。
     *
     * @param slotId   号源 ID
     * @param doctorId 当前医生 ID（归属校验）
     * @param suspended true=停诊，false=恢复
     * @return 更新后的号源
     */
    ScheduleSlot suspend(Long slotId, Long doctorId, boolean suspended);

    /**
     * 调整某时段号数（加号/减号）。
     *
     * @param slotId     号源 ID
     * @param doctorId   当前医生 ID（归属校验）
     * @param totalCount 新的总号数（不得小于已挂数）
     * @return 更新后的号源
     */
    ScheduleSlot updateCapacity(Long slotId, Long doctorId, int totalCount);

    /**
     * 首次启动兜底：当库里"今天及以后"一个号源都没有时，为全部医生生成近 N 天号源。
     *
     * <p>只在空库时执行一次，已有号源（含医生手工停诊/加号的结果）一律不动。</p>
     *
     * @param days     生成天数
     * @param capacity 每个时段的号数
     * @return 新建的号源行数；已有号源时返回 0
     */
    int seedIfEmpty(int days, int capacity);

    /**
     * 管理端：医生列表（可按科室筛），包含未上线医生（排班不要求在线）。
     *
     * @param department 科室名（可空）
     * @return 医生精简信息列表
     */
    List<Map<String, Object>> listDoctors(String department);

    /**
     * 停诊影响面预览：这些时段一旦停诊，会影响哪些患者、可以改约到哪里。
     *
     * @param slotIds 待停诊的号源 ID
     * @return 影响面（患者清单 + 改约建议）
     */
    ScheduleImpactVO impact(List<Long> slotIds);

    /**
     * 管理端停诊 / 恢复出诊（可跨医生批量）。
     *
     * <p>停诊时先锁号源行，再取消受影响挂号（cancel_reason=SUSPEND），最后把号源置为
     * SUSPENDED 且 booked_count 归零；force=false 且存在预约时直接拒绝并返回人数。</p>
     *
     * @param slotIds   号源 ID 列表
     * @param suspended true=停诊，false=恢复
     * @param force     是否确认退号（影响面已确认）
     * @return {changed: 变更时段数, cancelled: 退号数}
     */
    Map<String, Object> changeStatusByAdmin(List<Long> slotIds, boolean suspended, boolean force);

    /**
     * 一键改约并停诊：把受影响患者整体迁移到目标时段，再把源时段停掉。
     *
     * <p>顺序很关键：先停诊会有号源空窗，先改约又要保证失败可回滚——所以整个过程在一个事务里：
     * 逐单"取消原挂号(RESCHEDULE) → 回补旧号 → 在新时段重新下单"，最后用
     * {@code suspendIfEmpty} 把源时段置为停诊；任何一步失败（含目标被抢空、源时段被并发占用）
     * 整批回滚，不会出现"人改走了一半、时段又没停"。</p>
     *
     * @param fromSlotIds    待停诊的源时段
     * @param toSlotIds      目标时段（可多个，按顺序装人；可跨医生/科室）
     * @param appointmentIds 只迁移指定挂号单（空=迁移全部受影响患者）
     * @return {rescheduled, suspended, toSlotIds, assignments}
     */
    Map<String, Object> reschedule(List<Long> fromSlotIds, List<Long> toSlotIds, List<Long> appointmentIds);

    /**
     * 跨科室候选时段：给定科室/日期范围，返回仍可约的时段（含医生姓名）。
     *
     * @param department 科室（可空=全院）
     * @param startDate  起始日期（可空=今天）
     * @param days       天数
     * @param exclude    需要排除的号源 ID（待停诊时段）
     * @return 可约时段，最多 50 条
     */
    List<ScheduleSlotVO> listAlternatives(String department, LocalDate startDate, int days, List<Long> exclude);

    /**
     * 管理端调整号数（不校验医生归属）。
     *
     * @param slotId     号源 ID
     * @param totalCount 新的总号数
     * @return 更新后的号源
     */
    ScheduleSlot updateCapacityByAdmin(Long slotId, int totalCount);

    /**
     * AI 挂号用：把"明天上午9点"这类模糊时间解析到真正可约的号源。
     *
     * <p>优先同日、不早于期望时间的时段；同日无号则顺延到最近有号的日期。
     * 返回值里的时间才是最终就诊时间，调用方必须以此为准回复患者。</p>
     *
     * @param doctorId 医生 ID
     * @param desired  患者期望时间（可空，空则取最近可约）
     * @return 可约号源；近 7 天无号返回 null
     */
    ScheduleSlot resolveForAi(Long doctorId, LocalDateTime desired);

    /**
     * 按 ID 查询号源（AI 挂号确认轮用：拿回上一轮缓存的候选号源）。
     *
     * @param slotId 号源 ID
     * @return 号源；不存在返回 null
     */
    ScheduleSlot getById(Long slotId);
}

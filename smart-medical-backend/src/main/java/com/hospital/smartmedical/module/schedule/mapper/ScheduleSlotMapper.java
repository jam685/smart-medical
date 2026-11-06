package com.hospital.smartmedical.module.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 号源 Mapper。
 *
 * <p>扣减与回补都必须是「带条件的单条 UPDATE」，靠 InnoDB 行锁保证原子性，
 * 返回的受影响行数就是唯一的判定依据：0 = 没抢到，不需要重试也不需要分布式锁。</p>
 */
@Mapper
public interface ScheduleSlotMapper extends BaseMapper<ScheduleSlot> {

    /**
     * 原子扣减一个号：仅当状态可约且未满时才成功。
     *
     * @param id 号源 ID
     * @return 1=扣减成功；0=已满或不可约
     */
    @Update("UPDATE sm_schedule_slot SET booked_count = booked_count + 1, updated_at = NOW() "
            + "WHERE id = #{id} AND status = 'OPEN' AND booked_count < total_count")
    int deduct(@Param("id") Long id);

    /**
     * 回补一个号：取消挂号/停诊退号时调用，条件保证不会减成负数。
     *
     * @param id 号源 ID
     * @return 1=回补成功；0=无可回补的号
     */
    @Update("UPDATE sm_schedule_slot SET booked_count = booked_count - 1, updated_at = NOW() "
            + "WHERE id = #{id} AND booked_count > 0")
    int release(@Param("id") Long id);

    /**
     * 仅当号源已无预约时才置为停诊（乐观守卫）。
     *
     * <p>改约流程会先把患者迁走、回补旧号，再调本方法把源时段停掉。
     * 如果这期间有人抢到了该时段的号（booked_count &gt; 0），条件不成立、返回 0，
     * 调用方据此整单回滚——比"先锁行再操作"更不容易和下单链路形成死锁。</p>
     *
     * @param id 号源 ID
     * @return 1=已停诊；0=仍有预约，不能停
     */
    @Update("UPDATE sm_schedule_slot SET status = 'SUSPENDED', booked_count = 0, updated_at = NOW() "
            + "WHERE id = #{id} AND booked_count = 0")
    int suspendIfEmpty(@Param("id") Long id);

    /**
     * 锁定若干号源行（SELECT ... FOR UPDATE）。
     *
     * <p>停诊是"先取消患者挂号、再把号源置为停诊"的复合操作，必须先锁号源行，
     * 否则并发下单可能在这两步之间挤进来，拿到一个即将停诊的号。</p>
     *
     * @param ids 号源 ID
     * @return 实际锁到的号源 ID
     */
    @Select("<script>SELECT id FROM sm_schedule_slot WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY id FOR UPDATE</script>")
    List<Long> lockByIds(@Param("ids") List<Long> ids);
}

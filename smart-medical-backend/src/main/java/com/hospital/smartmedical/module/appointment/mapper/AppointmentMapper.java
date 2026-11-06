package com.hospital.smartmedical.module.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {

    /**
     * 条件取消：只有处于待确认/待就诊状态才会被改掉，重复点击或并发取消只会成功一次。
     *
     * @param id 挂号单 ID
     * @return 1=取消成功；0=状态已变更（已取消/已完成）
     */
    @Update("UPDATE sm_appointment SET status = 'CANCELLED', cancel_reason = #{reason}, updated_at = NOW() "
            + "WHERE id = #{id} AND status IN ('PENDING', 'CONFIRMED')")
    int cancelIfActive(@Param("id") Long id, @Param("reason") String reason);
}

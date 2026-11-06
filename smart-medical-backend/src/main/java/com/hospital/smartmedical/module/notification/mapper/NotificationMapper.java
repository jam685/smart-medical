package com.hospital.smartmedical.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 单条已读（限制归属人，防越权改他人消息） */
    @Update("UPDATE sm_notification SET read_flag = 1 WHERE id = #{id} AND user_id = #{userId} AND role = #{role}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("role") String role);

    /** 全部已读 */
    @Update("UPDATE sm_notification SET read_flag = 1 WHERE user_id = #{userId} AND role = #{role} AND read_flag = 0")
    int markAllRead(@Param("userId") Long userId, @Param("role") String role);
}

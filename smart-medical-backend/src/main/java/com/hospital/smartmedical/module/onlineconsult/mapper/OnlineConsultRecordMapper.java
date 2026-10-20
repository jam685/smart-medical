package com.hospital.smartmedical.module.onlineconsult.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线上面诊记录 Mapper（sm_online_consult_record）。
 *
 * <p>继承 MyBatis-Plus BaseMapper，提供问诊记录的增删改查。</p>
 */
@Mapper
public interface OnlineConsultRecordMapper extends BaseMapper<OnlineConsultRecord> {
}
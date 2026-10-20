package com.hospital.smartmedical.module.preconsult.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.preconsult.entity.PreConsult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能预问诊 Mapper（sm_pre_consult）。
 *
 * <p>继承 MyBatis-Plus BaseMapper，提供预问诊会话消息的查询与写入。</p>
 */
@Mapper
public interface PreConsultMapper extends BaseMapper<PreConsult> {
}
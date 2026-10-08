package com.hospital.smartmedical.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}

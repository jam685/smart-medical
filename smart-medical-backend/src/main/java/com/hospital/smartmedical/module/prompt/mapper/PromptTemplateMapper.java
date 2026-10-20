package com.hospital.smartmedical.module.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.prompt.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板 Mapper（sm_prompt_template）。
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
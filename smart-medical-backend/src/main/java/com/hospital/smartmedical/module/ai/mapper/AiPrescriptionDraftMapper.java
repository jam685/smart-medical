package com.hospital.smartmedical.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.ai.entity.AiPrescriptionDraft;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 草稿版本 Mapper（sm_prescription_draft）。
 *
 * <p>继承 MyBatis-Plus BaseMapper，提供 AI 草稿的版本插入与查询。</p>
 */
@Mapper
public interface AiPrescriptionDraftMapper extends BaseMapper<AiPrescriptionDraft> {
}

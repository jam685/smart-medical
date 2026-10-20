package com.hospital.smartmedical.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.knowledge.entity.MedicalKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 医学知识库 Mapper（sm_medical_knowledge_base）。
 */
@Mapper
public interface MedicalKnowledgeBaseMapper extends BaseMapper<MedicalKnowledgeBase> {
}
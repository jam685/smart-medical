package com.hospital.smartmedical.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.knowledge.entity.VectorIndex;
import org.apache.ibatis.annotations.Mapper;

/**
 * 向量化检索记录 Mapper（sm_vector_index）。
 */
@Mapper
public interface VectorIndexMapper extends BaseMapper<VectorIndex> {
}
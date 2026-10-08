package com.hospital.smartmedical.module.prescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.prescription.entity.Prescription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 处方主表 Mapper
 * 继承 MyBatis-Plus BaseMapper，提供 sm_prescription 的基础 CRUD。
 */
@Mapper
public interface PrescriptionMapper extends BaseMapper<Prescription> {
}

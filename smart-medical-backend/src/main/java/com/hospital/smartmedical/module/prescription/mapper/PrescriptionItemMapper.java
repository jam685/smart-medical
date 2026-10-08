package com.hospital.smartmedical.module.prescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.prescription.entity.PrescriptionItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 处方药品明细 Mapper
 * 继承 MyBatis-Plus BaseMapper，提供 sm_prescription_item 的基础 CRUD。
 */
@Mapper
public interface PrescriptionItemMapper extends BaseMapper<PrescriptionItem> {
}

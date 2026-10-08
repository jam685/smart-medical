package com.hospital.smartmedical.module.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
}

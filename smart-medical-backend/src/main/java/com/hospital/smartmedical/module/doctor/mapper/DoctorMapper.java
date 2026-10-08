package com.hospital.smartmedical.module.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DoctorMapper extends BaseMapper<Doctor> {
}

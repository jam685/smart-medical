package com.hospital.smartmedical.module.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {

    /**
     * 锁定患者行（SELECT ... FOR UPDATE），用于把同一患者的下单请求串行化。
     *
     * <p>防的是"同一患者双击/重放产生两张挂号单"——号源容量由号源行的条件更新保证，
     * 这一把锁只解决患者维度的重复提交，不参与号源扣减。</p>
     *
     * @param id 患者 ID
     * @return 患者 ID（不存在时为 null）
     */
    @Select("SELECT id FROM sm_patient WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}

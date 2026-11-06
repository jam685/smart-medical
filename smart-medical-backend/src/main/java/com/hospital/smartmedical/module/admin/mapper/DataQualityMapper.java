package com.hospital.smartmedical.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.smartmedical.module.admin.entity.DataQuality;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DataQualityMapper extends BaseMapper<DataQuality> {

    /** 任意表行数。 */
    @Select("SELECT COUNT(*) FROM ${table}")
    Integer countTable(@Param("table") String table);

    /** sm_knowledge_document 中已解析且有 chunk 的文档数。 */
    @Select("SELECT COUNT(*) FROM sm_knowledge_document WHERE parse_status='PARSED' AND chunk_count > 0")
    Integer countKnowledgeParsed();

    /** sm_doctor 中 bio（医生简介/擅长领域）非空的记录数。 */
    @Select("SELECT COUNT(*) FROM sm_doctor WHERE bio IS NOT NULL AND bio <> ''")
    Integer countDoctorWithSpecialty();

    /** sm_patient 中 phone 非空的记录数。 */
    @Select("SELECT COUNT(*) FROM sm_patient WHERE phone IS NOT NULL AND phone <> ''")
    Integer countPatientWithPhone();

    /**
     * 缺失率：所有检查项总数。
     * 巡检字段（全部来自实际表结构）：
     *   sm_patient: real_name, phone             (2 字段)
     *   sm_doctor:  real_name, department         (2 字段)
     *   sm_online_consult_record: patient_id, doctor_id  (2 字段)
     *   sm_knowledge_document: document_name, parse_status  (2 字段)
     */
    @Select("SELECT (SELECT COUNT(*) FROM sm_patient) * 2" +
            " + (SELECT COUNT(*) FROM sm_doctor) * 2" +
            " + (SELECT COUNT(*) FROM sm_online_consult_record) * 2" +
            " + (SELECT COUNT(*) FROM sm_knowledge_document) * 2")
    Integer totalFieldChecks();

    /** 缺失率：实际缺失的字段总次数。 */
    @Select("SELECT (" +
            "  SELECT COUNT(*) FROM sm_patient WHERE real_name IS NULL OR real_name='' OR phone IS NULL OR phone=''" +
            ") + (" +
            "  SELECT COUNT(*) FROM sm_doctor WHERE real_name IS NULL OR real_name='' OR department IS NULL OR department=''" +
            ") + (" +
            "  SELECT COUNT(*) FROM sm_online_consult_record WHERE patient_id IS NULL OR doctor_id IS NULL" +
            ") + (" +
            "  SELECT COUNT(*) FROM sm_knowledge_document WHERE document_name IS NULL OR document_name='' OR parse_status IS NULL OR parse_status=''" +
            ")")
    Integer totalMissingFields();

    /** 异常率：6 张核心表总记录数。 */
    @Select("SELECT (SELECT COUNT(*) FROM sm_patient) + (SELECT COUNT(*) FROM sm_doctor)" +
            " + (SELECT COUNT(*) FROM sm_online_consult_record) + (SELECT COUNT(*) FROM sm_consult_message)" +
            " + (SELECT COUNT(*) FROM sm_knowledge_document) + (SELECT COUNT(*) FROM sm_knowledge_chunk)")
    Integer totalRowsInCore();

    /**
     * 异常率：值异常的记录数。
     * - sm_doctor: phone 有值但不是 11 位数字
     * - sm_knowledge_document: parse_status 非法枚举
     * 注意：sm_patient 无 age 列，跳过年龄检查。
     */
    @Select("SELECT (" +
            "  SELECT COUNT(*) FROM sm_doctor WHERE phone IS NOT NULL AND phone <> '' AND phone NOT REGEXP '^[0-9]{11}$'" +
            ") + (" +
            "  SELECT COUNT(*) FROM sm_knowledge_document WHERE parse_status IS NOT NULL AND parse_status <> '' AND parse_status NOT IN ('PENDING','PARSED','FAILED')" +
            ")")
    Integer totalAbnormalRecords();
}

package com.hospital.smartmedical.module.prescription.vo;

import com.hospital.smartmedical.module.prescription.entity.Prescription;
import com.hospital.smartmedical.module.prescription.entity.PrescriptionItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处方详情 VO（主表 + 药品明细 + 患者/医生姓名）
 * 供医生接诊工作台、患者处方查看等场景使用。
 */
@Data
public class PrescriptionVO {

    /** 处方 ID */
    private Long id;

    /** 关联就诊进度 ID */
    private Long visitId;

    /** 关联挂号单 ID */
    private Long appointmentId;

    /** 患者 ID */
    private Long patientId;

    /** 患者姓名 */
    private String patientName;

    /** 开方医生 ID */
    private Long doctorId;

    /** 医生姓名 */
    private String doctorName;

    /** 开方科室 */
    private String department;

    /** 诊断 */
    private String diagnosis;

    /** 医嘱 */
    private String advice;

    /** 状态：DRAFT / ISSUED */
    private String status;

    /** 药品明细列表 */
    private List<Item> items;

    /** 开方时间 */
    private LocalDateTime createdAt;

    /**
     * 处方明细 VO
     */
    @Data
    public static class Item {
        /** 明细 ID */
        private Long id;
        /** 药品名称 */
        private String drugName;
        /** 规格 */
        private String specification;
        /** 单次剂量 */
        private String dosage;
        /** 频次 */
        private String frequency;
        /** 用药天数 */
        private Integer days;
        /** 数量 */
        private String quantity;
        /** 用法 */
        private String usageMethod;
        /** 备注 */
        private String remark;
    }

    /**
     * 由处方主表 + 明细列表 + 姓名组装 VO
     *
     * @param p           处方主表实体
     * @param items       明细列表（可空）
     * @param patientName 患者姓名（可空）
     * @param doctorName  医生姓名（可空）
     * @return 处方 VO
     */
    public static PrescriptionVO of(Prescription p, List<PrescriptionItem> items,
                                    String patientName, String doctorName) {
        PrescriptionVO vo = new PrescriptionVO();
        vo.setId(p.getId());
        vo.setVisitId(p.getVisitId());
        vo.setAppointmentId(p.getAppointmentId());
        vo.setPatientId(p.getPatientId());
        vo.setPatientName(patientName);
        vo.setDoctorId(p.getDoctorId());
        vo.setDoctorName(doctorName);
        vo.setDepartment(p.getDepartment());
        vo.setDiagnosis(p.getDiagnosis());
        vo.setAdvice(p.getAdvice());
        vo.setStatus(p.getStatus());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setItems(items == null ? Collections.emptyList()
                : items.stream().map(PrescriptionVO::toItem).collect(Collectors.toList()));
        return vo;
    }

    /** 明细实体转 VO */
    private static Item toItem(PrescriptionItem it) {
        Item i = new Item();
        i.setId(it.getId());
        i.setDrugName(it.getDrugName());
        i.setSpecification(it.getSpecification());
        i.setDosage(it.getDosage());
        i.setFrequency(it.getFrequency());
        i.setDays(it.getDays());
        i.setQuantity(it.getQuantity());
        i.setUsageMethod(it.getUsageMethod());
        i.setRemark(it.getRemark());
        return i;
    }
}

package com.hospital.smartmedical.module.prescription.vo;

import com.hospital.smartmedical.module.prescription.entity.Prescription;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处方详情 VO（按 prescription_no 分组：表头信息 + 药品明细列表）。
 *
 * <p>sm_prescription 在 V1.3 与明细合并，同一 prescription_no 的多行记录即一份处方的多个
 * 药品明细。本 VO 将一组同编号记录聚合为「表头字段 + items 明细列表」，供接诊工作台与
 * 患者处方查看使用。</p>
 */
@Data
public class PrescriptionVO {

    /** 处方 ID（取该组首行记录 ID） */
    private Long id;

    /** 处方编号（分组） */
    private String prescriptionNo;

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
     * 处方药品明细 VO。
     */
    @Data
    public static class Item {
        /** 明细行 ID */
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
     * 由同一 prescription_no 的一组处方行记录（含表头）组装 VO。
     *
     * @param rows        同一处方编号的多行记录（每行对应一个药品明细）
     * @param patientName 患者姓名（可空）
     * @param doctorName  医生姓名（可空）
     * @return 处方 VO；rows 为空时返回 null
     */
    public static PrescriptionVO of(List<Prescription> rows, String patientName, String doctorName) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Prescription head = rows.get(0);
        PrescriptionVO vo = new PrescriptionVO();
        vo.setId(head.getId());
        vo.setPrescriptionNo(head.getPrescriptionNo());
        vo.setVisitId(head.getVisitId());
        vo.setAppointmentId(head.getAppointmentId());
        vo.setPatientId(head.getPatientId());
        vo.setPatientName(patientName);
        vo.setDoctorId(head.getDoctorId());
        vo.setDoctorName(doctorName);
        vo.setDepartment(head.getDepartment());
        vo.setDiagnosis(head.getDiagnosis());
        vo.setAdvice(head.getAdvice());
        vo.setStatus(head.getStatus());
        vo.setCreatedAt(head.getCreatedAt());
        vo.setItems(rows.stream().map(PrescriptionVO::toItem).collect(Collectors.toList()));
        return vo;
    }

    /** 处方行记录转明细 VO */
    private static Item toItem(Prescription r) {
        Item i = new Item();
        i.setId(r.getId());
        i.setDrugName(r.getDrugName());
        i.setSpecification(r.getSpecification());
        i.setDosage(r.getDosage());
        i.setFrequency(r.getFrequency());
        i.setDays(r.getDays());
        i.setQuantity(r.getQuantity());
        i.setUsageMethod(r.getUsageMethod());
        i.setRemark(r.getRemark());
        return i;
    }
}
package com.hospital.smartmedical.module.prescription.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.module.prescription.vo.PrescriptionVO;

import java.util.List;

/**
 * 处方业务服务
 * 职责：医生在接诊时开具处方（主表 + 药品明细），提供按就诊/医生维度的查询。
 * 数据来源：sm_prescription（主表）+ sm_prescription_item（明细）+ sm_doctor + sm_patient（联表姓名）。
 */
public interface PrescriptionService {

    /**
     * 创建处方（医生接诊时开具）
     * 业务规则：
     * 1) 仅医生/管理员可开方；
     * 2) 开方医生必须与就诊记录的接诊医生一致（医生角色时）；
     * 3) 主表与明细在同一事务中写入，明细不能为空（至少一种药品）。
     *
     * @param visitId       就诊进度 ID
     * @param diagnosis     诊断
     * @param advice        医嘱
     * @param items         药品明细列表
     * @param userId        当前用户 ID
     * @param role          当前角色
     * @return 新建处方的 VO（含明细）
     */
    PrescriptionVO createPrescription(Long visitId, String diagnosis, String advice,
                                      List<PrescriptionVO.Item> items, Long userId, Role role);

    /**
     * 按就诊 ID 查询处方详情（含明细）
     *
     * @param visitId 就诊进度 ID
     * @param userId  当前用户 ID（归属校验）
     * @param role    当前角色
     * @return 处方 VO，无处方时返回 null
     */
    PrescriptionVO getByVisitId(Long visitId, Long userId, Role role);

    /**
     * 查询指定医生开具的处方列表
     *
     * @param doctorId 医生 ID（医生角色取本人）
     * @return 处方 VO 列表（不含明细，明细在详情接口取）
     */
    List<PrescriptionVO> listByDoctor(Long doctorId);
}

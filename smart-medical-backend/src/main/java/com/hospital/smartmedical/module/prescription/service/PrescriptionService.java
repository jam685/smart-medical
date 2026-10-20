package com.hospital.smartmedical.module.prescription.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.module.prescription.vo.PrescriptionVO;

import java.util.List;

/**
 * 处方业务服务接口。
 *
 * <p>职责：医生在接诊时开具处方（一份处方多个药品明细 = 同一 prescription_no 的多行记录），
 * 提供按就诊/医生维度的查询。数据来源 sm_prescription + sm_doctor + sm_patient。</p>
 */
public interface PrescriptionService {

    /**
     * 创建处方（RX-01，医生接诊时开具）。
     * <p>业务规则：仅医生/管理员可开方；医生开方须与就诊接诊医生一致；至少一种药品；
     * 生成 prescription_no 后逐条插入药品明细行。</p>
     *
     * @param visitId   就诊进度 ID
     * @param diagnosis 诊断
     * @param advice    医嘱
     * @param items     药品明细列表
     * @param userId    当前用户 ID
     * @param role      当前角色
     * @return 新建处方的 VO（含明细）
     */
    PrescriptionVO createPrescription(Long visitId, String diagnosis, String advice,
                                      List<PrescriptionVO.Item> items, Long userId, Role role);

    /**
     * 按就诊 ID 查询处方（RX-02，含药品明细，含归属校验）。
     *
     * @param visitId 就诊进度 ID
     * @param userId  当前用户 ID（归属校验）
     * @param role    当前角色
     * @return 处方 VO，无处方时返回 null
     */
    PrescriptionVO getByVisitId(Long visitId, Long userId, Role role);

    /**
     * 按医生查询处方列表（RX-03，当前医生开具的处方）。
     *
     * @param doctorId 医生 ID
     * @return 处方 VO 列表（每个元素含明细）
     */
    List<PrescriptionVO> listByDoctor(Long doctorId);

    /**
     * 按患者查询本人处方列表（RX-04，患者端"我的报告-处方报告"使用）。
     *
     * @param patientId 患者 ID
     * @return 处方 VO 列表（按 prescription_no 分组，每个元素含明细）
     */
    List<PrescriptionVO> listByPatient(Long patientId);
}
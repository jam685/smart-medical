package com.hospital.smartmedical.module.schedule.service;

import java.util.List;
import java.util.Map;

/**
 * 医生停诊申请：医生发起 → 管理员审批（通过即执行退号停诊）。
 */
public interface SuspendRequestService {

    /**
     * 医生提交停诊申请。
     *
     * @param doctorId 医生 ID
     * @param slotIds  待停诊时段
     * @param reason   原因
     * @return 申请详情
     */
    Map<String, Object> submit(Long doctorId, List<Long> slotIds, String reason);

    /** 医生的申请列表 */
    List<Map<String, Object>> listByDoctor(Long doctorId);

    /** 管理端按状态查（status 可空=全部） */
    List<Map<String, Object>> listByStatus(String status);

    /** 待审批数量（管理端角标） */
    long pendingCount();

    /**
     * 管理员审批：通过则执行"退号并停诊"（若时段已被处理过则跳过执行）。
     *
     * @param id      申请 ID
     * @param adminId 管理员 ID
     * @param approve true=通过，false=驳回
     * @param note    审批意见
     * @return 申请详情
     */
    Map<String, Object> review(Long id, Long adminId, boolean approve, String note);
}

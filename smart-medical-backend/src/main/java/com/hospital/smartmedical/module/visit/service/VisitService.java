package com.hospital.smartmedical.module.visit.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.visit.vo.VisitVO;

/**
 * 就诊进度业务服务
 * 职责：管理 sm_visit 表的查询、详情、状态推进；状态推进时联动写入 progress_nodes 节点。
 * 数据来源：sm_visit（主表）+ sm_doctor + sm_patient + sm_appointment（联表展示）。
 */
public interface VisitService {

    /**
     * 分页查询就诊进度列表
     *
     * @param userId 当前登录用户 ID（拦截器注入）
     * @param role   当前登录角色（患者只看本人，医生只看本人接诊，管理员看全部）
     * @param page   页码（1 起始）
     * @param size   每页条数
     * @param status 状态过滤（可空）：WAITING/CALLING/IN_PROGRESS/FINISHED
     * @return VisitVO 分页结果（含医生/患者姓名、进度节点、挂号时间）
     */
    PageResult<VisitVO> listVisits(Long userId, Role role, int page, int size, String status);

    /**
     * 查询单条就诊进度详情
     *
     * @param id     就诊进度 ID
     * @param userId 当前用户 ID（用于归属校验）
     * @param role   当前角色
     * @return VisitVO（含完整进度节点）
     */
    VisitVO getVisitDetail(Long id, Long userId, Role role);

    /**
     * 推进就诊状态（仅医生/管理员可操作）
     * 业务规则：按状态机推进，并联动追加 progress_nodes 节点；
     *           WAITING→CALLING 追加"叫号"，CALLING→IN_PROGRESS 追加"就诊"，
     *           IN_PROGRESS→FINISHED 追加"完成"。
     *
     * @param id     就诊进度 ID
     * @param status 目标状态（必须是合法状态码）
     * @param userId 当前用户 ID
     * @param role   当前角色
     */
    void updateStatus(Long id, String status, Long userId, Role role);
}

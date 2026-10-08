package com.hospital.smartmedical.module.onlineconsult.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.ai.entity.AiAnalysis;
import com.hospital.smartmedical.module.onlineconsult.entity.*;
import com.hospital.smartmedical.module.onlineconsult.vo.OnlineDoctorVO;

import java.util.List;

/**
 * 线上面诊模块服务接口。
 *
 * <p>覆盖患者发起问诊、医患消息往来、医生确认回复、关闭问诊与补充材料记录等核心流程。
 * AI 分析 / AI 回复草稿相关方法为 Demo 模拟，本期可不关注。</p>
 */
public interface OnlineConsultService {

    /**
     * 查询在线医生列表（按科室/姓名关键词筛选，返回脱敏视图，不含密码）。
     *
     * @param department 科室（可空）
     * @param keyword    医生姓名关键词（可空）
     * @return 在线医生列表
     */
    List<OnlineDoctorVO> getOnlineDoctors(String department, String keyword);

    /**
     * 患者发起线上问诊（校验目标医生在线）。
     *
     * @param patientId      患者 ID
     * @param doctorId       接诊医生 ID
     * @param department     科室
     * @param chiefComplaint 主诉
     * @param description    病情描述
     * @return 新建的问诊记录
     */
    OnlineConsult createConsult(Long patientId, Long doctorId, String department,
                                String chiefComplaint, String description);

    /**
     * 分页查询问诊列表（按角色过滤：患者本人 / 医生本人 / 管理员全部）。
     */
    PageResult<OnlineConsult> listConsults(Long userId, String role, int page, int size);

    /**
     * 问诊详情（含归属校验；返回脱敏医生与患者信息）。
     *
     * @param id     问诊 ID
     * @param userId 当前登录用户 ID
     * @param role   当前登录角色
     * @return 问诊 + 医生 + 患者组合详情
     */
    Object getConsultDetail(Long id, Long userId, Role role);

    /**
     * 发送消息（患者补充描述或上传图片；带敏感词检测与归属校验）。
     *
     * @param userId    当前登录用户 ID
     * @param role      当前登录角色
     * @param consultId 问诊 ID
     * @param content   文本内容
     * @param imageUrl  图片地址（可空，非空即图片消息）
     * @return 发送结果（命中敏感词时返回阻断信息）
     */
    Object sendMessage(Long userId, Role role, Long consultId,
                       String content, String imageUrl);

    /**
     * 获取消息列表（含归属校验）。
     */
    List<ConsultMessage> listMessages(Long consultId, Long userId, Role role);

    /**
     * 生成 AI 分析（Demo 模式，本期不关注）。
     */
    AiAnalysis generateAiAnalysis(Long consultId);

    /**
     * 获取已生成的 AI 分析（Demo 模式，本期不关注）。
     */
    AiAnalysis getAiAnalysis(Long consultId);

    /**
     * 医生请求 AI 生成回复草稿（Demo 模式，本期不关注）。
     */
    ConsultReply generateReplyDraft(Long consultId, Long doctorId);

    /**
     * 医生修改草稿（Demo 模式，本期不关注）。
     */
    ConsultReply updateReplyDraft(Long consultId, String aiDraft);

    /**
     * 医生确认最终回复（写入消息流，患者可见；仅接诊医生本人可操作）。
     *
     * @param consultId  问诊 ID
     * @param doctorId   医生 ID（用于归属校验）
     * @param finalReply 最终回复内容
     */
    void confirmReply(Long consultId, Long doctorId, String finalReply);

    /**
     * 关闭问诊（医生本人或管理员可操作；关闭时自动生成病历草稿）。
     *
     * @param consultId 问诊 ID
     * @param userId    当前登录用户 ID
     * @param role      当前登录角色
     */
    void closeConsult(Long consultId, Long userId, Role role);

    /**
     * 获取补充材料记录（含归属校验）。
     */
    List<ConsultSupplement> listSupplementRecords(Long consultId, Long userId, Role role);
}

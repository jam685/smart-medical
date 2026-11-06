package com.hospital.smartmedical.module.onlineconsult.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.onlineconsult.entity.ConsultMessage;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import com.hospital.smartmedical.module.onlineconsult.vo.OnlineDoctorVO;

import java.util.List;

/**
 * 线上面诊模块服务接口（/online-consults）。
 *
 * <p>V2.3 覆盖患者发起问诊、医患消息往来、医生确认回复（OC-09 写入 DOCTOR 消息）与关闭问诊
 * 等核心流程。AI 分析 / AI 回复草稿已迁往 /ai/*，本模块移除 OC-05~08 与 OC-13 补充材料记录。</p>
 */
public interface OnlineConsultService {

    /**
     * 查询在线医生列表（OC-12）——按科室/姓名关键词筛选，返回脱敏视图，不含密码。
     *
     * @param department 科室（可空）
     * @param keyword    医生姓名关键词（可空）
     * @return 在线医生列表
     */
    List<OnlineDoctorVO> getOnlineDoctors(String department, String keyword);

    /**
     * 查询患者自己的「挂号主治医生」列表（问诊发起专用）。
     * 业务规则：患者只能对自己挂号成功（挂号状态非 CANCELLED）的主治医生发起在线问诊，
     * 因此该列表仅返回 sm_appointment 中该患者存在有效挂号的医生（按医生去重），
     * 未挂号成功的医生不可发起问诊。
     *
     * @param patientId 患者 ID
     * @return 该患者的挂号主治医生列表（脱敏视图，不含密码）
     */
    List<OnlineDoctorVO> getMyDoctors(Long patientId);

    /**
     * 患者发起线上问诊（OC-01）——创建问诊记录，并将主诉/描述作为首条 PATIENT 消息写入问诊消息表。
     *
     * @param patientId              患者 ID
     * @param doctorId               接诊医生 ID
     * @param department             科室
     * @param content                主诉 / 病情描述（作为首条消息）
     * @param preConsultSessionId    可选：关联的 AI 预问诊 sessionId（供医生端回溯预问诊摘要）
     * @return 新建的问诊记录
     */
    OnlineConsultRecord createConsult(Long patientId, Long doctorId, String department, String content,
                                      String preConsultSessionId);

    /**
     * 分页查询问诊列表（OC-02）——按角色过滤：患者本人 / 医生本人 / 管理员全部。
     * 医生视角返回增强 VO（患者姓名、叫号时间、医生最新回复时间），
     * 并按医生发送消息的最新时间倒序（无回复的沉底）。
     */
    PageResult<Object> listConsults(Long userId, String role, int page, int size);

    /**
     * 问诊详情（OC-03）——含归属校验，返回脱敏医生与患者信息。
     */
    Object getConsultDetail(Long id, Long userId, Role role);

    /**
     * 发送消息（OC-04）——患者补充描述或上传图片。
     */
    ConsultMessage sendMessage(Long userId, Role role, Long consultId, String content, String imageUrl);

    /**
     * 消息列表（OC-11）——按时间升序返回，含归属校验。
     */
    List<ConsultMessage> listMessages(Long consultId, Long userId, Role role);

    /**
     * 医生确认并回复患者（OC-09）——将最终回复写入一条 DOCTOR 消息，患者端可见。
     */
    void confirmReply(Long consultId, Long doctorId, String finalReply);

    /**
     * 关闭问诊（OC-10）——医生本人或管理员可操作，关闭时置结束时间。
     */
    void closeConsult(Long consultId, Long userId, Role role);
}
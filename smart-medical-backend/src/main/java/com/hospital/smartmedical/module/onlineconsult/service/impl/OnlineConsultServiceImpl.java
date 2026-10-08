package com.hospital.smartmedical.module.onlineconsult.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.admin.entity.AuditLog;
import com.hospital.smartmedical.module.admin.entity.SensitiveContent;
import com.hospital.smartmedical.module.admin.mapper.AuditLogMapper;
import com.hospital.smartmedical.module.admin.mapper.SensitiveContentMapper;
import com.hospital.smartmedical.module.ai.entity.AiAnalysis;
import com.hospital.smartmedical.module.ai.mapper.AiAnalysisMapper;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNote;
import com.hospital.smartmedical.module.medicalnote.mapper.MedicalNoteMapper;
import com.hospital.smartmedical.module.onlineconsult.entity.*;
import com.hospital.smartmedical.module.onlineconsult.mapper.*;
import com.hospital.smartmedical.module.onlineconsult.service.OnlineConsultService;
import com.hospital.smartmedical.module.onlineconsult.vo.OnlineDoctorVO;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 线上面诊模块服务实现。
 *
 * <p>核心职责：患者发起问诊 → 医患消息往来 → 医生确认回复（写入消息流）→ 关闭问诊。
 * 所有涉及隐私的接口均做归属校验与脱敏，避免越权与敏感信息泄露。</p>
 */
@Service
@RequiredArgsConstructor
public class OnlineConsultServiceImpl implements OnlineConsultService {

    private final OnlineConsultMapper onlineConsultMapper;
    private final ConsultMessageMapper consultMessageMapper;
    private final ConsultSupplementMapper consultSupplementMapper;
    private final ConsultReplyMapper consultReplyMapper;
    private final AiAnalysisMapper aiAnalysisMapper;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    private final MedicalNoteMapper medicalNoteMapper;
    private final SensitiveContentMapper sensitiveContentMapper;
    private final AuditLogMapper auditLogMapper;

    /** 敏感词关键词列表（Demo 内置） */
    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList(
            "政治", "暴力", "自杀", "枪支", "炸药", "违禁药", "色情", "毒品"
    );

    /** 问诊状态常量 */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_AI_ANALYZED = "AI_ANALYZED";
    private static final String STATUS_DOCTOR_REVIEWING = "DOCTOR_REVIEWING";
    private static final String STATUS_REPLIED = "REPLIED";
    private static final String STATUS_CLOSED = "CLOSED";

    /** 消息发送方常量 */
    private static final String SENDER_PATIENT = "PATIENT";
    private static final String SENDER_DOCTOR = "DOCTOR";
    private static final String SENDER_SYSTEM = "SYSTEM";

    /** 消息类型常量 */
    private static final String MSG_TYPE_TEXT = "TEXT";
    private static final String MSG_TYPE_IMAGE = "IMAGE";
    private static final String MSG_TYPE_SYSTEM = "SYSTEM";

    // ===================== 敏感词检测 =====================

    /**
     * 检测内容是否命中敏感词，命中返回命中的关键词，否则返回 null。
     */
    private String checkSensitive(String content) {
        if (content == null || content.isBlank()) return null;
        for (String kw : SENSITIVE_KEYWORDS) {
            if (content.contains(kw)) return kw;
        }
        return null;
    }

    /**
     * 命中敏感词后落库：敏感内容表 + 审计日志。
     */
    private void recordSensitive(Long userId, String role, String content, String keyword, Long consultId) {
        SensitiveContent sc = new SensitiveContent();
        sc.setUserId(userId);
        sc.setRole(role);
        sc.setContent(content);
        sc.setSensitiveType(keyword);
        sc.setSensitiveLevel(2);
        sc.setActionTaken("BLOCKED");
        sc.setReviewStatus("PENDING");
        sc.setCreatedAt(LocalDateTime.now());
        sensitiveContentMapper.insert(sc);

        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setRole(role);
        log.setAction("SEND_MESSAGE_BLOCKED");
        log.setTargetType("CONSULT");
        log.setTargetId(consultId);
        log.setDetail("命中敏感词：" + keyword);
        log.setSensitiveType(keyword);
        log.setSensitiveLevel(2);
        log.setActionTaken("BLOCKED");
        log.setReviewStatus("PENDING");
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    // ===================== 归属校验 =====================

    /**
     * 加载问诊并做归属校验：
     * 管理员可见全部；患者仅本人；医生仅本人接诊的问诊。越权抛 403。
     */
    private OnlineConsult getOwnedConsult(Long consultId, Long userId, Role role) {
        OnlineConsult c = onlineConsultMapper.selectById(consultId);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (role == Role.ADMIN) return c;
        if (role == Role.PATIENT && !c.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (role == Role.DOCTOR && !c.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return c;
    }

    /**
     * 身份证号脱敏：保留前 3 位与后 4 位，中间以 * 填充；长度不足 8 位原样返回。
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        int len = idCard.length();
        return idCard.substring(0, 3) + "*".repeat(len - 7) + idCard.substring(len - 4);
    }

    /**
     * 患者信息脱敏视图：剔除密码，身份证号脱敏，仅返回问诊详情所需公开字段。
     */
    private Map<String, Object> toPatientSafe(Patient p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("username", p.getUsername());
        map.put("realName", p.getRealName());
        map.put("gender", p.getGender());
        map.put("phone", p.getPhone());
        map.put("idCard", maskIdCard(p.getIdCard()));
        map.put("avatar", p.getAvatar());
        map.put("status", p.getStatus());
        return map;
    }

    // ===================== 1. 在线医生列表 =====================

    @Override
    public List<OnlineDoctorVO> getOnlineDoctors(String department, String keyword) {
        LambdaQueryWrapper<Doctor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Doctor::getOnlineStatus, "ONLINE");
        if (department != null && !department.isBlank()) {
            wrapper.eq(Doctor::getDepartment, department);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Doctor::getRealName, keyword);
        }
        wrapper.eq(Doctor::getStatus, "ACTIVE");
        wrapper.orderByDesc(Doctor::getUpdatedAt);
        List<Doctor> doctors = doctorMapper.selectList(wrapper);

        // 返回脱敏视图，剔除 password 等敏感字段
        return doctors.stream().map(OnlineDoctorVO::from).collect(Collectors.toList());
    }

    // ===================== 2. 发起问诊 =====================

    @Override
    @Transactional
    public OnlineConsult createConsult(Long patientId, Long doctorId, String department,
                                       String chiefComplaint, String description) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BizException("医生不存在");
        }
        if (!"ONLINE".equalsIgnoreCase(doctor.getOnlineStatus())) {
            throw new BizException("该医生当前不在线，无法发起问诊");
        }

        OnlineConsult consult = new OnlineConsult();
        consult.setPatientId(patientId);
        consult.setDoctorId(doctorId);
        consult.setDepartment(department != null && !department.isBlank()
                ? department : doctor.getDepartment());
        consult.setChiefComplaint(chiefComplaint);
        consult.setDescription(description);
        consult.setStatus(STATUS_PENDING);
        consult.setSupplementCount(0);
        consult.setCreatedAt(LocalDateTime.now());
        consult.setUpdatedAt(LocalDateTime.now());
        onlineConsultMapper.insert(consult);

        // 同时创建一条系统消息
        ConsultMessage sysMsg = new ConsultMessage();
        sysMsg.setConsultId(consult.getId());
        sysMsg.setSender(SENDER_SYSTEM);
        sysMsg.setMessageType(MSG_TYPE_SYSTEM);
        sysMsg.setContent("问诊已发起，等待医生接诊");
        sysMsg.setCreatedAt(LocalDateTime.now());
        consultMessageMapper.insert(sysMsg);

        return consult;
    }

    // ===================== 3. 问诊列表（分页） =====================

    @Override
    public PageResult<OnlineConsult> listConsults(Long userId, String role, int page, int size) {
        Page<OnlineConsult> pageParam = Page.of(page, size);
        LambdaQueryWrapper<OnlineConsult> wrapper = new LambdaQueryWrapper<>();

        Role r = Role.of(role);
        if (r == Role.PATIENT) {
            wrapper.eq(OnlineConsult::getPatientId, userId);
        } else if (r == Role.DOCTOR) {
            // 医生返回自己的未关闭问诊（待处理 + 进行中 + 已回复）
            wrapper.eq(OnlineConsult::getDoctorId, userId);
            wrapper.in(OnlineConsult::getStatus, STATUS_PENDING, STATUS_AI_ANALYZED,
                    STATUS_DOCTOR_REVIEWING, STATUS_REPLIED);
        }
        // ADMIN 返回全部

        wrapper.orderByDesc(OnlineConsult::getCreatedAt);
        Page<OnlineConsult> result = onlineConsultMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    // ===================== 4. 问诊详情 =====================

    @Override
    public Object getConsultDetail(Long id, Long userId, Role role) {
        OnlineConsult consult = getOwnedConsult(id, userId, role);

        Doctor doctor = doctorMapper.selectById(consult.getDoctorId());
        Patient patient = patientMapper.selectById(consult.getPatientId());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("consult", consult);
        detail.put("doctor", doctor != null ? OnlineDoctorVO.from(doctor) : null);
        detail.put("patient", patient != null ? toPatientSafe(patient) : null);
        return detail;
    }

    // ===================== 5. 发送消息 =====================

    @Override
    @Transactional
    public Object sendMessage(Long userId, Role role, Long consultId,
                              String content, String imageUrl) {
        OnlineConsult consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 仅问诊患者本人可发送消息（医生通过确认回复接口回复）
        if (role != Role.PATIENT || !consult.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        String keyword = checkSensitive(content);
        if (keyword != null) {
            recordSensitive(userId, role.name(), content, keyword, consultId);
            Map<String, Object> blocked = new LinkedHashMap<>();
            blocked.put("blocked", true);
            blocked.put("message", "您的内容包含不适宜信息，已被拦截，请修改后重新发送。");
            blocked.put("keyword", keyword);
            return blocked;
        }

        boolean isImage = imageUrl != null && !imageUrl.isBlank();

        ConsultMessage msg = new ConsultMessage();
        msg.setConsultId(consultId);
        msg.setSender(SENDER_PATIENT);
        msg.setMessageType(isImage ? MSG_TYPE_IMAGE : MSG_TYPE_TEXT);
        msg.setContent(content);
        msg.setImageUrl(imageUrl);
        msg.setSensitiveFlag(0);
        msg.setCreatedAt(LocalDateTime.now());
        consultMessageMapper.insert(msg);

        // 图片消息：递增补充次数并记录补充材料
        if (isImage) {
            int newCount = (consult.getSupplementCount() == null ? 0 : consult.getSupplementCount()) + 1;
            LambdaUpdateWrapper<OnlineConsult> upd = new LambdaUpdateWrapper<>();
            upd.eq(OnlineConsult::getId, consultId);
            upd.set(OnlineConsult::getSupplementCount, newCount);
            upd.set(OnlineConsult::getUpdatedAt, LocalDateTime.now());
            onlineConsultMapper.update(null, upd);

            ConsultSupplement sup = new ConsultSupplement();
            sup.setConsultId(consultId);
            sup.setSupplementNo(newCount);
            sup.setMaterialType("IMAGE");
            sup.setContent(content);
            sup.setImageUrl(imageUrl);
            sup.setCreatedAt(LocalDateTime.now());
            consultSupplementMapper.insert(sup);
        }

        return msg;
    }

    // ===================== 6. 消息列表 =====================

    @Override
    public List<ConsultMessage> listMessages(Long consultId, Long userId, Role role) {
        getOwnedConsult(consultId, userId, role);
        LambdaQueryWrapper<ConsultMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultMessage::getConsultId, consultId);
        wrapper.orderByAsc(ConsultMessage::getCreatedAt);
        return consultMessageMapper.selectList(wrapper);
    }

    // ===================== 7. 生成 AI 分析（Demo） =====================

    @Override
    @Transactional
    public AiAnalysis generateAiAnalysis(Long consultId) {
        OnlineConsult consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // 若已有分析则直接返回
        LambdaQueryWrapper<AiAnalysis> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(AiAnalysis::getConsultId, consultId);
        AiAnalysis exist = aiAnalysisMapper.selectOne(existWrapper);
        if (exist != null) return exist;

        AiAnalysis analysis = new AiAnalysis();
        analysis.setConsultId(consultId);
        analysis.setPatientId(consult.getPatientId());
        analysis.setAnalysisType("CONSULT");

        // Demo 模拟数据
        analysis.setPossibleCauses("基于主诉和描述，可能的原因包括：上呼吸道感染、急性胃炎或季节性过敏反应。建议结合既往病史进一步排查。");
        analysis.setDifferential("鉴别诊断需考虑：1) 病毒感染 vs 细菌感染；2) 过敏性鼻炎 vs 鼻窦炎；3) 功能性腹痛 vs 器质性病变。");
        analysis.setDiagnosisSuggestions("建议完善：血常规、C反应蛋白、肝肾功能检查，并根据需要补充影像学检查。");
        analysis.setMedicationSuggestions("对症治疗建议：1) 解热镇痛可选用对乙酰氨基酚；2) 消化道症状可考虑奥美拉唑；3) 过敏反应可使用氯雷他定。具体用药请结合实际情况。");
        analysis.setRequiredMaterials("建议患者提供：既往病历、近期检查报告、过敏史记录。");
        analysis.setRiskLevel("MEDIUM");
        analysis.setConfidence(new BigDecimal("0.85"));
        analysis.setReferences("参考文献：《临床诊疗指南（内科分册）》2023版；UpToDate 临床决策支持系统。");
        analysis.setDisclaimer("本 AI 分析仅供参考，不能替代医生的专业诊断。请务必在医生指导下进行后续诊疗。");
        analysis.setCreatedAt(LocalDateTime.now());

        aiAnalysisMapper.insert(analysis);

        // 更新问诊状态
        LambdaUpdateWrapper<OnlineConsult> upd = new LambdaUpdateWrapper<>();
        upd.eq(OnlineConsult::getId, consultId);
        upd.set(OnlineConsult::getStatus, STATUS_AI_ANALYZED);
        upd.set(OnlineConsult::getUpdatedAt, LocalDateTime.now());
        onlineConsultMapper.update(null, upd);

        return analysis;
    }

    // ===================== 8. 获取 AI 分析 =====================

    @Override
    public AiAnalysis getAiAnalysis(Long consultId) {
        LambdaQueryWrapper<AiAnalysis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAnalysis::getConsultId, consultId);
        AiAnalysis analysis = aiAnalysisMapper.selectOne(wrapper);
        if (analysis == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return analysis;
    }

    // ===================== 9. AI 生成回复草稿 =====================

    @Override
    @Transactional
    public ConsultReply generateReplyDraft(Long consultId, Long doctorId) {
        // 查询或创建 reply 记录
        LambdaQueryWrapper<ConsultReply> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(ConsultReply::getConsultId, consultId);
        ConsultReply reply = consultReplyMapper.selectOne(existWrapper);

        if (reply == null) {
            reply = new ConsultReply();
            reply.setConsultId(consultId);
            reply.setDoctorId(doctorId);
            reply.setCreatedAt(LocalDateTime.now());
        }

        // Demo 模拟 AI 草稿文本
        String demoDraft = "您好，根据您描述的症状和相关检查，初步考虑为常见的上呼吸道感染。建议您：\n" +
                "1. 注意休息，多饮水；\n" +
                "2. 如有发热可服用对乙酰氨基酚；\n" +
                "3. 观察 3-5 天，如症状加重或持续不缓解请及时复诊。\n" +
                "（以上为 AI 生成草稿，请医生审核修改后确认）";

        reply.setAiDraft(demoDraft);
        if (reply.getId() == null) {
            consultReplyMapper.insert(reply);
        } else {
            consultReplyMapper.updateById(reply);
        }
        return reply;
    }

    // ===================== 10. 修改草稿 =====================

    @Override
    public ConsultReply updateReplyDraft(Long consultId, String aiDraft) {
        LambdaQueryWrapper<ConsultReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultReply::getConsultId, consultId);
        ConsultReply reply = consultReplyMapper.selectOne(wrapper);
        if (reply == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        reply.setAiDraft(aiDraft);
        consultReplyMapper.updateById(reply);
        return reply;
    }

    // ===================== 11. 确认最终回复 =====================

    @Override
    @Transactional
    public void confirmReply(Long consultId, Long doctorId, String finalReply) {
        OnlineConsult consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 仅接诊医生本人可回复
        if (!consult.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (finalReply == null || finalReply.isBlank()) {
            throw new BizException("回复内容不能为空");
        }

        // 1) 将医生回复写入消息流，患者端列表可见（核心：保证医患在线交流闭环）
        ConsultMessage msg = new ConsultMessage();
        msg.setConsultId(consultId);
        msg.setSender(SENDER_DOCTOR);
        msg.setMessageType(MSG_TYPE_TEXT);
        msg.setContent(finalReply);
        msg.setSensitiveFlag(0);
        msg.setCreatedAt(LocalDateTime.now());
        consultMessageMapper.insert(msg);

        // 2) 留痕：写入 / 更新医生回复表（final_reply）
        LambdaQueryWrapper<ConsultReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultReply::getConsultId, consultId);
        ConsultReply reply = consultReplyMapper.selectOne(wrapper);
        if (reply == null) {
            reply = new ConsultReply();
            reply.setConsultId(consultId);
            reply.setDoctorId(doctorId);
            reply.setCreatedAt(LocalDateTime.now());
        }
        reply.setFinalReply(finalReply);
        reply.setRepliedAt(LocalDateTime.now());
        if (reply.getId() == null) {
            consultReplyMapper.insert(reply);
        } else {
            consultReplyMapper.updateById(reply);
        }

        // 3) 更新问诊状态
        LambdaUpdateWrapper<OnlineConsult> upd = new LambdaUpdateWrapper<>();
        upd.eq(OnlineConsult::getId, consultId);
        upd.set(OnlineConsult::getStatus, STATUS_REPLIED);
        upd.set(OnlineConsult::getUpdatedAt, LocalDateTime.now());
        onlineConsultMapper.update(null, upd);
    }

    // ===================== 12. 关闭问诊 =====================

    @Override
    @Transactional
    public void closeConsult(Long consultId, Long userId, Role role) {
        OnlineConsult consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 仅接诊医生本人或管理员可关闭
        boolean allowed = role == Role.ADMIN
                || (role == Role.DOCTOR && consult.getDoctorId().equals(userId));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        LambdaUpdateWrapper<OnlineConsult> upd = new LambdaUpdateWrapper<>();
        upd.eq(OnlineConsult::getId, consultId);
        upd.set(OnlineConsult::getStatus, STATUS_CLOSED);
        upd.set(OnlineConsult::getClosedAt, LocalDateTime.now());
        upd.set(OnlineConsult::getUpdatedAt, LocalDateTime.now());
        onlineConsultMapper.update(null, upd);

        // 自动病历：如果关闭时没有病历，自动生成一条 PREVIEW 状态的 MedicalNote
        LambdaQueryWrapper<MedicalNote> noteWrapper = new LambdaQueryWrapper<>();
        noteWrapper.eq(MedicalNote::getConsultId, consultId);
        Long count = medicalNoteMapper.selectCount(noteWrapper);
        if (count == 0) {
            MedicalNote note = new MedicalNote();
            note.setConsultId(consultId);
            note.setPatientId(consult.getPatientId());
            note.setDoctorId(consult.getDoctorId());
            note.setStatus("PREVIEW");
            note.setModifiedCount(0);
            note.setCreatedAt(LocalDateTime.now());
            note.setUpdatedAt(LocalDateTime.now());
            medicalNoteMapper.insert(note);
        }
    }

    // ===================== 13. 补充材料记录 =====================

    @Override
    public List<ConsultSupplement> listSupplementRecords(Long consultId, Long userId, Role role) {
        getOwnedConsult(consultId, userId, role);
        LambdaQueryWrapper<ConsultSupplement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultSupplement::getConsultId, consultId);
        wrapper.orderByAsc(ConsultSupplement::getSupplementNo);
        return consultSupplementMapper.selectList(wrapper);
    }
}

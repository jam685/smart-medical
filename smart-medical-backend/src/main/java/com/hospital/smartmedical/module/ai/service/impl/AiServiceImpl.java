package com.hospital.smartmedical.module.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.admin.entity.AuditLog;
import com.hospital.smartmedical.module.admin.entity.SensitiveContent;
import com.hospital.smartmedical.module.admin.mapper.AuditLogMapper;
import com.hospital.smartmedical.module.admin.mapper.SensitiveContentMapper;
import com.hospital.smartmedical.module.ai.entity.AiChatMessage;
import com.hospital.smartmedical.module.ai.entity.AiChatSession;
import com.hospital.smartmedical.module.ai.mapper.AiChatMessageMapper;
import com.hospital.smartmedical.module.ai.mapper.AiChatSessionMapper;
import com.hospital.smartmedical.module.ai.service.AiService;
import com.hospital.smartmedical.module.report.entity.ReportInterpretation;
import com.hospital.smartmedical.module.report.mapper.ReportInterpretationMapper;
import com.hospital.smartmedical.module.report.entity.Report;
import com.hospital.smartmedical.module.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiChatSessionMapper aiChatSessionMapper;
    private final AiChatMessageMapper aiChatMessageMapper;
    private final SensitiveContentMapper sensitiveContentMapper;
    private final AuditLogMapper auditLogMapper;
    private final ReportMapper reportMapper;
    private final ReportInterpretationMapper reportInterpretationMapper;

    /** 敏感词关键词列表 */
    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList(
            "政治", "暴力", "自杀", "枪支", "炸药", "违禁药", "色情", "毒品"
    );

    private static final String SESSION_STATUS_OPEN = "OPEN";
    private static final String SESSION_STATUS_CLOSED = "CLOSED";
    private static final String SENDER_USER = "USER";
    private static final String SENDER_BOT = "BOT";
    private static final String SENDER_SYSTEM = "SYSTEM";

    // ===================== 敏感词检测 =====================

    private String checkSensitive(String content) {
        if (content == null || content.isBlank()) return null;
        for (String kw : SENSITIVE_KEYWORDS) {
            if (content.contains(kw)) return kw;
        }
        return null;
    }

    private AiChatSession ensureSession(Long userId, String role, String sessionKey) {
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getSessionKey, sessionKey);
        AiChatSession session = aiChatSessionMapper.selectOne(wrapper);
        if (session == null) {
            session = new AiChatSession();
            session.setUserId(userId);
            session.setUserRole(role);
            session.setSessionKey(sessionKey);
            session.setStatus(SESSION_STATUS_OPEN);
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            aiChatSessionMapper.insert(session);
        }
        return session;
    }

    private void saveMessage(Long sessionId, String sender, String content,
                             BigDecimal confidence, Integer needHuman) {
        AiChatMessage msg = new AiChatMessage();
        msg.setSessionId(sessionId);
        msg.setSender(sender);
        msg.setContent(content);
        msg.setConfidence(confidence);
        msg.setNeedHuman(needHuman);
        msg.setCreatedAt(LocalDateTime.now());
        aiChatMessageMapper.insert(msg);
    }

    // ===================== 1. AI 客服对话（Demo） =====================

    @Override
    @Transactional
    public Map<String, Object> chat(Long userId, String role, String sessionKey, String message) {
        // 敏感词检测：命中则转人工
        String keyword = checkSensitive(message);
        AiChatSession session = ensureSession(userId, role, sessionKey);

        // 先存用户消息
        saveMessage(session.getId(), SENDER_USER, message, null, 0);

        if (keyword != null) {
            // 记录敏感内容
            SensitiveContent sc = new SensitiveContent();
            sc.setUserId(userId);
            sc.setRole(role);
            sc.setContent(message);
            sc.setSensitiveType(keyword);
            sc.setSensitiveLevel(2);
            sc.setActionTaken("TRANSFER_TO_HUMAN");
            sc.setReviewStatus("PENDING");
            sc.setCreatedAt(LocalDateTime.now());
            sensitiveContentMapper.insert(sc);

            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setRole(role);
            log.setAction("AI_CHAT_SENSITIVE_TRANSFER");
            log.setTargetType("AI_CHAT");
            log.setTargetId(session.getId());
            log.setDetail("命中敏感词 " + keyword + "，自动转人工");
            log.setSensitiveType(keyword);
            log.setSensitiveLevel(2);
            log.setActionTaken("TRANSFER_TO_HUMAN");
            log.setReviewStatus("PENDING");
            log.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(log);

            String botReply = "您的消息涉及敏感内容（" + keyword + "），已自动为您转接人工客服，请稍候...";
            saveMessage(session.getId(), SENDER_BOT, botReply, new BigDecimal("0.90"), 1);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("reply", botReply);
            res.put("needHuman", true);
            res.put("confidence", new BigDecimal("0.90"));
            res.put("transferred", true);
            return res;
        }

        // Demo 模式：简单关键词匹配
        String reply;
        boolean needHuman = false;

        if (message.contains("头痛") || message.contains("头疼")) {
            reply = "头痛可能由多种原因引起，如紧张性头痛、偏头痛、感冒等。建议：\n1. 注意休息，避免长时间低头；\n2. 保证充足睡眠；\n3. 如持续加重或伴随呕吐、视力模糊等症状，请及时就医。";
        } else if (message.contains("发烧") || message.contains("发热") || message.contains("体温")) {
            reply = "正常体温一般在 36.0 - 37.2℃。轻度发热（37.5-38℃）可多饮水、物理降温。若超过 38.5℃ 或持续发热超过 3 天，建议及时就诊查明原因。";
        } else if (message.contains("咳嗽")) {
            reply = "咳嗽是呼吸道常见症状。干咳建议多饮水、避免刺激性气体；若有痰可适当止咳化痰。如伴发热、呼吸困难或持续超过 2 周，建议拍胸片排查。";
        } else if (message.contains("腹泻") || message.contains("拉肚子")) {
            reply = "常见于急性胃肠炎，建议清淡饮食、多饮水补充电解质。若伴严重腹痛、血便或脱水症状，建议立即就诊。";
        } else if (message.contains("预约") || message.contains("挂号")) {
            reply = "可以通过线上问诊模块或挂号窗口预约医生。请问您想预约哪个科室？我可以帮您推荐在线医生。";
        } else if (message.contains("药品") || message.contains("吃药")) {
            reply = "关于用药建议，请您描述具体症状或药品名称。温馨提示：所有药品需在医生或药师指导下使用。";
        } else if (message.contains("你好") || message.contains("您好") || message.contains("在吗")) {
            reply = "您好！我是智能医疗助手，可以帮助您解答常见健康问题、推荐科室、协助线上问诊。请问有什么可以帮您？";
        } else if (message.contains("人工") || message.contains("客服")) {
            reply = "好的，正在为您转接人工客服，请稍候...";
            needHuman = true;
        } else {
            reply = "感谢您的咨询！我可以帮助您解答常见健康问题。为了给您更准确的建议，请详细描述您的症状，我会尽力协助您。如需人工客服，可直接说「转人工」。";
            needHuman = false;
        }

        BigDecimal confidence = new BigDecimal("0.85");
        saveMessage(session.getId(), SENDER_BOT, reply, confidence, needHuman ? 1 : 0);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reply", reply);
        res.put("needHuman", needHuman);
        res.put("confidence", confidence);
        res.put("transferred", false);
        return res;
    }

    // ===================== 2. 转人工 =====================

    @Override
    public Map<String, Object> transferToHuman(String sessionKey) {
        LambdaUpdateWrapper<AiChatSession> upd = new LambdaUpdateWrapper<>();
        upd.eq(AiChatSession::getSessionKey, sessionKey);
        upd.set(AiChatSession::getStatus, SESSION_STATUS_CLOSED);
        upd.set(AiChatSession::getUpdatedAt, LocalDateTime.now());
        aiChatSessionMapper.update(null, upd);

        // 记录一条系统消息
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getSessionKey, sessionKey);
        AiChatSession session = aiChatSessionMapper.selectOne(wrapper);
        if (session != null) {
            saveMessage(session.getId(), SENDER_SYSTEM, "会话已关闭，等待人工客服接待", null, 1);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("transferred", true);
        res.put("sessionStatus", SESSION_STATUS_CLOSED);
        return res;
    }

    // ===================== 3. 会话历史 =====================

    @Override
    public List<AiChatMessage> getChatHistory(String sessionKey) {
        LambdaQueryWrapper<AiChatSession> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.eq(AiChatSession::getSessionKey, sessionKey);
        AiChatSession session = aiChatSessionMapper.selectOne(sessionWrapper);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        LambdaQueryWrapper<AiChatMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(AiChatMessage::getSessionId, session.getId());
        msgWrapper.orderByAsc(AiChatMessage::getCreatedAt);
        return aiChatMessageMapper.selectList(msgWrapper);
    }

    // ===================== 4. 医生端：病情分析（Demo） =====================

    @Override
    public Map<String, Object> clinicalAnalysis(String chiefComplaint, String description,
                                                Map<String, Object> patientInfo) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("possibleCauses", "基于「" + chiefComplaint + "」和您的描述，可能的原因包括：" +
                "感染性疾病、自身免疫反应或功能性紊乱。建议结合既往史进一步排查。");
        res.put("differential", "鉴别诊断：\n" +
                "1. 细菌性 vs 病毒性感染\n" +
                "2. 急性 vs 慢性炎症\n" +
                "3. 器质性病变 vs 功能性症状");
        res.put("diagnosisSuggestions", "建议完善：血常规、生化全项、影像学检查。");
        res.put("medicationSuggestions", "对症治疗建议在明确诊断后进行。当前可考虑：\n" +
                "- 解热镇痛：对乙酰氨基酚（必要时）\n" +
                "- 保持水电解质平衡\n" +
                "具体用药请结合患者肝肾功能调整。");
        res.put("requiredMaterials", "需要患者补充：既往病历、过敏史、近期用药记录。");
        res.put("riskLevel", "MEDIUM");
        res.put("confidence", new BigDecimal("0.82"));
        res.put("references", "《临床诊疗指南》内科分册 2023；UpToDate 临床决策支持");
        res.put("disclaimer", "本 AI 分析仅供医生参考，不能替代临床综合判断。");
        return res;
    }

    // ===================== 5. 医生端：检查建议（Demo） =====================

    @Override
    public Map<String, Object> examinationSuggestions(String chiefComplaint, String description) {
        List<String> suggestions = new ArrayList<>();
        if (chiefComplaint != null && (chiefComplaint.contains("咳嗽") || chiefComplaint.contains("发热"))) {
            suggestions.add("胸部 X 光检查");
            suggestions.add("血常规 + CRP");
            suggestions.add("咽拭子病毒检测");
        }
        if (chiefComplaint != null && (chiefComplaint.contains("腹痛") || chiefComplaint.contains("腹泻"))) {
            suggestions.add("腹部 B 超");
            suggestions.add("大便常规 + 潜血");
            suggestions.add("肝功能");
        }
        if (chiefComplaint != null && (chiefComplaint.contains("头痛") || chiefComplaint.contains("头晕"))) {
            suggestions.add("血压测量");
            suggestions.add("头颅 CT");
            suggestions.add("血糖、血脂");
        }
        // 默认补充
        if (suggestions.isEmpty()) {
            suggestions.add("血常规 + CRP");
            suggestions.add("生化全项");
            suggestions.add("心电图");
        }
        suggestions.add("根据实际情况补充：血沉、肿瘤标志物等");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("examinationList", suggestions);
        res.put("urgency", "MEDIUM");
        res.put("confidence", new BigDecimal("0.86"));
        res.put("note", "建议按优先级安排检查，先完成基础筛查。");
        return res;
    }

    // ===================== 6. 医生端：回复草稿（Demo） =====================

    @Override
    public Map<String, Object> replyDrafts(String chiefComplaint, String description) {
        String draft = "您好！根据您描述的症状（" + (chiefComplaint != null ? chiefComplaint : "未明确") + "），" +
                "我已了解您的情况。初步意见如下：\n" +
                "1. 建议注意休息，保持良好作息；\n" +
                "2. 观察症状变化，如出现加重请及时复诊；\n" +
                "3. 具体治疗方案需结合进一步检查结果确定。\n" +
                "\n" +
                "（以上为 AI 生成草稿，供医生参考）";

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("draft", draft);
        res.put("confidence", new BigDecimal("0.80"));
        res.put("tone", "professional");
        res.put("suggestedEdit", "建议医生结合问诊实际情况进行个性化调整。");
        return res;
    }

    // ===================== 7. 医生端：图片/报告分析 =====================

    @Override
    public Map<String, Object> imageAnalysis(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        LambdaQueryWrapper<ReportInterpretation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportInterpretation::getReportId, reportId);
        ReportInterpretation interp = reportInterpretationMapper.selectOne(wrapper);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reportId", reportId);
        res.put("reportTitle", report.getReportType() != null ? report.getReportType() : "未命名报告");
        if (interp != null) {
            res.put("recognizedContent", interp.getRecognizedContent());
            res.put("analysis", interp.getAnalysis());
            res.put("abnormalItems", interp.getAbnormalItems());
            res.put("referenceRanges", interp.getReferenceRanges());
            res.put("confidence", interp.getConfidence());
            res.put("needHuman", interp.getNeedHuman() != null && interp.getNeedHuman() == 1);
            res.put("disclaimer", interp.getDisclaimer());
        } else {
            res.put("analysis", "未找到该报告的 AI 解读记录。");
            res.put("confidence", BigDecimal.ZERO);
            res.put("needHuman", true);
        }
        return res;
    }
}

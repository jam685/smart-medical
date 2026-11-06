package com.hospital.smartmedical.module.ai.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.admin.entity.AuditLog;
import com.hospital.smartmedical.module.admin.mapper.AuditLogMapper;
import com.hospital.smartmedical.module.ai.agent.llm.LlmClientService;
import com.hospital.smartmedical.module.ai.agent.llm.PromptTemplateLoader;
import com.hospital.smartmedical.module.ai.agent.rag.ClinicalRagService;
import com.hospital.smartmedical.module.ai.entity.AiPrescriptionDraft;
import com.hospital.smartmedical.module.ai.mapper.AiPrescriptionDraftMapper;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNote;
import com.hospital.smartmedical.module.medicalnote.mapper.MedicalNoteMapper;
import com.hospital.smartmedical.module.onlineconsult.entity.ConsultMessage;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import com.hospital.smartmedical.module.onlineconsult.mapper.ConsultMessageMapper;
import com.hospital.smartmedical.module.onlineconsult.mapper.OnlineConsultRecordMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.preconsult.entity.PreConsult;
import com.hospital.smartmedical.module.preconsult.mapper.PreConsultMapper;
import com.hospital.smartmedical.module.prescription.entity.Prescription;
import com.hospital.smartmedical.module.prescription.mapper.PrescriptionMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 医生 AI 辅助问诊分析服务（ConsultHandle 右侧分析面板数据源）。
 *
 * <p>患者每发新消息，前端自动调用分析：装配上下文（患者档案脱敏 + 预问诊摘要 +
 * 历史病历 + 本次问诊对话）→ 临床指南库 RAG 检索 → doctor-analyze 提示词 → LLM 一次调用
 * 输出严格 JSON（信息充足度/缺失项/引导追问草稿/病情分析/就诊建议/处方建议）→
 * 处方药品名对历史处方表模糊匹配标注「本院开过」→ 分析结果留痕草稿表 + 审计日志（PENDING 待人工审核）→
 * 返回结构化结果。分析全程不写正式处方表。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorAssistAnalyzeService {

    /** 分析草稿类型（sm_prescription_draft.type） */
    private static final String DRAFT_TYPE = "ANALYSIS";

    /** 分析会话 ID 前缀（dc_=doctor consult analysis，与图会话前缀区分） */
    private static final String SESSION_PREFIX = "dc_";

    /** 参与分析的最大对话消息数（防爆 token） */
    private static final int MAX_MESSAGES = 30;

    /** 单条消息截断长度 */
    private static final int MAX_MSG_LEN = 300;

    private final OnlineConsultRecordMapper consultRecordMapper;
    private final ConsultMessageMapper consultMessageMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final PreConsultMapper preConsultMapper;
    private final MedicalNoteMapper medicalNoteMapper;
    private final PrescriptionMapper prescriptionMapper;
    private final ClinicalRagService clinicalRagService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LlmClientService llmClientService;
    private final AiPrescriptionDraftMapper draftMapper;
    private final AuditLogMapper auditLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对一次线上问诊做 AI 辅助分析（医生角色，校验问诊归属）。
     *
     * @param doctorId  当前登录医生 ID
     * @param consultId 问诊记录 ID
     * @return 结构化分析结果（信息充足度/缺失项/草稿/诊断/建议/处方建议 + 患者档案区数据）
     */
    public Map<String, Object> analyze(Long doctorId, Long consultId) {
        AnalysisSession session = prepare(doctorId, consultId);
        JsonNode analysis;
        if (llmClientService.isMockMode()) {
            analysis = mockAnalysis(session.getMessages(), session.getLatestMessage());
        } else {
            String llmOut = llmClientService.chat(session.getPrompt(), "请严格按 JSON 结构输出分析结果。");
            analysis = parseAnalysisJson(llmOut);
        }
        if (analysis == null) {
            // LLM 输出无法解析时兜底为信息不足追问，保证面板可用
            analysis = fallbackAnalysis();
        }
        return finalizeAnalysis(session, analysis);
    }

    /**
     * 流式分析：模型增量输出过程中逐字段回调（前端分节渐进渲染，消除整段等待）。
     *
     * @param session  预装配的分析上下文
     * @param onField  顶层字段完成回调（字段名 + JSON 节点）
     * @param onToken  文本字段增量回调（draftReply 打字机效果）
     * @return 完整结构化分析结果（同非流式）
     */
    public Map<String, Object> streamAnalyze(AnalysisSession session,
                                             BiConsumer<String, JsonNode> onField,
                                             Consumer<String> onToken) {
        AnalysisStreamParser parser = new AnalysisStreamParser(objectMapper, new AnalysisStreamParser.Listener() {
            @Override
            public void onField(String key, JsonNode value) {
                onField.accept(key, value);
            }

            @Override
            public void onToken(String delta) {
                onToken.accept(delta);
            }
        });

        JsonNode analysis;
        if (llmClientService.isMockMode()) {
            // mock 模式：序列化模拟结果后按块喂给解析器，复现流式节奏
            analysis = mockAnalysis(session.getMessages(), session.getLatestMessage());
            try {
                String json = objectMapper.writeValueAsString(analysis);
                for (int i = 0; i < json.length(); i += 60) {
                    parser.accept(json.substring(i, Math.min(i + 60, json.length())));
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                parser.finish();
            } catch (Exception e) {
                log.warn("mock 流式推送失败：{}", e.getMessage());
            }
        } else {
            String llmOut = llmClientService.chatStreaming(session.getPrompt(),
                    "请严格按 JSON 结构输出分析结果。", null, parser::accept);
            parser.finish();
            analysis = parseAnalysisJson(llmOut);
        }
        if (analysis == null) {
            analysis = fallbackAnalysis();
        }
        return finalizeAnalysis(session, analysis);
    }

    /**
     * 预装配分析上下文（校验归属 + 患者/对话/预问诊/病史 + RAG + 提示词渲染）。
     * 不调用模型，可在流式接口中先行推送档案区数据，实现档案秒显。
     *
     * @param doctorId  当前登录医生 ID
     * @param consultId 问诊记录 ID
     * @return 分析上下文快照
     */
    public AnalysisSession prepare(Long doctorId, Long consultId) {
        // 1) 问诊归属校验：仅接诊医生可分析自己的问诊
        OnlineConsultRecord record = consultRecordMapper.selectById(consultId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (record.getDoctorId() == null || !record.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Patient patient = patientMapper.selectById(record.getPatientId());
        Doctor doctor = doctorMapper.selectById(doctorId);

        // 2) 装配上下文
        List<ConsultMessage> messages = consultMessageMapper.selectList(
                new LambdaQueryWrapper<ConsultMessage>()
                        .eq(ConsultMessage::getConsultId, consultId)
                        .orderByAsc(ConsultMessage::getId));
        String chatHistory = buildChatHistory(messages);
        String latestMessage = lastPatientMessage(messages);
        String preConsultSummary = buildPreConsultSummary(record);
        String medicalHistory = buildMedicalHistory(record.getPatientId());

        // 3) 临床指南 RAG 检索（以对话全文 + 最新消息为检索文本）
        String ragQuery = (chatHistory + "\n" + latestMessage).trim();
        String ragContext = clinicalRagService.buildRagContext(ragQuery, 3);
        // 同时拿结构化来源列表（finalizeAnalysis 时附带进结果 JSON，前端渲染依据来源）
        List<ClinicalRagService.ClinicalMatch> ragSources = clinicalRagService.search(ragQuery, 5);

        // 4) 渲染提示词
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("doctorProfile", buildDoctorProfile(doctor));
        vars.put("patientInfo", buildPatientInfo(patient));
        vars.put("preConsultSummary", preConsultSummary.isBlank() ? "（无预问诊记录）" : preConsultSummary);
        vars.put("medicalHistory", medicalHistory.isBlank() ? "（无历史病历）" : medicalHistory);
        vars.put("chatHistory", chatHistory.isBlank() ? "（暂无对话）" : chatHistory);
        vars.put("latestMessage", latestMessage.isBlank() ? "（无）" : latestMessage);
        vars.put("ragContext", ragContext.isBlank() ? "（临床指南库未检索到相关内容）" : ragContext);
        String prompt = promptTemplateLoader.render("doctor-analyze", vars);

        AnalysisSession session = new AnalysisSession();
        session.setRecord(record);
        session.setPatient(patient);
        session.setMessages(messages);
        session.setChatHistory(chatHistory);
        session.setLatestMessage(latestMessage);
        session.setPreConsultSummary(preConsultSummary);
        session.setMedicalHistory(medicalHistory);
        session.setPrompt(prompt);
        session.setPatientInfo(buildPatientInfoMap(patient));
        session.setRagSources(ragSources);
        return session;
    }

    /**
     * 档案区数据载荷（流式接口首个事件，患者档案/预问诊摘要/历史病史）。
     */
    public Map<String, Object> profilePayload(AnalysisSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientInfo", session.getPatientInfo());
        payload.put("preConsultSummary", session.getPreConsultSummary());
        payload.put("medicalHistory", session.getMedicalHistory());
        return payload;
    }

    /**
     * 分析收尾：历史处方匹配 → 组装完整结果（含档案区）→ 草稿/审计落库 → 返回。
     *
     * <p>顺序调整：先 finalize 出完整 result（含 patientInfo / preConsultSummary / medicalHistory /
     * analyzedAt），再把完整 JSON 存草稿。这样缓存命中时 getLatestDraft 能返回档案区字段，
     * 前端直接渲染，不用再单独流式 profile 事件。</p>
     */
    private Map<String, Object> finalizeAnalysis(AnalysisSession session, JsonNode analysis) {
        // 处方药品名对历史处方表模糊匹配，标注「本院开过」
        markInHospitalDrugs(analysis);

        // 组装完整结果（先组装，再存草稿——保证草稿 JSON 里有档案区字段）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("consultId", session.getRecord().getId());
        result.put("sufficiency", textOf(analysis, "sufficiency", "INSUFFICIENT"));
        result.put("missingItems", toStringList(analysis.get("missingItems")));
        result.put("draftReply", textOf(analysis, "draftReply", ""));
        result.put("diagnosis", textOf(analysis, "diagnosis", ""));
        result.put("advice", textOf(analysis, "advice", ""));
        result.put("prescription", toDrugList(analysis.get("prescription")));
        result.put("patientInfo", session.getPatientInfo());
        result.put("preConsultSummary", session.getPreConsultSummary());
        result.put("medicalHistory", session.getMedicalHistory());
        result.put("analyzedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        // 临床指南依据来源（按 documentName 去重，同一指南取最高相似度；score 归一到 0~1 保留 2 位）
        result.put("ragSources", dedupeRagSources(session.getRagSources()));

        // 草稿 + 审计落库（存完整 result JSON，而非 LLM 原始 analysis）
        try {
            JsonNode fullJson = objectMapper.valueToTree(result);
            saveDraftAndAudit(session.getRecord(), fullJson);
        } catch (Exception e) {
            log.warn("完整结果转 JsonNode 失败，降级存原始分析：{}", e.getMessage());
            saveDraftAndAudit(session.getRecord(), analysis);
        }

        return result;
    }

    /**
     * 一次分析的上下文快照（prepare 阶段产出，不含模型结果）。
     */
    @Data
    public static class AnalysisSession {
        /** 问诊记录 */
        private OnlineConsultRecord record;
        /** 患者实体 */
        private Patient patient;
        /** 本次问诊消息列表 */
        private List<ConsultMessage> messages;
        /** 格式化对话历史（提示词用） */
        private String chatHistory;
        /** 最后一条患者消息 */
        private String latestMessage;
        /** 预问诊摘要 */
        private String preConsultSummary;
        /** 历史病历摘要 */
        private String medicalHistory;
        /** 渲染完成的提示词 */
        private String prompt;
        /** 患者档案结构化数据（前端档案区） */
        private Map<String, Object> patientInfo;
        /** 临床指南检索结构化来源（documentName + score，finalizeAnalysis 时去重后塞进 result） */
        private List<ClinicalRagService.ClinicalMatch> ragSources;
    }

    // ===================== 上下文装配 =====================

    /**
     * 格式化本次问诊对话记录（最近 MAX_MESSAGES 条，患者/医生/系统前缀）。
     */
    private String buildChatHistory(List<ConsultMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<ConsultMessage> tail = messages.size() > MAX_MESSAGES
                ? messages.subList(messages.size() - MAX_MESSAGES, messages.size()) : messages;
        StringBuilder sb = new StringBuilder();
        for (ConsultMessage m : tail) {
            String sender = switch (m.getSender() == null ? "" : m.getSender()) {
                case "PATIENT" -> "患者";
                case "DOCTOR" -> "医生";
                default -> "系统";
            };
            String text = "IMAGE".equalsIgnoreCase(m.getMessageType())
                    ? "[图片] " + (m.getContent() == null ? "" : m.getContent())
                    : (m.getContent() == null ? "" : m.getContent());
            if (text.length() > MAX_MSG_LEN) {
                text = text.substring(0, MAX_MSG_LEN) + "…";
            }
            sb.append(sender).append("：").append(text).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 取最后一条患者消息文本（分析触发点）。
     */
    private String lastPatientMessage(List<ConsultMessage> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConsultMessage m = messages.get(i);
            if ("PATIENT".equalsIgnoreCase(m.getSender())) {
                return m.getContent() == null ? "" : m.getContent();
            }
        }
        return "";
    }

    /**
     * 预问诊摘要：
     * 1) 优先使用问诊记录上关联的 pre_consult_session_id 做**精确匹配**——患者走过 AI 预问诊后
     *    发起问诊时会带上这个 sessionId，保证回溯到正确的那次预问诊；
     * 2) 没有关联 sessionId 时，fallback 到「按 patientId 取最近一次 session」（兼容历史问诊和
     *    未经过 AI 预问诊的患者，至少不会丢失已有数据）；
     * 3) 只拼患者发言（sender=PATIENT），过滤掉 AI 助手的引导话术。
     */
    private String buildPreConsultSummary(OnlineConsultRecord record) {
        if (record == null || record.getPatientId() == null) {
            return "";
        }
        List<PreConsult> recent;
        String targetSession = record.getPreConsultSessionId();
        if (targetSession != null && !targetSession.isBlank()) {
            // 精确关联：问诊上存了 sessionId，只取该 session 的记录
            recent = preConsultMapper.selectList(
                    new LambdaQueryWrapper<PreConsult>()
                            .eq(PreConsult::getPatientId, record.getPatientId())
                            .eq(PreConsult::getSessionId, targetSession)
                            .orderByAsc(PreConsult::getId));
        } else {
            // 兜底：取该患者最近一次 session
            List<PreConsult> all = preConsultMapper.selectList(
                    new LambdaQueryWrapper<PreConsult>()
                            .eq(PreConsult::getPatientId, record.getPatientId())
                            .orderByDesc(PreConsult::getId)
                            .last("LIMIT 50"));
            if (all.isEmpty()) {
                return "";
            }
            targetSession = all.get(0).getSessionId();
            recent = all;
        }
        // 收集该 session 的 PATIENT 发言（最多 5 条，按顺序）
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (PreConsult p : recent) {
            if (!targetSession.equals(p.getSessionId())) {
                break;
            }
            if (!"PATIENT".equalsIgnoreCase(p.getSender()) || p.getContent() == null || p.getContent().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(p.getContent());
            if (++count >= 5) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 历史病历摘要：最近 3 份病历的草稿内容（各截取 200 字）。
     */
    private String buildMedicalHistory(Long patientId) {
        if (patientId == null) {
            return "";
        }
        List<MedicalNote> notes = medicalNoteMapper.selectList(
                new LambdaQueryWrapper<MedicalNote>()
                        .eq(MedicalNote::getPatientId, patientId)
                        .orderByDesc(MedicalNote::getId)
                        .last("LIMIT 3"));
        StringBuilder sb = new StringBuilder();
        for (MedicalNote n : notes) {
            String draft = n.getDraft() == null ? "" : n.getDraft().trim();
            if (draft.isBlank()) {
                continue;
            }
            if (draft.length() > 200) {
                draft = draft.substring(0, 200) + "…";
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("· ").append(draft);
        }
        return sb.toString();
    }

    // ===================== LLM 输出解析 =====================

    /**
     * 解析 LLM 输出的分析 JSON（容忍 ```json 包裹与首尾杂文本）。
     */
    private JsonNode parseAnalysisJson(String llmOut) {
        if (llmOut == null || llmOut.isBlank()) {
            return null;
        }
        String json = llmOut.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        // 截取首个 { 到末个 }，容忍模型在 JSON 前后输出解释文字
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("问诊分析 JSON 解析失败：{}，原文：{}", e.getMessage(), llmOut);
            return null;
        }
    }

    /**
     * 解析失败兜底：返回信息不足追问结构。
     */
    private JsonNode fallbackAnalysis() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sufficiency", "INSUFFICIENT");
        ArrayNode missing = node.putArray("missingItems");
        missing.add("症状持续时间");
        missing.add("加重或缓解因素");
        node.put("draftReply", "您好，为了更准确地判断您的情况，能否补充一下症状持续的时间，以及有没有加重或缓解的因素？");
        node.put("diagnosis", "");
        node.put("advice", "");
        node.putArray("prescription");
        return node;
    }

    /**
     * mock 模式本地模拟分析：患者消息 ≥2 条视为信息充足，否则信息不足。
     */
    private JsonNode mockAnalysis(List<ConsultMessage> messages, String latestMessage) {
        long patientMsgs = messages == null ? 0
                : messages.stream().filter(m -> "PATIENT".equalsIgnoreCase(m.getSender())).count();
        ObjectNode node = objectMapper.createObjectNode();
        if (patientMsgs >= 2) {
            node.put("sufficiency", "SUFFICIENT");
            node.putArray("missingItems");
            node.put("draftReply", "您好，结合您描述的情况，初步考虑常见病症，建议按医嘱用药并注意休息，如症状加重请及时复诊。");
            node.put("diagnosis", "【mock】根据患者描述（" + abbreviate(latestMessage) + "），初步考虑常见病症，建议结合面诊进一步确认。");
            node.put("advice", "【mock】注意休息、多饮水，观察症状变化；如加重及时来院复诊。");
            ArrayNode drugs = node.putArray("prescription");
            ObjectNode drug = drugs.addObject();
            drug.put("drugName", "布洛芬缓释胶囊");
            drug.put("specification", "0.3g*20粒");
            drug.put("dosage", "0.3g");
            drug.put("frequency", "每日2次");
            drug.put("days", 3);
            drug.put("usageMethod", "口服");
        } else {
            node.put("sufficiency", "INSUFFICIENT");
            ArrayNode missing = node.putArray("missingItems");
            missing.add("症状持续时间");
            missing.add("是否发热及体温");
            missing.add("既往病史与药物过敏史");
            node.put("draftReply", "您好，为了更准确地判断您的情况，请补充：1）症状持续多久了？2）有没有发热，体温多少？3）以前有什么基础疾病，有没有药物过敏？");
            node.put("diagnosis", "");
            node.put("advice", "");
            node.putArray("prescription");
        }
        return node;
    }

    // ===================== 历史处方匹配 =====================

    /**
     * 处方建议药品名逐个对 sm_prescription 历史药品模糊匹配，命中标注 inHospital（本院开过）。
     */
    private void markInHospitalDrugs(JsonNode analysis) {
        JsonNode drugs = analysis.get("prescription");
        if (!(drugs instanceof ArrayNode arr)) {
            return;
        }
        for (JsonNode drug : arr) {
            if (!(drug instanceof ObjectNode obj)) {
                continue;
            }
            String name = textOf(obj, "drugName", "");
            boolean inHospital = false;
            if (!name.isBlank()) {
                Long cnt = prescriptionMapper.selectCount(new LambdaQueryWrapper<Prescription>()
                        .like(Prescription::getDrugName, name)
                        .last("LIMIT 1"));
                inHospital = cnt != null && cnt > 0;
            }
            obj.put("inHospital", inHospital);
        }
    }

    /**
     * 临床指南来源去重：同一 documentName 可能匹配多条 chunk，取最高相似度保留。
     *
     * @param sources search() 原始返回（可能同一文档多条）
     * @return 去重后的来源列表 [{documentName, chunkTitle, score}]，按 score 降序
     */
    private List<Map<String, Object>> dedupeRagSources(List<ClinicalRagService.ClinicalMatch> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> byDoc = new HashMap<>();
        for (ClinicalRagService.ClinicalMatch m : sources) {
            String doc = m.documentName();
            if (doc == null) doc = "";
            Map<String, Object> existing = byDoc.get(doc);
            if (existing == null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("documentName", doc);
                item.put("chunkTitle", m.chunkTitle() == null ? "" : m.chunkTitle());
                item.put("score", round2(m.score()));
                byDoc.put(doc, item);
            } else {
                double existingScore = ((Number) existing.get("score")).doubleValue();
                if (m.score() > existingScore) {
                    existing.put("chunkTitle", m.chunkTitle() == null ? "" : m.chunkTitle());
                    existing.put("score", round2(m.score()));
                }
            }
        }
        return byDoc.values().stream()
                .sorted(Comparator.comparingDouble((Map<String, Object> m) -> ((Number) m.get("score")).doubleValue()).reversed())
                .toList();
    }

    /** 四舍五入保留 2 位小数（余弦相似度 0~1，2 位够用）。 */
    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ===================== 草稿与审计 =====================

    /**
     * 读取某问诊最新的一份 AI 分析草稿（缓存命中场景：打开问诊页患者没发新消息时直接用）。
     *
     * <p>取 sm_prescription_draft 中 sessionId = "dc_{consultId}"、type = "ANALYSIS"
     * 的 version 最大那一条；把 JSON content 反序列化成 Map 返回，附加 version / createdAt / expired 元数据。</p>
     *
     * @param doctorId  登录医生 ID（归属校验）
     * @param consultId 问诊记录 ID
     * @return 最新草稿；若无草稿或归属不匹配返回 null
     */
    public Map<String, Object> getLatestDraft(Long doctorId, Long consultId) {
        OnlineConsultRecord record = consultRecordMapper.selectById(consultId);
        if (record == null || !record.getDoctorId().equals(doctorId)) {
            // 问诊不存在或不归当前医生，静默返回 null（调用方走实时分析）
            return null;
        }
        String sessionId = SESSION_PREFIX + consultId;
        AiPrescriptionDraft latest = draftMapper.selectOne(new LambdaQueryWrapper<AiPrescriptionDraft>()
                .eq(AiPrescriptionDraft::getSessionId, sessionId)
                .eq(AiPrescriptionDraft::getType, DRAFT_TYPE)
                .orderByDesc(AiPrescriptionDraft::getVersion)
                .last("LIMIT 1"));
        if (latest == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(latest.getContent());
            Map<String, Object> result = objectMapper.convertValue(node, Map.class);
            // 附加元数据，前端可以显示「v3 · 5 分钟前 · 患者未发新消息」
            result.put("_cached", true);
            result.put("_version", latest.getVersion());
            result.put("_cachedAt", latest.getCreatedAt());
            result.put("_draftId", latest.getId());
            return result;
        } catch (Exception e) {
            log.warn("最新草稿 JSON 反序列化失败：draftId={}, {}", latest.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 分析结果留痕：写草稿表（版本自增，PENDING）+ 审计日志（AI_DRAFT_GENERATED）。
     * 复用 SaveDraftAuditNode 的落库模式，仅审计不生成正式处方。
     */
    private void saveDraftAndAudit(OnlineConsultRecord record, JsonNode analysis) {
        try {
            String sessionId = SESSION_PREFIX + record.getId();
            // 版本自增：该问诊分析会话已有草稿数 +1
            Long exist = draftMapper.selectCount(new LambdaQueryWrapper<AiPrescriptionDraft>()
                    .eq(AiPrescriptionDraft::getSessionId, sessionId)
                    .eq(AiPrescriptionDraft::getType, DRAFT_TYPE));
            int version = (exist == null ? 0 : exist.intValue()) + 1;

            AiPrescriptionDraft draft = new AiPrescriptionDraft();
            draft.setConsultId(record.getId());
            draft.setSessionId(sessionId);
            draft.setPatientId(record.getPatientId());
            draft.setDoctorId(record.getDoctorId());
            draft.setType(DRAFT_TYPE);
            draft.setContent(objectMapper.writeValueAsString(analysis));
            draft.setVersion(version);
            draft.setStatus("PENDING");
            draft.setCreatedAt(LocalDateTime.now());
            draft.setUpdatedAt(LocalDateTime.now());
            draftMapper.insert(draft);

            AuditLog audit = new AuditLog();
            audit.setUserId(record.getDoctorId());
            audit.setRole("DOCTOR");
            audit.setAction("AI_DRAFT_GENERATED");
            audit.setTargetId(draft.getId());
            audit.setDetail("AI 生成问诊分析草稿 v" + version + "，问诊：" + record.getId()
                    + "，充足度：" + textOf(analysis, "sufficiency", ""));
            audit.setSensitiveLevel("MEDIUM");
            audit.setReviewStatus("PENDING");
            audit.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(audit);
            log.info("问诊分析草稿已落库：consultId={}, version={}, draftId={}", record.getId(), version, draft.getId());
        } catch (Exception e) {
            // 留痕失败不影响分析结果返回
            log.error("问诊分析草稿保存失败：{}", e.getMessage(), e);
        }
    }

    // ===================== 档案区数据 =====================

    /**
     * 医生档案文本（姓名/科室/职称/工号）。
     */
    private String buildDoctorProfile(Doctor doctor) {
        if (doctor == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (doctor.getRealName() != null && !doctor.getRealName().isBlank()) {
            sb.append(doctor.getRealName());
        }
        if (doctor.getDepartment() != null && !doctor.getDepartment().isBlank()) {
            sb.append("，").append(doctor.getDepartment());
        }
        if (doctor.getTitle() != null && !doctor.getTitle().isBlank()) {
            sb.append("，").append(doctor.getTitle());
        }
        if (doctor.getEmployeeNo() != null && !doctor.getEmployeeNo().isBlank()) {
            sb.append("，工号 ").append(doctor.getEmployeeNo());
        }
        return sb.toString();
    }

    /**
     * 患者档案文本（姓名/性别/年龄，手机号脱敏，注入提示词用）。
     */
    private String buildPatientInfo(Patient patient) {
        if (patient == null) {
            return "（患者信息缺失）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(patient.getRealName() == null ? "未知" : patient.getRealName());
        sb.append("，").append(patient.getGender() == null ? "未知" : patient.getGender());
        int age = ageOf(patient.getIdCard());
        sb.append("，").append(age > 0 ? age + "岁" : "年龄未知");
        if (patient.getPhone() != null && patient.getPhone().length() >= 7) {
            sb.append("，电话 ").append(patient.getPhone(), 0, 3).append("****")
                    .append(patient.getPhone().substring(patient.getPhone().length() - 4));
        }
        return sb.toString();
    }

    /**
     * 患者档案结构化数据（前端档案区展示用）。
     */
    private Map<String, Object> buildPatientInfoMap(Patient patient) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (patient == null) {
            return info;
        }
        info.put("name", patient.getRealName());
        info.put("gender", patient.getGender());
        int age = ageOf(patient.getIdCard());
        info.put("age", age > 0 ? age : null);
        info.put("phone", patient.getPhone());
        return info;
    }

    /**
     * 从 18 位身份证号推算周岁年龄（无法解析返回 0）。
     */
    private int ageOf(String idCard) {
        if (idCard == null || idCard.length() < 14) {
            return 0;
        }
        try {
            LocalDate birth = LocalDate.parse(idCard.substring(6, 14), DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (birth.isAfter(LocalDate.now())) {
                return 0;
            }
            return Period.between(birth, LocalDate.now()).getYears();
        } catch (Exception e) {
            return 0;
        }
    }

    // ===================== 工具方法 =====================

    /**
     * 从 JSON 节点读取字符串字段（缺失/null 返回默认值）。
     */
    private String textOf(JsonNode node, String field, String defaultValue) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull()) {
            return defaultValue;
        }
        String s = v.asText();
        return s == null ? defaultValue : s;
    }

    /**
     * JSON 数组转字符串列表。
     */
    private List<String> toStringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                String s = item.asText();
                if (s != null && !s.isBlank()) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    /**
     * JSON 处方数组转前端结构（含 inHospital 标注）。
     */
    private List<Map<String, Object>> toDrugList(JsonNode arr) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                Map<String, Object> drug = new LinkedHashMap<>();
                drug.put("drugName", textOf(item, "drugName", ""));
                drug.put("specification", textOf(item, "specification", ""));
                drug.put("dosage", textOf(item, "dosage", ""));
                drug.put("frequency", textOf(item, "frequency", ""));
                drug.put("days", item.has("days") && item.get("days").isNumber() ? item.get("days").asInt() : null);
                drug.put("usageMethod", textOf(item, "usageMethod", ""));
                drug.put("inHospital", item.has("inHospital") && item.get("inHospital").asBoolean());
                list.add(drug);
            }
        }
        return list;
    }

    /**
     * 截取文本前 30 字（mock 诊断描述用）。
     */
    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "症状描述";
        }
        return text.length() > 30 ? text.substring(0, 30) + "…" : text;
    }
}

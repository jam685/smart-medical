package com.hospital.smartmedical.module.ai.agent.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.module.ai.agent.enums.IntentKeywords;
import com.hospital.smartmedical.module.ai.agent.enums.ReplySource;
import com.hospital.smartmedical.module.ai.agent.llm.LlmClientService;
import com.hospital.smartmedical.module.ai.agent.llm.PromptTemplateLoader;
import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import com.hospital.smartmedical.module.ai.agent.tool.BusinessToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务 Agent 节点（businessAgentNode）。
 *
 * <p>全阶段可用，内部通过 role + 白名单控制权限（BusinessToolExecutor）；
 * 执行链路：LLM 意图识别（business-intent，失败/UNKNOWN 回退关键词）→ 业务工具执行（结构化结果写回图状态）
 * → LLM 回复润色（business-polish，mock 模式/失败回退工具原文）；❌不做任何医疗诊断推理。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessAgentNode implements NodeAction<AgentState> {

    private final BusinessToolExecutor businessToolExecutor;
    /** 大模型客户端（意图识别/回复润色） */
    private final LlmClientService llmClientService;
    /** 提示词加载器（管理员端模板热更新） */
    private final PromptTemplateLoader promptTemplateLoader;
    /** Jackson JSON 解析（LLM 意图 JSON 输出解析） */
    private final ObjectMapper objectMapper;

    /** 合法意图枚举集合（与路由共用同一份，避免两处枚举漂移） */
    private static final Set<String> VALID_INTENTS = IntentKeywords.VALID_INTENTS;

    /** 时间格式化 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 节点执行：LLM 意图识别 → 角色白名单业务分派 → LLM 回复润色，返回业务处理结果。
     *
     * @param state 图状态（含 graph 键的 MedicalGraphState）
     * @return 更新后的 graph 状态
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        MedicalGraphState g = state.<MedicalGraphState>value("graph")
                .orElseThrow(() -> new IllegalStateException("缺少 graph 状态"));
        try {
            // 1) 意图识别：LLM 为主（business-intent 模板），解析失败/UNKNOWN 回退关键词兜底
            String intent = classifyIntent(g);
            // 2) 业务工具执行（结构化结果写回图状态，供后续节点/审计使用）
            Map<String, Object> result = businessToolExecutor.execute(g, intent);
            String reply = result.getOrDefault("reply", "").toString();
            g.setLastBusinessResult(result);
            // 本轮结构化卡片（挂号卡/进度卡）：写回图状态供 SSE done 实时渲染，并挂到 AI 消息供历史回放
            Map<String, Object> card = extractCard(result);
            g.setCurrentRoundCard(card);
            // 3) 业务回复 LLM 润色（business-polish 模板；mock 模式或润色失败回退工具原文）
            reply = polishReply(g, intent, reply);
            g.setReply(reply);
            g.setReplyDraft(reply);
            // 标记来源：业务回复含实时号源/列表，语义缓存必须拒绝（见 MedicalGraphState.replySource）
            g.setReplySource(ReplySource.BUSINESS);
            // 业务查询结果直接展示，不落草稿表（hasNewDraft=false）
            g.setHasNewDraft(false);
            // 追加 AI 消息到对话历史（携带本轮卡片，重新打开会话时前端据此渲染）
            ChatMessage aiMsg = new ChatMessage("AI", "TEXT", reply, null, LocalDateTime.now().format(TIME_FMT));
            aiMsg.setCard(card);
            g.getChatHistory().add(aiMsg);
            // 清空本次图片
            g.setImageUrls(new ArrayList<>());
        } catch (Exception e) {
            log.error("业务节点执行失败：{}", e.getMessage(), e);
            g.setErrorMsg("业务节点执行失败：" + e.getMessage());
            g.setAuditInfo("BUSINESS_NODE_ERROR");
            g.setReply("业务处理遇到问题，请稍后再试。");
        }
        return Map.of("graph", g);
    }

    // ===================== 意图识别 =====================

    /**
     * LLM 意图识别入口：渲染 business-intent 模板 → 调模型 → 解析 JSON。
     * 解析失败 / 返回 UNKNOWN / 输出非法枚举时回退关键词兜底 detectIntent，保证路由不中断。
     *
     * @param g 图状态（含角色、对话历史、最新消息）
     * @return 意图枚举值
     */
    private String classifyIntent(MedicalGraphState g) {
        // 路由阶段已通过 LLM 判定意图并写入 routeIntent（真实模式）→ 直接复用，避免对同一消息重复调用 LLM
        String routeIntent = g.getRouteIntent();
        if (routeIntent != null && isValidIntent(routeIntent) && !"UNKNOWN".equals(routeIntent)) {
            return routeIntent;
        }
        // mock 模式不调 LLM，直接走关键词兜底（mock 回复非 JSON，解析必然失败，主动跳过节省开销）
        if (!llmClientService.isMockMode()) {
            try {
                Map<String, Object> vars = new HashMap<>();
                vars.put("userRole", g.getRole() == null ? "" : g.getRole().name());
                vars.put("chatHistory", buildHistoryText(g, 2));
                vars.put("userInput", g.getLastUserText() == null ? "" : g.getLastUserText());
                // 医生端小林使用 doctor-intent，患者客服使用 assistant-intent，预问诊使用 business-intent
                String templateCode = intentTemplateCode(g);
                String prompt = promptTemplateLoader.render(templateCode, vars);
                if (!prompt.isBlank()) {
                    String llmOut = llmClientService.chat(prompt, "");
                    JsonNode node = parseIntentJson(llmOut);
                    String intent = textOf(node, "intent");
                    if (isValidIntent(intent) && !"UNKNOWN".equals(intent)) {
                        // 实体（科室/医生/患者/时间范围）写回图状态，供业务工具精准查询
                        g.setIntentDept(textOf(node, "dept"));
                        g.setIntentDoctorName(textOf(node, "doctorName"));
                        g.setIntentPatientName(textOf(node, "patientName"));
                        g.setIntentTimeDesc(textOf(node, "timeDesc"));
                        return intent;
                    }
                    log.info("LLM 意图识别结果无效（intent={}），回退关键词兜底", intent);
                }
            } catch (Exception e) {
                log.warn("LLM 意图识别失败，回退关键词兜底：{}", e.getMessage());
            }
        }
        return businessToolExecutor.detectIntent(g);
    }

    /**
     * 意图识别模板编码（医生端小林 / 患者端小林 / 预问诊三者独立模板）。
     *
     * @param g 图状态
     * @return 模板编码
     */
    private String intentTemplateCode(MedicalGraphState g) {
        if (g.isAssistantSession()) {
            return g.getRole() == com.hospital.smartmedical.module.ai.agent.enums.UserRole.DOCTOR
                    ? "doctor-intent" : "assistant-intent";
        }
        return "business-intent";
    }

    /**
     * 解析 LLM 意图 JSON 输出（容忍 ```json 代码块包裹与首尾空白），返回整个 JSON 节点。
     *
     * @param llmOut 模型原始输出
     * @return JSON 节点（含 intent/dept/doctorName/timeDesc）；解析失败返回 null
     */
    private JsonNode parseIntentJson(String llmOut) {
        if (llmOut == null || llmOut.isBlank()) {
            return null;
        }
        String json = llmOut.trim();
        // 去除可能的 markdown 代码块包裹
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("LLM 意图 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从意图 JSON 中读取字符串字段（不存在/null/空白/字面量 null 统一返回 null）。
     *
     * @param node JSON 节点（可空）
     * @param name 字段名
     * @return 字段值；无有效值时返回 null
     */
    private String textOf(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.get(name);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText().trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    /**
     * 从业务工具返回结果中提取本轮卡片（挂号成功卡/就诊进度卡），无卡片返回 null。
     *
     * @param result 业务工具结构化结果
     * @return 卡片 Map；无卡片返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractCard(Map<String, Object> result) {
        Object card = result == null ? null : result.get("card");
        return card instanceof Map ? (Map<String, Object>) card : null;
    }

    /**
     * 校验意图值是否在合法枚举集合内。
     *
     * @param intent 意图值
     * @return true=合法
     */
    private boolean isValidIntent(String intent) {
        return intent != null && VALID_INTENTS.contains(intent);
    }

    /**
     * 组装最近 N 轮对话历史文本（供意图识别上下文理解，取最近 2 轮即 4 条消息）。
     *
     * @param g      图状态（含对话历史）
     * @param rounds 取最近轮数
     * @return 历史文本（无历史返回"（无）"）
     */
    private String buildHistoryText(MedicalGraphState g, int rounds) {
        List<ChatMessage> history = g.getChatHistory();
        if (history == null || history.isEmpty()) {
            return "（无）";
        }
        int start = Math.max(0, history.size() - rounds * 2);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            sb.append(m.getSender()).append("：").append(m.getContent() == null ? "" : m.getContent()).append("\n");
        }
        return sb.toString();
    }

    // ===================== 回复润色 =====================

    /**
     * 业务回复 LLM 润色：渲染 business-polish 模板 → 调模型。
     * mock 模式 / 模板缺失 / 润色失败 / 输出为空时回退工具原文，保证业务数据不被破坏。
     *
     * @param g        图状态（透传，当前未使用，保留签名一致性）
     * @param intent   意图（映射润色场景类型）
     * @param rawReply 业务工具原始回复
     * @return 润色后文案（异常/不支持场景回退原文）
     */
    private String polishReply(MedicalGraphState g, String intent, String rawReply) {
        if (llmClientService.isMockMode() || rawReply == null || rawReply.isBlank()) {
            return rawReply;
        }
        String sceneType = toSceneType(intent);
        if (sceneType == null) {
            return rawReply;
        }
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("sceneType", sceneType);
            vars.put("rawContent", rawReply);
            // 医生端小林使用 doctor-polish，患者客服使用 assistant-polish，预问诊使用 business-polish
            String templateCode;
            if (g.isAssistantSession()) {
                templateCode = g.getRole() == com.hospital.smartmedical.module.ai.agent.enums.UserRole.DOCTOR
                        ? "doctor-polish" : "assistant-polish";
            } else {
                templateCode = "business-polish";
            }
            String prompt = promptTemplateLoader.render(templateCode, vars);
            if (prompt.isBlank()) {
                return rawReply;
            }
            String polished = llmClientService.chat(prompt, "");
            if (polished == null || polished.isBlank()) {
                return rawReply;
            }
            return polished.trim();
        } catch (Exception e) {
            log.warn("业务回复润色失败，回退工具原文：{}", e.getMessage());
            return rawReply;
        }
    }

    /**
     * 意图 → 润色场景类型映射（不支持的意图返回 null，跳过润色）。
     *
     * @param intent 意图
     * @return 场景类型；不支持返回 null
     */
    private String toSceneType(String intent) {
        switch (intent == null ? "" : intent) {
            case "APPOINT":
                return "APPOINT";
            case "PROGRESS":
                return "PROGRESS";
            case "PRESCRIPTION_REPORT":
                return "PRESCRIPTION_REPORT";
            case "DOCTOR_PATIENTS":
                return "DOCTOR_PATIENTS";
            case "DOCTOR_PROGRESS":
                return "DOCTOR_PROGRESS";
            default:
                return null;
        }
    }
}

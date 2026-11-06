package com.hospital.smartmedical.module.ai.agent.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.module.ai.agent.enums.ConsultStage;
import com.hospital.smartmedical.module.ai.agent.enums.IntentKeywords;
import com.hospital.smartmedical.module.ai.agent.enums.UserRole;
import com.hospital.smartmedical.module.ai.agent.llm.LlmClientService;
import com.hospital.smartmedical.module.ai.agent.llm.PromptTemplateLoader;
import com.hospital.smartmedical.module.ai.agent.node.AssistantDialogNode;
import com.hospital.smartmedical.module.ai.agent.node.BusinessAgentNode;
import com.hospital.smartmedical.module.ai.agent.node.DoctorAssistAgentNode;
import com.hospital.smartmedical.module.ai.agent.node.PatientDialogAgentNode;
import com.hospital.smartmedical.module.ai.agent.node.SaveDraftAuditNode;
import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import com.hospital.smartmedical.module.ai.agent.tool.TimeTextParser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 多智能体图装配器（LangGraph4j StateGraph）。
 *
 * <p>无 LLM 主管控，采用【会话阶段枚举 + 代码硬编码路由】确定性流转。
 * 节点清单（4 个）：patientDialogAgentNode / businessAgentNode / doctorAssistAgentNode / saveDraftAuditNode。
 * 路由优先级：currentStage &gt; role &gt; 用户意图；各节点固定汇入公共收口节点 saveDraftAuditNode 后结束。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MedicalGraphBuilder {

    /** 患者对话节点名 */
    public static final String NODE_PATIENT_DIALOG = "patientDialogAgentNode";
    /** 数字医生小林（AI 客服）对话节点名 */
    public static final String NODE_ASSISTANT_DIALOG = "assistantDialogAgentNode";
    /** 业务节点名 */
    public static final String NODE_BUSINESS = "businessAgentNode";
    /** 医生辅助节点名 */
    public static final String NODE_DOCTOR_ASSIST = "doctorAssistAgentNode";
    /** 草稿审计收口节点名 */
    public static final String NODE_SAVE_DRAFT = "saveDraftAuditNode";
    /** 条件边映射 key：患者对话 */
    public static final String KEY_PATIENT_DIALOG = "patientDialog";
    /** 条件边映射 key：数字医生小林客服对话 */
    public static final String KEY_ASSISTANT_DIALOG = "assistantDialog";
    /** 条件边映射 key：业务 */
    public static final String KEY_BUSINESS = "business";
    /** 条件边映射 key：医生辅助 */
    public static final String KEY_DOCTOR_ASSIST = "doctorAssist";

    private final PatientDialogAgentNode patientDialogAgentNode;
    private final AssistantDialogNode assistantDialogNode;
    private final BusinessAgentNode businessAgentNode;
    private final DoctorAssistAgentNode doctorAssistAgentNode;
    private final SaveDraftAuditNode saveDraftAuditNode;
    /** 大模型客户端（路由意图 LLM 判断，mock 模式自动跳过） */
    private final LlmClientService llmClientService;
    /** 提示词加载器（管理员端模板热更新） */
    private final PromptTemplateLoader promptTemplateLoader;
    /** Jackson JSON 解析（路由 LLM 意图 JSON 输出解析） */
    private final ObjectMapper objectMapper;

    /** 合法意图枚举集合（与 IntentKeywords 共用同一份，避免路由/业务节点两套枚举漂移） */
    private static final Set<String> VALID_INTENTS = IntentKeywords.VALID_INTENTS;

    /** 编译后的可执行图（线程安全） */
    private volatile CompiledGraph<AgentState> compiledGraph;

    /**
     * 装配并编译图（应用启动时执行一次）。
     */
    @PostConstruct
    public void init() {
        try {
            StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);
            // 注册 5 个节点
            graph.addNode(NODE_PATIENT_DIALOG, AsyncNodeAction.node_async(patientDialogAgentNode));
            graph.addNode(NODE_ASSISTANT_DIALOG, AsyncNodeAction.node_async(assistantDialogNode));
            graph.addNode(NODE_BUSINESS, AsyncNodeAction.node_async(businessAgentNode));
            graph.addNode(NODE_DOCTOR_ASSIST, AsyncNodeAction.node_async(doctorAssistAgentNode));
            graph.addNode(NODE_SAVE_DRAFT, AsyncNodeAction.node_async(saveDraftAuditNode));
            // 入口条件路由：START → 根据会话来源/阶段/角色/意图分发
            graph.addConditionalEdges(GraphDefinition.START, AsyncEdgeAction.edge_async(this::route),
                    Map.of(KEY_PATIENT_DIALOG, NODE_PATIENT_DIALOG,
                            KEY_ASSISTANT_DIALOG, NODE_ASSISTANT_DIALOG,
                            KEY_BUSINESS, NODE_BUSINESS,
                            KEY_DOCTOR_ASSIST, NODE_DOCTOR_ASSIST));
            // 4 个主节点固定汇入公共收口节点
            graph.addEdge(NODE_PATIENT_DIALOG, NODE_SAVE_DRAFT);
            graph.addEdge(NODE_ASSISTANT_DIALOG, NODE_SAVE_DRAFT);
            graph.addEdge(NODE_BUSINESS, NODE_SAVE_DRAFT);
            graph.addEdge(NODE_DOCTOR_ASSIST, NODE_SAVE_DRAFT);
            // 收口节点 → END
            graph.addEdge(NODE_SAVE_DRAFT, GraphDefinition.END);
            this.compiledGraph = graph.compile();
            log.info("AI 多智能体图装配完成：5 节点 + 条件路由（客服会话/预问诊/DOCTOR_RECEIVE）");
        } catch (Exception e) {
            log.error("AI 多智能体图装配失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 获取编译后的可执行图。
     *
     * @return CompiledGraph
     */
    public CompiledGraph<AgentState> graph() {
        return compiledGraph;
    }

    // ===================== 条件路由 =====================

    /**
     * 条件路由核心（会话来源 > currentStage > role > 意图）：
     * <ul>
     *   <li>AI 客服会话（数字医生小林）：业务意图进 businessAgentNode，其余一律 assistantDialogAgentNode</li>
     *   <li>PRE_CONSULT：默认 patientDialogAgentNode；识别到挂号意图切 businessAgentNode</li>
     *   <li>DOCTOR_RECEIVE：默认 doctorAssistAgentNode；仅查询类意图允许进 businessAgentNode</li>
     *   <li>FINISHED：只允许 businessAgentNode</li>
     * </ul>
     *
     * @param state 图状态
     * @return 条件边映射 key（patientDialog / assistantDialog / business / doctorAssist），与 Map.of 的 key 严格一致
     */
    private String route(AgentState state) {
        MedicalGraphState g = state.<MedicalGraphState>value("graph")
                .orElseThrow(() -> new IllegalStateException("缺少 graph 状态"));
        // 意图只解析一次（LLM 主判 + 关键词兜底），各路由分支复用，避免重复调用模型
        String intent = resolveIntent(g);
        boolean business = isBusinessIntentName(intent) && intentAllowed(g, intent);
        // AI 客服会话与预问诊完全隔离：非业务意图（含 FAQ / UNKNOWN）一律走客服对话节点
        if (g.isAssistantSession()) {
            return business ? KEY_BUSINESS : KEY_ASSISTANT_DIALOG;
        }
        ConsultStage stage = g.getCurrentStage() == null ? ConsultStage.PRE_CONSULT : g.getCurrentStage();
        switch (stage) {
            case DOCTOR_RECEIVE:
                return business ? KEY_BUSINESS : KEY_DOCTOR_ASSIST;
            case PRE_CONSULT:
            default:
                return business ? KEY_BUSINESS : KEY_PATIENT_DIALOG;
        }
    }

    /**
     * 意图解析（LLM 主判 + 关键词兜底）：返回合法意图，无法判断返回 null。
     *
     * <p><b>关键规则：合法枚举一律采纳（含 UNKNOWN）。</b>模型判断"这不是业务"本身就是有效结论，
     * 不能因为不是业务就回退关键词——否则「挂号流程是什么」会被关键词「挂号」改判成 APPOINT，
     * 最终答出一屏医生推荐（历史故障）。</p>
     *
     * <p>只有这些情况才回退关键词：模型未返回、JSON 解析失败、输出非法枚举、调用异常、mock 模式。</p>
     *
     * <p>补充：模型判 UNKNOWN 但用户明显在办业务（命中 {@link IntentKeywords#APPOINT_ACTION} 动作短语）时，
     * 兜底提升为 APPOINT——关键词只做"定向兜底"，不再做"全量覆盖"。</p>
     *
     * @param g 图状态
     * @return 意图（APPOINT、FAQ、PROGRESS、PRESCRIPTION_REPORT、DOCTOR_ 系列、UNKNOWN）；无法判断返回 null
     */
    private String resolveIntent(MedicalGraphState g) {
        String text = g.getLastUserText();
        if (text == null || text.isBlank()) {
            return null;
        }
        if (!llmClientService.isMockMode()) {
            try {
                Map<String, Object> vars = new HashMap<>();
                vars.put("userRole", g.getRole() == null ? "" : g.getRole().name());
                vars.put("chatHistory", buildRouteHistory(g));
                vars.put("userInput", text);
                String prompt = promptTemplateLoader.render(intentTemplateCode(g), vars);
                if (!prompt.isBlank()) {
                    String llmOut = llmClientService.chat(prompt, "");
                    JsonNode node = parseRouteIntentJson(llmOut);
                    String intent = textOf(node, "intent");
                    if (intent != null && VALID_INTENTS.contains(intent)) {
                        // 采纳合法枚举（含 UNKNOWN），并写回状态供业务节点复用、避免二次调用模型
                        g.setRouteIntent(intent);
                        g.setIntentDept(textOf(node, "dept"));
                        g.setIntentDoctorName(textOf(node, "doctorName"));
                        g.setIntentPatientName(textOf(node, "patientName"));
                        g.setIntentTimeDesc(textOf(node, "timeDesc"));
                        log.info("路由 LLM 意图：intent={}，dept={}，doctor={}，patient={}，time={}", intent,
                                g.getIntentDept(), g.getIntentDoctorName(), g.getIntentPatientName(), g.getIntentTimeDesc());
                        // 能力隔离：当前会话不开放的意图降级为 UNKNOWN，交给对话节点引导
                        if (!"UNKNOWN".equals(intent) && !intentAllowed(g, intent)) {
                            log.info("会话能力隔离：当前会话不开放意图 {}，交由对话节点引导（sessionId={}）",
                                    intent, g.getSessionId());
                            return "UNKNOWN";
                        }
                        // 模型判 UNKNOWN，但用户其实在说"帮我挂/我要挂" → 定向提升为 APPOINT
                        if ("UNKNOWN".equals(intent) && containsAny(text, IntentKeywords.APPOINT_ACTION)) {
                            log.info("LLM 判 UNKNOWN 但命中挂号动作短语，兜底提升为 APPOINT（sessionId={}）", g.getSessionId());
                            g.setRouteIntent("APPOINT");
                            return "APPOINT";
                        }
                        // 模型判 UNKNOWN，但存在待确认号源且患者回复"可以/好的" → 提升为 APPOINT（确认轮）
                        if ("UNKNOWN".equals(intent) && g.getPendingSlotId() != null
                                && containsAny(text, IntentKeywords.APPOINT_CONFIRM)) {
                            log.info("LLM 判 UNKNOWN 但存在待确认号源且患者表示同意，提升为 APPOINT（sessionId={}）",
                                    g.getSessionId());
                            g.setRouteIntent("APPOINT");
                            return "APPOINT";
                        }
                        // 模型判 UNKNOWN，但上一轮 AI 在问就诊时间、本轮是纯时间回复（如"明天下午四点"）→ APPOINT
                        if ("UNKNOWN".equals(intent) && lastAiAsksAppointTime(g)
                                && TimeTextParser.parse(text) != null) {
                            log.info("LLM 判 UNKNOWN 但处于挂号时间确认环节，提升为 APPOINT（sessionId={}）",
                                    g.getSessionId());
                            g.setRouteIntent("APPOINT");
                            return "APPOINT";
                        }
                        return intent;
                    }
                    log.info("路由 LLM 输出非法意图（intent={}），回退关键词兜底", intent);
                }
            } catch (Exception e) {
                log.warn("路由 LLM 意图识别失败，回退关键词兜底：{}", e.getMessage());
            }
        }
        return keywordIntent(g);
    }

    /**
     * 是否属于「需要进入业务节点」的意图。
     *
     * <p>FAQ（问流程/材料/费用）与 UNKNOWN（闲聊/咨询）都不算业务，交给对话节点。</p>
     *
     * @param intent 意图
     * @return true=业务意图
     */
    static boolean isBusinessIntentName(String intent) {
        return "APPOINT".equals(intent)
                || "PROGRESS".equals(intent)
                || "PRESCRIPTION_REPORT".equals(intent)
                || "DOCTOR_PATIENTS".equals(intent)
                || "DOCTOR_PROGRESS".equals(intent);
    }

    /**
     * 意图识别模板编码：客服会话用小林专属模板，其余用通用业务模板。
     *
     * @param g 图状态
     * @return 模板编码
     */
    private String intentTemplateCode(MedicalGraphState g) {
        if (g.isAssistantSession()) {
            // 医生端小林使用医生专属意图模板（抽取患者姓名与时间范围），患者客服使用小林模板
            return g.getRole() == UserRole.DOCTOR ? "doctor-intent" : "assistant-intent";
        }
        return "business-intent";
    }

    /**
     * 会话能力白名单：医生端小林开放接诊患者与就诊进度查询；患者端小林开放全部患者业务能力；
     * 预问诊患者仅开放挂号。
     *
     * @param g      图状态
     * @param intent 识别出的意图
     * @return true=该会话允许执行此业务意图
     */
    private boolean intentAllowed(MedicalGraphState g, String intent) {
        // 医生会话能力边界优先判定（医生端只开放接诊相关查询，不允许患者侧挂号/报告意图）
        if (g.getRole() == UserRole.DOCTOR) {
            return "DOCTOR_PATIENTS".equals(intent) || "DOCTOR_PROGRESS".equals(intent);
        }
        if (g.isAssistantSession()) {
            return true;
        }
        return "APPOINT".equals(intent);
    }

    /**
     * 路由意图 LLM 输出 JSON 解析（容忍 ```json 代码块包裹与首尾空白），返回整个 JSON 节点。
     *
     * @param llmOut 模型原始输出
     * @return JSON 节点（含 intent/dept/doctorName/timeDesc）；解析失败返回 null
     */
    private JsonNode parseRouteIntentJson(String llmOut) {
        if (llmOut == null || llmOut.isBlank()) {
            return null;
        }
        String json = llmOut.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("路由 LLM 意图 JSON 解析失败：{}", e.getMessage());
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
     * 组装最近 2 轮对话历史文本（供路由 LLM 意图判断理解上下文）。
     *
     * @param g 图状态（含对话历史）
     * @return 历史文本（无历史返回"（无）"）
     */
    private String buildRouteHistory(MedicalGraphState g) {
        List<ChatMessage> history = g.getChatHistory();
        if (history == null || history.isEmpty()) {
            return "（无）";
        }
        int start = Math.max(0, history.size() - 4);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            sb.append(m.getSender()).append("：").append(m.getContent() == null ? "" : m.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 关键词兜底意图（仅在模型未给出合法枚举时使用：mock 模式 / 解析失败 / 非法值 / 调用异常）。
     *
     * @param g 图状态
     * @return 意图；无命中返回 UNKNOWN
     */
    private String keywordIntent(MedicalGraphState g) {
        String text = g.getLastUserText();
        if (text == null || text.isBlank()) {
            return "UNKNOWN";
        }
        if (g.getRole() == UserRole.DOCTOR) {
            // 就诊进度查询优先（"查患者就诊进度"同时命中两类关键词时按进度处理）
            if (containsAny(text, IntentKeywords.DOCTOR_PROGRESS)) {
                return "DOCTOR_PROGRESS";
            }
            return containsAny(text, IntentKeywords.DOCTOR_PATIENTS) ? "DOCTOR_PATIENTS" : "UNKNOWN";
        }
        // 挂号确认轮：存在待确认号源且患者回复"可以/好的" → 仍走业务节点完成下单
        if (g.getPendingSlotId() != null
                && !containsAny(text, IntentKeywords.APPOINT_DENY)
                && containsAny(text, IntentKeywords.APPOINT_CONFIRM)) {
            return "APPOINT";
        }
        // 挂号时间确认轮：上一轮 AI 刚问过就诊时间，本轮是纯时间回复（支持中文数字）→ 仍走业务节点
        if (lastAiAsksAppointTime(g) && TimeTextParser.parse(text) != null) {
            return "APPOINT";
        }
        // 科室推荐 + 流程/材料/费用/时间/地点类问题不算挂号意图
        if (containsAny(text, IntentKeywords.APPOINT_EXCLUDED)) {
            return "UNKNOWN";
        }
        // 挂号确认环节：患者只回复时间（如"明天上午9点"）且最近 AI 在引导确认挂号时间 → 仍走业务节点
        if (containsAny(text, IntentKeywords.APPOINT_TIME_WORDS)
                && lastAiAsksAppointTime(g)) {
            return "APPOINT";
        }
        // 挂号意图：负向词闸门前置拦截"挂念/挂科/挂电话"这类通用词；不再用单字"挂"
        boolean negative = containsAny(text, IntentKeywords.APPOINT_NEGATIVE);
        if (!negative && containsAny(text, IntentKeywords.APPOINT_POSITIVE)) {
            return "APPOINT";
        }
        // 能力隔离：预问诊会话只提供挂号引导，进度/报告/处方查询由数字医生小林承担
        if (!g.isAssistantSession()) {
            return "UNKNOWN";
        }
        if (containsAny(text, IntentKeywords.PROGRESS)) {
            return "PROGRESS";
        }
        if (containsAny(text, IntentKeywords.REPORT_PRESCRIPTION)) {
            return "PRESCRIPTION_REPORT";
        }
        return "UNKNOWN";
    }

    /**
     * 判断最近一条 AI 回复是否在引导患者确认挂号时间（跨轮挂号确认场景）。
     *
     * @param g 图状态（含对话历史）
     * @return true=最近 AI 回复含挂号时间引导文案
     */
    private boolean lastAiAsksAppointTime(MedicalGraphState g) {
        List<ChatMessage> history = g.getChatHistory();
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if ("AI".equals(msg.getSender()) && msg.getContent() != null) {
                return msg.getContent().contains("就诊的时间") || msg.getContent().contains("为您挂号");
            }
        }
        return false;
    }

    /**
     * 是否包含任一关键词。
     */
    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}

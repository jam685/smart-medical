package com.hospital.smartmedical.module.ai.agent.node;

import com.hospital.smartmedical.module.ai.agent.enums.ConsultStage;
import com.hospital.smartmedical.module.ai.agent.llm.LlmClientService;
import com.hospital.smartmedical.module.ai.agent.llm.PromptTemplateLoader;
import com.hospital.smartmedical.module.ai.agent.rag.DoctorRagService;
import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import com.hospital.smartmedical.module.ai.agent.stream.StreamTokenRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 患者对话 Agent 节点（patientDialogAgentNode）。
 *
 * <p>仅 PRE_CONSULT 阶段可用：引导患者描述症状、解析图片、汇总症状摘要、推荐科室；
 * 只做预问诊交互，不处理业务查询（业务查询由 businessAgentNode 承担）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatientDialogAgentNode implements NodeAction<AgentState> {

    private final LlmClientService llmClientService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final DoctorRagService doctorRagService;

    /** 时间格式化 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 判断是否为本次会话的首轮对话（仅患者一条消息、尚无 AI 回复）。
     *
     * <p>首轮时提示词注入开场白要求（问候患者），非首轮直接承接上文回复。</p>
     *
     * @param history 对话历史（含刚追加的本次用户消息）
     * @return true=首轮
     */
    private boolean isFirstRound(List<ChatMessage> history) {
        if (history == null) {
            return false;
        }
        // 对话历史中只有本次用户消息（首条 PATIENT 消息、无任何 AI 回复）视为首轮
        if (history.size() != 1) {
            return false;
        }
        ChatMessage first = history.get(0);
        return first != null && !"AI".equals(first.getSender());
    }

    /**
     * 组装本轮 RAG 病情检索上下文文本（最新症状描述 + 已收集症状摘要）。
     *
     * @param g 图状态
     * @return 检索上下文文本（不可用/无可检索内容时返回空串）
     */
    private String buildRagContext(MedicalGraphState g) {
        StringBuilder query = new StringBuilder();
        if (g.getSymptomSummary() != null && !g.getSymptomSummary().isBlank()) {
            query.append(g.getSymptomSummary()).append(" ");
        }
        String text = g.getLastUserText();
        if (text != null && !text.isBlank()) {
            query.append(text);
        }
        if (query.length() == 0) {
            return "";
        }
        // 语义检索知识库，最多取 5 条相关科室医生
        return doctorRagService.buildRagContext(query.toString().trim(), 2);
    }

    /**
     * 节点执行：生成症状摘要 + 科室推荐 + 引导回复草稿。
     *
     * @param state 图状态（含 graph 键的 MedicalGraphState）
     * @return 更新后的 graph 状态（Map 单键返回）
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        MedicalGraphState g = state.<MedicalGraphState>value("graph")
                .orElseThrow(() -> new IllegalStateException("缺少 graph 状态"));
        // 阶段门禁：仅 PRE_CONSULT 允许进入本节点
        if (g.getCurrentStage() != ConsultStage.PRE_CONSULT) {
            g.setErrorMsg("patientDialogAgentNode 仅允许 PRE_CONSULT 阶段调用（当前阶段：" + g.getCurrentStage() + "）");
            log.warn("阶段门禁拦截：{}", g.getErrorMsg());
            return Map.of("graph", g);
        }
        try {
            String lastText = g.getLastUserText() == null ? "" : g.getLastUserText();
            // 最近对话历史（患者/AI 实际往来，注入提示词让模型知道已问过什么、患者已回答过什么，避免重复提问）
            String history = buildHistory(g.getChatHistory());
            String imageList = (g.getImageUrls() == null || g.getImageUrls().isEmpty())
                    ? "无" : String.join("、", g.getImageUrls());
            // 首轮开场白指令：仅新会话第一条患者消息时要求问候，非首轮直接承接上文
            boolean firstRound = isFirstRound(g.getChatHistory());
            String greeting = firstRound
                    ? "这是本次对话的开场。请先用自然亲切的语气问候患者并自我介绍，"
                            + "例如：你好，我是预问诊AI助手，很高兴为您服务。请问今天有什么可以帮您？"
                            + "（仅开场使用，后续对话不必重复问候）"
                    : "（非开场轮次，直接承接上文回复，不要重复问候与自我介绍）";
            // 每轮 RAG 病情检索：以最新症状描述语义检索知识库，注入匹配科室医生供推荐参考
            String ragContext = buildRagContext(g);
            // 患者基本情况档案（姓名/性别/年龄），Agent 跨轮记忆用
            String profile = g.getPatientProfile() == null ? "" : g.getPatientProfile();
            // 提示词外置模板（真实模式发送给大模型，mock 模式由 LlmClientService 本地规则回复）
            String system = promptTemplateLoader.render("patient-dialog",
                    Map.of("symptom", lastText,
                            "summary", g.getSymptomSummary() == null ? "" : g.getSymptomSummary(),
                            "history", history,
                            "imageList", imageList,
                            "profile", profile,
                            "ragContext", ragContext,
                            "greeting", greeting));
            // 1) 症状摘要更新 + 科室推荐（阶段 A：关键词规则；真实模式结构化解析后续接入）
            g.setSymptomSummary(llmClientService.summarizeSymptoms(g.getSymptomSummary(), lastText));
            g.setSuggestDept(llmClientService.suggestDepartment(lastText));
            // 2) 生成患者引导回复草稿（流式请求走增量推送，消除加载等待阶段；节点线程从注册表取回调）
            java.util.function.Consumer<String> onToken = StreamTokenRegistry.get(g.getSessionId());
            String reply;
            if (onToken != null) {
                reply = llmClientService.chatStreaming(system, lastText, g.getImageUrls(), onToken);
            } else {
                reply = llmClientService.chatWithImages(system, lastText, g.getImageUrls());
            }
            g.setReplyDraft(reply);
            g.setReply(reply);
            // 3) 本轮产生症状摘要草稿（saveDraftAuditNode 落库 + 审计）
            g.setHasNewDraft(true);
            g.setDraftType("SYMPTOM_SUMMARY");
            // 4) 追加 AI 消息到对话历史
            g.getChatHistory().add(new ChatMessage("AI", "TEXT", reply, null, LocalDateTime.now().format(TIME_FMT)));
            // 5) 清空本次图片（已消费进上下文）
            g.setImageUrls(new ArrayList<>());
        } catch (Exception e) {
            log.error("患者对话节点执行失败：{}", e.getMessage(), e);
            g.setErrorMsg("患者对话节点执行失败：" + e.getMessage());
            g.setAuditInfo("PATIENT_DIALOG_ERROR");
            g.setReply("抱歉，暂时无法回复，请稍后再试。");
        }
        return Map.of("graph", g);
    }

    /**
     * 从对话历史提取最近几轮患者与 AI 的往来文本，注入提示词供模型参考。
     * 对话历史已包含本次用户消息（AiAgentService 先追加再走图），模型据此避免重复提问已答信息。
     *
     * @param history 完整对话历史
     * @return 最近 6 条格式化为 "患者：xxx / AI：xxx" 的文本；无记录返回"（暂无）"
     */
    private String buildHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "（暂无）";
        }
        List<ChatMessage> recent = history.size() > 6 ? history.subList(history.size() - 6, history.size()) : history;
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : recent) {
            String sender = "AI".equals(m.getSender()) ? "AI" : "患者";
            String content = m.getContent() == null || m.getContent().isBlank() ? "（图片）" : m.getContent();
            sb.append(sender).append("：").append(content).append("\n");
        }
        return sb.toString();
    }
}

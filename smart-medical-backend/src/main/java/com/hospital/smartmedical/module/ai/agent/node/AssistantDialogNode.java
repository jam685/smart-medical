package com.hospital.smartmedical.module.ai.agent.node;

import com.hospital.smartmedical.module.ai.agent.enums.ReplySource;
import com.hospital.smartmedical.module.ai.agent.enums.UserRole;
import com.hospital.smartmedical.module.ai.agent.llm.LlmClientService;
import com.hospital.smartmedical.module.ai.agent.llm.PromptTemplateLoader;
import com.hospital.smartmedical.module.ai.agent.rag.FaqRagService;
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
import java.util.function.Consumer;

/**
 * 数字医生小林（AI 客服）对话节点（assistantDialogAgentNode）。
 *
 * <p>仅客服会话（source=assistant / cs_ 会话）使用，与预问诊节点完全隔离：
 * 不采集症状、不生成症状摘要、不做科室推荐 RAG，只用 assistant-dialog 模板做客服接待与流程引导；
 * 业务查询（进度/报告/挂号）仍由 businessAgentNode 承担。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantDialogNode implements NodeAction<AgentState> {

    private final LlmClientService llmClientService;
    private final PromptTemplateLoader promptTemplateLoader;
    /** FAQ 知识库（流程/材料/费用类问题的回答依据） */
    private final FaqRagService faqRagService;

    /** 时间格式化 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 患者端客服可提供的服务说明（注入提示词，约束回复边界） */
    private static final String CAPABILITIES = "查询就诊进度、查看检查报告与处方、预约挂号、就诊须知与科室位置咨询";

    /** 医生端小林可提供的服务说明（仅查询本人接诊患者的就诊进度） */
    private static final String DOCTOR_CAPABILITIES = "按患者姓名与时间段查询本人接诊患者的就诊进度";

    /** 医生端模板缺失时的兜底引导文案（模板由管理员端补齐后自动生效） */
    private static final String DOCTOR_FALLBACK_REPLY =
            "您好，我是数字医生小林。您可以这样问我：帮我查一下张伟今天上午的就诊进度、查看本周的接诊进度。";

    /** 患者端模板缺失时的兜底引导文案 */
    private static final String PATIENT_FALLBACK_REPLY =
            "您好，我是数字医生小林，可以帮您查询就诊进度、查看检查报告与处方、预约挂号。请问有什么需要帮忙的？";

    /**
     * 节点执行：渲染客服专属模板 → 生成接待回复（流式时增量推送）→ 追加 AI 消息。
     *
     * @param state 图状态（含 graph 键的 MedicalGraphState）
     * @return 更新后的 graph 状态
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        MedicalGraphState g = state.<MedicalGraphState>value("graph")
                .orElseThrow(() -> new IllegalStateException("缺少 graph 状态"));
        if (!g.isAssistantSession()) {
            log.warn("非客服会话进入 AssistantDialogNode（sessionId={}），按客服模板兜底处理", g.getSessionId());
        }
        try {
            String lastText = g.getLastUserText() == null ? "" : g.getLastUserText();
            boolean doctor = g.getRole() == UserRole.DOCTOR;
            // 首轮开场白：自报服务范围；非首轮直接承接上文
            String greeting = isFirstRound(g.getChatHistory())
                    ? (doctor
                    ? "这是本次对话的开场。请用简洁专业的语气问候医生并自我介绍（例如：您好，我是数字医生小林），"
                            + "并一句话说明可以按患者姓名与时间段查询其就诊进度。"
                    : "这是本次对话的开场。请用亲切简洁的语气问候患者并自我介绍（例如：你好，我是数字医生小林），"
                            + "并一句话说明你可以帮他查询就诊进度、查看报告处方或预约挂号。")
                    : "（非开场轮次，直接承接上文回复，不要重复问候与自我介绍）";
            // 档案注入：医生端使用医生档案（姓名/科室/职称/工号），患者端使用患者档案（姓名/性别/年龄）
            String profile = doctor
                    ? (g.getDoctorProfile() == null ? "" : g.getDoctorProfile())
                    : (g.getPatientProfile() == null ? "" : g.getPatientProfile());
            String system = promptTemplateLoader.render(doctor ? "doctor-dialog" : "assistant-dialog",
                    Map.of("profile", profile,
                            "capabilities", doctor ? DOCTOR_CAPABILITIES : CAPABILITIES,
                            "history", buildHistory(g.getChatHistory(), doctor),
                            "userInput", lastText,
                            "greeting", greeting));
            // FAQ 知识库：流程/材料/费用/时间类问题优先用知识库内容作答（仅患者端；医生端不注入）
            String faqContext = doctor ? "" : faqRagService.buildContext(lastText, 3);
            if (!system.isBlank() && !faqContext.isBlank()) {
                system = system + "\n\n【本院流程与须知（来自知识库，回答流程/材料/费用/时间类问题时优先使用；"
                        + "与常识冲突时以本段为准；不要编造本段未提供的信息）】\n" + faqContext;
            }
            String reply;
            if (system.isBlank()) {
                // 模板缺失（管理员端尚未配置）：不做 LLM 调用，直接返回兜底引导，避免空系统提示词乱答
                log.warn("提示词模板缺失：{}，使用兜底引导文案（sessionId={}）",
                        doctor ? "doctor-dialog" : "assistant-dialog", g.getSessionId());
                reply = doctor ? DOCTOR_FALLBACK_REPLY : PATIENT_FALLBACK_REPLY;
            } else {
                // 流式请求走增量推送（节点线程从注册表取回调），非流式一次性返回
                Consumer<String> onToken = StreamTokenRegistry.get(g.getSessionId());
                reply = onToken != null
                        ? llmClientService.chatStreaming(system, lastText, g.getImageUrls(), onToken)
                        : llmClientService.chatWithImages(system, lastText, g.getImageUrls());
            }
            g.setReply(reply);
            g.setReplyDraft(reply);
            // 标记来源：只有客服对话节点的通用问答才允许进入语义缓存
            g.setReplySource(ReplySource.ASSISTANT_DIALOG);
            // 客服会话不产生预问诊草稿，不落草稿表
            g.setHasNewDraft(false);
            g.getChatHistory().add(new ChatMessage("AI", "TEXT", reply, null, LocalDateTime.now().format(TIME_FMT)));
            // 清空本次图片（已消费进上下文）
            g.setImageUrls(new ArrayList<>());
        } catch (Exception e) {
            log.error("客服对话节点执行失败：{}", e.getMessage(), e);
            g.setErrorMsg("客服对话节点执行失败：" + e.getMessage());
            g.setAuditInfo("ASSISTANT_DIALOG_ERROR");
            g.setReply("抱歉，小林暂时无法回复，请稍后再试。");
        }
        return Map.of("graph", g);
    }

    /**
     * 判断是否本次会话首轮（仅一条患者消息、尚无 AI 回复）。
     *
     * @param history 对话历史（含刚追加的本次用户消息）
     * @return true=首轮
     */
    private boolean isFirstRound(List<ChatMessage> history) {
        if (history == null || history.size() != 1) {
            return false;
        }
        ChatMessage first = history.get(0);
        return first != null && !"AI".equals(first.getSender());
    }

    /**
     * 组装最近 6 条对话历史文本（供客服模板理解上下文）。
     *
     * @param history 完整对话历史
     * @param doctor  是否医生端会话（决定用户侧称呼：医生/患者）
     * @return 格式化文本；无记录返回"（暂无）"
     */
    private String buildHistory(List<ChatMessage> history, boolean doctor) {
        if (history == null || history.isEmpty()) {
            return "（暂无）";
        }
        List<ChatMessage> recent = history.size() > 6 ? history.subList(history.size() - 6, history.size()) : history;
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : recent) {
            String sender = "AI".equals(m.getSender()) ? "小林" : (doctor ? "医生" : "患者");
            String content = m.getContent() == null || m.getContent().isBlank() ? "（图片）" : m.getContent();
            sb.append(sender).append("：").append(content).append("\n");
        }
        return sb.toString();
    }
}

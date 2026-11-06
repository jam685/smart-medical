package com.hospital.smartmedical.module.ai.agent.node;

import com.hospital.smartmedical.module.ai.agent.enums.ConsultStage;
import com.hospital.smartmedical.module.ai.agent.enums.ReplySource;
import com.hospital.smartmedical.module.ai.agent.llm.LlmClientService;
import com.hospital.smartmedical.module.ai.agent.llm.PromptTemplateLoader;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 医生辅助 Agent 节点（doctorAssistAgentNode）。
 *
 * <p>仅 DOCTOR_RECEIVE 阶段可用：读取医患对话 + 图片，生成医生回复草稿；
 * 医生消息含面诊结束/开方意图时生成处方草稿；草稿仅写入 State，不操作数据库
 * （落库由公共节点 saveDraftAuditNode 统一收口）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DoctorAssistAgentNode implements NodeAction<AgentState> {

    private final LlmClientService llmClientService;
    private final PromptTemplateLoader promptTemplateLoader;

    /** 时间格式化 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 节点执行：基于医患对话历史 + 图片生成医生回复草稿 / 处方草稿。
     *
     * @param state 图状态（含 graph 键的 MedicalGraphState）
     * @return 更新后的 graph 状态
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        MedicalGraphState g = state.<MedicalGraphState>value("graph")
                .orElseThrow(() -> new IllegalStateException("缺少 graph 状态"));
        // 阶段门禁：仅 DOCTOR_RECEIVE 允许进入本节点
        if (g.getCurrentStage() != ConsultStage.DOCTOR_RECEIVE) {
            g.setErrorMsg("doctorAssistAgentNode 仅允许 DOCTOR_RECEIVE 阶段调用（当前阶段：" + g.getCurrentStage() + "）");
            log.warn("阶段门禁拦截：{}", g.getErrorMsg());
            return Map.of("graph", g);
        }
        try {
            // 医患对话历史文本化（不含 AI 消息，仅 PATIENT/DOCTOR 实际对话）
            String history = g.getChatHistory().stream()
                    .filter(m -> !"AI".equals(m.getSender()))
                    .map(m -> (m.getSender() == null ? "" : m.getSender()) + "：" + (m.getContent() == null ? "（图片）" : m.getContent()))
                    .collect(Collectors.joining("\n"));
            String imageNote = (g.getImageUrls() == null || g.getImageUrls().isEmpty())
                    ? "无" : String.join("、", g.getImageUrls());
            String system = promptTemplateLoader.render("doctor-assist",
                    Map.of("history", history, "images", imageNote));
            // 生成医生回复草稿（流式请求走增量推送，真实模式走大模型，mock 模式医生专用本地规则；节点线程从注册表取回调）
            java.util.function.Consumer<String> onToken = StreamTokenRegistry.get(g.getSessionId());
            String replyDraft;
            if (onToken != null) {
                replyDraft = llmClientService.doctorAssistChatStreaming(system, history, imageNote, onToken);
            } else {
                replyDraft = llmClientService.doctorAssistChat(system, history, imageNote);
            }
            g.setReplyDraft(replyDraft);
            // 医生消息含面诊结束/开方意图 → 生成处方草稿
            String last = g.getLastUserText() == null ? "" : g.getLastUserText();
            boolean finishIntent = last.contains("处方") || last.contains("开药") || last.contains("开方")
                    || last.contains("结束") || last.contains("完成面诊");
            if (finishIntent) {
                g.setPrescriptionDraft(llmClientService.prescriptionDraft(history));
                g.setDraftType("PRESCRIPTION_DRAFT");
            } else {
                g.setDraftType("REPLY_DRAFT");
            }
            g.setReply(replyDraft);
            // 标记来源：医生草稿不参与语义缓存
            g.setReplySource(ReplySource.DOCTOR_ASSIST);
            g.setHasNewDraft(true);
            // 追加 AI 消息到对话历史
            g.getChatHistory().add(new ChatMessage("AI", "TEXT", replyDraft, null, LocalDateTime.now().format(TIME_FMT)));
        } catch (Exception e) {
            log.error("医生辅助节点执行失败：{}", e.getMessage(), e);
            g.setErrorMsg("医生辅助节点执行失败：" + e.getMessage());
            g.setAuditInfo("DOCTOR_ASSIST_ERROR");
            g.setReply("草稿生成失败，请稍后再试。");
        }
        return Map.of("graph", g);
    }
}

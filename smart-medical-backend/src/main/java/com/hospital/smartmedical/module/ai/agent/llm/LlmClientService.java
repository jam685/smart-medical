package com.hospital.smartmedical.module.ai.agent.llm;

import com.hospital.smartmedical.module.ai.agent.config.AiModelConfigProvider;
import com.hospital.smartmedical.module.ai.agent.config.LlmConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 大模型调用客户端（LangChain4j 封装，所有 Agent 复用）。
 *
 * <p>mock-mode=true：本地规则生成模拟回复（开发演示用，不发外部请求）；
 * mock-mode=false：OpenAI 兼容 ChatModel，带超时 + 重试 + 调用日志。
 * 真实模式结构化输出：提示词要求返回 JSON，由调用方用 Jackson 解析（异常兜底写入 auditInfo）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmClientService {

    private final LlmConfig config;

    /** 模型配置提供者：优先使用 sm_model_config 表中启用的聊天模型，管理端修改后热更新 */
    private final AiModelConfigProvider modelConfigProvider;

    /** 当前模型客户端对应的配置签名（签名变化即重建 ChatModel，实现热更新） */
    private volatile String modelSignature;

    /** 是否 mock 模式（application.yml ai.mock-mode） */
    @Value("${ai.mock-mode:true}")
    private boolean mockMode;

    /**
     * 是否 mock 模式（业务意图识别/回复润色在 mock 模式下不调 LLM，直接走关键词兜底与工具原文）。
     *
     * @return true=mock 模式（本地规则生成模拟回复）
     */
    public boolean isMockMode() {
        return mockMode;
    }

    /** 真实 ChatModel（懒加载，mock 模式不创建） */
    private volatile ChatModel chatModel;

    /** 真实流式 ChatModel（懒加载，流式请求才创建） */
    private volatile StreamingChatModel streamingChatModel;

    /**
     * 通用文本生成：system + user 拼接为单提示词调用模型（无流式回调，一次性返回全文）。
     *
     * @param systemPrompt 系统提示词（风控约束、输出格式要求）
     * @param userContent  用户内容
     * @return 模型回复文本
     */
    public String chat(String systemPrompt, String userContent) {
        return doChat(systemPrompt, userContent, null, null);
    }

    /**
     * 图文输入生成（阶段 A mock 模式直接把图片地址作为提示词上下文，真实多模态调用后续阶段接入）。
     */
    public String chatWithImages(String systemPrompt, String userContent, List<String> imageUrls) {
        return doChat(systemPrompt, userContent, imageUrls, null);
    }

    /**
     * 图文输入流式生成：模型增量文本实时回调 onToken（SSE 推送），结束后返回全文。
     * 用于患者对话节点：流式输出消除"等待生成"加载阶段，逐句引导提升体验。
     *
     * @param systemPrompt 系统提示词
     * @param userContent  用户内容
     * @param imageUrls    本次上传图片地址（可空）
     * @param onToken      增量文本回调（每产生一个片段即调用一次）
     * @return 模型完整回复文本
     */
    public String chatStreaming(String systemPrompt, String userContent, List<String> imageUrls, Consumer<String> onToken) {
        return doChat(systemPrompt, userContent, imageUrls, onToken);
    }

    /**
     * 通用文本生成核心逻辑（流式/非流式统一收口）。
     * mock 模式：本地规则生成模拟回复，有 onToken 时按句推送模拟流式节奏；
     * 真实模式：有 onToken 走 StreamingChatModel 增量回调，无则同步调用。
     */
    private String doChat(String systemPrompt, String userContent, List<String> imageUrls, Consumer<String> onToken) {
        String content = userContent;
        if (imageUrls != null && !imageUrls.isEmpty()) {
            content = content + "\n[本次上传图片 " + imageUrls.size() + " 张，地址：" + String.join("、", imageUrls) + "]";
        }
        if (mockMode) {
            String reply = mockReply(content);
            if (onToken != null) {
                streamMockText(reply, onToken);
            }
            return reply;
        }
        String prompt = systemPrompt + "\n\n[用户内容]\n" + content;
        if (onToken != null) {
            return streamReal(prompt, onToken);
        }
        return callReal(prompt);
    }

    /**
     * 科室推荐（返回科室名，无法判断返回 null）。
     */
    public String suggestDepartment(String symptom) {
        if (symptom == null || symptom.isBlank()) {
            return null;
        }
        // 内分泌相关
        if (symptom.contains("血糖") || symptom.contains("糖尿病") || symptom.contains("内分泌")
                || symptom.contains("口渴") || symptom.contains("多饮") || symptom.contains("多尿")) {
            return "内分泌科";
        }
        // 心血管相关
        if (symptom.contains("胸痛") || symptom.contains("心慌") || symptom.contains("心血管")
                || symptom.contains("高血压") || symptom.contains("心悸")) {
            return "心内科";
        }
        // 呼吸相关
        if (symptom.contains("咳嗽") || symptom.contains("呼吸") || symptom.contains("发热")
                || symptom.contains("咳痰") || symptom.contains("气喘")) {
            return "呼吸科";
        }
        return null;
    }

    /**
     * 症状摘要更新（追加式合并，避免重复）。
     */
    public String summarizeSymptoms(String existing, String newText) {
        if (newText == null || newText.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return newText.trim();
        }
        // 简单去重合并：新文本已包含摘要主关键词时不再重复追加
        if (existing.contains(newText.trim()) || existing.length() >= 200) {
            return existing;
        }
        return existing + "；" + newText.trim();
    }

    /**
     * 预问诊患者引导回复（患者对话节点使用）。
     */
    public String patientDialogReply(String symptomSummary, String suggestDept, String imageNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("您好，我已经记录到您的症状：").append(symptomSummary == null || symptomSummary.isBlank()
                ? "（请您补充具体不适）" : symptomSummary).append("。");
        if (suggestDept != null) {
            sb.append("根据描述，初步建议您挂【").append(suggestDept).append("】做进一步检查。");
        }
        sb.append("为了更准确地帮您引导，能否补充：1）症状持续多久了？2）有没有加重或缓解的因素？")
                .append(imageNote)
                .append("（我是智能预问诊助手，只做症状采集与挂号引导，不做诊断、不开方，最终诊疗请以医生判断为准）");
        return sb.toString();
    }

    /**
     * 医生辅助回复生成（doctorAssistAgentNode 专用，非流式，一次性返回全文）：
     * mock 模式按历史文本规则生成回复草稿；真实模式把 system + 历史 + 图片上下文发给模型。
     */
    public String doctorAssistChat(String systemPrompt, String history, String imageNote) {
        return doDoctorAssistChat(systemPrompt, history, imageNote, null);
    }

    /**
     * 医生辅助回复流式生成：模型增量文本实时回调 onToken（SSE 推送），结束后返回全文。
     * 用于医生辅助节点：流式输出让医生无需等待即可看到草稿逐步生成。
     *
     * @param systemPrompt 系统提示词
     * @param history      医患对话历史文本
     * @param imageNote    图片上下文说明（无图片传"无"）
     * @param onToken      增量文本回调（每产生一个片段即调用一次）
     * @return 医生回复草稿全文
     */
    public String doctorAssistChatStreaming(String systemPrompt, String history, String imageNote, Consumer<String> onToken) {
        return doDoctorAssistChat(systemPrompt, history, imageNote, onToken);
    }

    /**
     * 医生辅助生成核心逻辑（流式/非流式统一收口）。
     * mock 模式：本地规则生成回复草稿，有 onToken 时按句推送模拟流式节奏；
     * 真实模式：有 onToken 走 StreamingChatModel 增量回调，无则同步调用。
     */
    private String doDoctorAssistChat(String systemPrompt, String history, String imageNote, Consumer<String> onToken) {
        if (mockMode) {
            String reply = doctorReplyDraft(history, imageNote);
            if (onToken != null) {
                streamMockText(reply, onToken);
            }
            return reply;
        }
        String prompt = systemPrompt + "\n\n[医患对话历史]\n" + history + "\n[图片]\n" + imageNote;
        if (onToken != null) {
            return streamReal(prompt, onToken);
        }
        return callReal(prompt);
    }

    /**
     * 医生回复草稿（医生辅助节点使用，mock 规则模板）。
     */
    public String doctorReplyDraft(String history, String imageNote) {
        String dept = suggestDepartment(history == null ? "" : history);
        StringBuilder sb = new StringBuilder("根据患者的描述与对话记录，已为您整理以下要点：");
        if (dept != null) {
            sb.append("症状主要与【").append(dept).append("】相关，建议结合专科查体与辅助检查进一步明确诊断。");
        } else {
            sb.append("建议进一步询问症状持续时间与加重/缓解因素，必要时完善辅助检查以明确诊断。");
        }
        sb.append(imageNote == null || imageNote.isEmpty() ? "" : "患者上传的检查图片请一并查看。");
        sb.append("【AI 草稿，仅供医生参考，需人工审核，确认后可直接发送给患者】");
        return sb.toString();
    }

    /**
     * 处方草稿（医生标记面诊结束时生成，mock 返回占位模板；AI 不直接开正式处方）。
     */
    public String prescriptionDraft(String history) {
        return "处方建议草稿：基于患者主诉与检查结果，建议进一步明确诊断后由医生结合患者情况开具具体用药。"
                + "【AI 草稿，仅供医生参考，需人工审核】本草稿不会自动提交为正式处方。";
    }

    // ===================== 内部工具 =====================

    /**
     * 真实模式调用：OpenAI 兼容 ChatModel + 超时 + 重试。
     */
    private String callReal(String prompt) {
        int maxRetries = config.getMaxRetries() == null ? 2 : config.getMaxRetries();
        for (int i = 0; i <= maxRetries; i++) {
            try {
                log.info("AI 调用 model={}, prompt长度={}", modelConfigProvider.chatModel().getModelName(), prompt == null ? 0 : prompt.length());
                String resp = model().chat(prompt);
                log.info("AI 调用成功，回复长度={}", resp == null ? 0 : resp.length());
                return resp;
            } catch (Exception e) {
                log.error("AI 调用失败（第 {} 次）：{}", i + 1, e.getMessage());
                if (i == maxRetries) {
                    throw new IllegalStateException("AI 调用失败：" + e.getMessage(), e);
                }
            }
        }
        throw new IllegalStateException("AI 调用失败");
    }

    /**
     * 懒加载真实 ChatModel（OpenAI 兼容）。
     * 配置签名变化（管理端热更新模型配置）时重建客户端，保证新配置立即生效。
     */
    private ChatModel model() {
        AiModelConfigProvider.ChatModelSpec spec = modelConfigProvider.chatModel();
        String signature = spec.signature();
        ChatModel current = chatModel;
        if (current != null && signature.equals(modelSignature)) {
            return current;
        }
        synchronized (this) {
            if (!signature.equals(modelSignature)) {
                chatModel = null;
                streamingChatModel = null;
                modelSignature = signature;
            }
            if (chatModel == null) {
                chatModel = buildChatModel(spec);
            }
            return chatModel;
        }
    }

    /**
     * 按当前生效配置构建同步 ChatModel。
     *
     * @param spec 生效模型配置
     * @return OpenAI 兼容 ChatModel
     */
    private ChatModel buildChatModel(AiModelConfigProvider.ChatModelSpec spec) {
        var builder = OpenAiChatModel.builder()
                .baseUrl(spec.getApiUrl())
                .apiKey(spec.getApiKey())
                .modelName(spec.getModelName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() == null ? 60 : config.getTimeoutSeconds()));
        if (spec.getMaxTokens() != null && spec.getMaxTokens() > 0) {
            builder.maxTokens(spec.getMaxTokens());
        }
        log.info("构建 ChatModel：source={}, configName={}, model={}, baseUrl={}", spec.getSource(),
                spec.getConfigName(), spec.getModelName(), spec.getApiUrl());
        return builder.build();
    }

    /**
     * 真实流式调用：OpenAI 兼容 StreamingChatModel，增量文本实时回调 onToken，完成后返回全文。
     * 通过 CompletableFuture 阻塞等待流结束，保证图流转的后续节点（草稿落库等）拿到完整回复。
     */
    private String streamReal(String prompt, Consumer<String> onToken) {
        long timeoutSeconds = config.getTimeoutSeconds() == null ? 60 : config.getTimeoutSeconds();
        try {
            CompletableFuture<Void> done = new CompletableFuture<>();
            StringBuilder fullText = new StringBuilder();
            log.info("AI 流式调用 model={}, prompt长度={}", modelConfigProvider.chatModel().getModelName(), prompt == null ? 0 : prompt.length());
            streamingModel().chat(prompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String delta) {
                    appendAndPush(delta);
                }

                @Override
                public void onPartialResponse(PartialResponse response, PartialResponseContext context) {
                    appendAndPush(response == null ? null : response.text());
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    done.complete(null);
                }

                @Override
                public void onError(Throwable error) {
                    done.completeExceptionally(error);
                }

                /** 追加增量文本并实时推送 */
                private void appendAndPush(String delta) {
                    if (delta != null) {
                        fullText.append(delta);
                        onToken.accept(delta);
                    }
                }
            });
            done.get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("AI 流式调用成功，回复长度={}", fullText.length());
            return fullText.toString();
        } catch (Exception e) {
            log.error("AI 流式调用失败：{}", e.getMessage());
            throw new IllegalStateException("AI 流式调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 懒加载真实流式 ChatModel（OpenAI 兼容）。
     * 配置签名变化时重建客户端，保证管理端热更新后流式回复同样使用新配置。
     */
    private StreamingChatModel streamingModel() {
        AiModelConfigProvider.ChatModelSpec spec = modelConfigProvider.chatModel();
        String signature = spec.signature();
        StreamingChatModel current = streamingChatModel;
        if (current != null && signature.equals(modelSignature)) {
            return current;
        }
        synchronized (this) {
            if (!signature.equals(modelSignature)) {
                chatModel = null;
                streamingChatModel = null;
                modelSignature = signature;
            }
            if (streamingChatModel == null) {
                streamingChatModel = buildStreamingModel(spec);
            }
            return streamingChatModel;
        }
    }

    /**
     * 按当前生效配置构建流式 ChatModel。
     *
     * @param spec 生效模型配置
     * @return OpenAI 兼容流式 ChatModel
     */
    private StreamingChatModel buildStreamingModel(AiModelConfigProvider.ChatModelSpec spec) {
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(spec.getApiUrl())
                .apiKey(spec.getApiKey())
                .modelName(spec.getModelName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() == null ? 60 : config.getTimeoutSeconds()));
        if (spec.getMaxTokens() != null && spec.getMaxTokens() > 0) {
            builder.maxTokens(spec.getMaxTokens());
        }
        log.info("构建 StreamingChatModel：source={}, configName={}, model={}, baseUrl={}", spec.getSource(),
                spec.getConfigName(), spec.getModelName(), spec.getApiUrl());
        return builder.build();
    }

    /**
     * mock 模式流式模拟：按句号/问号/叹号/换行拆分为句子，间隔推送，模拟真实流式节奏。
     *
     * @param text    待推送的完整回复文本
     * @param onToken 增量文本回调
     */
    private void streamMockText(String text, Consumer<String> onToken) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String sentence : splitSentences(text)) {
            onToken.accept(sentence);
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 将文本按句子边界（。！？；\n）拆分，保留标点，逐句返回。
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                sentences.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            sentences.add(sb.toString());
        }
        return sentences;
    }

    /**
     * mock 模式规则回复（模拟大模型输出，覆盖常见症状引导）。
     */
    private String mockReply(String userContent) {
        if (userContent == null || userContent.isBlank()) {
            return "您好，请问您主要有哪些不适症状？方便描述一下症状持续多久了吗？我会根据您的描述帮您梳理，并引导您挂合适的科室（仅作参考，不做诊断）。";
        }
        String dept = suggestDepartment(userContent);
        if (dept != null) {
            return "根据您的描述，这类情况通常与【" + dept + "】相关，建议您可以挂该科室做进一步检查。"
                    + "在此之前，为了更好为您引导挂号，能否告诉我这种情况持续多久了？有没有加重或缓解的因素？"
                    + "（我是智能预问诊助手，只做症状采集与挂号引导，不做诊断、不开方）";
        }
        return "您好，感谢您的描述。为了帮您挂到合适的科室，请问您可以补充一下：1）症状从什么时候开始？"
                + "2）大概在什么部位？3）有没有加重或缓解的因素？我是智能预问诊助手，只做症状采集与挂号引导，"
                + "不做诊断、不开方，最终诊疗请以医生的判断为准。";
    }
}

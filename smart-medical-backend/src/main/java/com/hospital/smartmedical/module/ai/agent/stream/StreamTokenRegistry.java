package com.hospital.smartmedical.module.ai.agent.stream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * AI 流式 token 回调注册表（按会话 ID 存取，供图内节点线程读取）。
 *
 * <p>LangGraph4j 节点在异步线程池执行，且 State 在节点间传递会经过序列化，
 * 挂在 State 对象上的回调字段（transient/@JsonIgnore）会在节点内丢失；
 * 改为进程内注册表按 sessionId 存取回调：图流转前由 AiAgentService 注册，
 * 节点线程从注册表实时取出回调推送增量文本，请求结束由调用方移除，避免线程与序列化问题。</p>
 */
public class StreamTokenRegistry {

    /** 会话 ID → 增量文本回调（ConcurrentHashMap 保证并发安全） */
    private static final Map<String, Consumer<String>> HOLDER = new ConcurrentHashMap<>();

    /** 工具类，禁止实例化 */
    private StreamTokenRegistry() {
    }

    /**
     * 注册会话的流式回调（图流转前调用）。
     *
     * @param sessionId 会话 ID（与图内 MedicalGraphState.sessionId 一致）
     * @param onToken   增量文本回调（每产生一个片段即调用一次）
     */
    public static void register(String sessionId, Consumer<String> onToken) {
        if (sessionId != null && onToken != null) {
            HOLDER.put(sessionId, onToken);
        }
    }

    /**
     * 获取会话的流式回调（图节点内调用；非流式请求时为 null）。
     *
     * @param sessionId 会话 ID
     * @return 增量文本回调或 null
     */
    public static Consumer<String> get(String sessionId) {
        return sessionId == null ? null : HOLDER.get(sessionId);
    }

    /**
     * 移除会话的流式回调（图流转结束/异常时调用，防止注册表泄漏）。
     *
     * @param sessionId 会话 ID
     */
    public static void unregister(String sessionId) {
        if (sessionId != null) {
            HOLDER.remove(sessionId);
        }
    }
}

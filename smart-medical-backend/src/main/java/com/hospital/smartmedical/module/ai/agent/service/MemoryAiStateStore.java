package com.hospital.smartmedical.module.ai.agent.service;

import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存版会话 State 存储（默认，无需 Redis 也可运行）。
 *
 * <p>ConcurrentHashMap 隔离各会话；维护患者 → 会话ID 索引用于历史会话列表。
 * 演示/开发环境使用；高并发生产建议切换 ai.state-store=redis。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "ai.state-store", havingValue = "memory", matchIfMissing = true)
public class MemoryAiStateStore implements AiStateStore {

    /** 会话存储（sessionId → 状态） */
    private final Map<String, MedicalGraphState> store = new ConcurrentHashMap<>();

    /** 患者索引（patientId → sessionId 集合） */
    private final Map<Long, Set<String>> patientIndex = new ConcurrentHashMap<>();

    /**
     * 读取会话状态。
     */
    @Override
    public Optional<MedicalGraphState> get(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    /**
     * 保存会话状态并维护患者索引。
     */
    @Override
    public void save(String sessionId, MedicalGraphState state) {
        store.put(sessionId, state);
        if (state.getPatientId() != null) {
            patientIndex.computeIfAbsent(state.getPatientId(), k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }
    }

    /**
     * 删除会话状态。
     */
    @Override
    public void delete(String sessionId) {
        store.remove(sessionId);
    }

    /**
     * 按患者列出会话（按最后消息时间倒序）。
     */
    @Override
    public List<MedicalGraphState> listByPatient(Long patientId) {
        Set<String> ids = patientIndex.getOrDefault(patientId, Set.of());
        return ids.stream()
                .map(store::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MemoryAiStateStore::lastActiveTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 取会话最后活动时间（最后一条消息的 createdAt，无消息用 epoch）。
     */
    private static String lastActiveTime(MedicalGraphState s) {
        if (s.getChatHistory() == null || s.getChatHistory().isEmpty()) {
            return "";
        }
        return s.getChatHistory().get(s.getChatHistory().size() - 1).getCreatedAt();
    }
}

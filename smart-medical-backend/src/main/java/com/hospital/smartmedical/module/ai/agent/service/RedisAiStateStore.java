package com.hospital.smartmedical.module.ai.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 版会话 State 存储（ai.state-store=redis 时启用）。
 *
 * <p>会话状态以 JSON 存 Redis（key: ai:session:{sessionId}，24h TTL）；
 * 维护患者 → 会话ID 索引集合（ai:patient:{patientId}）支持历史会话列表。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.state-store", havingValue = "redis")
public class RedisAiStateStore implements AiStateStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 会话 key 前缀 */
    private static final String KEY_PREFIX = "ai:session:";
    /** 患者索引 key 前缀 */
    private static final String PATIENT_PREFIX = "ai:patient:";
    /** 会话 TTL：24 小时 */
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * 读取会话状态（JSON 反序列化）。
     */
    @Override
    public Optional<MedicalGraphState> get(String sessionId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(json, MedicalGraphState.class));
        } catch (JsonProcessingException e) {
            log.error("Redis 会话状态反序列化失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 保存会话状态（JSON 序列化 + TTL + 患者索引）。
     */
    @Override
    public void save(String sessionId, MedicalGraphState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            String key = KEY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(key, json, TTL);
            if (state.getPatientId() != null) {
                String pKey = PATIENT_PREFIX + state.getPatientId();
                redisTemplate.opsForSet().add(pKey, sessionId);
                redisTemplate.expire(pKey, TTL);
            }
        } catch (JsonProcessingException e) {
            log.error("Redis 会话状态序列化失败：{}", e.getMessage());
        }
    }

    /**
     * 删除会话状态。
     */
    @Override
    public void delete(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }

    /**
     * 按患者列出会话（通过索引集合读取）。
     */
    @Override
    public List<MedicalGraphState> listByPatient(Long patientId) {
        Set<String> ids = redisTemplate.opsForSet().members(PATIENT_PREFIX + patientId);
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<MedicalGraphState> list = new ArrayList<>();
        for (String id : ids) {
            get(id).ifPresent(list::add);
        }
        list.sort(Comparator.comparing((MedicalGraphState s) ->
                s.getChatHistory() == null || s.getChatHistory().isEmpty()
                        ? "" : s.getChatHistory().get(s.getChatHistory().size() - 1).getCreatedAt()).reversed());
        return list;
    }
}

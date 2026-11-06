package com.hospital.smartmedical.module.ai.agent.service;

import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话存储降级测试：锁定「Redis 故障时不再抛异常、自动走内存、冷却后自动重试」。
 *
 * <p>回归点：Redis 停止时 /ai/patient-chat 曾直接返回 500。</p>
 */
class ResilientAiStateStoreTest {

    @Test
    @DisplayName("Redis 故障时 save 不抛异常，并写入内存兜底")
    void saveFallsBackWhenPrimaryFails() {
        FlakyStore primary = new FlakyStore(true);
        MemoryAiStateStore fallback = new MemoryAiStateStore();
        ResilientAiStateStore store = new ResilientAiStateStore(primary, fallback, 30_000L, () -> 1_000L);

        MedicalGraphState state = state("s1", 1L);
        assertDoesNotThrow(() -> store.save("s1", state));

        assertEquals(1, primary.calls, "应只尝试一次主存储");
        assertTrue(store.isDegradedNow(), "失败后应进入冷却期");
        assertTrue(store.get("s1").isPresent(), "应能从内存兜底读到");
        assertEquals(1, store.primaryFailures());
    }

    @Test
    @DisplayName("冷却期内不再访问主存储（避免请求被连接超时拖死）")
    void cooldownSkipsPrimary() {
        FlakyStore primary = new FlakyStore(true);
        AtomicLong now = new AtomicLong(1_000L);
        ResilientAiStateStore store = new ResilientAiStateStore(primary, new MemoryAiStateStore(), 30_000L, now::get);

        store.save("s1", state("s1", 1L));           // 失败 → 降级，primary.calls = 1
        store.get("s1");                              // 冷却期内：只走内存
        store.get("s1");
        assertEquals(1, primary.calls, "冷却期内不应再尝试 Redis");
        assertEquals(3, store.fallbackCalls(), "save 兜底 1 次 + get 兜底 2 次");

        now.addAndGet(30_001L);                       // 冷却结束
        primary.fail = false;
        store.save("s1", state("s1", 1L));            // 重试成功
        assertEquals(2, primary.calls);
        assertFalse(store.isDegradedNow(), "恢复后应退出冷却期");
    }

    @Test
    @DisplayName("兜底存储也失败时不抛异常，按无会话处理")
    void fallbackFailureIsSafe() {
        AiStateStore brokenFallback = new AiStateStore() {
            @Override
            public Optional<MedicalGraphState> get(String sessionId) {
                throw new IllegalStateException("memory broken");
            }

            @Override
            public void save(String sessionId, MedicalGraphState state) {
                throw new IllegalStateException("memory broken");
            }

            @Override
            public void delete(String sessionId) {
                throw new IllegalStateException("memory broken");
            }

            @Override
            public List<MedicalGraphState> listByPatient(Long patientId) {
                throw new IllegalStateException("memory broken");
            }
        };
        ResilientAiStateStore store = new ResilientAiStateStore(
                new FlakyStore(true), brokenFallback, 30_000L, () -> 1_000L);

        assertDoesNotThrow(() -> store.save("s1", state("s1", 1L)));
        assertTrue(store.get("s1").isEmpty(), "兜底也失败时应返回空会话而不是抛异常");
        assertDoesNotThrow(() -> store.delete("s1"));
    }

    @Test
    @DisplayName("listByPatient 同样降级到内存")
    void listByPatientFallsBack() {
        FlakyStore primary = new FlakyStore(true);
        MemoryAiStateStore fallback = new MemoryAiStateStore();
        ResilientAiStateStore store = new ResilientAiStateStore(primary, fallback, 30_000L, () -> 1_000L);

        fallback.save("s1", state("s1", 7L));

        List<MedicalGraphState> sessions = store.listByPatient(7L);
        assertEquals(1, sessions.size());
        assertEquals(1, primary.calls);
    }

    // ===================== 工具 =====================

    private static MedicalGraphState state(String sessionId, Long patientId) {
        MedicalGraphState s = new MedicalGraphState();
        s.setSessionId(sessionId);
        s.setPatientId(patientId);
        return s;
    }

    /**
     * 可切换故障的主存储桩：fail=true 时所有操作抛异常，并记录调用次数。
     */
    static class FlakyStore implements AiStateStore {

        final Map<String, MedicalGraphState> map = new HashMap<>();
        final List<MedicalGraphState> all = new ArrayList<>();
        boolean fail;
        int calls;

        FlakyStore(boolean fail) {
            this.fail = fail;
        }

        private void check() {
            calls++;
            if (fail) {
                throw new IllegalStateException("redis unavailable");
            }
        }

        @Override
        public Optional<MedicalGraphState> get(String sessionId) {
            check();
            return Optional.ofNullable(map.get(sessionId));
        }

        @Override
        public void save(String sessionId, MedicalGraphState state) {
            check();
            map.put(sessionId, state);
            all.add(state);
        }

        @Override
        public void delete(String sessionId) {
            check();
            map.remove(sessionId);
        }

        @Override
        public List<MedicalGraphState> listByPatient(Long patientId) {
            check();
            return all.stream().filter(s -> patientId.equals(s.getPatientId())).toList();
        }
    }
}

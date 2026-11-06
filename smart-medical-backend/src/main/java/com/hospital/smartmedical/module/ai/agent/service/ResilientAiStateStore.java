package com.hospital.smartmedical.module.ai.agent.service;

import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 会话存储降级装饰器：Redis 为主，内存为兜底，失败后进入冷却期（熔断），避免每个请求都卡在连接超时上。
 *
 * <p>背景：{@code ai.state-store=redis} 时，Redis 一旦不可用，{@code RedisAiStateStore.get/save}
 * 会抛 {@code RedisConnectionFailureException}，直接冒泡到 GlobalExceptionHandler 变成 500，
 * 导致整个 AI 对话不可用（实测：Redis 停止后 /ai/patient-chat 返回 500）。</p>
 *
 * <p>策略：
 * <ul>
 *   <li>正常情况下全部走 Redis（行为不变）；</li>
 *   <li>任一次 Redis 操作失败 → 记录并进入 {@code cooldownMs} 冷却期，期间直接走内存，不再尝试 Redis；</li>
 *   <li>冷却期结束后自动重试 Redis，成功则切回并打印"已恢复"。</li>
 * </ul></p>
 *
 * <p>降级期间的会话只存在内存：服务重启会丢、多实例之间不共享。这是"可用性优先"的取舍，
 * 对演示/单机环境可接受；生产应保证 Redis 高可用而不是长期依赖降级。</p>
 */
@Slf4j
public class ResilientAiStateStore implements AiStateStore {

    /** 主存储（Redis） */
    private final AiStateStore primary;

    /** 兜底存储（内存） */
    private final AiStateStore fallback;

    /** 熔断冷却时长（毫秒）：失败后这段时间内不再尝试主存储 */
    private final long cooldownMs;

    /** 时钟（可注入，便于测试） */
    private final LongSupplier clock;

    /** 冷却截止时间戳；0 表示未降级 */
    private volatile long degradedUntilMs = 0L;

    /** 是否处于降级状态（仅用于日志去重） */
    private volatile boolean degraded = false;

    private final AtomicLong primaryFailures = new AtomicLong();
    private final AtomicLong fallbackCalls = new AtomicLong();

    /**
     * 生产用构造：使用系统时钟。
     *
     * @param primary    主存储
     * @param fallback   兜底存储
     * @param cooldownMs 冷却时长（毫秒）
     */
    public ResilientAiStateStore(AiStateStore primary, AiStateStore fallback, long cooldownMs) {
        this(primary, fallback, cooldownMs, System::currentTimeMillis);
    }

    /**
     * 测试用构造：可注入时钟，避免 sleep。
     *
     * @param primary    主存储
     * @param fallback   兜底存储
     * @param cooldownMs 冷却时长（毫秒）
     * @param clock      时钟
     */
    ResilientAiStateStore(AiStateStore primary, AiStateStore fallback, long cooldownMs, LongSupplier clock) {
        this.primary = primary;
        this.fallback = fallback;
        this.cooldownMs = cooldownMs;
        this.clock = clock;
    }

    @Override
    public Optional<MedicalGraphState> get(String sessionId) {
        if (isCoolingDown()) {
            return fallbackGet(sessionId);
        }
        try {
            Optional<MedicalGraphState> result = primary.get(sessionId);
            markRecovered();
            return result;
        } catch (Exception e) {
            degrade("get", e);
            return fallbackGet(sessionId);
        }
    }

    @Override
    public void save(String sessionId, MedicalGraphState state) {
        if (isCoolingDown()) {
            fallbackSave(sessionId, state);
            return;
        }
        try {
            primary.save(sessionId, state);
            markRecovered();
        } catch (Exception e) {
            degrade("save", e);
            fallbackSave(sessionId, state);
        }
    }

    @Override
    public void delete(String sessionId) {
        if (isCoolingDown()) {
            fallbackCalls.incrementAndGet();
            safeFallback(() -> fallback.delete(sessionId), "delete");
            return;
        }
        try {
            primary.delete(sessionId);
            markRecovered();
        } catch (Exception e) {
            degrade("delete", e);
            fallbackCalls.incrementAndGet();
            safeFallback(() -> fallback.delete(sessionId), "delete");
        }
    }

    @Override
    public List<MedicalGraphState> listByPatient(Long patientId) {
        if (isCoolingDown()) {
            fallbackCalls.incrementAndGet();
            return fallback.listByPatient(patientId);
        }
        try {
            List<MedicalGraphState> result = primary.listByPatient(patientId);
            markRecovered();
            return result;
        } catch (Exception e) {
            degrade("listByPatient", e);
            fallbackCalls.incrementAndGet();
            return fallback.listByPatient(patientId);
        }
    }

    // ===================== 内部 =====================

    private boolean isCoolingDown() {
        return clock.getAsLong() < degradedUntilMs;
    }

    private Optional<MedicalGraphState> fallbackGet(String sessionId) {
        fallbackCalls.incrementAndGet();
        return safeFallback(() -> fallback.get(sessionId), "get").orElse(Optional.empty());
    }

    private void fallbackSave(String sessionId, MedicalGraphState state) {
        fallbackCalls.incrementAndGet();
        safeFallback(() -> {
            fallback.save(sessionId, state);
            return null;
        }, "save");
    }

    /**
     * 兜底存储自身也失败时的最后一道防线：不再抛异常，返回 empty，让请求按"新会话"继续。
     *
     * @param action 兜底操作
     * @param op     操作名（日志用）
     * @return 操作结果；失败返回 empty
     */
    private <T> Optional<T> safeFallback(java.util.function.Supplier<T> action, String op) {
        try {
            return Optional.ofNullable(action.get());
        } catch (Exception e) {
            log.error("兜底会话存储执行失败（op={}），本次按无会话处理：{}", op, e.getMessage());
            return Optional.empty();
        }
    }

    private void safeFallback(Runnable action, String op) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("兜底会话存储执行失败（op={}）：{}", op, e.getMessage());
        }
    }

    /**
     * 标记降级：设置冷却截止时间，并在状态切换时打印一次告警（避免刷屏）。
     *
     * @param op 失败的操作
     * @param e  异常
     */
    private void degrade(String op, Exception e) {
        primaryFailures.incrementAndGet();
        degradedUntilMs = clock.getAsLong() + cooldownMs;
        if (!degraded) {
            degraded = true;
            log.warn("会话存储（Redis）不可用，降级为内存存储 {} 秒：op={}，原因={}",
                    cooldownMs / 1000, op, e.getMessage());
        } else {
            log.debug("会话存储（Redis）仍不可用（op={}）：{}", op, e.getMessage());
        }
    }

    /**
     * 冷却期后重试成功：清除降级标记并打印一次恢复日志。
     */
    private void markRecovered() {
        if (degraded) {
            degraded = false;
            degradedUntilMs = 0L;
            log.info("会话存储（Redis）已恢复，切回 Redis");
        }
    }

    // ===================== 观测（测试/运维用） =====================

    /** @return 主存储失败次数 */
    long primaryFailures() {
        return primaryFailures.get();
    }

    /** @return 走兜底存储的次数 */
    long fallbackCalls() {
        return fallbackCalls.get();
    }

    /** @return 当前是否处于冷却期 */
    boolean isDegradedNow() {
        return isCoolingDown();
    }
}

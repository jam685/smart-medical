package com.hospital.smartmedical.module.ai.agent.config;

import com.hospital.smartmedical.module.ai.agent.service.AiStateStore;
import com.hospital.smartmedical.module.ai.agent.service.MemoryAiStateStore;
import com.hospital.smartmedical.module.ai.agent.service.RedisAiStateStore;
import com.hospital.smartmedical.module.ai.agent.service.ResilientAiStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 会话存储装配：Redis 模式下用「Redis 主 + 内存兜底」的降级装饰器。
 *
 * <p>不降级时的行为与直接注入 {@link RedisAiStateStore} 完全一致；
 * 仅在 Redis 故障时切换为内存，避免 /ai/patient-chat 直接 500。</p>
 *
 * <p>内存兜底实例不注册为 Bean（避免与 {@code ai.state-store=memory} 的装配冲突），
 * 由本配置直接 new 出来交给装饰器持有。</p>
 */
@Configuration
@ConditionalOnProperty(name = "ai.state-store", havingValue = "redis")
public class AiStateStoreFallbackConfig {

    /**
     * 主用的会话存储：Redis 失败自动降级内存，冷却后自动重试。
     *
     * @param redisAiStateStore Redis 实现
     * @param cooldownSeconds   熔断冷却时长（秒）
     * @return 带降级能力的会话存储
     */
    @Bean
    @Primary
    public AiStateStore resilientAiStateStore(RedisAiStateStore redisAiStateStore,
                                              @Value("${ai.state-store-fallback-cooldown-seconds:30}") long cooldownSeconds) {
        return new ResilientAiStateStore(redisAiStateStore, new MemoryAiStateStore(),
                Math.max(1, cooldownSeconds) * 1000L);
    }
}

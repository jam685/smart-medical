package com.hospital.smartmedical.common.trace;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 异步线程池配置。核心：TaskDecorator 把调用线程的 MDC 上下文（traceId / userId / role 等）
 * 复制到异步线程，否则 {@code @Async} 或 {@code CompletableFuture.runAsync} 里的日志会丢 traceId。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>{@code @Async} 标注的方法（用默认的 taskExecutor Bean）</li>
 *   <li>{@code CompletableFuture.runAsync(runnable, taskExecutor)} 指定这个 executor 的调用</li>
 * </ul>
 *
 * <p>注意：ForkJoinPool.commonPool（CompletableFuture.runAsync 不传 executor 时的默认池）
 * 不受 Spring TaskDecorator 管控。业务代码里如果用 runAsync 想带 traceId，必须显式传 {@code taskExecutor}。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        // ===== 关键：把调用线程的 MDC 复制到异步线程 =====
        executor.setTaskDecorator(runnable -> {
            Map<String, String> ctx = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (ctx != null) {
                        MDC.setContextMap(ctx);
                    }
                    runnable.run();
                } finally {
                    // 异步线程用完也要清理，否则线程池复用时串上一个任务的 MDC
                    MDC.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}

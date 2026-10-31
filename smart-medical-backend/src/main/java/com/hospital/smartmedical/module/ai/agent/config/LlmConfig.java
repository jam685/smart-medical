package com.hospital.smartmedical.module.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 大模型配置（读取 application.yml 的 ai.llm.*）。
 *
 * <p>mock-mode=true 时不创建真实模型，由 LlmClientService 走本地规则模拟；
 * mock-mode=false 时使用 base-url/api-key/model 构建 OpenAI 兼容 ChatModel。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.llm")
public class LlmConfig {

    /** OpenAI 兼容接口地址（如 https://api.openai.com/v1） */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名（如 gpt-4o-mini） */
    private String model;

    /** 采样温度 */
    private Double temperature = 0.7;

    /** 单次调用超时（秒） */
    private Integer timeoutSeconds = 60;

    /** 失败重试次数 */
    private Integer maxRetries = 2;
}

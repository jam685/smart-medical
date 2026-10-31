package com.hospital.smartmedical.module.ai.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.module.admin.entity.ModelConfig;
import com.hospital.smartmedical.module.admin.mapper.ModelConfigMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置提供者：把管理端维护的 sm_model_config 表与运行时 LLM 客户端打通。
 *
 * <p>读取「聊天模型」中启用的一条配置作为当前生效模型；管理端增删改后调用
 * {@link #invalidate()} 使缓存失效，下一次 AI 调用即用新配置（热更新，无需重启）。
 * 表中无有效配置（或为演示占位数据）时回退 application.yml 的 ai.llm.* 配置。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiModelConfigProvider {

    private final ModelConfigMapper modelConfigMapper;

    /** yml 默认配置（数据库无有效配置时回退） */
    private final LlmConfig fallbackConfig;

    /** 配置类型：聊天模型 */
    private static final String TYPE_CHAT = "聊天模型";

    /** 缓存有效期（毫秒）：多实例部署时兜底刷新，避免其他节点改配置后本节点长期不更新 */
    private static final long TTL_MS = 30_000L;

    /** 生效配置缓存 */
    private volatile ChatModelSpec cached;

    /** 缓存加载时间 */
    private volatile long cachedAt;

    /**
     * 获取当前生效的聊天模型配置（带 30 秒兜底缓存；管理端变更时主动失效）。
     *
     * @return 聊天模型配置（永远不会为空，最差回退 yml 配置）
     */
    public ChatModelSpec chatModel() {
        long now = System.currentTimeMillis();
        ChatModelSpec current = cached;
        if (current != null && now - cachedAt < TTL_MS) {
            return current;
        }
        synchronized (this) {
            current = cached;
            if (current != null && System.currentTimeMillis() - cachedAt < TTL_MS) {
                return current;
            }
            current = reload();
            cached = current;
            cachedAt = System.currentTimeMillis();
            return current;
        }
    }

    /**
     * 使配置缓存失效（管理端新增/修改/删除/启停模型配置后调用），下一次调用重新读表。
     */
    public void invalidate() {
        synchronized (this) {
            cached = null;
            cachedAt = 0;
        }
        log.info("模型配置缓存已失效，下一次 AI 调用将加载最新配置");
    }

    /**
     * 重新从数据库加载生效配置，失败或无有效配置时回退 yml。
     *
     * @return 生效聊天模型配置
     */
    private ChatModelSpec reload() {
        try {
            ModelConfig mc = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfig>()
                    .eq(ModelConfig::getType, TYPE_CHAT)
                    .eq(ModelConfig::getEnabled, 1)
                    .orderByDesc(ModelConfig::getUpdatedAt)
                    .last("LIMIT 1"));
            if (mc != null && !isPlaceholder(mc)) {
                ChatModelSpec spec = new ChatModelSpec();
                spec.setConfigId(mc.getId());
                spec.setConfigName(mc.getConfigName());
                spec.setProvider(mc.getProvider());
                spec.setApiUrl(mc.getApiUrl());
                // 表内 API Key 为空时沿用 yml（避免把密钥落到数据库）
                spec.setApiKey(blank(mc.getApiKey()) ? fallbackConfig.getApiKey() : mc.getApiKey());
                spec.setModelName(mc.getModelName());
                spec.setMaxTokens(mc.getMaxTokens());
                spec.setSource("数据库");
                log.info("加载数据库模型配置：configName={}, model={}, baseUrl={}", mc.getConfigName(),
                        mc.getModelName(), mc.getApiUrl());
                return spec;
            }
            if (mc != null) {
                log.info("模型配置为演示占位数据（configName={}），回退 application.yml 配置", mc.getConfigName());
            }
        } catch (Exception e) {
            // 数据库异常不影响 AI 主流程：回退 yml 配置
            log.warn("加载数据库模型配置失败，回退 application.yml 配置：{}", e.getMessage());
        }
        return fallbackSpec();
    }

    /**
     * yml 默认配置转为生效配置。
     *
     * @return 配置文件来源的模型配置
     */
    private ChatModelSpec fallbackSpec() {
        ChatModelSpec spec = new ChatModelSpec();
        spec.setConfigName("application.yml 默认配置");
        spec.setProvider("配置文件");
        spec.setApiUrl(fallbackConfig.getBaseUrl());
        spec.setApiKey(fallbackConfig.getApiKey());
        spec.setModelName(fallbackConfig.getModel());
        spec.setSource("配置文件");
        return spec;
    }

    /**
     * 判断是否为演示占位配置（localhost 地址 / demo 模型 / 本地模拟服务商），此类配置不生效。
     *
     * @param mc 数据库配置
     * @return true=占位配置（不生效，回退 yml）
     */
    private boolean isPlaceholder(ModelConfig mc) {
        String url = mc.getApiUrl() == null ? "" : mc.getApiUrl().toLowerCase();
        String model = mc.getModelName() == null ? "" : mc.getModelName().toLowerCase();
        String provider = mc.getProvider() == null ? "" : mc.getProvider();
        return provider.contains("本地模拟") || url.contains("localhost") || model.startsWith("demo");
    }

    /**
     * 空字符串判断。
     */
    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 生效聊天模型配置（数据库字段 → 运行时模型参数映射）。
     */
    @Data
    public static class ChatModelSpec {

        /** 数据库配置 ID（来自 yml 时为空） */
        private Long configId;

        /** 配置名称 */
        private String configName;

        /** 服务商 */
        private String provider;

        /** 接口地址 */
        private String apiUrl;

        /** API Key */
        private String apiKey;

        /** 模型名称 */
        private String modelName;

        /** 最大 token 量 */
        private Integer maxTokens;

        /** 配置来源：数据库 / 配置文件 */
        private String source;

        /**
         * 生成用于判断模型是否需要重建的签名（任一项变化即重建客户端）。
         *
         * @return 配置签名
         */
        public String signature() {
            return String.join("|", String.valueOf(configId), String.valueOf(apiUrl), String.valueOf(modelName),
                    String.valueOf(apiKey), String.valueOf(maxTokens));
        }
    }
}

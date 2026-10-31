package com.hospital.smartmedical.module.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 向量模型配置（读取 application.yml 的 ai.llm.embedding.*）。
 *
 * <p>用于科室介绍库 RAG 的文本向量化：配置千问（DashScope）API Key 时使用
 * 千问 text-embedding-v4（OpenAI 兼容接口）；API Key 为空时自动降级本地
 * BGE-small-zh-v1.5 向量模型（离线可用）。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.llm.embedding")
public class EmbeddingConfig {

    /** 千问向量模型 OpenAI 兼容接口地址（DashScope compatible-mode） */
    private String baseUrl;

    /** 千问（DashScope）API Key，为空时降级本地 BGE 向量模型 */
    private String apiKey;

    /** 千问向量模型名（如 text-embedding-v4） */
    private String model;

    /** 向量维度（千问 text-embedding-v4 为 1024） */
    private Integer dimension = 1024;

    /** Milvus 向量库配置（科室介绍库向量持久化存储） */
    private Milvus milvus = new Milvus();

    /**
     * Milvus 向量库连接配置。
     */
    @Data
    public static class Milvus {
        /** 是否启用 Milvus（false 时回退内存向量库） */
        private boolean enabled = false;
        /** Milvus 服务地址 */
        private String host = "localhost";
        /** Milvus gRPC 端口 */
        private Integer port = 19530;
        /** 向量集合名 */
        private String collectionName = "smart_medical_knowledge";
        /** 数据库名（空=默认库） */
        private String databaseName = "";
    }
}

package com.hospital.smartmedical.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识文档实体（sm_knowledge_document，V1.3 新增）。
 *
 * <p>一篇挂靠在某个知识库下的文档，记录文档名称、类型、解析状态、片段数量与原文。</p>
 */
@Data
@TableName("sm_knowledge_document")
public class KnowledgeDocument implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档名称 */
    private String documentName;

    /** 文档类型 */
    private String type;

    /** 所属知识库 ID（sm_medical_knowledge_base.id） */
    private Long knowledgeBaseId;

    /** 解析状态，如 PARSED / FAILED */
    private String parseStatus;

    /** 片段数量 */
    private Integer chunkCount;

    /** 原文 */
    private String originalText;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
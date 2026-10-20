package com.hospital.smartmedical.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识片段实体（sm_knowledge_chunk，V1.3 新增）。
 *
 * <p>文档解析后切分的知识片段，记录来源文档、片段序号、标题与具体内容。</p>
 */
@Data
@TableName("sm_knowledge_chunk")
public class KnowledgeChunk implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源文档 ID（sm_knowledge_document.id） */
    private Long documentId;

    /** 片段序号 */
    private Integer chunkIndex;

    /** 片段标题 */
    private String chunkTitle;

    /** 片段具体内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
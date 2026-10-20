package com.hospital.smartmedical.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 向量化与检索记录实体（sm_vector_index，V1.3 新增）。
 *
 * <p>记录知识片段被向量化的元数据（不存向量数据，仅记录片段标题、来源文档、向量模型、
 * 维度与状态）。</p>
 */
@Data
@TableName("sm_vector_index")
public class VectorIndex implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联知识片段 ID（sm_knowledge_chunk.id） */
    private Long chunkId;

    /** 片段标题 */
    private String chunkTitle;

    /** 来源文档 ID */
    private Long sourceDocumentId;

    /** 向量模型 */
    private String vectorModel;

    /** 维度 */
    private Integer dimension;

    /** 状态，如 READY / PROCESSING / FAILED */
    private String status;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
package com.hospital.smartmedical.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sm_knowledge_base")
public class KnowledgeBase implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String type;

    private String content;

    private String source;

    private LocalDateTime updatedAt;
}

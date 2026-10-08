package com.hospital.smartmedical.module.onlineconsult.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 补充材料记录表（sm_consult_supplement）。
 *
 * <p>记录患者应医生/AI 要求补充上传的材料，仅统计图片上传次数（supplement_no 为第几次补充）。
 * 不导出、不分类统计。</p>
 */
@Data
@TableName("sm_consult_supplement")
public class ConsultSupplement implements Serializable {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问诊 ID（sm_online_consult.id） */
    private Long consultId;

    /** 第几次补充 */
    private Integer supplementNo;

    /** 材料类型 */
    private String materialType;

    /** 文本内容 */
    private String content;

    /** 图片地址 */
    private String imageUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

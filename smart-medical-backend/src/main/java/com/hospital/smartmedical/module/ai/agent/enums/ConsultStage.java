package com.hospital.smartmedical.module.ai.agent.enums;

/**
 * 问诊阶段枚举（AI 多智能体图路由核心依据）。
 *
 * <p>路由优先级：currentStage &gt; role &gt; 用户意图。</p>
 */
public enum ConsultStage {

    /** 预问诊阶段：患者预问诊、收集症状、科室推荐、挂号、查询 */
    PRE_CONSULT,

    /** 医生接诊阶段：医患对话、AI 生成回复草稿、处方草稿 */
    DOCTOR_RECEIVE,

    /** 问诊结束阶段：仅支持患者查询报告、处方 */
    FINISHED
}

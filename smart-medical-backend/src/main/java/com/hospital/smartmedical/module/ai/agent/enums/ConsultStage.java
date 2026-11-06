package com.hospital.smartmedical.module.ai.agent.enums;

/**
 * 问诊阶段枚举（AI 多智能体图路由核心依据）。
 *
 * <p>路由优先级：currentStage &gt; role &gt; 用户意图。</p>
 *
 * <p>设计说明：阶段机实质只有两个状态——PRE_CONSULT（患者预问诊/挂号）和 DOCTOR_RECEIVE（医生接诊）。
 * 曾有过 FINISHED 状态分支但从未被赋值（全库仅 3 处 setCurrentStage，全是 PRE_CONSULT / DOCTOR_RECEIVE），
 * 对应 MedicalGraphBuilder 里 FINISHED case 也是死代码，已一并删除。</p>
 */
public enum ConsultStage {

    /** 预问诊阶段：患者预问诊、收集症状、科室推荐、挂号、查询 */
    PRE_CONSULT,

    /** 医生接诊阶段：医患对话、AI 生成回复草稿、处方草稿 */
    DOCTOR_RECEIVE
}

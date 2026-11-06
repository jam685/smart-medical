package com.hospital.smartmedical.module.ai.agent.enums;

/**
 * 本轮回复来源节点（语义缓存来源过滤依据）。
 *
 * <p>背景：语义缓存的读写在 {@code AiAgentService}（图外），写入取的是 {@code g.getReply()}。
 * 但图内不同节点产出的回复性质完全不同：
 * <ul>
 *   <li>{@link #ASSISTANT_DIALOG}：客服通用问答，话术稳定、可跨患者复用 —— 唯一允许进缓存的来源；</li>
 *   <li>{@link #BUSINESS}：业务节点，回复含实时号源/医生列表/个人数据 —— 绝不能缓存；</li>
 *   <li>{@link #PATIENT_DIALOG}：预问诊引导，带症状上下文 —— 不参与缓存（当前也不在 cs_ 会话内）；</li>
 *   <li>{@link #DOCTOR_ASSIST}：医生草稿 —— 不参与缓存；</li>
 *   <li>{@link #CACHE}：本轮直接回放了缓存答案 —— 不重复写入。</li>
 * </ul></p>
 *
 * <p>好处：把「谁产的回复」显式记录在状态里，缓存决策不再依赖 routeIntent
 * （关键词兜底时为空）或 lastBusinessResult（跨轮不清空）这类不可靠信号。</p>
 */
public enum ReplySource {

    /** 客服对话节点（assistantDialogAgentNode）：唯一可缓存的来源 */
    ASSISTANT_DIALOG,

    /** 业务节点（businessAgentNode）：回复含实时数据，禁止缓存 */
    BUSINESS,

    /** 预问诊对话节点（patientDialogAgentNode）：带症状上下文，不缓存 */
    PATIENT_DIALOG,

    /** 医生辅助节点（doctorAssistAgentNode）：草稿，不缓存 */
    DOCTOR_ASSIST,

    /** 本轮命中缓存直接回放，不重复写入 */
    CACHE
}

package com.hospital.smartmedical.module.ai.agent.enums;

import java.util.Set;

/**
 * 业务意图关键词常量表（全库唯一可信来源）。
 *
 * <p>用途：BusinessToolExecutor（硬编码关键词兜底）与 MedicalGraphBuilder（路由）统一从这里引用，
 * 避免两处各维护一份关键词表导致漂移。</p>
 *
 * <p>设计规则：
 * <ul>
 *   <li>不用单字关键词（"挂""点""号"等容易误判），只用多字明确短语</li>
 *   <li>挂号意图加负向闸门关键词（"挂念""挂科""挂电话"等）</li>
 *   <li>科室推荐、流程/材料/费用/时间/地点类问题不算挂号意图</li>
 *   <li>动作性短语单独成表（{@link #APPOINT_ACTION}），仅用于"模型判 UNKNOWN 但用户其实要办理"的兜底提升</li>
 * </ul></p>
 */
public final class IntentKeywords {

    private IntentKeywords() {}

    /**
     * 合法意图枚举（含 FAQ 与 UNKNOWN）。
     *
     * <p>路由与业务节点共用同一份，避免"路由放行、业务节点判非法"的漂移。</p>
     */
    public static final Set<String> VALID_INTENTS = Set.of(
            "APPOINT", "FAQ", "PROGRESS", "PRESCRIPTION_REPORT",
            "DOCTOR_PATIENTS", "DOCTOR_PROGRESS", "UNKNOWN");

    // ===== 挂号意图（APPOINT） =====
    /** 挂号意图正向关键词（不含单字"挂"，避免"挂念/挂钩/挂水/挂科/挂电话"误判） */
    public static final String[] APPOINT_POSITIVE = {
            "挂号", "预约", "挂一个", "挂个", "的号", "号吗", "门诊号"
    };

    /**
     * 明确动作短语（比正向词更严格：只保留"要做"的表达，不含"挂号/预约"这类话题词）。
     *
     * <p>用途：模型判 UNKNOWN 时，如果用户其实在说"帮我挂/我要挂/挂个号"，
     * 用这张表做兜底提升，避免"是否回退关键词"二选一带来的召回损失。</p>
     */
    public static final String[] APPOINT_ACTION = {
            "帮我挂", "我要挂", "我想挂", "挂个", "挂一个", "的号", "门诊号"
    };
    /** 挂号意图负向关键词（含"挂"字但不是挂号语义，需前置闸门拦截） */
    public static final String[] APPOINT_NEGATIVE = {
            "挂念", "挂钩", "挂水", "挂衣服", "挂电话", "挂科", "挂起", "牵挂"
    };
    /**
     * 科室推荐 + 流程/须知类关键词：命中则<b>不算</b>挂号意图。
     *
     * <p>前 7 个是"该挂什么科"型；后 12 个是"流程/材料/费用/时间/地点"型——
     * 「挂号流程是什么」「挂号要带什么」「挂号费多少钱」这类问题问的是信息，
     * 不是办理动作，必须挡在业务节点之外（否则会被 doAppoint 当成挂号请求，
     * 用整句做 RAG 推荐，答出"为您推荐可挂号医生"）。</p>
     */
    public static final String[] APPOINT_EXCLUDED = {
            "什么科", "哪个科", "哪科", "推荐挂", "应该挂", "挂什么", "挂哪个",
            "流程", "怎么挂", "如何挂", "怎么预约", "如何预约", "需要带", "带什么",
            "材料", "费用", "多少钱", "收费", "几点", "上班时间", "在哪", "地址", "多久"
    };

    // ===== 挂号确认（仅当存在待确认号源时生效） =====
    /** 挂号确认词：上一轮 AI 给出"最近可约，可以吗？"后，患者回复这些词表示同意 */
    public static final String[] APPOINT_CONFIRM = {
            "可以", "好的", "好", "行", "确认", "没问题", "嗯", "是的", "对", "就这个", "要"
    };

    /** 挂号拒绝词：患者改主意或拒绝建议时命中（判断时优先于确认词） */
    public static final String[] APPOINT_DENY = {
            "不", "算了", "换", "别的", "不要", "取消"
    };

    // ===== 就诊进度意图（PROGRESS / DOCTOR_PROGRESS） =====
    public static final String[] PROGRESS = {
            "进度", "到哪", "排到", "就诊状态", "号到",
            "就诊进度", "就诊记录", "接诊记录", "就诊情况"
    };

    // ===== 医生端查询（DOCTOR_PROGRESS 单独判定） =====
    public static final String[] DOCTOR_PROGRESS = {
            "就诊进度", "进度", "就诊记录", "接诊记录", "就诊情况"
    };
    public static final String[] DOCTOR_PATIENTS = {
            "接诊患者", "我的患者", "待就诊", "叫号", "查患者"
    };

    // ===== 报告/处方意图 =====
    public static final String[] REPORT_PRESCRIPTION = {
            "报告", "处方", "药方", "检查结果", "检验"
    };

    // ===== 挂号时间确认（仅当 AI 上轮在引导确认挂号时间时才触发） =====
    public static final String[] APPOINT_TIME_WORDS = {
            "明天", "今天", "后天", "上午", "下午", "晚上", "中午", "点"
    };
}

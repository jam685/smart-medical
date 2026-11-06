package com.hospital.smartmedical.module.ai.agent.state;

import com.hospital.smartmedical.module.ai.agent.enums.ConsultStage;
import com.hospital.smartmedical.module.ai.agent.enums.ReplySource;
import com.hospital.smartmedical.module.ai.agent.enums.UserRole;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 多智能体图状态（MedicalGraphState）。
 *
 * <p>作为图内单一状态对象（state key = "graph"），每次节点执行返回更新后的完整对象。
 * 会话独立存储（Redis/内存），通过 sessionId + patientId + doctorId 隔离上下文，杜绝串病情。</p>
 */
@Data
public class MedicalGraphState implements Serializable {

    /** 会话唯一 ID（Redis 缓存 Key 组成部分） */
    private String sessionId;

    /** 患者 ID */
    private Long patientId;

    /** 患者基本情况档案（姓名/性别/年龄，预问诊注入提示词供 Agent 记忆） */
    private String patientProfile;

    /** 医生基本情况档案（姓名/科室/职称/工号，医生端会话注入提示词供 Agent 认识当前医生） */
    private String doctorProfile;

    /** 医生 ID（预问诊阶段可为 null） */
    private Long doctorId;

    /** 当前操作用户身份 PATIENT / DOCTOR */
    private UserRole role;

    /** 当前问诊阶段（路由核心依据） */
    private ConsultStage currentStage;

    /** 对话历史（文本 + 图片多模态） */
    private List<ChatMessage> chatHistory = new ArrayList<>();

    /** 本次上传图片地址集合 */
    private List<String> imageUrls = new ArrayList<>();

    /** 收集到的症状摘要（预问诊填充） */
    private String symptomSummary;

    /** AI 推荐科室 */
    private String suggestDept;

    /** AI 生成给医生的回复草稿 */
    private String replyDraft;

    /** AI 处方建议草稿 */
    private String prescriptionDraft;

    /** 草稿版本号（每次落库自增） */
    private Integer draftVersion = 0;

    /** 审计临时信息（LLM 调用异常、解析失败等） */
    private String auditInfo;

    /** 异常信息（图正常终止不卡死） */
    private String errorMsg;

    /** 本次流转是否产生需要落库的草稿（saveDraftAuditNode 执行依据） */
    private boolean hasNewDraft;

    /** 草稿类型：SYMPTOM_SUMMARY 症状摘要 / REPLY_DRAFT 回复草稿 / PRESCRIPTION_DRAFT 处方草稿 */
    private String draftType;

    /** 给前端展示的最终回复文本 */
    private String reply;

    /**
     * 本轮回复来源节点（每轮开始清空）。
     *
     * <p>语义缓存据此过滤：只有 {@code ASSISTANT_DIALOG} 的回复允许进缓存；
     * 业务节点的回复含实时号源/列表，不能跨患者复用。</p>
     */
    private ReplySource replySource;

    /** 当前用户最新一条文本消息（节点快捷读取用） */
    private String lastUserText;

    /** 最近一次业务工具执行结果（结构化：intent/success/action/appointmentId/data，供后续节点与审计使用） */
    private Map<String, Object> lastBusinessResult;

    /** 路由阶段 LLM 判定的业务意图缓存（businessAgentNode 直接复用，避免重复调用 LLM） */
    private String routeIntent;

    /** 本轮产生的结构化卡片（如挂号成功卡片，每轮开始清空，供前端实时渲染） */
    private Map<String, Object> currentRoundCard;

    /** 意图识别抽取的科室实体（本轮消息，供业务工具精准查询；每轮开始清空） */
    private String intentDept;

    /**
     * 待确认的号源 ID（跨轮保留）。
     *
     * <p>场景：患者期望时段已满，系统给出"最近可约"建议但不直接下单，
     * 把候选号源记在这里；下一轮患者回复"可以"时再用它完成挂号。
     * 不做每轮清空，只在"确认下单 / 明确拒绝 / 换时间"时清除。</p>
     */
    private Long pendingSlotId;

    /** 意图识别抽取的医生姓名实体（本轮消息；每轮开始清空） */
    private String intentDoctorName;

    /** 医生端意图识别抽取的患者姓名实体（本轮消息；每轮开始清空） */
    private String intentPatientName;

    /** 医生端意图识别抽取的时间范围描述（如"今天""上周""10月1日到10月5日"；每轮开始清空） */
    private String intentTimeDesc;

    /** 是否数字医生小林（AI 客服）独立会话：与预问诊会话隔离，不进入预问诊历史会话列表 */
    private boolean assistantSession;
}

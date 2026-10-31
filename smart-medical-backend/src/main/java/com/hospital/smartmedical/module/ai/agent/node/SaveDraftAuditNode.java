package com.hospital.smartmedical.module.ai.agent.node;

import com.hospital.smartmedical.module.admin.entity.AuditLog;
import com.hospital.smartmedical.module.admin.mapper.AuditLogMapper;
import com.hospital.smartmedical.module.ai.entity.AiPrescriptionDraft;
import com.hospital.smartmedical.module.ai.mapper.AiPrescriptionDraftMapper;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 草稿 & 审计保存公共节点（saveDraftAuditNode）。
 *
 * <p>统一收口：读取 State 内草稿，写入草稿版本表 sm_prescription_draft + 审计日志表 sm_audit_log；
 * 强制禁止写入正式处方表（正式处方入库唯一入口为医生确认按钮触发的后端业务逻辑）；
 * 草稿版本自增，每次 AI 生成草稿自动新增版本并审计，满足追溯要求。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaveDraftAuditNode implements NodeAction<AgentState> {

    private final AiPrescriptionDraftMapper draftMapper;
    private final AuditLogMapper auditLogMapper;

    /**
     * 节点执行：无新草稿直接返回；有草稿则版本自增 + 落库草稿表 + 写审计日志。
     *
     * @param state 图状态（含 graph 键的 MedicalGraphState）
     * @return 更新后的 graph 状态
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        MedicalGraphState g = state.<MedicalGraphState>value("graph")
                .orElseThrow(() -> new IllegalStateException("缺少 graph 状态"));
        // 无新草稿：直接返回，不落库（保持图正常终止）
        if (!g.isHasNewDraft()) {
            return Map.of("graph", g);
        }
        try {
            // 1) 草稿版本自增
            int version = (g.getDraftVersion() == null ? 0 : g.getDraftVersion()) + 1;
            g.setDraftVersion(version);
            // 2) 写入草稿版本表（AI 只写草稿，禁止写正式处方表）
            AiPrescriptionDraft draft = new AiPrescriptionDraft();
            draft.setSessionId(g.getSessionId());
            draft.setPatientId(g.getPatientId());
            draft.setDoctorId(g.getDoctorId());
            draft.setType(g.getDraftType());
            draft.setContent(draftContent(g, g.getDraftType()));
            draft.setVersion(version);
            draft.setStatus("PENDING");
            draft.setCreatedAt(LocalDateTime.now());
            draft.setUpdatedAt(LocalDateTime.now());
            draftMapper.insert(draft);
            // 3) 写审计日志（AI_DRAFT_GENERATED，敏感等级 MEDIUM，待审核 PENDING）
            AuditLog audit = new AuditLog();
            audit.setUserId(g.getRole() != null && g.getRole().name().equals("DOCTOR") ? g.getDoctorId() : g.getPatientId());
            audit.setRole(g.getRole() == null ? "AI" : g.getRole().name());
            audit.setAction("AI_DRAFT_GENERATED");
            audit.setTargetId(draft.getId());
            audit.setDetail("AI 生成" + typeDesc(g.getDraftType()) + "草稿 v" + version + "，会话：" + g.getSessionId());
            audit.setSensitiveLevel("MEDIUM");
            audit.setReviewStatus("PENDING");
            audit.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(audit);
            // 4) 标记本轮草稿已落库
            g.setHasNewDraft(false);
            log.info("AI 草稿已落库：session={}, type={}, version={}, draftId={}",
                    g.getSessionId(), g.getDraftType(), version, draft.getId());
        } catch (Exception e) {
            log.error("草稿保存失败：{}", e.getMessage(), e);
            g.setErrorMsg("草稿保存失败：" + e.getMessage());
            g.setAuditInfo("SAVE_DRAFT_ERROR");
        }
        return Map.of("graph", g);
    }

    /**
     * 按草稿类型取 State 中的草稿内容。
     */
    private String draftContent(MedicalGraphState g, String type) {
        if (type == null) {
            return g.getReplyDraft();
        }
        switch (type) {
            case "SYMPTOM_SUMMARY":
                return g.getSymptomSummary();
            case "PRESCRIPTION_DRAFT":
                return g.getPrescriptionDraft();
            case "REPLY_DRAFT":
            default:
                return g.getReplyDraft();
        }
    }

    /**
     * 草稿类型中文描述（审计详情用）。
     */
    private String typeDesc(String type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case "SYMPTOM_SUMMARY":
                return "症状摘要";
            case "PRESCRIPTION_DRAFT":
                return "处方建议";
            case "REPLY_DRAFT":
                return "回复";
            default:
                return type;
        }
    }
}

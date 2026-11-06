package com.hospital.smartmedical.module.ai.service.impl;

import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.report.entity.Report;
import com.hospital.smartmedical.module.report.mapper.ReportMapper;
import com.hospital.smartmedical.module.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助分析服务实现（Demo 模拟模式，见 application.yml 的 ai.mock-mode）。
 * V2.3 阶段先返回结构化模拟结果，接入真实 FastAPI + 大模型后再替换内部实现。
 */
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final ReportMapper reportMapper;

    // ===================== 1. 病情分析（Demo） =====================

    @Override
    public Map<String, Object> clinicalAnalysis(String chiefComplaint, String description,
                                                Map<String, Object> patientInfo) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("conditionAnalysis", "基于主诉「" + chiefComplaint + "」及您的补充描述，可能的原因包括："
                + "感染性疾病、自身免疫反应或功能性紊乱。建议结合既往史、家族史进一步排查。");
        res.put("diagnosisSuggestion", "建议完善：血常规、生化全项、相关影像学检查，以明确诊断方向。");
        res.put("differentialDiagnosis", "鉴别诊断：\n1. 细菌性 vs 病毒性感染\n"
                + "2. 急性 vs 慢性炎症\n3. 器质性病变 vs 功能性症状");
        res.put("medicationSuggestion", "确诊病例前不推荐具体药物；必要时的对症处理需结合患者肝肾功能与用药史，"
                + "具体用药请医生决定。（如发热可先物理降温，必要时解热镇痛并评估后使用）");
        res.put("riskLevel", "MEDIUM");
        res.put("confidence", new BigDecimal("0.82"));
        res.put("references", "《临床诊疗指南》内科分册 2023；UpToDate 临床决策支持");
        res.put("disclaimer", "本 AI 分析仅供医生参考，不能替代临床综合判断。");
        return res;
    }

    // ===================== 2. 检查建议（Demo） =====================

    @Override
    public Map<String, Object> examinationSuggestions(String chiefComplaint, String description) {
        List<String> suggestions = new ArrayList<>();
        if (chiefComplaint != null && (chiefComplaint.contains("咳嗽") || chiefComplaint.contains("发热"))) {
            suggestions.add("胸部 X 光检查");
            suggestions.add("血常规 + CRP");
            suggestions.add("咽拭子病毒检测");
        }
        if (chiefComplaint != null && (chiefComplaint.contains("腹痛") || chiefComplaint.contains("腹泻"))) {
            suggestions.add("腹部 B 超");
            suggestions.add("大便常规 + 潜血");
            suggestions.add("肝功能");
        }
        if (chiefComplaint != null && (chiefComplaint.contains("头痛") || chiefComplaint.contains("头晕"))) {
            suggestions.add("血压测量");
            suggestions.add("头颅 CT");
            suggestions.add("血糖、血脂");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("血常规 + CRP");
            suggestions.add("生化全项");
            suggestions.add("心电图");
        }
        suggestions.add("根据实际情况补充：血沉、肿瘤标志物等");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("examinationList", suggestions);
        res.put("urgency", "MEDIUM");
        res.put("confidence", new BigDecimal("0.86"));
        res.put("note", "建议按优先级安排检查，先完成基础筛查。");
        return res;
    }

    // ===================== 3. 回复草稿（Demo） =====================

    @Override
    public Map<String, Object> replyDrafts(String chiefComplaint, String description) {
        String draft = "您好！根据您描述的症状（" + (chiefComplaint != null ? chiefComplaint : "未明确")
                + "），我已了解您的情况。初步意见如下：\n"
                + "1. 建议注意休息，保持良好作息；\n"
                + "2. 观察症状变化，如出现加重请及时复诊；\n"
                + "3. 具体治疗方案需结合进一步检查结果确定。\n\n（以上为 AI 生成草稿，供医生参考）";
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("draft", draft);
        res.put("confidence", new BigDecimal("0.80"));
        res.put("tone", "professional");
        res.put("suggestedEdit", "建议医生结合问诊实际情况进行个性化调整。");
        return res;
    }

    // ===================== 4. 图片/报告分析（Demo, 带归属校验） =====================

    @Override
    public Map<String, Object> imageAnalysis(Long reportId, Long userId, com.hospital.smartmedical.common.enums.Role role) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 归属校验：患者只能看自己的报告；医生/管理员放行
        if (role == com.hospital.smartmedical.common.enums.Role.PATIENT
                && report.getPatientId() != null
                && !userId.equals(report.getPatientId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看他人报告");
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reportId", reportId);
        res.put("reportTitle", report.getReportType() != null ? report.getReportType() : "未命名报告");
        res.put("analysis", "（Demo）已读取报告，正式 AI 图片理解将在接真实大模型后提供。");
        res.put("confidence", BigDecimal.ZERO);
        res.put("needHuman", true);
        res.put("disclaimer", "本 AI 分析仅供医生参考，不能替代临床综合判断。");
        return res;
    }
}
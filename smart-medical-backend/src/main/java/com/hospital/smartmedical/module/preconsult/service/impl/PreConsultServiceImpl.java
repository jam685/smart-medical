package com.hospital.smartmedical.module.preconsult.service.impl;

import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.preconsult.entity.PreConsult;
import com.hospital.smartmedical.module.preconsult.mapper.PreConsultMapper;
import com.hospital.smartmedical.module.preconsult.service.PreConsultService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 智能预问诊服务实现。
 *
 * <p>核心逻辑：保存患者消息 -> 基于关键词模拟 AI 引导话术 -> 保存 AI 回复（同 sessionId）。
 * AI 只做症状采集与挂号引导，不做诊断、不开方（PRE_CONSULT 边界）。</p>
 */
@Service
@RequiredArgsConstructor
public class PreConsultServiceImpl implements PreConsultService {

    private final PreConsultMapper preConsultMapper;

    /** 发送方常量 */
    private static final String SENDER_PATIENT = "PATIENT";
    private static final String SENDER_AI = "AI";

    /** 消息类型常量 */
    private static final String MSG_TEXT = "TEXT";
    private static final String MSG_IMAGE = "IMAGE";

    /**
     * 发送预问诊消息（PRE-01）——旧规则引擎链路，已废弃。
     *
     * <p>患者端全链路已迁移到 {@link com.hospital.smartmedical.module.ai.agent.service.AiAgentService}
     * （/ai/patient-chat，LangGraph 真实 LLM 图流转）。AiAgentService 的 mirrorToPreConsult 方法是
     * sm_pre_consult 表的唯一写入方。</p>
     *
     * <p>本方法保留仅为兼容 `/pre-consults` 读接口（getHistory / listSessions）对应的 Controller
     * 路由定义完整。前端无任何页面调用此写接口。</p>
     *
     * @deprecated 由 AiAgentService.process() + mirrorToPreConsult() 取代
     */
    @Deprecated
    @Override
    @Transactional
    public Map<String, Object> sendMessage(Long patientId, String sessionId, String content, String imageUrl) {
        String sid = (sessionId == null || sessionId.isBlank()) ? genSessionId() : sessionId;

        boolean isImage = imageUrl != null && !imageUrl.isBlank();

        // 1) 保存患者消息
        PreConsult patientMsg = new PreConsult();
        patientMsg.setPatientId(patientId);
        patientMsg.setSessionId(sid);
        patientMsg.setSender(SENDER_PATIENT);
        patientMsg.setMessageType(isImage ? MSG_IMAGE : MSG_TEXT);
        patientMsg.setContent(content);
        patientMsg.setImageUrl(imageUrl);
        patientMsg.setCreatedAt(LocalDateTime.now());
        preConsultMapper.insert(patientMsg);

        // 2) 生成 AI 引导话术并保存
        String aiReply = generateAiReply(content);
        PreConsult aiMsg = new PreConsult();
        aiMsg.setPatientId(patientId);
        aiMsg.setSessionId(sid);
        aiMsg.setSender(SENDER_AI);
        aiMsg.setMessageType(MSG_TEXT);
        aiMsg.setContent(aiReply);
        aiMsg.setCreatedAt(LocalDateTime.now());
        preConsultMapper.insert(aiMsg);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sid);
        result.put("reply", aiReply);
        result.put("aiMessage", aiMsg);
        return result;
    }

    /**
     * 获取预问诊会话历史（PRE-02）。
     */
    @Override
    public List<PreConsult> getHistory(Long patientId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        LambdaQueryWrapper<PreConsult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PreConsult::getPatientId, patientId);
        wrapper.eq(PreConsult::getSessionId, sessionId);
        wrapper.orderByAsc(PreConsult::getCreatedAt);
        return preConsultMapper.selectList(wrapper);
    }

    /**
     * 患者预问诊记录列表（PRE-03）——按会话去重，返回每次会话的首条信息与消息数。
     */
    @Override
    public List<Map<String, Object>> listSessions(Long patientId) {
        LambdaQueryWrapper<PreConsult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PreConsult::getPatientId, patientId);
        wrapper.orderByDesc(PreConsult::getCreatedAt);
        List<PreConsult> list = preConsultMapper.selectList(wrapper);

        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (PreConsult m : list) {
            if (seen.add(m.getSessionId())) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("sessionId", m.getSessionId());
                s.put("lastContent", m.getContent());
                s.put("lastTime", m.getCreatedAt());
                sessions.add(s);
            }
        }
        return sessions;
    }

    /**
     * 结束预问诊会话（PRE-04）。V1.3 表无状态列，仅做会话存在性校验。
     */
    @Override
    public void closeSession(Long patientId, String sessionId) {
        LambdaQueryWrapper<PreConsult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PreConsult::getPatientId, patientId);
        wrapper.eq(PreConsult::getSessionId, sessionId);
        Long count = preConsultMapper.selectCount(wrapper);
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    // ===================== 内部工具 =====================

    /** 生成会话标识。 */
    private String genSessionId() {
        return "pre_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 基于关键词模拟 AI 引导话术。
     * <p>合规边界：只做症状采集与挂号引导，不诊断、不开方。</p>
     *
     * @param content 患者输入文本
     * @return AI 引导话术
     */
    private String generateAiReply(String content) {
        if (content == null || content.isBlank()) {
            return "您好，请问您主要有哪些不适症状？方便描述一下症状持续多久了吗？我会根据您的描述帮您梳理，并引导您挂合适的科室（仅作参考，不做诊断）。";
        }
        if (content.contains("血糖") || content.contains("糖尿病") || content.contains("内分泌")
                || content.contains("口渴") || content.contains("多饮") || content.contains("多尿")) {
            return "您好，您提到了口渴、多尿或血糖相关的困扰，这类情况通常与内分泌系统有关。建议您可以挂内分泌科，先做空腹血糖和糖化血红蛋白检查。"
                    + "在此之前，为了更好为您引导挂号，能否告诉我这种情况持续多久了？最近有没有体重明显变化？"
                    + "（我只是帮您采集症状、引导挂号，开药和诊断需要由医生完成）";
        }
        if (content.contains("发音") || content.contains("心血管") || content.contains("胸痛")
                || content.contains("心慌") || content.contains("高血压")) {
            return "您好，您提到胸痛、心慌或高血压等不适，建议优先挂心内科进行进一步检查。请问这种不适是阵发性还是持续性的？平时有高血压病史吗？"
                    + "（我只做症状采集和挂号引导，请以医生诊断为准）";
        }
        if (content.contains("咳嗽") || content.contains("呼吸") || content.contains("发热")) {
            return "您好，您提到咳嗽、发热或呼吸不适，建议挂呼吸科做进一步检查。请问咳嗽持续多久了？有痰吗？有没有头痛或肌肉酸痛？"
                    + "（我只做症状采集和挂号引导，具体诊疗请由医生完成）";
        }
        return "您好，感谢您的描述。为了帮您挂到合适的科室，请问您可以补充一下：1）症状从什么时候开始？2）大概在什么部位？3）有没有加重或缓解的因素？"
                + "我是智能预问诊助手，只做症状采集与挂号引导，不做诊断、不开方，最终诊疗请以医生的判断为准。";
    }
}
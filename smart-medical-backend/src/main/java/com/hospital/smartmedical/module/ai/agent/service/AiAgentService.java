package com.hospital.smartmedical.module.ai.agent.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.module.ai.agent.enums.ConsultStage;
import com.hospital.smartmedical.module.ai.agent.enums.UserRole;
import com.hospital.smartmedical.module.ai.agent.graph.MedicalGraphBuilder;
import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import com.hospital.smartmedical.module.ai.agent.stream.StreamTokenRegistry;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * AI 多智能体入口服务。
 *
 * <p>职责：加载/创建会话 State（会话隔离）→ 追加本次用户消息（文本/图片）→
 * 图流转（条件路由分派节点）→ State 回写存储 → 返回前端展示结果。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAgentService {

    private final MedicalGraphBuilder graphBuilder;
    private final AiStateStore stateStore;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;

    /** 时间格式化 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 处理一次 AI 对话请求（非流式，患者客服 / 医生辅助统一入口，按角色走图）。
     *
     * @param userId    当前登录用户 ID
     * @param role      当前登录角色（PATIENT/DOCTOR）
     * @param sessionId 会话 ID（为空则新建）
     * @param content   用户消息文本
     * @param imageUrls 本次上传图片地址（可为空）
     * @return 前端展示结果（sessionId/reply/科室推荐/症状摘要/草稿等）
     */
    public Map<String, Object> process(Long userId, Role role, String sessionId, String content, List<String> imageUrls) {
        return process(userId, role, sessionId, content, imageUrls, null);
    }

    /**
     * 兼容预问诊/医生辅助入口：不区分会话来源（source 为空，会话默认 ai_ 前缀）。
     *
     * @param userId    当前登录用户 ID
     * @param role      当前登录角色
     * @param sessionId 会话 ID（为空则新建）
     * @param content   用户消息文本
     * @param imageUrls 图片地址集合（可空）
     * @param onToken   增量文本回调（非流式传 null）
     * @return 前端展示结果
     */
    public Map<String, Object> process(Long userId, Role role, String sessionId, String content, List<String> imageUrls,
                                       Consumer<String> onToken) {
        return process(userId, role, sessionId, content, imageUrls, onToken, null);
    }

    /**
     * 处理一次 AI 对话请求（流式/非流式统一入口，患者客服 / 医生辅助，按角色走图）。
     *
     * @param userId    当前登录用户 ID
     * @param role      当前登录角色（PATIENT/DOCTOR）
     * @param sessionId 会话 ID（为空则新建）
     * @param content   用户消息文本
     * @param imageUrls 本次上传图片地址（可为空）
     * @param onToken   模型增量文本回调（流式 SSE 用；非流式传 null）
     * @param source    会话来源：assistant=患者端数字医生小林（cs_ 前缀）；doctor_assistant=医生端数字医生小林（ds_ 前缀）；
     *                  null/其他=预问诊。客服/医生助手会话均与预问诊隔离
     * @return 前端展示结果（sessionId/reply/科室推荐/症状摘要/草稿/卡片等）
     */
    public Map<String, Object> process(Long userId, Role role, String sessionId, String content, List<String> imageUrls,
                                       Consumer<String> onToken, String source) {
        // 医生端数字医生小林会话（ds_ 前缀）：与患者端客服会话独立隔离
        boolean doctorAssistant = "doctor_assistant".equalsIgnoreCase(source);
        // 是否数字医生小林会话（含医生端）：独立前缀 + 不出现在预问诊历史会话列表
        boolean assistant = "assistant".equalsIgnoreCase(source) || doctorAssistant;
        // 1) 会话标识：为空则新建（患者客服 cs_ / 医生助手 ds_ / 预问诊 ai_ 前缀区分）
        String prefix = doctorAssistant ? "ds_" : (assistant ? "cs_" : "ai_");
        String sid = (sessionId == null || sessionId.isBlank())
                ? prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : sessionId;
        // 2) 加载或创建会话 State（会话隔离）；注册流式 token 回调（节点线程从注册表实时取增量推送，绕开序列化/线程问题）
        MedicalGraphState g = stateStore.get(sid).orElseGet(() -> createState(sid, userId, role));
        // 标记客服会话（兼容已持久化的 cs_/ds_ 会话），供历史会话列表过滤
        g.setAssistantSession(assistant || sid.startsWith("cs_") || sid.startsWith("ds_"));
        StreamTokenRegistry.register(sid, onToken);
        try {
        // 3) 追加本次用户消息
        boolean isImage = imageUrls != null && !imageUrls.isEmpty();
        g.setLastUserText(content == null ? "" : content);
        ChatMessage userMsg = new ChatMessage(role == Role.DOCTOR ? "DOCTOR" : "PATIENT",
                isImage ? "IMAGE" : "TEXT",
                content,
                isImage ? imageUrls.get(0) : null,
                LocalDateTime.now().format(TIME_FMT));
        g.getChatHistory().add(userMsg);
        g.setImageUrls(imageUrls == null ? new ArrayList<>() : imageUrls);
        // 清空上一轮卡片、意图实体与路由意图，避免非业务轮次返回过期卡片/拿旧数据过滤
        // （lastBusinessResult 不清空：需保留 pendingDoctor 供确认轮续挂号）
        g.setCurrentRoundCard(null);
        g.setIntentDept(null);
        g.setIntentDoctorName(null);
        g.setIntentPatientName(null);
        g.setIntentTimeDesc(null);
        g.setRouteIntent(null);
        // 4) 身份兜底与阶段初始化
        if (role == Role.PATIENT) {
            g.setPatientId(userId);
            g.setRole(UserRole.PATIENT);
            // 患者档案：首次对话时从患者表读取真实基本信息（姓名/性别/年龄），供预问诊 Agent 记忆
            if (g.getPatientProfile() == null || g.getPatientProfile().isBlank()) {
                g.setPatientProfile(buildPatientProfile(userId));
            }
            if (g.getCurrentStage() == null) {
                g.setCurrentStage(ConsultStage.PRE_CONSULT);
            }
        } else if (role == Role.DOCTOR) {
            g.setDoctorId(userId);
            g.setRole(UserRole.DOCTOR);
            // 医生档案：首次对话时从医生表读取真实基本信息（姓名/科室/职称/工号），供医生端 Agent 认识当前医生
            if (g.getDoctorProfile() == null || g.getDoctorProfile().isBlank()) {
                g.setDoctorProfile(buildDoctorProfile(userId));
            }
            if (g.getCurrentStage() == null) {
                g.setCurrentStage(ConsultStage.DOCTOR_RECEIVE);
            }
        }
        // 5) 图流转（异常兜底：写入 errorMsg，图正常终止不卡死）
        try {
            Optional<AgentState> out = graphBuilder.graph().invoke(Map.of("graph", g));
            MedicalGraphState finalG = g;
            g = out.map(s -> s.<MedicalGraphState>value("graph").orElse(finalG)).orElse(g);
        } catch (Exception e) {
            log.error("AI 图执行失败：{}", e.getMessage(), e);
            g.setErrorMsg("AI 图执行失败：" + e.getMessage());
            if (g.getReply() == null || g.getReply().isBlank()) {
                g.setReply("抱歉，暂时无法回复，请稍后再试。");
            }
        }
        // 6) State 回写存储
        stateStore.save(sid, g);
        // 7) 组装返回
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sid);
        result.put("reply", g.getReply());
        result.put("suggestDept", g.getSuggestDept());
        result.put("symptomSummary", g.getSymptomSummary());
        result.put("currentStage", g.getCurrentStage() == null ? null : g.getCurrentStage().name());
        result.put("draftVersion", g.getDraftVersion());
        result.put("replyDraft", g.getReplyDraft());
        result.put("prescriptionDraft", g.getPrescriptionDraft());
        result.put("errorMsg", g.getErrorMsg());
        // 结构化卡片（挂号成功时前端渲染挂号信息小卡片，其他场景为 null）
        result.put("card", resolveCard(g));
        return result;
        } finally {
            // 8) 清理流式回调注册（图流转结束/异常均执行，防止注册表泄漏；非流式请求 onToken 为 null 无影响）
            StreamTokenRegistry.unregister(sid);
        }
    }

    /**
     * 提取本轮产生的结构化卡片数据（挂号成功才有 card，其余场景返回 null）。
     *
     * @param g 图状态（含 currentRoundCard）
     * @return 卡片数据 Map；无卡片返回 null
     */
    private Map<String, Object> resolveCard(MedicalGraphState g) {
        return g.getCurrentRoundCard();
    }

    /**
     * 查询会话对话历史（归属校验：患者仅本人，医生仅本人）。
     *
     * @param userId    登录用户 ID
     * @param role      登录角色
     * @param sessionId 会话 ID
     * @return 历史消息列表
     */
    public List<ChatMessage> history(Long userId, Role role, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayList<>();
        }
        Optional<MedicalGraphState> state = stateStore.get(sessionId);
        if (state.isEmpty()) {
            return new ArrayList<>();
        }
        MedicalGraphState g = state.get();
        // 归属校验：防止跨用户读取会话
        if (role == Role.PATIENT && !userId.equals(g.getPatientId())) {
            return new ArrayList<>();
        }
        if (role == Role.DOCTOR && !userId.equals(g.getDoctorId())) {
            return new ArrayList<>();
        }
        return g.getChatHistory();
    }

    /**
     * 患者历史会话列表（前端会话面板）。
     *
     * @param patientId 患者 ID
     * @return 会话摘要列表
     */
    public List<Map<String, Object>> listSessions(Long patientId) {
        List<MedicalGraphState> sessions = stateStore.listByPatient(patientId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MedicalGraphState s : sessions) {
            // 数字医生小林的客服会话独立，不出现在预问诊历史会话列表
            if (s.isAssistantSession() || (s.getSessionId() != null && s.getSessionId().startsWith("cs_"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", s.getSessionId());
            item.put("lastContent", lastContent(s));
            item.put("lastTime", lastTime(s));
            result.add(item);
        }
        return result;
    }

    // ===================== 内部工具 =====================

    /**
     * 组装患者基本情况档案（姓名/性别/年龄）。
     *
     * <p>数据来源为 sm_patient 表真实数据：姓名 realName、性别 gender；
     * 年龄从 18 位身份证号第 7~14 位（出生日期 YYYYMMDD）推算周岁。
     * 档案用于注入预问诊提示词，让 Agent 跨轮记住患者基本信息。</p>
     *
     * @param patientId 患者 ID
     * @return 档案文本（如「张三，男，35 岁」；患者不存在时返回空串）
     */
    private String buildPatientProfile(Long patientId) {
        Patient patient = patientId == null ? null : patientMapper.selectById(patientId);
        if (patient == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (patient.getRealName() != null && !patient.getRealName().isBlank()) {
            sb.append(patient.getRealName());
        }
        if (patient.getGender() != null && !patient.getGender().isBlank()) {
            sb.append("，").append(patient.getGender());
        }
        int age = ageFromIdCard(patient.getIdCard());
        if (age > 0) {
            sb.append("，").append(age).append(" 岁");
        }
        return sb.toString();
    }

    /**
     * 组装医生基本情况档案（姓名/科室/职称/工号）。
     *
     * <p>数据来源为 sm_doctor 表真实数据：姓名 realName、科室 department、职称 title、工号 employeeNo。
     * 档案用于注入医生端提示词，让 Agent 认识当前登录医生，可自然以其姓名/科室称呼与作答。</p>
     *
     * @param doctorId 医生 ID
     * @return 档案文本（如「张医生，呼吸科，主治医师，工号 D003」；医生不存在时返回空串）
     */
    private String buildDoctorProfile(Long doctorId) {
        Doctor doctor = doctorId == null ? null : doctorMapper.selectById(doctorId);
        if (doctor == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (doctor.getRealName() != null && !doctor.getRealName().isBlank()) {
            sb.append(doctor.getRealName());
        }
        if (doctor.getDepartment() != null && !doctor.getDepartment().isBlank()) {
            sb.append("，").append(doctor.getDepartment());
        }
        if (doctor.getTitle() != null && !doctor.getTitle().isBlank()) {
            sb.append("，").append(doctor.getTitle());
        }
        if (doctor.getEmployeeNo() != null && !doctor.getEmployeeNo().isBlank()) {
            sb.append("，工号 ").append(doctor.getEmployeeNo());
        }
        return sb.toString();
    }

    /**
     * 从 18 位身份证号提取出生日期并推算周岁年龄。
     *
     * @param idCard 身份证号（可能为空/格式异常）
     * @return 周岁年龄；无法解析（空/不足 14 位/出生日期非法/生于未来）时返回 0
     */
    private int ageFromIdCard(String idCard) {
        if (idCard == null || idCard.length() < 14) {
            return 0;
        }
        try {
            // 身份证第 7~14 位为出生日期 YYYYMMDD（18 位身份证，15 位旧证此处也含出生日期）
            int year = Integer.parseInt(idCard.substring(6, 10));
            int month = Integer.parseInt(idCard.substring(10, 12));
            int day = Integer.parseInt(idCard.substring(12, 14));
            LocalDate birth = LocalDate.of(year, month, day);
            LocalDate today = LocalDate.now();
            if (birth.isAfter(today)) {
                return 0;
            }
            int age = today.getYear() - birth.getYear();
            // 未过今年生日则减一岁
            if (today.getMonthValue() < birth.getMonthValue()
                    || (today.getMonthValue() == birth.getMonthValue() && today.getDayOfMonth() < birth.getDayOfMonth())) {
                age--;
            }
            return Math.max(age, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 创建新会话 State。
     */
    private MedicalGraphState createState(String sessionId, Long userId, Role role) {
        MedicalGraphState s = new MedicalGraphState();
        s.setSessionId(sessionId);
        s.setPatientId(role == Role.PATIENT ? userId : null);
        s.setDoctorId(role == Role.DOCTOR ? userId : null);
        s.setRole(role == Role.DOCTOR ? UserRole.DOCTOR : UserRole.PATIENT);
        s.setCurrentStage(role == Role.DOCTOR ? ConsultStage.DOCTOR_RECEIVE : ConsultStage.PRE_CONSULT);
        s.setDraftVersion(0);
        return s;
    }

    /**
     * 取会话最后一条消息内容。
     */
    private String lastContent(MedicalGraphState s) {
        List<ChatMessage> list = s.getChatHistory();
        if (list == null || list.isEmpty()) {
            return "";
        }
        ChatMessage last = list.get(list.size() - 1);
        return last.getContent() == null || last.getContent().isBlank()
                ? ("IMAGE".equals(last.getMessageType()) ? "[图片]" : "")
                : last.getContent();
    }

    /**
     * 取会话最后一条消息时间。
     */
    private String lastTime(MedicalGraphState s) {
        List<ChatMessage> list = s.getChatHistory();
        if (list == null || list.isEmpty()) {
            return "";
        }
        return list.get(list.size() - 1).getCreatedAt();
    }
}

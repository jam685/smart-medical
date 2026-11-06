package com.hospital.smartmedical.module.ai.agent.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.ai.agent.cache.SemanticCacheService;
import com.hospital.smartmedical.module.ai.agent.enums.ConsultStage;
import com.hospital.smartmedical.module.ai.agent.enums.ReplySource;
import com.hospital.smartmedical.module.ai.agent.enums.UserRole;
import com.hospital.smartmedical.module.ai.agent.graph.MedicalGraphBuilder;
import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import com.hospital.smartmedical.module.ai.agent.stream.StreamTokenRegistry;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.preconsult.entity.PreConsult;
import com.hospital.smartmedical.module.preconsult.mapper.PreConsultMapper;
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
import java.util.concurrent.CompletableFuture;
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
    private final SemanticCacheService semanticCacheService;
    private final PreConsultMapper preConsultMapper;

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
        // 2) 加载或创建会话 State（会话隔离 + 归属校验，防会话 IDOR 横向越权）
        Optional<MedicalGraphState> existing = stateStore.get(sid);
        MedicalGraphState g;
        boolean stateIsNew;
        if (existing.isPresent()) {
            // 会话已存在：做归属校验，防止攻击者拿他人 sessionId 读对话
            MedicalGraphState prev = existing.get();
            boolean mismatched = (role == Role.PATIENT && prev.getPatientId() != null && !userId.equals(prev.getPatientId()))
                    || (role == Role.DOCTOR && prev.getDoctorId() != null && !userId.equals(prev.getDoctorId()));
            if (mismatched) {
                log.warn("会话归属不匹配，拒绝访问：sid={}, loginRole={}, loginUserId={}, state.patientId={}, state.doctorId={}",
                        sid, role, userId, prev.getPatientId(), prev.getDoctorId());
                throw new BizException(ErrorCode.FORBIDDEN, "会话归属校验失败，无权访问该会话");
            }
            g = prev;
            stateIsNew = false;
        } else {
            g = createState(sid, userId, role);
            stateIsNew = true;
        }
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
        // 首轮判定（此刻历史上只有本轮用户消息）：首轮回答通常带"您好，我是数字医生小林"等开场白，
        // 写进语义缓存后会被复用到其他患者，串场且无复用价值 → 首轮不写缓存
        boolean firstRound = g.getChatHistory().size() <= 1;
        g.setImageUrls(imageUrls == null ? new ArrayList<>() : imageUrls);
        // 清空上一轮卡片、意图实体与路由意图，避免非业务轮次返回过期卡片/拿旧数据过滤
        // （lastBusinessResult 不清空：需保留 pendingDoctor 供确认轮续挂号）
        g.setCurrentRoundCard(null);
        g.setIntentDept(null);
        g.setIntentDoctorName(null);
        g.setIntentPatientName(null);
        g.setIntentTimeDesc(null);
        g.setRouteIntent(null);
        // 本轮回复来源（语义缓存过滤用）：每轮重置，避免沿用上一轮的来源误判
        g.setReplySource(null);
        // 4) 身份兜底与阶段初始化
        //    关键修复：stateIsNew=true 时才写入 patientId/doctorId；已存在会话禁止覆盖归属人（防 IDOR 横向越权）
        if (role == Role.PATIENT) {
            if (stateIsNew) g.setPatientId(userId);   // 仅新建会话写 patientId，已存在的不动
            g.setRole(UserRole.PATIENT);
            // 患者档案：首次对话时从患者表读取真实基本信息（姓名/性别/年龄），供预问诊 Agent 记忆
            if (g.getPatientProfile() == null || g.getPatientProfile().isBlank()) {
                g.setPatientProfile(buildPatientProfile(userId));
            }
            if (g.getCurrentStage() == null) {
                g.setCurrentStage(ConsultStage.PRE_CONSULT);
            }
        } else if (role == Role.DOCTOR) {
            if (stateIsNew) g.setDoctorId(userId);     // 仅新建会话写 doctorId，已存在的不动
            g.setRole(UserRole.DOCTOR);
            // 医生档案：首次对话时从医生表读取真实基本信息（姓名/科室/职称/工号），供医生端 Agent 认识当前医生
            if (g.getDoctorProfile() == null || g.getDoctorProfile().isBlank()) {
                g.setDoctorProfile(buildDoctorProfile(userId));
            }
            if (g.getCurrentStage() == null) {
                g.setCurrentStage(ConsultStage.DOCTOR_RECEIVE);
            }
        }
        // 语义缓存：仅患者端小林客服（cs_）参与；首轮不读也不写——
        // 首轮回答含"您好，我是数字医生小林"等开场白，命中别人的缓存会串场，写进去也会污染后续首轮
        boolean cacheEligible = assistant && !doctorAssistant && role == Role.PATIENT;
        boolean cacheScene = cacheEligible && !firstRound;
        if (cacheEligible && firstRound) {
            log.info("[语义缓存] 首轮不读不写缓存（sid={}）", sid);
        }
        SemanticCacheService.Probe cacheProbe = cacheScene ? semanticCacheService.probe(g.getLastUserText()) : null;
        boolean cacheHit = cacheProbe != null && cacheProbe.hit() && cacheProbe.cachedAnswer() != null;
        if (cacheHit) {
            // P2 命中返回：跳过图流转与 LLM 调用，缓存答案直接作为本轮回复
            String cached = cacheProbe.cachedAnswer();
            log.info("[语义缓存] 命中返回：similarity={}，跳过 LLM（sid={}）",
                    String.format("%.3f", cacheProbe.score()), sid);
            g.setReply(cached);
            // 本轮直接回放缓存，标记来源为 CACHE（写入侧会因 probe.hit() 跳过，不会重复写）
            g.setReplySource(ReplySource.CACHE);
            g.getChatHistory().add(new ChatMessage("AI", "TEXT", cached, null, LocalDateTime.now().format(TIME_FMT)));
            // 命中轮同样要消费掉本轮图片，避免图片地址残留到下一轮上下文
            g.setImageUrls(new ArrayList<>());
            pushCachedTokens(onToken, cached);
        } else {
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
        }
        // 6) State 回写存储
        stateStore.save(sid, g);
        // 6.5) 双写 MySQL sm_pre_consult：仅患者端小林客服（cs_）做持久化
        //      医生端 ds_ 仅为内部数字医生助手，无需落预问诊表
        mirrorToPreConsult(userId, role, sid, content, imageUrls, g, isImage);
        // 语义缓存沉淀（异步写入：向量+Redis 答案；仅客服对话节点的通用问答可写，业务卡片/业务节点回复不写）
        if (cacheScene && cacheableReplySource(g)) {
            // writeAfterReply 已改为同步（便于测试），这里用 CompletableFuture 保持「不阻塞响应」
            final String replyForCache = g.getReply();
            final String intentForCache = g.getRouteIntent();
            CompletableFuture.runAsync(() -> {
                try {
                    semanticCacheService.writeAfterReply(cacheProbe, replyForCache, intentForCache);
                } catch (Exception e) {
                    log.warn("语义缓存写入异常（已忽略，不影响主流程）：{}", e.getMessage());
                }
            });
        }
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
        // 语义缓存标记：前端可据此提示「来自相似问题的既往解答」，运维可据此统计真实节省的模型调用
        result.put("cached", cacheHit);
        result.put("cacheScore", cacheHit ? Math.round(cacheProbe.score() * 1000) / 1000.0 : null);
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
     * 语义缓存来源过滤：只有客服对话节点的回复允许进入缓存。
     *
     * <p>为什么必须过滤：图内多个节点都会写 {@code g.reply}，而业务节点（businessAgentNode）
     * 的回复携带实时号源、医生列表等上下文相关数据，一旦被跨患者复用会返回错误信息
     * （曾出现「挂号流程是什么」被业务节点答成推荐医生列表，并被缓存 30 天）。</p>
     *
     * <p>用 replySource 而不是 routeIntent / lastBusinessResult 的原因：前者在关键词兜底时为空，
     * 后者为了保留 pendingDoctor 跨轮不清空，都不能可靠表示「本轮谁回答的」。</p>
     *
     * @param g 图状态
     * @return true=本轮回复可以写入语义缓存
     */
    static boolean cacheableReplySource(MedicalGraphState g) {
        return g.getReplySource() == ReplySource.ASSISTANT_DIALOG
                && g.getCurrentRoundCard() == null;
    }

    /**
     * 缓存命中时把答案按小块推给 SSE 通道，保持前端打字机渲染节奏（非流式请求 onToken 为 null 直接跳过）。
     *
     * @param onToken 流式回调（可为 null）
     * @param answer  缓存答案全文
     */
    private void pushCachedTokens(Consumer<String> onToken, String answer) {
        if (onToken == null || answer == null || answer.isEmpty()) {
            return;
        }
        int chunk = 8;
        for (int i = 0; i < answer.length(); i += chunk) {
            onToken.accept(answer.substring(i, Math.min(i + chunk, answer.length())));
        }
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

    /**
     * 将患者端会话（ai_ 智能预问诊 / cs_ 小林客服）每轮对话同步写入 sm_pre_consult。
     *
     * <p>背景：AiAgentService 主存储是 Redis（AiStateStore），而医生端 AI 分析回溯预问诊摘要
     * 依赖 MySQL sm_pre_consult 表。此处做双写：每轮患者消息 + AI 回复各 insert 一条，
     * 使 DoctorAssistAnalyzeService.buildPreConsultSummary 的精确匹配（按 sessionId
     * 过滤 + 收集 PATIENT 发言）能命中 ai_/cs_ 会话。</p>
     *
     * <p>写入范围：患者角色 + 非医生助手（非 ds_ 前缀）即写。
     * 医生端 ds_ 会话是内部数字医生助手，不进预问诊表。</p>
     *
     * <p>本方法是 sm_pre_consult 表的<b>唯一写入方</b>。旧规则引擎链路
     * PreConsultServiceImpl.sendMessage() 已废弃不再调用（患者端全链路已迁移到 AiAgentService）。</p>
     *
     * <p>双写异常仅 warn，不中断主流程（主存储 Redis 不受影响）。</p>
     *
     * @param userId    登录用户 ID
     * @param role      登录角色
     * @param sid       会话 ID
     * @param content   本轮用户输入（可能为空）
     * @param imageUrls 本轮图片地址（可空）
     * @param g         当前会话状态（含 AI 回复）
     * @param isImage   本轮用户输入是否为图片
     */
    private void mirrorToPreConsult(Long userId, Role role, String sid, String content,
                                     List<String> imageUrls, MedicalGraphState g, boolean isImage) {
        try {
            // 仅对患者角色双写（cs_ 小林客服 / ai_ 智能预问诊页面）；ds_ 医生助手跳过
            if (role != Role.PATIENT || sid == null || sid.startsWith("ds_")) {
                return;
            }
            Long patientId = g.getPatientId() != null ? g.getPatientId() : userId;
            LocalDateTime now = LocalDateTime.now();
            // 1) 本轮用户消息（PATIENT）
            PreConsult userRow = new PreConsult();
            userRow.setPatientId(patientId);
            userRow.setSessionId(sid);
            userRow.setSender("PATIENT");
            userRow.setMessageType(isImage ? "IMAGE" : "TEXT");
            userRow.setContent(content == null ? "" : content);
            userRow.setImageUrl(isImage && imageUrls != null && !imageUrls.isEmpty() ? imageUrls.get(0) : null);
            userRow.setCreatedAt(now);
            preConsultMapper.insert(userRow);
            // 2) 本轮 AI 回复（AI）—— 仅 reply 非空时写入
            String reply = g.getReply();
            if (reply != null && !reply.isBlank()) {
                PreConsult aiRow = new PreConsult();
                aiRow.setPatientId(patientId);
                aiRow.setSessionId(sid);
                aiRow.setSender("AI");
                aiRow.setMessageType("TEXT");
                aiRow.setContent(reply);
                aiRow.setCreatedAt(now);
                preConsultMapper.insert(aiRow);
            }
        } catch (Exception e) {
            log.warn("预问诊双写 MySQL 失败（sessionId={}，已走 Redis 兜底，不影响主流程）：{}", sid, e.getMessage());
        }
    }
}

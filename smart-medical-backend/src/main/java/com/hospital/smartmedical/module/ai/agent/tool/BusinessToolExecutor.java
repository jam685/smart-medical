package com.hospital.smartmedical.module.ai.agent.tool;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.module.ai.agent.enums.IntentKeywords;
import com.hospital.smartmedical.module.ai.agent.rag.DoctorRagService;
import com.hospital.smartmedical.module.ai.agent.state.ChatMessage;
import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.service.AppointmentService;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.doctor.service.DoctorService;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.prescription.service.PrescriptionService;
import com.hospital.smartmedical.module.report.service.ReportService;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.service.ScheduleService;
import com.hospital.smartmedical.module.visit.service.VisitService;
import com.hospital.smartmedical.module.visit.vo.VisitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务工具执行器（businessAgentNode 内部角色白名单调用）。
 *
 * <p>只做白名单内的业务查询/操作，不做任何医疗诊断推理。
 * PATIENT 白名单：挂号、查询就诊进度、查询处方报告；
 * DOCTOR 白名单：查询当前接诊患者基础信息（叫号引导）。</p>
 *
 * <p>v2 结构化返回：execute 返回 {intent, reply, success, action, appointmentId, data, error}；
 * 挂号前置校验链（时间/患者/医生可挂/重复挂号）；RAG 推荐批量查询号源去 N+1；异常分级。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessToolExecutor {

    private final AppointmentService appointmentService;
    private final VisitService visitService;
    private final PrescriptionService prescriptionService;
    private final ReportService reportService;
    private final DoctorService doctorService;
    private final DoctorMapper doctorMapper;
    /** 患者 Mapper（挂号前置校验：核实患者身份有效性，不信任图状态身份） */
    private final PatientMapper patientMapper;
    /** 医生科室知识库 RAG 推荐服务（患者未指定科室/医生时按症状推荐医生） */
    private final DoctorRagService doctorRagService;
    /** 号源服务：把"明天上午9点"这类模糊时间解析成真正可约的号源 */
    private final ScheduleService scheduleService;

    /** 时间格式化（业务展示用） */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 时段起止时间格式化（08:30） */
    private static final DateTimeFormatter HM_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** 就诊进度卡片单次最多展示条数（未指定科室时的"最近 N 条"上限，筛选条件由意图实体驱动） */
    private static final int PROGRESS_MAX = 3;

    /** 报告/处方卡片单次最多展示条数 */
    private static final int REPORT_MAX = 3;

    /** 医生端就诊进度卡片单次最多展示条数 */
    private static final int DOCTOR_PROGRESS_MAX = 5;

    /**
     * 执行业务工具（按角色 + 意图白名单分派），返回结构化结果。
     *
     * @param g      图状态（含用户角色、身份 ID、最新消息）
     * @param intent 意图（由 businessAgentNode LLM 识别或关键词兜底得出）
     * @return 结构化结果 Map：intent/reply/success/action/appointmentId/data/error
     */
    public Map<String, Object> execute(MedicalGraphState g, String intent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", intent);
        result.put("success", false);
        result.put("action", "NONE");
        result.put("appointmentId", null);
        
        // 能力守卫（最后一道防线）：进度/报告/处方查询仅在 AI 客服会话开放，预问诊会话直接引导至数字医生小林
        if (g.getRole() != com.hospital.smartmedical.module.ai.agent.enums.UserRole.DOCTOR
                && !g.isAssistantSession()
                && ("PROGRESS".equals(intent) || "PRESCRIPTION_REPORT".equals(intent))) {
            String tip = "就诊进度、检查报告与处方查询，请点击页面右下角「数字医生小林」进行查询。我也可以帮您预约挂号。";
            result.put("reply", tip);
            result.put("data", tip);
            result.put("card", null);
            return result;
        }
        try {
            String reply;
            if (g.getRole() == com.hospital.smartmedical.module.ai.agent.enums.UserRole.DOCTOR) {
                // 医生侧业务：结构化结果透传（进度查询需返回卡片），action 由各工具自行指定
                Map<String, Object> doctorResult = doctorIntent(g, intent);
                reply = doctorResult.getOrDefault("reply", "").toString();
                result.putAll(doctorResult);
            } else {
                Map<String, Object> patientResult = patientIntent(g, intent);
                reply = patientResult.getOrDefault("reply", "").toString();
                // 结构化字段透传（action/appointmentId/pendingDoctor 等），供下一轮与审计使用
                result.putAll(patientResult);
            }
            result.put("reply", reply);
            result.put("data", reply);
            result.put("success", true);
        } catch (BizException e) {
            // 业务异常：结构化 code + 引导话术（安抚语气，不暴露内部细节）
            log.warn("业务工具执行失败（BizException）：code={}, msg={}", e.getCode(), e.getMessage());
            result.put("success", false);
            result.put("action", "BIZ_ERROR");
            result.put("reply", "很抱歉，" + e.getMessage() + "。如需帮助可回复「人工客服」，或前往对应功能页面办理。");
            result.put("error", Map.of("type", "BIZ", "code", e.getCode(), "detail", e.getMessage()));
        } catch (Exception e) {
            // 系统异常：生成 traceId + 完整堆栈（仅日志留档，不向用户暴露堆栈）
            String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            log.error("业务工具执行失败：traceId={}, msg={}", traceId, e.getMessage(), e);
            result.put("success", false);
            result.put("action", "SYS_ERROR");
            result.put("reply", "系统开小差了，请稍后再试。（错误编号 " + traceId + "）");
            result.put("error", Map.of("type", "SYS", "traceId", traceId, "detail", e.getMessage()));
        }
        return result;
    }

    // ===================== 意图识别 =====================

    /**
     * 关键词兜底意图识别入口（businessAgentNode 在 LLM 识别失败/UNKNOWN 时调用）。
     *
     * @param g 图状态（含角色与最新消息）
     * @return 意图枚举值（APPOINT/PROGRESS/PRESCRIPTION_REPORT/DOCTOR_PATIENTS/UNKNOWN）
     */
    public String detectIntent(MedicalGraphState g) {
        String text = g.getLastUserText() == null ? "" : g.getLastUserText();
        return detectIntent(g, text);
    }

    /**
     * 轻量意图识别（关键词规则，代码硬编码路由，规避大模型意图幻觉）。
     * 挂号确认环节：患者只回复时间（如"明天上午9点"）时结合最近 AI 引导文案判定为 APPOINT。
     */
    private String detectIntent(MedicalGraphState g, String text) {
        boolean doctor = g.getRole() == com.hospital.smartmedical.module.ai.agent.enums.UserRole.DOCTOR;
        if (doctor) {
            // 就诊进度查询优先（"查患者就诊进度"同时命中两类关键词时按进度处理）
            if (containsAny(text, IntentKeywords.DOCTOR_PROGRESS)) {
                return "DOCTOR_PROGRESS";
            }
            if (containsAny(text, IntentKeywords.DOCTOR_PATIENTS)) {
                return "DOCTOR_PATIENTS";
            }
            return "UNKNOWN";
        }
        // 科室推荐类问题不算挂号意图，交由预问诊 LLM 推荐科室
        if (containsAny(text, IntentKeywords.APPOINT_EXCLUDED)) {
            return "UNKNOWN";
        }
        // 挂号意图（APPOINT）：负向词闸门前置拦截，彻底堵死"挂念/挂科/挂电话"误匹配
        boolean negative = containsAny(text, IntentKeywords.APPOINT_NEGATIVE);
        if (!negative && containsAny(text, IntentKeywords.APPOINT_POSITIVE)) {
            return "APPOINT";
        }
        // 挂号确认环节：患者只回复时间（如"明天上午9点"）且 AI 上轮在引导确认
        if (containsAny(text, IntentKeywords.APPOINT_TIME_WORDS)
                && lastAiAsksAppointTime(g)) {
            return "APPOINT";
        }
         // 能力隔离：就诊进度、报告/处方查询仅在 AI 客服（数字医生小林）会话开放，预问诊只做挂号引导
        if (!g.isAssistantSession()) {
            return "UNKNOWN";
        }
        if (containsAny(text, IntentKeywords.PROGRESS)) {
            return "PROGRESS";
        }
        if (containsAny(text, IntentKeywords.REPORT_PRESCRIPTION)) {
            return "PRESCRIPTION_REPORT";
        }
        return "UNKNOWN";
    }

    /**
     * 判断最近一条 AI 回复是否在引导患者确认挂号时间（跨轮挂号确认场景）。
     *
     * @param g 图状态（含对话历史）
     * @return true=最近 AI 回复含挂号时间引导文案
     */
    private boolean lastAiAsksAppointTime(MedicalGraphState g) {
        List<ChatMessage> history = g.getChatHistory();
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if ("AI".equals(msg.getSender()) && msg.getContent() != null) {
                return msg.getContent().contains("就诊的时间") || msg.getContent().contains("为您挂号");
            }
        }
        return false;
    }

    // ===================== 患者侧业务 =====================

    /**
     * 患者侧业务分派（白名单：挂号/进度/处方报告），返回结构化结果。
     *
     * @param g      图状态
     * @param intent 意图
     * @return Map：reply 回复文本 / action 动作类型 / appointmentId 挂号单 ID（可空）
     */
    private Map<String, Object> patientIntent(MedicalGraphState g, String intent) {
        Map<String, Object> r = new LinkedHashMap<>();
        switch (intent) {
            case "APPOINT":
                return doAppoint(g);
            case "PROGRESS":
                return doProgress(g);
            case "PRESCRIPTION_REPORT":
                return doPrescriptionReport(g);
            default:
                r.put("action", "HELP");
                r.put("appointmentId", null);
                r.put("reply", "您可以向我查询：挂号（例：挂呼吸科的号）、就诊进度（例：我的就诊进度）、"
                        + "处方报告（例：我的报告）。我是业务助手，不做诊断，诊疗请以医生为准。");
                return r;
        }
    }

    /**
     * 挂号工具（两段式确认：先选医生，再确认时间后创建挂号）。
     * <ul>
     *   <li>1) 患者指定医生姓名：带明确时间直接挂号；无时间则引导回复就诊时间（例：今天下午3点）</li>
     *   <li>2) 患者只回复时间（确认环节）：从最近一条 AI 回复提取医生姓名继续挂号</li>
     *   <li>3) 识别科室：列出可挂号医生供选择</li>
     *   <li>4) 未带科室：自动带出预问诊推荐科室 suggestDept，再列出医生</li>
     *   <li>5) 均无：引导患者提供科室</li>
     * </ul>
     *
     * @param g 图状态
     * @return Map：reply 回复文本 / action=APPOINT / appointmentId 挂号单 ID（创建成功才有值）
     */
    private Map<String, Object> doAppoint(MedicalGraphState g) {
        String text = g.getLastUserText();
        // 0) 上一轮给出了"最近可约"建议：本轮确认则按候选号源下单，拒绝则取消，说别的则放弃建议继续解析
        Map<String, Object> confirmation = handlePendingConfirmation(g, text);
        if (confirmation != null) {
            return confirmation;
        }
        String dept = matchDept(text);
        String doctorName = matchDoctorName(text);
        LocalDateTime appointTime = extractTime(text);
        // 1) 患者指定医生姓名
        if (doctorName != null && dept == null) {
            Doctor doctor = findDoctorByName(doctorName);
            if (doctor == null) {
                return appointReply("没有找到姓名为「" + doctorName + "」的医生，请确认医生姓名后再试。");
            }
            if (appointTime == null) {
                // 待选医生写入结构化结果（pendingDoctor），供下一轮患者只回复时间时直接取用（不依赖润色后文本正则）
                Map<String, Object> r = appointReply("已为您找到【" + doctor.getDepartment() + " " + doctor.getRealName() + "】医生，"
                        + "请回复希望就诊的时间（例：今天下午3点 / 明天上午9点），我为您挂号。");
                r.put("pendingDoctor", doctor.getRealName());
                return r;
            }
            return createAppointmentReply(g, doctor, appointTime);
        }
        // 2) 患者只回复了时间（挂号确认环节）：优先取上一轮结构化结果中的待挂号医生，缺失再回退历史文本提取
        if (appointTime != null) {
            String prevDoctor = pendingDoctorFromState(g);
            if (prevDoctor == null) {
                prevDoctor = extractDoctorFromHistory(g);
            }
            if (prevDoctor != null) {
                Doctor doctor = findDoctorByName(prevDoctor);
                if (doctor != null) {
                    return createAppointmentReply(g, doctor, appointTime);
                }
            }
        }
        // 3) 识别科室 → 列出可挂号医生供选择
        if (dept != null) {
            return appointReply(listDoctorsReply(dept));
        }
        // 4) 未带科室/医生：基于症状信息 RAG 推荐最匹配的可挂号医生
        String ragQuery = buildRagQuery(g);
        if (ragQuery != null && !ragQuery.isBlank()) {
            String ragReply = recommendDoctorsReply(ragQuery);
            if (ragReply != null) {
                return appointReply(ragReply);
            }
        }
        // 5) 未带科室：自动带出预问诊推荐科室，减少一轮对话
        if (g.getSuggestDept() != null && !g.getSuggestDept().isBlank()) {
            return appointReply(listDoctorsReply(g.getSuggestDept()));
        }
        return appointReply("请问您想挂哪个科室的号？（如：挂呼吸科的号）或者直接说医生姓名，我帮您挂号。");
    }

    /**
     * 组装挂号类回复的统一结构化 Map（未创建挂号的引导/提示场景）。
     *
     * @param reply 回复文本
     * @return Map：reply / action=APPOINT / appointmentId=null
     */
    private Map<String, Object> appointReply(String reply) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("action", "APPOINT");
        r.put("appointmentId", null);
        r.put("reply", reply);
        return r;
    }

    /**
     * 组装 RAG 检索查询文本（预问诊症状摘要 + 本轮症状描述 + 预问诊推荐科室）。
     *
     * @param g 图状态
     * @return 检索文本（无可检索内容时返回空串）
     */
    private String buildRagQuery(MedicalGraphState g) {
        StringBuilder query = new StringBuilder();
        if (g.getSymptomSummary() != null && !g.getSymptomSummary().isBlank()) {
            query.append(g.getSymptomSummary()).append(" ");
        }
        String text = g.getLastUserText();
        if (text != null && !text.isBlank()) {
            // 去除"帮我挂号/挂号"等指令词，保留症状描述
            String cleaned = text.replace("帮我挂号", " ").replace("帮我挂个号", " ").replace("挂号", " ").trim();
            if (!cleaned.isBlank()) {
                query.append(cleaned).append(" ");
            }
        }
        if (g.getSuggestDept() != null && !g.getSuggestDept().isBlank()) {
            query.append(g.getSuggestDept());
        }
        return query.toString().trim();
    }

    /**
     * RAG 医生推荐：语义检索 → 批量查询可挂号号源（去 N+1）→ 过滤在线医生 → 生成推荐文案。
     *
     * @param query 检索查询文本
     * @return 推荐文案（无可挂号推荐时返回 null，由调用方走兜底流程）
     */
    private String recommendDoctorsReply(String query) {
        List<DoctorRagService.DoctorMatch> matches = doctorRagService.recommend(query, 8);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        // 收集所有命中医生所在科室，一次性批量查询在线号源（避免逐科室 N+1）
        Set<String> depts = matches.stream()
                .map(m -> m.doctor().getDepartment())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> availableAll = depts.isEmpty() ? List.of()
                : appointmentService.listAvailableDoctorsByDepts(depts);
        log.info("RAG 医生推荐批量查询号源：命中科室数={}, 在线医生数={}（一次查询，无 N+1）", depts.size(), availableAll.size());
        // 在线号源去重键：科室::姓名（同科室同名医生视作同一人）
        Set<String> availableKeys = availableAll.stream()
                .map(a -> a.get("department") + "::" + a.get("realName"))
                .collect(Collectors.toSet());
        // 过滤可挂号（在线）医生，最多推荐 3 位
        List<Doctor> picked = new ArrayList<>();
        for (DoctorRagService.DoctorMatch match : matches) {
            Doctor doctor = match.doctor();
            String key = doctor.getDepartment() + "::" + doctor.getRealName();
            if (availableKeys.contains(key)) {
                picked.add(doctor);
            }
            if (picked.size() >= 3) {
                break;
            }
        }
        if (picked.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("根据您的描述，为您推荐可挂号医生：");
        for (Doctor d : picked) {
            sb.append("\n· ").append(d.getDepartment()).append(" ").append(d.getRealName())
                    .append("（").append(d.getTitle() == null || d.getTitle().isBlank() ? "医生" : d.getTitle())
                    .append("）：").append(shortBio(d.getBio()));
        }
        sb.append("\n回复「挂 医生姓名 的号」即可直接挂号。");
        return sb.toString();
    }

    /**
     * 截取医生擅长领域简介（超过 40 字截断展示）。
     *
     * @param bio 医生简介
     * @return 截断后的简介
     */
    private String shortBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return "暂未维护擅长领域介绍";
        }
        return bio.length() > 40 ? bio.substring(0, 40) + "…" : bio;
    }

    /**
     * 挂号前置校验链：时间有效性 → 患者身份（不信任图状态身份，回库核实）→ 医生可挂（在线）→ 重复挂号。
     *
     * @param g      图状态（取患者 ID）
     * @param doctor 目标医生
     * @param time   就诊时间
     * @return null=校验通过；否则返回引导话术（不落库）
     */
    private String validateAppointment(MedicalGraphState g, Doctor doctor, LocalDateTime time) {
        // 1) 时间校验：只拦"过去的日期"；同一天内已过的时段由号源解析自动顺延到最近可约时段
        if (time == null) {
            return "请告诉我希望就诊的时间（例：明天上午9点），我按号源为您安排。";
        }
        if (time.toLocalDate().isBefore(LocalDate.now())) {
            return "很抱歉，这个日期已经过去了，请告诉我一个未来的就诊时间（例：明天上午9点）。";
        }
        // 2) 患者身份校验：图状态中的 patientId 不可信，回库核实患者档案
        if (g.getPatientId() == null) {
            return "您还未登录或身份信息缺失，请先登录后再挂号。";
        }
        Patient patient = patientMapper.selectById(g.getPatientId());
        if (patient == null) {
            return "未找到您的患者档案，请重新登录后再挂号。";
        }
        if ("DISABLED".equalsIgnoreCase(patient.getStatus())) {
            return "您的账号已被禁用，暂时无法挂号，请联系管理员处理。";
        }
        // 3) 医生可挂校验：当前必须在线上（ONLINE）
        List<Map<String, Object>> available = appointmentService.listAvailableDoctors(doctor.getDepartment(), doctor.getRealName());
        boolean online = available.stream().anyMatch(a -> doctor.getRealName().equals(a.get("realName")));
        if (!online) {
            return "很抱歉，「" + doctor.getRealName() + "」医生当前不在线或不可挂号，请选择其他医生或稍后再试。";
        }
        // 4) 重复挂号校验：同一患者同一医生同一天不可重复挂号
        if (appointmentService.hasAppointmentOnDate(g.getPatientId(), doctor.getId(), time.toLocalDate())) {
            return "您当天已挂过【" + doctor.getDepartment() + " " + doctor.getRealName() + "】医生的号，请勿重复挂号。"
                    + "如需改约，请先取消原挂号。";
        }
        return null;
    }

    /**
     * 创建挂号并生成成功回复文案（前置校验链通过才落库，失败返回引导话术不落库）。
     *
     * @param g      图状态（取患者 ID）
     * @param doctor 目标医生
     * @param time   就诊时间
     * @return Map：reply 回复文本 / action=APPOINT / appointmentId 创建成功后的挂号单 ID
     */
    private Map<String, Object> createAppointmentReply(MedicalGraphState g, Doctor doctor, LocalDateTime time) {
        String invalid = validateAppointment(g, doctor, time);
        if (invalid != null) {
            return appointReply(invalid);
        }
        // 把"明天上午9点"解析成真正可约的号源：同日优先、不早于期望时间；当日无号则顺延最近日期
        ScheduleSlot slot = scheduleService.resolveForAi(doctor.getId(), time);
        if (slot == null) {
            return appointReply("很抱歉，「" + doctor.getRealName() + "」医生近 7 天暂无可预约号源，"
                    + "建议您选择其他医生，或稍后再试。");
        }
        LocalDateTime slotStart = LocalDateTime.of(slot.getWorkDate(), slot.getStartTime());
        // "改期"判定：日期不同、或时段晚于期望时间（同一号源起点不算改期）都视为改期
        boolean moved = time == null
                || !slotStart.toLocalDate().equals(time.toLocalDate())
                || slotStart.toLocalTime().isAfter(time.toLocalTime());
        if (moved) {
            // 关键：期望时段已满时不再"先斩后奏"——只给建议、把候选号源写进状态，等患者确认再下单
            g.setPendingSlotId(slot.getId());
            Map<String, Object> r = appointReply("您期望的"
                    + (time == null ? "时间暂未明确" : TIME_FMT.format(time))
                    + "暂无可约号源。最近可约：【" + doctor.getDepartment() + " " + doctor.getRealName()
                    + "】" + TIME_FMT.format(slotStart) + "-" + slot.getEndTime().format(HM_FMT)
                    + "，可以吗？回复「可以」我就为您挂号。");
            r.put("pendingDoctor", doctor.getRealName());
            r.put("pendingSlotId", slot.getId());
            return r;
        }
        return bookSlot(g, doctor, slot, false);
    }

    /**
     * 真正落库挂号并组装成功回复（唯一的下单入口，直接挂号轮与确认轮共用）。
     *
     * @param g         图状态
     * @param doctor    医生
     * @param slot      已确定的号源
     * @param confirmed 是否为"患者确认建议"后下单
     * @return 挂号成功结构化结果（含卡片）
     */
    private Map<String, Object> bookSlot(MedicalGraphState g, Doctor doctor, ScheduleSlot slot, boolean confirmed) {
        LocalDateTime slotStart = LocalDateTime.of(slot.getWorkDate(), slot.getStartTime());
        Appointment app;
        try {
            app = appointmentService.createAppointmentBySlot(g.getPatientId(), Role.PATIENT, slot.getId());
        } catch (BizException e) {
            // 号源被抢空/重复挂号等业务拦截：转成引导话术，不落库
            return appointReply("很抱歉，" + e.getMessage() + "。您可以换个时段，或告诉我「换个医生」，我再帮您找。");
        }
        g.setPendingSlotId(null);
        String slotText = TIME_FMT.format(slotStart) + "-" + slot.getEndTime().format(HM_FMT);
        String head = confirmed ? "好的，已按您确认的时间为您挂好" : "已为您成功挂好";
        String reply = head + "【" + doctor.getDepartment() + " " + doctor.getRealName()
                + "】医生的号，就诊时间 " + slotText
                + "，就诊序号 " + app.getQueueNo() + " 号，请提前 15 分钟到院签到。"
                + "（挂号单号 " + app.getId() + "，可在「我的挂号」中查看与取消）";
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("action", "APPOINT");
        r.put("appointmentId", app.getId());
        r.put("slotId", slot.getId());
        r.put("queueNo", app.getQueueNo());
        r.put("reply", reply);
        // 挂号信息小卡片结构化数据（前端按字段渲染，不依赖润色后的文案，避免事实被改写）
        r.put("card", buildAppointmentCard(doctor, app));
        return r;
    }

    /**
     * 处理"上一轮给出的最近可约建议"。
     *
     * <p>三种结果：同意 → 按候选号源下单；拒绝 → 取消建议；说别的内容 → 清空建议，继续正常解析。</p>
     *
     * @param g    图状态（含 pendingSlotId）
     * @param text 用户本轮消息
     * @return 处理结果；未触发确认逻辑返回 null
     */
    private Map<String, Object> handlePendingConfirmation(MedicalGraphState g, String text) {
        Long pendingSlotId = g.getPendingSlotId();
        // 双重保险：必须有待确认号源，且最近一条 AI 回复确实在征求确认（防止陈旧建议被"可以"误触发）
        if (pendingSlotId == null || !lastAiAskedConfirm(g)) {
            return null;
        }
        if (containsAny(text, IntentKeywords.APPOINT_DENY)) {
            g.setPendingSlotId(null);
            return appointReply("好的，已取消这次安排。您可以告诉我其他时间或科室，我再帮您找。");
        }
        if (!containsAny(text, IntentKeywords.APPOINT_CONFIRM)) {
            // 患者说了新时间/新科室：放弃旧建议，继续走正常解析
            g.setPendingSlotId(null);
            return null;
        }
        ScheduleSlot slot = scheduleService.getById(pendingSlotId);
        if (slot == null || !"OPEN".equals(slot.getStatus())) {
            g.setPendingSlotId(null);
            return appointReply("抱歉，刚才建议的时段已不可预约，请重新告诉我时间或科室。");
        }
        Doctor doctor = doctorMapper.selectById(slot.getDoctorId());
        if (doctor == null) {
            g.setPendingSlotId(null);
            return appointReply("抱歉，医生信息异常，请重新选择。");
        }
        return bookSlot(g, doctor, slot, true);
    }

    /**
     * 最近一条 AI 回复是否在征求挂号确认。
     */
    private boolean lastAiAskedConfirm(MedicalGraphState g) {
        List<ChatMessage> history = g.getChatHistory();
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if ("AI".equals(msg.getSender()) && msg.getContent() != null) {
                return msg.getContent().contains("可以吗") || msg.getContent().contains("回复「可以」");
            }
        }
        return false;
    }

    /**
     * 组装挂号信息卡片的展示数据（医生姓名/科室/挂号单号/预约时间，全部取自数据库真实值）。
     *
     * @param doctor 已挂号医生
     * @param app    挂号记录（单号取记录 ID，时间/序号取号源扣减后的真实值）
     * @return 卡片数据 Map（type=APPOINTMENT，前端据此选择卡片样式）
     */
    private Map<String, Object> buildAppointmentCard(Doctor doctor, Appointment app) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "APPOINTMENT");
        card.put("doctorName", doctor.getRealName());
        card.put("department", doctor.getDepartment());
        card.put("appointmentNo", app.getId());
        card.put("appointmentTime", app.getAppointmentTime() == null ? null : TIME_FMT.format(app.getAppointmentTime()));
        card.put("queueNo", app.getQueueNo());
        card.put("slotId", app.getScheduleId());
        return card;
    }

    /**
     * 列出指定科室可挂号医生供患者选择。
     *
     * @param dept 科室名
     * @return 医生列表文案或暂无提示
     */
    private String listDoctorsReply(String dept) {
        List<Map<String, Object>> doctors = appointmentService.listAvailableDoctors(dept, null);
        if (doctors == null || doctors.isEmpty()) {
            return "【" + dept + "】暂无在线可挂号医生，建议换个科室或稍后再试。";
        }
        StringBuilder sb = new StringBuilder("为您找到【" + dept + "】可挂号医生：");
        for (Map<String, Object> d : doctors) {
            sb.append("\n· ").append(d.get("realName")).append("（").append(d.get("title")).append("）");
        }
        sb.append("\n回复「挂 医生姓名 的号」即可直接挂号。");
        return sb.toString();
    }

    /**
     * 从文本解析就诊时间（日期词 + 时段 + 中文/阿拉伯数字 + 半/一刻/三刻/冒号格式）。
     * 无法解析出明确时间时返回 null（用于引导患者补充时间）。
     *
     * @param text 用户消息
     * @return 解析出的就诊时间，或 null
     */
    private LocalDateTime extractTime(String text) {
        return TimeTextParser.parse(text);
    }

    /**
     * 从图状态上一轮结构化业务结果中读取待挂号医生（挂号确认环节用）。
     * 相比解析润色后的 AI 回复文本更可靠（润色可能改写格式导致正则失配）。
     *
     * @param g 图状态（含 lastBusinessResult）
     * @return 待挂号医生姓名，无则返回 null
     */
    private String pendingDoctorFromState(MedicalGraphState g) {
        Map<String, Object> last = g.getLastBusinessResult();
        if (last == null) {
            return null;
        }
        Object pending = last.get("pendingDoctor");
        return pending == null ? null : pending.toString();
    }

    /**
     * 从最近一条 AI 回复中提取医生称呼（如「张伟医生」）。
     * 用于患者只回复就诊时间（挂号确认环节）时继续完成挂号。
     *
     * @param g 图状态（含对话历史）
     * @return 医生称呼（含「医生」后缀），未找到返回 null
     */
    private String extractDoctorFromHistory(MedicalGraphState g) {
        List<ChatMessage> history = g.getChatHistory();
        if (history == null) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (!"AI".equals(msg.getSender()) || msg.getContent() == null) {
                continue;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("【[^】]*?\\s*(\\S+?医生)】")
                    .matcher(msg.getContent());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /**
     * 就诊进度查询工具：按意图识别抽取的科室/医生实体筛选就诊记录，生成进度文案与结构化卡片。
     *
     * <p>未指定科室/医生时返回最近 3 条（PROGRESS_MAX 为展示上限）；
     * 科室/医生筛选条件完全由 LLM 意图实体（intentDept/intentDoctorName）驱动，
     * LLM 未抽取到时用关键词 matchDept 兜底，不在代码里写死任何科室。</p>
     *
     * @param g 图状态（含患者 ID、本轮意图实体）
     * @return Map：reply 文案 / action=PROGRESS_QUERY / card 进度卡片（无记录时为 null）
     */
    private Map<String, Object> doProgress(MedicalGraphState g) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("action", "PROGRESS_QUERY");
        r.put("appointmentId", null);
        // 拉取最近 20 条（内存筛选后仅展示最近 3 条，避免全量加载）
        var page = visitService.listVisits(g.getPatientId(), Role.PATIENT, 1, 20, null);
        List<VisitVO> all = page == null || page.getList() == null
                ? new ArrayList<>() : new ArrayList<>(page.getList());
        // 科室：优先用 LLM 实体，缺失时用关键词兜底
        String dept = g.getIntentDept();
        if (dept == null || dept.isBlank()) {
            dept = matchDept(g.getLastUserText() == null ? "" : g.getLastUserText());
        }
        String doctorName = g.getIntentDoctorName();
        // 按实体过滤
        List<VisitVO> matched = new ArrayList<>();
        for (VisitVO v : all) {
            if (!deptMatched(dept, v.getDepartment())) {
                continue;
            }
            if (!doctorMatched(doctorName, v.getDoctorName())) {
                continue;
            }
            matched.add(v);
        }
        if (matched.isEmpty()) {
            r.put("card", null);
            r.put("reply", (dept == null || dept.isBlank())
                    ? "您当前没有就诊记录。需要我帮您挂号吗？（例：挂呼吸科的号）"
                    : "没有查询到您在【" + dept + "】的就诊记录。如需要可先挂号（例：挂呼吸科的号）。");
            return r;
        }
        // 按预约时间倒序（无预约时间时用创建时间），保证"最近"优先
        matched.sort((a, b) -> {
            String ta = a.getAppointmentTime() == null ? a.getCreatedAt() : a.getAppointmentTime();
            String tb = b.getAppointmentTime() == null ? b.getCreatedAt() : b.getAppointmentTime();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        List<VisitVO> picked = matched.subList(0, Math.min(PROGRESS_MAX, matched.size()));
        StringBuilder sb = new StringBuilder();
        sb.append(dept == null || dept.isBlank()
                ? "您最近的" + picked.size() + "条就诊记录："
                : "您在【" + dept + "】的就诊记录：");
        List<Map<String, Object>> cardVisits = new ArrayList<>();
        for (VisitVO v : picked) {
            sb.append("\n· ").append(nvl(v.getDepartment())).append("｜").append(nvl(v.getDoctorName()))
                    .append(v.getDoctorTitle() == null || v.getDoctorTitle().isBlank()
                            ? "" : "（" + v.getDoctorTitle() + "）")
                    .append("｜").append(nvl(v.getStatusDesc()))
                    .append("\n  预约时间：").append(nvl(v.getAppointmentTime()))
                    .append(" · 单号：").append(nvl(v.getOrderNo()))
                    .append("\n  当前节点：").append(nvl(v.getCurrentNode()))
                    .append(v.getNextNode() == null ? "" : " → 下一步：" + v.getNextNode());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", v.getId());
            item.put("department", v.getDepartment());
            item.put("doctorName", v.getDoctorName());
            item.put("doctorTitle", v.getDoctorTitle());
            item.put("status", v.getStatus());
            item.put("statusDesc", v.getStatusDesc());
            item.put("appointmentTime", v.getAppointmentTime());
            item.put("orderNo", v.getOrderNo());
            item.put("currentNode", v.getCurrentNode());
            item.put("nextNode", v.getNextNode());
            cardVisits.add(item);
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "PROGRESS");
        card.put("visits", cardVisits);
        r.put("card", card);
        r.put("reply", sb.toString());
        return r;
    }

    /**
     * 科室匹配：忽略"科"后缀差异（如"呼吸科"与"呼吸"视为同一科室），任一为空视为不限。
     *
     * @param intentDept 意图抽取的科室
     * @param visitDept  就诊记录科室
     * @return true=匹配
     */
    private boolean deptMatched(String intentDept, String visitDept) {
        if (intentDept == null || intentDept.isBlank()) {
            return true;
        }
        if (visitDept == null || visitDept.isBlank()) {
            return false;
        }
        String a = intentDept.replace("科", "").trim();
        String b = visitDept.replace("科", "").trim();
        return !a.isEmpty() && (b.contains(a) || a.contains(b));
    }

    /**
     * 医生匹配：忽略"医生/大夫/主任"等称呼后缀，任一为空视为不限。
     *
     * @param intentDoctor 意图抽取的医生姓名
     * @param visitDoctor  就诊记录医生姓名
     * @return true=匹配
     */
    private boolean doctorMatched(String intentDoctor, String visitDoctor) {
        if (intentDoctor == null || intentDoctor.isBlank()) {
            return true;
        }
        if (visitDoctor == null || visitDoctor.isBlank()) {
            return false;
        }
        String a = intentDoctor.replaceAll("(医生|大夫|主任)$", "").trim();
        String b = visitDoctor.replaceAll("(医生|大夫|主任)$", "").trim();
        return !a.isEmpty() && (b.contains(a) || a.contains(b));
    }

    /**
     * 空值安全转字符串（null 返回空串，避免文案出现 "null"）。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String nvl(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 处方报告查询工具：汇总本人最近处方与体检报告，生成文案与结构化卡片。
     *
     * <p>卡片包含报告类型/状态/出具时间/内容摘要/图片地址，供患者直接查看报告内容
     * 或跳转报告详情；只展示最近 REPORT_MAX 条，完整列表在「我的报告」页面。</p>
     *
     * @param g 图状态（含患者 ID）
     * @return Map：reply 文案 / action=REPORT_QUERY / card 报告卡片（无记录时为 null）
     */
    private Map<String, Object> doPrescriptionReport(MedicalGraphState g) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("action", "REPORT_QUERY");
        r.put("appointmentId", null);
        // 处方与报告均按患者本人查询（服务层已做数据隔离）
        List<com.hospital.smartmedical.module.prescription.vo.PrescriptionVO> prescriptions =
                prescriptionService.listByPatient(g.getPatientId());
        var page = reportService.listReports(g.getPatientId(), Role.PATIENT, 1, REPORT_MAX);
        List<com.hospital.smartmedical.module.report.entity.Report> reports = new ArrayList<>();
        if (page != null && page.getList() != null) {
            reports.addAll(page.getList());
        }
        int rxCount = prescriptions == null ? 0 : prescriptions.size();
        int repCount = reports.size();
        if (rxCount == 0 && repCount == 0) {
            r.put("card", null);
            r.put("reply", "您目前还没有处方或报告记录。就诊后医生开具的处方、上传的检查检验报告会显示在这里。");
            return r;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("您共有 ").append(repCount).append(" 条报告记录、").append(rxCount).append(" 条处方记录。");
        List<Map<String, Object>> reportItems = new ArrayList<>();
        for (var rep : reports) {
            String type = rep.getReportType() == null || rep.getReportType().isBlank()
                    ? "检查报告" : rep.getReportType();
            String statusDesc = reportStatusDesc(rep.getStatus());
            sb.append("\n· ").append(type).append("｜").append(statusDesc)
                    .append("\n  出具时间：").append(rep.getCreatedAt() == null ? "—" : rep.getCreatedAt().format(TIME_FMT));
            if (rep.getImageMeta() != null && !rep.getImageMeta().isBlank()) {
                sb.append("\n  内容摘要：").append(rep.getImageMeta());
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rep.getId());
            item.put("reportType", type);
            item.put("status", rep.getStatus());
            item.put("statusDesc", statusDesc);
            item.put("imageUrl", rep.getImageUrl());
            item.put("imageMeta", rep.getImageMeta());
            item.put("createdAt", rep.getCreatedAt() == null ? null : rep.getCreatedAt().format(TIME_FMT));
            reportItems.add(item);
        }
        List<Map<String, Object>> rxItems = new ArrayList<>();
        int i = 0;
        for (var p : prescriptions == null ? List.<com.hospital.smartmedical.module.prescription.vo.PrescriptionVO>of() : prescriptions) {
            if (i++ >= REPORT_MAX) {
                break;
            }
            sb.append("\n· 处方：").append(p.getDiagnosis() == null || p.getDiagnosis().isBlank()
                            ? "（诊断待补充）" : p.getDiagnosis())
                    .append("｜开方医生：").append(p.getDoctorName() == null ? "未知" : p.getDoctorName())
                    .append("\n  开方时间：").append(p.getCreatedAt() == null ? "—" : p.getCreatedAt().format(TIME_FMT));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("visitId", p.getVisitId());
            item.put("prescriptionNo", p.getPrescriptionNo());
            item.put("diagnosis", p.getDiagnosis());
            item.put("doctorName", p.getDoctorName());
            item.put("department", p.getDepartment());
            item.put("advice", p.getAdvice());
            item.put("drugNames", p.getItems() == null ? null
                    : p.getItems().stream().map(com.hospital.smartmedical.module.prescription.vo.PrescriptionVO.Item::getDrugName)
                    .filter(Objects::nonNull).collect(Collectors.toList()));
            item.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().format(TIME_FMT));
            rxItems.add(item);
        }
        sb.append("\n（点击卡片可查看报告内容或处方详情）");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "REPORT");
        card.put("reports", reportItems);
        card.put("prescriptions", rxItems);
        r.put("card", card);
        r.put("reply", sb.toString());
        return r;
    }

    /**
     * 报告状态 → 中文描述（与患者端「我的报告」页面保持一致）。
     *
     * @param status 报告状态（UPLOADED/ANALYZED/SIGNED）
     * @return 中文描述
     */
    private String reportStatusDesc(String status) {
        if ("UPLOADED".equals(status)) {
            return "已上传";
        }
        if ("ANALYZED".equals(status)) {
            return "已解读";
        }
        if ("SIGNED".equals(status)) {
            return "已签名";
        }
        return status == null || status.isBlank() ? "未知" : status;
    }

    // ===================== 医生侧业务 =====================

    /**
     * 医生侧业务分派（白名单：当前接诊患者 / 指定患者时间段的就诊进度）。
     *
     * @param g      图状态
     * @param intent 意图
     * @return 结构化结果 Map：reply 回复文本 / action 动作类型 / card 卡片（进度查询才有）
     */
    private Map<String, Object> doctorIntent(MedicalGraphState g, String intent) {
        if ("DOCTOR_PROGRESS".equals(intent)) {
            return doDoctorProgress(g);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("action", "QUERY");
        r.put("appointmentId", null);
        if ("DOCTOR_PATIENTS".equals(intent)) {
            List<Map<String, Object>> patients = doctorService.getMyPatients(g.getDoctorId());
            if (patients == null || patients.isEmpty()) {
                r.put("reply", "您当前没有待接诊的患者。");
                return r;
            }
            StringBuilder sb = new StringBuilder("您当前共有 " + patients.size() + " 位接诊患者：");
            for (Map<String, Object> p : patients) {
                sb.append("\n· ").append(p.get("realName")).append("｜").append(p.get("phone"));
            }
            sb.append("\n叫号请到「接诊管理」页对 WAITING 状态的挂号点击【叫号】。");
            r.put("reply", sb.toString());
            return r;
        }
        r.put("reply", "医生您好，您可以查询：指定患者某个时间段的就诊进度（例：帮我查一下张伟今天上午的就诊进度）、"
                + "或当前接诊患者（例：我的接诊患者）。叫号请到「接诊管理」页面操作。");
        return r;
    }

    /**
     * 医生端就诊进度查询工具：按本轮意图抽取的「患者姓名 + 时间范围」筛选本人接诊记录，
     * 生成进度文案与结构化卡片（卡片只展示查询的时间范围与患者进度列表）。
     *
     * <p>数据源为 listVisits(doctorId, DOCTOR, ...)，天然只包含当前医生本人接诊的患者，不存在越权；
     * 患者姓名由 LLM 抽取，缺失时用"消息中出现的患者姓名"兜底；时间范围由 timeDesc 解析，
     * 解析失败则不过滤时间并在文案中说明。展示上限 DOCTOR_PROGRESS_MAX 条。</p>
     *
     * @param g 图状态（含医生 ID、本轮意图实体）
     * @return Map：reply 文案 / action=DOCTOR_PROGRESS_QUERY / card 进度卡片（无记录时为 null）
     */
    private Map<String, Object> doDoctorProgress(MedicalGraphState g) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("action", "DOCTOR_PROGRESS_QUERY");
        r.put("appointmentId", null);
        // 医生本人接诊范围（服务层按角色隔离，最多取 50 条在内存中筛选）
        var page = visitService.listVisits(g.getDoctorId(), Role.DOCTOR, 1, 50, null);
        List<VisitVO> all = page == null || page.getList() == null
                ? new ArrayList<>() : new ArrayList<>(page.getList());

        String patientName = g.getIntentPatientName();
        if (patientName == null || patientName.isBlank()) {
            patientName = matchPatientNameFromText(g.getLastUserText(), all);
        }
        LocalDate[] range = parseTimeRange(g.getIntentTimeDesc(), g.getLastUserText());
        String timeLabel = range != null
                ? range[0] + " 至 " + range[1]
                : (g.getIntentTimeDesc() == null || g.getIntentTimeDesc().isBlank() ? "" : g.getIntentTimeDesc());

        List<VisitVO> matched = new ArrayList<>();
        for (VisitVO v : all) {
            if (patientName != null && !patientName.isBlank() && !doctorMatched(patientName, v.getPatientName())) {
                continue;
            }
            if (range != null) {
                LocalDate d = visitDate(v);
                if (d == null || d.isBefore(range[0]) || d.isAfter(range[1])) {
                    continue;
                }
            }
            matched.add(v);
        }
        String scope = (patientName == null || patientName.isBlank() ? "" : "患者「" + patientName + "」")
                + (timeLabel.isBlank() ? "" : "（" + timeLabel + "）");
        if (matched.isEmpty()) {
            r.put("card", null);
            r.put("reply", (scope.isEmpty() ? "没有查询到您的接诊记录。" : "没有查询到" + scope + "的就诊记录。")
                    + "您可以换个时间范围或患者姓名再试，也可到「接诊管理」页查看全部。");
            return r;
        }
        // 按就诊时间倒序（无预约时间用创建时间），保证"最近"优先
        matched.sort((a, b) -> {
            LocalDate da = visitDate(a);
            LocalDate db = visitDate(b);
            if (da == null && db == null) {
                return 0;
            }
            if (da == null) {
                return 1;
            }
            if (db == null) {
                return -1;
            }
            return db.compareTo(da);
        });
        List<VisitVO> picked = matched.subList(0, Math.min(DOCTOR_PROGRESS_MAX, matched.size()));
        StringBuilder sb = new StringBuilder(scope.isEmpty()
                ? "为您查询到 " + picked.size() + " 条就诊记录："
                : "为您查询到" + scope + "的 " + picked.size() + " 条就诊记录：");
        List<Map<String, Object>> cardVisits = new ArrayList<>();
        for (VisitVO v : picked) {
            sb.append("\n· ").append(nvl(v.getPatientName())).append("｜").append(nvl(v.getDepartment()))
                    .append("｜").append(nvl(v.getStatusDesc()))
                    .append("\n  就诊时间：").append(nvl(v.getAppointmentTime()))
                    .append(" · 单号：").append(nvl(v.getOrderNo()))
                    .append("\n  当前节点：").append(nvl(v.getCurrentNode()))
                    .append(v.getNextNode() == null ? "" : " → 下一步：" + v.getNextNode());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", v.getId());
            item.put("patientId", v.getPatientId());
            item.put("patientName", v.getPatientName());
            item.put("department", v.getDepartment());
            item.put("status", v.getStatus());
            item.put("statusDesc", v.getStatusDesc());
            item.put("appointmentTime", v.getAppointmentTime());
            item.put("orderNo", v.getOrderNo());
            item.put("currentNode", v.getCurrentNode());
            item.put("nextNode", v.getNextNode());
            cardVisits.add(item);
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "DOCTOR_PROGRESS");
        card.put("patientName", patientName == null ? "" : patientName);
        card.put("timeLabel", timeLabel);
        card.put("dateFrom", range == null ? null : range[0].toString());
        card.put("dateTo", range == null ? null : range[1].toString());
        card.put("visits", cardVisits);
        r.put("card", card);
        r.put("reply", sb.toString());
        return r;
    }

    /**
     * 就诊记录的日期（取预约时间，缺失时回退创建时间）。
     *
     * @param v 就诊记录
     * @return 日期；两者都缺失时返回 null
     */
    private LocalDate visitDate(VisitVO v) {
        String t = v.getAppointmentTime() != null && !v.getAppointmentTime().isBlank()
                ? v.getAppointmentTime() : v.getCreatedAt();
        if (t == null || t.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(t.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 患者姓名兜底抽取：从消息文本中匹配当前医生接诊记录里出现过的患者姓名。
     *
     * @param text 用户消息
     * @param all  当前医生的接诊记录（LLM 未抽取患者姓名时用于兜底）
     * @return 命中的患者姓名；未命中返回 null
     */
    private String matchPatientNameFromText(String text, List<VisitVO> all) {
        if (text == null || text.isBlank() || all == null) {
            return null;
        }
        for (VisitVO v : all) {
            String name = v.getPatientName();
            if (name != null && !name.isBlank() && text.contains(name)) {
                return name;
            }
        }
        return null;
    }

    /**
     * 解析时间范围描述（支持今天/昨天/本周/上周/本月/上月/最近N天/日期区间/单个日期）。
     *
     * @param timeDesc LLM 抽取的时间描述（优先）
     * @param text     用户原始消息（timeDesc 为空或解析失败时兜底）
     * @return [起, 止] 日期；无法解析返回 null（不做时间过滤）
     */
    private LocalDate[] parseTimeRange(String timeDesc, String text) {
        LocalDate[] fromDesc = parseTimeExpr(timeDesc);
        LocalDate[] fromText = parseTimeExpr(text);
        // LLM 抽取的时间可能只保留起点（如"10月16号"）：原文含更完整的区间时以原文为准
        if (fromDesc != null && fromDesc[0].equals(fromDesc[1])
                && fromText != null && !fromText[0].equals(fromText[1])) {
            return fromText;
        }
        return fromDesc != null ? fromDesc : fromText;
    }

    /**
     * 时间表达式解析（相对日期 + 绝对日期，含区间与单日）。
     *
     * @param src 时间描述文本
     * @return [起, 止] 日期；无法解析返回 null
     */
    private LocalDate[] parseTimeExpr(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        // 1) 单日相对词
        if (containsAny(src, "今天", "今日", "当天", "本日")) {
            return new LocalDate[]{today, today};
        }
        if (containsAny(src, "昨天", "昨日")) {
            LocalDate d = today.minusDays(1);
            return new LocalDate[]{d, d};
        }
        if (containsAny(src, "前天")) {
            LocalDate d = today.minusDays(2);
            return new LocalDate[]{d, d};
        }
        if (containsAny(src, "明天", "明日")) {
            LocalDate d = today.plusDays(1);
            return new LocalDate[]{d, d};
        }
        if (containsAny(src, "后天")) {
            LocalDate d = today.plusDays(2);
            return new LocalDate[]{d, d};
        }
        // 2) 周范围
        if (containsAny(src, "上周", "上礼拜", "上个礼拜")) {
            LocalDate start = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new LocalDate[]{start, start.plusDays(6)};
        }
        if (containsAny(src, "本周", "这周", "这一周", "这礼拜", "这个礼拜")) {
            LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new LocalDate[]{start, today};
        }
        // 3) 月范围
        if (containsAny(src, "上月", "上个月", "上个 月")) {
            LocalDate first = today.minusMonths(1).withDayOfMonth(1);
            return new LocalDate[]{first, first.with(TemporalAdjusters.lastDayOfMonth())};
        }
        if (containsAny(src, "本月", "这个月", "这月")) {
            return new LocalDate[]{today.withDayOfMonth(1), today};
        }
        // 4) 最近 N 天 / 最近一周 / 最近一月
        java.util.regex.Matcher recent = java.util.regex.Pattern
                .compile("(?:最近|近)\\s*(\\d{1,3})\\s*天").matcher(src);
        if (recent.find()) {
            int n = Math.max(1, Integer.parseInt(recent.group(1)));
            return new LocalDate[]{today.minusDays(n - 1L), today};
        }
        if (containsAny(src, "最近一周", "近一周", "最近7天", "近7天", "最近七天")) {
            return new LocalDate[]{today.minusDays(6), today};
        }
        if (containsAny(src, "最近一月", "近一月", "最近一个月", "近一个月", "最近30天", "近30天")) {
            return new LocalDate[]{today.minusDays(29), today};
        }
        // 5) 绝对日期区间（2026-10-01 到 2026-10-05 / 2026年10月1日到10月5日）
        java.util.regex.Matcher isoRange = java.util.regex.Pattern.compile(
                "(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?:\\s*日)?\\s*(?:到|至|~|—)\\s*(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})")
                .matcher(src);
        if (isoRange.find()) {
            LocalDate from = safeDate(isoRange.group(1), isoRange.group(2), isoRange.group(3));
            LocalDate to = safeDate(isoRange.group(4), isoRange.group(5), isoRange.group(6));
            if (from != null && to != null) {
                return from.isAfter(to) ? new LocalDate[]{to, from} : new LocalDate[]{from, to};
            }
        }
        java.util.regex.Matcher cnRange = java.util.regex.Pattern.compile(
                "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?\\s*(?:到|至|~|—)\\s*(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?")
                .matcher(src);
        if (cnRange.find()) {
            LocalDate from = safeDate(cnRange.group(1) == null ? String.valueOf(today.getYear()) : cnRange.group(1),
                    cnRange.group(2), cnRange.group(3));
            LocalDate to = safeDate(cnRange.group(4) == null ? String.valueOf(today.getYear()) : cnRange.group(4),
                    cnRange.group(5), cnRange.group(6));
            if (from != null && to != null) {
                return from.isAfter(to) ? new LocalDate[]{to, from} : new LocalDate[]{from, to};
            }
        }
        // 6) 单个绝对日期
        java.util.regex.Matcher isoDay = java.util.regex.Pattern
                .compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").matcher(src);
        if (isoDay.find()) {
            LocalDate d = safeDate(isoDay.group(1), isoDay.group(2), isoDay.group(3));
            if (d != null) {
                return new LocalDate[]{d, d};
            }
        }
        java.util.regex.Matcher cnDay = java.util.regex.Pattern
                .compile("(\\d{1,2})月(\\d{1,2})日?").matcher(src);
        if (cnDay.find()) {
            LocalDate d = safeDate(String.valueOf(today.getYear()), cnDay.group(1), cnDay.group(2));
            if (d != null) {
                return new LocalDate[]{d, d};
            }
        }
        // 7) 仅月份（如"10月"）：按整月范围
        java.util.regex.Matcher cnMonth = java.util.regex.Pattern.compile("(\\d{1,2})月份?").matcher(src);
        if (cnMonth.find()) {
            int month = Integer.parseInt(cnMonth.group(1));
            if (month >= 1 && month <= 12) {
                LocalDate first = LocalDate.of(today.getYear(), month, 1);
                return new LocalDate[]{first, first.with(TemporalAdjusters.lastDayOfMonth())};
            }
        }
        return null;
    }

    /**
     * 安全构造日期（非法年月日返回 null）。
     *
     * @param year  年
     * @param month 月
     * @param day   日
     * @return 日期；非法返回 null
     */
    private LocalDate safeDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (Exception e) {
            return null;
        }
    }

    // ===================== 内部工具 =====================

    /**
     * 从文本匹配科室名（常用科室集合）。
     */
    private String matchDept(String text) {
        if (text == null) {
            return null;
        }
        String[] depts = {"呼吸科", "心内科", "内分泌科", "神经内科", "消化内科", "骨科", "眼科", "耳鼻喉科", "儿科", "妇产科", "皮肤科", "泌尿外科", "普外科"};
        for (String d : depts) {
            if (text.contains(d)) {
                return d;
            }
        }
        return null;
    }

    /**
     * 从文本匹配医生姓名（匹配"挂 XX 的号"句式中的姓名，返回查询命中或可回退的称呼）。
     */
    private String matchDoctorName(String text) {
        if (text == null) {
            return null;
        }
        if (text.contains("的号") || text.contains("医生")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("挂(.{2,4})(医生)?的号").matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    /**
     * 按医生姓名查医生：先按完整称呼精确匹配，匹配不到再去掉「医生」等称呼后缀回退。
     */
    private Doctor findDoctorByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Doctor> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Doctor>()
                        .eq(Doctor::getRealName, name).last("LIMIT 1");
        Doctor doctor = doctorMapper.selectOne(wrapper);
        if (doctor != null) {
            return doctor;
        }
        // 去掉「医生/主任」等称呼后缀后再次匹配（兼容 realName 为姓名的医生）
        String stripped = name.replaceAll("(医生|主任|医师|大夫)$", "");
        if (stripped.isBlank() || stripped.equals(name)) {
            return null;
        }
        return doctorMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getRealName, stripped).last("LIMIT 1"));
    }

    /**
     * 是否包含任一关键词。
     */
    private boolean containsAny(String text, String... keys) {
        if (text == null) {
            return false;
        }
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}

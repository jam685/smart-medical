package com.hospital.smartmedical.common.security;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.common.trace.TraceIdFilter;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录态与粗粒度角色拦截器。
 *
 * <p>职责边界：只做「是否登录」与「该路径是否只对某类角色开放」的最小校验。
 * 资源归属（这条挂号/报告/问诊是不是你的）必须由 Controller / Service 层校验，
 * 拦截器无法感知具体资源，也不应试图承担这一职责。</p>
 *
 * <p>角色规则使用「路径前缀 → 允许角色集合」表达。原因是绝大多数业务资源是
 * <b>多角色共用</b>的：患者与医生都会调用 /appointments、/reports、/online-consults，
 * 用「单角色路径清单」必然误伤另一方（曾出现：患者发起在线问诊被 403）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /** 白名单路径（无需认证） */
    private static final List<String> WHITELIST = Arrays.asList(
            "/patients/login", "/patients/logout", "/patients/register",
            "/patients/password-reset", "/patients/password-reset/code",
            "/doctors/login", "/doctors/logout",
            "/admin/login", "/admin/logout"
    );

    /**
     * 角色路径规则：仅收录「明确只属于某类角色」的路径。
     *
     * <p>未收录的路径表示「任意已登录角色可访问」，由业务层做资源级归属校验。
     * 这样既保留了粗粒度边界，又不会把多角色共用的业务接口误判为越权。</p>
     */
    private static final Map<String, Set<Role>> ROLE_RULES = new LinkedHashMap<>();

    static {
        // 管理端专属
        ROLE_RULES.put("/admin", EnumSet.of(Role.ADMIN));
        // 医生端病历（管理员审计只读放行）
        ROLE_RULES.put("/medical-notes", EnumSet.of(Role.DOCTOR, Role.ADMIN));
        // 医生端 AI 辅助（管理员只读放行）
        ROLE_RULES.put("/ai/doctor-assist", EnumSet.of(Role.DOCTOR, Role.ADMIN));
        ROLE_RULES.put("/ai/clinical-analysis", EnumSet.of(Role.DOCTOR, Role.ADMIN));
        ROLE_RULES.put("/ai/examination-suggestions", EnumSet.of(Role.DOCTOR, Role.ADMIN));
        ROLE_RULES.put("/ai/reply-drafts", EnumSet.of(Role.DOCTOR, Role.ADMIN));
        ROLE_RULES.put("/ai/image-analysis", EnumSet.of(Role.DOCTOR, Role.ADMIN));
        // 以下路径为多角色共用，故意不设规则（由 Controller / Service 做资源级校验）：
        //   /appointments     患者挂号 + 医生查看接诊列表
        //   /reports          患者查看本人报告 + 医生查看/上传
        //   /online-consults  患者发起问诊与发消息 + 医生接诊回复
        //   /patients         患者个人资料
        //   /ai/patient-chat  患者端小林 + 医生端小林（source=doctor_assistant）
    }

    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USER_ROLE = "currentUserRole";
    public static final String ATTR_USERNAME = "currentUsername";

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        String path = uri.substring(ctx.length());

        // 预检
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;

        // 白名单
        if (WHITELIST.stream().anyMatch(path::startsWith)) return true;

        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(res, ErrorCode.UNAUTHORIZED);
            return false;
        }
        String token = auth.substring(7);
        Claims claims;
        try { claims = jwtUtil.parse(token); }
        catch (Exception e) {
            writeError(res, ErrorCode.UNAUTHORIZED);
            return false;
        }

        Role role = jwtUtil.role(claims);
        Long userId = jwtUtil.userId(claims);
        String username = jwtUtil.username(claims);

        // ===== 回填 MDC（TraceIdFilter 先注入空串占位，这里验完 JWT 拿到真实值覆盖） =====
        MDC.put(TraceIdFilter.MDC_USER_ID,   userId == null ? "" : String.valueOf(userId));
        MDC.put(TraceIdFilter.MDC_ROLE,      role == null ? "" : role.name());
        MDC.put(TraceIdFilter.MDC_USERNAME,  username == null ? "" : username);

        // 角色规则校验：命中规则才校验；未命中表示任意已登录角色可访问
        Set<Role> allowedRoles = matchAllowedRoles(path);
        if (allowedRoles != null && !allowedRoles.contains(role)) {
            log.warn("角色越权拦截：path={}，role={}，userId={}", path, role, userId);
            writeError(res, ErrorCode.FORBIDDEN);
            return false;
        }

        req.setAttribute(ATTR_USER_ID, userId);
        req.setAttribute(ATTR_USER_ROLE, role);
        req.setAttribute(ATTR_USERNAME, jwtUtil.username(claims));
        return true;
    }

    /**
     * 按「最长前缀」匹配路径对应的允许角色集合。
     *
     * @param path 请求路径（已去除 context-path）
     * @return 允许访问的角色集合；无匹配规则时返回 null（表示任意已登录角色可访问）
     */
    private static Set<Role> matchAllowedRoles(String path) {
        Set<Role> matched = null;
        int matchedLength = -1;
        for (Map.Entry<String, Set<Role>> entry : ROLE_RULES.entrySet()) {
            String prefix = entry.getKey();
            if (prefix.length() > matchedLength && matches(path, prefix)) {
                matched = entry.getValue();
                matchedLength = prefix.length();
            }
        }
        return matched;
    }

    /**
     * 路径匹配：等于前缀本身，或是以「前缀 + /」开头。
     *
     * <p>必须同时判断「等于」：像 /appointments、/reports 这类资源根路径，
     * 只写 startsWith("/appointments/") 会漏掉，导致根路径不受规则约束。</p>
     *
     * @param path   请求路径
     * @param prefix 规则前缀
     * @return true=命中
     */
    private static boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void writeError(HttpServletResponse res, ErrorCode err) throws Exception {
        res.setStatus(200);
        res.setContentType("application/json;charset=UTF-8");
        Result<Void> r = Result.fail(err);
        // 直接写 response.getWriter() 绕过了 ResponseBodyAdvice，这里手动塞 traceId
        r.setTraceId(MDC.get(TraceIdFilter.MDC_TRACE_ID));
        res.getWriter().write(objectMapper.writeValueAsString(r));
    }
}

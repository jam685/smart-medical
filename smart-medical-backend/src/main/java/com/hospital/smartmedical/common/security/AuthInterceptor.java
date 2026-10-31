package com.hospital.smartmedical.common.security;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    // 白名单路径（无需认证）
    private static final List<String> WHITELIST = Arrays.asList(
            "/patients/login", "/patients/logout", "/patients/register",
            "/patients/password-reset", "/patients/password-reset/code",
            "/doctors/login", "/doctors/logout",
            "/admin/login", "/admin/logout"
    );

    // 角色特定路径前缀映射
    private static final List<String> ADMIN_PATHS = Arrays.asList("/admin/");
    private static final List<String> DOCTOR_PATHS = Arrays.asList("/doctors/", "/online-consults/", "/medical-notes/", "/ai/clinical-analysis", "/ai/examination-suggestions", "/ai/reply-drafts", "/ai/image-analysis");
    private static final List<String> PATIENT_PATHS = Arrays.asList("/patients/", "/appointments/", "/reports/", "/ai/patient-chat");

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

        // 权限校验：管理员路径只能 ADMIN 访问
        if (ADMIN_PATHS.stream().anyMatch(path::startsWith)) {
            if (role != Role.ADMIN) { writeError(res, ErrorCode.FORBIDDEN); return false; }
        }

        req.setAttribute(ATTR_USER_ID, userId);
        req.setAttribute(ATTR_USER_ROLE, role);
        req.setAttribute(ATTR_USERNAME, jwtUtil.username(claims));
        return true;
    }

    private void writeError(HttpServletResponse res, ErrorCode err) throws Exception {
        res.setStatus(200);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(Result.fail(err)));
    }
}

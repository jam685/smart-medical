package com.hospital.smartmedical.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.smartmedical.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限矩阵回归测试：锁定「路径 → 角色」规则，防止再次出现「患者访问在线问诊被 403」这类事故。
 *
 * <p>覆盖 2026-11-04 修复的回归点：/online-consults 属于患者与医生共用，
 * 以及「根路径必须精确匹配」（/appointments 不能被 startsWith("/appointments/") 漏掉）。</p>
 */
class AuthInterceptorRoleTest {

    private JwtUtil jwtUtil;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "unit-test-secret-key-0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expireMinutes", 60L);
        interceptor = new AuthInterceptor(jwtUtil, new ObjectMapper());
    }

    @Test
    @DisplayName("患者访问在线问诊（共用资源）应放行——回归点")
    void patientCanAccessOnlineConsult() throws Exception {
        assertTrue(handle("/online-consults/my-doctors", token(Role.PATIENT)));
        assertTrue(handle("/online-consults/online-doctors", token(Role.PATIENT)));
        assertTrue(handle("/online-consults/1/messages", token(Role.PATIENT)));
    }

    @Test
    @DisplayName("医生访问在线问诊（共用资源）应放行")
    void doctorCanAccessOnlineConsult() throws Exception {
        assertTrue(handle("/online-consults/1/confirm-reply", token(Role.DOCTOR)));
    }

    @Test
    @DisplayName("患者访问医生专属接口应拒绝")
    void patientDeniedOnDoctorOnlyPaths() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(handle("/medical-notes", token(Role.PATIENT), res));
        assertTrue(res.getContentAsString().contains("无权限"));

        assertFalse(handle("/medical-notes/1", token(Role.PATIENT)));
        assertFalse(handle("/ai/doctor-assist/draft/1", token(Role.PATIENT)));
        assertFalse(handle("/ai/clinical-analysis", token(Role.PATIENT)));
    }

    @Test
    @DisplayName("医生访问医生专属接口应放行")
    void doctorAllowedOnDoctorOnlyPaths() throws Exception {
        assertTrue(handle("/medical-notes", token(Role.DOCTOR)));
        assertTrue(handle("/ai/doctor-assist/analyze", token(Role.DOCTOR)));
        assertTrue(handle("/ai/clinical-analysis", token(Role.DOCTOR)));
    }

    @Test
    @DisplayName("医生端小林（/ai/patient-chat）对医生放行——之前被误拦")
    void doctorCanUsePatientChatChannel() throws Exception {
        assertTrue(handle("/ai/patient-chat", token(Role.DOCTOR)));
        assertTrue(handle("/ai/patient-chat/sid/history", token(Role.DOCTOR)));
    }

    @Test
    @DisplayName("管理端接口仅管理员可访问")
    void adminOnly() throws Exception {
        assertTrue(handle("/admin/semantic-cache/stats", token(Role.ADMIN)));
        assertFalse(handle("/admin/semantic-cache/stats", token(Role.PATIENT)));
        assertFalse(handle("/admin/semantic-cache/stats", token(Role.DOCTOR)));
    }

    @Test
    @DisplayName("根路径必须精确匹配：/appointments 不能被规则漏掉")
    void rootPathMatching() throws Exception {
        assertTrue(handle("/appointments", token(Role.PATIENT)));
        assertTrue(handle("/appointments/1", token(Role.PATIENT)));
        assertTrue(handle("/reports", token(Role.DOCTOR)));
    }

    @Test
    @DisplayName("未带 token 一律拒绝")
    void noTokenRejected() throws Exception {
        assertFalse(handle("/online-consults/my-doctors", null));
    }

    // ===================== 工具 =====================

    private String token(Role role) {
        return jwtUtil.generate(1L, role, "tester");
    }

    private boolean handle(String path, String token) throws Exception {
        return handle(path, token, new MockHttpServletResponse());
    }

    private boolean handle(String path, String token, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return interceptor.preHandle(request, response, new Object());
    }
}

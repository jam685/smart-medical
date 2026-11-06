package com.hospital.smartmedical.common.security;

import com.hospital.smartmedical.common.enums.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 密钥启动校验测试。
 *
 * <p>回归点：密钥曾硬编码在 application.yml（demo-only），任何人拿到仓库即可伪造任意身份。
 * 现在要求密钥来自环境变量/本地密钥文件，且缺失或过短时启动失败。</p>
 */
class JwtUtilTest {

    @Test
    @DisplayName("密钥为空：启动校验失败并给出可操作提示")
    void blankSecretFailsFast() {
        JwtUtil jwt = jwt("", 60L);
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwt::validateSecret);
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    @DisplayName("密钥过短：启动校验失败（HS256 至少 32 字节）")
    void shortSecretFailsFast() {
        JwtUtil jwt = jwt("too-short-secret", 60L);
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwt::validateSecret);
        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    @DisplayName("合法密钥：可正常签发与解析，角色/用户 ID 不丢")
    void validSecretRoundTrip() {
        String secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        JwtUtil jwt = jwt(secret, 60L);
        jwt.validateSecret();

        String token = jwt.generate(42L, Role.PATIENT, "zhangsan");
        Claims claims = jwt.parse(token);

        assertEquals(42L, jwt.userId(claims));
        assertEquals(Role.PATIENT, jwt.role(claims));
        assertEquals("zhangsan", jwt.username(claims));
    }

    private JwtUtil jwt(String secret, long expireMinutes) {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", secret);
        ReflectionTestUtils.setField(jwt, "expireMinutes", expireMinutes);
        return jwt;
    }
}

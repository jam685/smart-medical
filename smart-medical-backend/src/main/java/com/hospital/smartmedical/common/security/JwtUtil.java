package com.hospital.smartmedical.common.security;

import com.hospital.smartmedical.common.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具。
 *
 * <p>密钥不再硬编码在 application.yml（旧值 {@code smart-medical-local-demo-secret-key...}
 * 任何人拿到仓库即可伪造任意身份）。现在从环境变量 {@code JWT_SECRET} 或本地
 * {@code ai-secret.yml} 读取，并在启动时做长度校验：缺失或弱密钥直接启动失败，
 * 避免"带病上线"。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    /** HS256 最低密钥长度（字节） */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expire-minutes}")
    private long expireMinutes;

    /**
     * 启动校验：密钥必须存在且不少于 32 字节。
     *
     * <p>失败即抛异常终止启动——这是刻意的：一个没有有效 JWT 密钥的服务，
     * 对外提供的任何"登录态"都是假的。</p>
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置：请设置环境变量 JWT_SECRET，或写入本地 ai-secret.yml（至少 32 字节）");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 过短（当前 " + bytes + " 字节，至少 " + MIN_SECRET_BYTES + " 字节）：请更换为随机强密钥");
        }
        log.info("JWT 密钥已加载（长度 {} 字节，有效期 {} 分钟）", bytes, expireMinutes);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, Role role, String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireMinutes * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .claim("username", username)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public Long userId(Claims c) { return Long.valueOf(c.getSubject()); }
    public Role role(Claims c) { return Role.of(c.get("role", String.class)); }
    public String username(Claims c) { return c.get("username", String.class); }
}

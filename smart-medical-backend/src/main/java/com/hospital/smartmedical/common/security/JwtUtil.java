package com.hospital.smartmedical.common.security;

import com.hospital.smartmedical.common.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-minutes}")
    private long expireMinutes;

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

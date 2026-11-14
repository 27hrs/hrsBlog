package com.example.hrsblog.security;

import com.example.hrsblog.config.JwtProperties;
import com.example.hrsblog.domain.AdminUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验。
 *
 * <p>使用 HS256（密钥长度决定算法），签名密钥只来自环境变量；解析时强制校验签名、签发方与有效期，
 * 任何异常都以 {@link JwtException} / {@link IllegalArgumentException} 形式抛出，由调用方统一处理。
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USER_ID = "uid";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build();
    }

    /** 签发访问令牌。 */
    public IssuedToken issue(AdminUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        String token = Jwts.builder()
                .subject(user.getUsername())
                .issuer(properties.issuer())
                .claim(CLAIM_USER_ID, user.getId())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt, Duration.between(issuedAt, expiresAt).toSeconds());
    }

    /**
     * 校验并解析令牌。
     *
     * @throws JwtException             签名无效、已过期、签发方不匹配等
     * @throws IllegalArgumentException 令牌格式非法
     */
    public JwtPrincipal parse(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        Object userId = claims.get(CLAIM_USER_ID);
        Long id = userId == null ? null : Long.valueOf(userId.toString());
        return new JwtPrincipal(id, claims.getSubject(), claims.getExpiration().toInstant());
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }

    public record JwtPrincipal(Long userId, String username, Instant expiresAt) {
    }
}

package com.example.hrsblog.security;

import com.example.hrsblog.config.JwtProperties;
import com.example.hrsblog.domain.AdminUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWT 签发/校验单元测试：正常签发、篡改、换密钥、过期、签发方不匹配、格式非法。 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-0123456789-0123456789-abcdef";
    private static final String OTHER_SECRET = "unit-test-secret-other-9876543210-zyxwvutsrq";
    private static final String ISSUER = "hrsblog-test";

    private final JwtTokenProvider provider = provider(SECRET, ISSUER, Duration.ofHours(2));

    @Test
    void issuesTokenCarryingUsernameUserIdAndExpiry() {
        AdminUser admin = admin(7L, "hrs-admin");

        JwtTokenProvider.IssuedToken issued = provider.issue(admin);
        JwtTokenProvider.JwtPrincipal principal = provider.parse(issued.token());

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(Duration.ofHours(2).toSeconds());
        assertThat(principal.username()).isEqualTo("hrs-admin");
        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.expiresAt()).isAfter(Instant.now());
        // JWT 的 exp 以「秒」为单位写入，比较时截断到秒
        assertThat(principal.expiresAt()).isEqualTo(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void rejectsTamperedToken() {
        String token = provider.issue(admin(1L, "admin")).token();
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> provider.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtTokenProvider attacker = provider(OTHER_SECRET, ISSUER, Duration.ofHours(2));
        String forged = attacker.issue(admin(1L, "admin")).token();

        assertThatThrownBy(() -> provider.parse(forged))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject("admin")
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key(SECRET))
                .compact();

        assertThatThrownBy(() -> provider.parse(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtTokenProvider foreign = provider(SECRET, "another-issuer", Duration.ofHours(2));
        String token = foreign.issue(admin(1L, "admin")).token();

        assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> provider.parse("not-a-jwt")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenContainsExpectedClaims() {
        String token = provider.issue(admin(42L, "claim-admin")).token();

        Claims claims = Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("claim-admin");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.get(JwtTokenProvider.CLAIM_USER_ID, Long.class)).isEqualTo(42L);
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    private JwtTokenProvider provider(String secret, String issuer, Duration ttl) {
        return new JwtTokenProvider(new JwtProperties(secret, issuer, ttl));
    }

    private SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private AdminUser admin(Long id, String username) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setUsername(username);
        admin.setPasswordHash("{bcrypt}irrelevant");
        admin.setDisplayName("Admin");
        return admin;
    }
}

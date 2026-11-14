package com.example.hrsblog.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT 配置，全部来自环境变量：{@code JWT_SECRET} / {@code JWT_ISSUER} / {@code JWT_TTL}。
 *
 * <p>密钥不允许硬编码：缺失或短于 32 字符时应用启动失败（HS256 要求 256 位密钥）。
 */
@Validated
@ConfigurationProperties(prefix = "hrsblog.jwt")
public record JwtProperties(
        @NotBlank(message = "hrsblog.jwt.secret 未配置，请通过环境变量 JWT_SECRET 注入")
        @Size(min = 32, message = "JWT 密钥至少需要 32 个字符（256 位）")
        String secret,

        @NotBlank(message = "hrsblog.jwt.issuer 不能为空")
        String issuer,

        @NotNull(message = "hrsblog.jwt.ttl 不能为空")
        Duration ttl
) {

    public JwtProperties {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("hrsblog.jwt.ttl 必须是正的时长，例如 2h");
        }
    }
}

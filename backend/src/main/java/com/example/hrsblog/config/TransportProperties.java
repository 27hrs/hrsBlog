package com.example.hrsblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 传输层（TCP 层）配置。
 *
 * @param proxyProtocolVersion PROXY protocol 版本：空字符串=禁用；{@code v1}/{@code v2}=启用。
 *                             与 frpc 的 {@code transport.proxyProtocolVersion} 语义对齐，
 *                             生产通过环境变量 {@code PROXY_PROTOCOL_VERSION} 注入。
 */
@ConfigurationProperties(prefix = "hrsblog.transport")
public record TransportProperties(String proxyProtocolVersion) {

    /** 允许的 PROXY protocol 版本取值（忽略大小写）。 */
    private static final String VERSION_V1 = "v1";

    private static final String VERSION_V2 = "v2";

    public TransportProperties {
        proxyProtocolVersion = proxyProtocolVersion == null ? "" : proxyProtocolVersion.trim();
        if (!proxyProtocolVersion.isEmpty()
                && !VERSION_V1.equalsIgnoreCase(proxyProtocolVersion)
                && !VERSION_V2.equalsIgnoreCase(proxyProtocolVersion)) {
            throw new IllegalArgumentException(
                    "hrsblog.transport.proxy-protocol-version 只允许为空、v1 或 v2，当前值: " + proxyProtocolVersion);
        }
    }

    /**
     * @return 是否启用 PROXY protocol 解析；默认（未配置）返回 {@code false}，行为与引入本特性前完全一致。
     */
    public boolean proxyProtocolEnabled() {
        return !proxyProtocolVersion.isEmpty();
    }
}

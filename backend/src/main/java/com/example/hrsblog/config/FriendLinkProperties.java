package com.example.hrsblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 友链功能的运行期参数（{@code hrsblog.friend-link}）。
 *
 * @param dailySubmitLimit        同一个 IP 每天最多可提交的友链申请次数；0 表示不限制
 * @param trustForwardedHeaders    是否信任反向代理写入的 {@code X-Forwarded-For} / {@code X-Real-IP} 头
 */
@ConfigurationProperties(prefix = "hrsblog.friend-link")
public record FriendLinkProperties(
        Integer dailySubmitLimit,
        Boolean trustForwardedHeaders
) {

    public FriendLinkProperties {
        dailySubmitLimit = dailySubmitLimit == null ? 5 : Math.max(0, dailySubmitLimit);
        trustForwardedHeaders = trustForwardedHeaders == null || trustForwardedHeaders;
    }
}

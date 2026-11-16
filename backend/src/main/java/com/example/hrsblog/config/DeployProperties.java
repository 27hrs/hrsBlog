package com.example.hrsblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 部署通知（「内容变更 → Webhook → CI 重建」）的运行期参数。
 *
 * <p>站点设置（{@code site_setting} 表）里的 webhook* 配置项优先于这里的默认值：
 * 管理后台可以随时调整事件类型、防抖窗口与重试次数，这里只提供兜底值与
 * 「不随站点设置变化」的运行时参数（退避节奏、HTTP 超时）。
 *
 * @param defaultEventType       事件类型默认值，站点未配置时使用
 * @param defaultDebounceSeconds 防抖窗口默认值（秒）
 * @param defaultMaxRetries      失败重试次数默认值（不含首次尝试）
 * @param backoffSeconds         逐次退避时长（秒）：第 1 次失败后取第 1 个，第 2 次取第 2 个……
 * @param connectTimeoutSeconds  Webhook 连接超时（秒）
 * @param readTimeoutSeconds     Webhook 读取超时（秒）
 */
@ConfigurationProperties(prefix = "hrsblog.deploy")
public record DeployProperties(
        String defaultEventType,
        Integer defaultDebounceSeconds,
        Integer defaultMaxRetries,
        List<Long> backoffSeconds,
        Integer connectTimeoutSeconds,
        Integer readTimeoutSeconds) {

    public DeployProperties {
        defaultEventType = (defaultEventType == null || defaultEventType.isBlank())
                ? "blog-content-updated"
                : defaultEventType.trim();
        defaultDebounceSeconds = defaultDebounceSeconds == null ? 10 : defaultDebounceSeconds;
        defaultMaxRetries = defaultMaxRetries == null ? 2 : defaultMaxRetries;
        backoffSeconds = (backoffSeconds == null || backoffSeconds.isEmpty())
                ? List.of(5L, 15L, 30L, 60L)
                : List.copyOf(backoffSeconds);
        connectTimeoutSeconds = connectTimeoutSeconds == null ? 5 : connectTimeoutSeconds;
        readTimeoutSeconds = readTimeoutSeconds == null ? 10 : readTimeoutSeconds;
    }

    /**
     * 取第 {@code attempt} 次失败后应等待的秒数。
     *
     * @param attempt 已经失败的那一次尝试（从 1 开始）
     * @return 等待秒数；超出退避表长度时沿用最后一个值
     */
    public long backoffFor(int attempt) {
        int index = Math.min(Math.max(attempt, 1), backoffSeconds.size()) - 1;
        return backoffSeconds.get(index);
    }
}

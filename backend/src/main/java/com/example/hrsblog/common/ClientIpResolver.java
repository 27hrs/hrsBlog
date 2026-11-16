package com.example.hrsblog.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析：把请求还原成「真实访客地址」。
 *
 * <p>部署形态是「nginx → 后端」，nginx 会写入 {@code X-Forwarded-For}
 * （{@code $proxy_add_x_forwarded_for}）与 {@code X-Real-IP}（{@code $remote_addr}），
 * 因此优先取 {@code X-Forwarded-For} 的第一段——那是最初发起请求的客户端地址。
 * 两者都取不到时回退到 Servlet 层的 {@code getRemoteAddr()}（直连或 PROXY protocol 场景）。
 *
 * <p><b>安全提示</b>：转发头可以被伪造。是否信任它们由
 * {@code hrsblog.friend-link.trust-forwarded-headers} 决定：后端端口没有对公网暴露时
 * 可以保持开启（否则所有访客会共用 nginx 的地址、限频形同虚设）；
 * 若后端端口可能被直连，请关闭它，限频将退化为按直连来源地址统计。
 */
public final class ClientIpResolver {

    /** 无法识别时的占位值。 */
    public static final String UNKNOWN = "unknown";

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    /** IPv6 最长表示长度（含 IPv4-mapped 形式），与 friend_link.submitted_ip 列长度一致。 */
    private static final int MAX_LENGTH = 45;

    private ClientIpResolver() {
    }

    /**
     * 解析真实客户端 IP。
     *
     * @param request                当前请求，可为 null
     * @param trustForwardedHeaders  是否信任反向代理写入的转发头
     * @return 客户端 IP；无法识别时返回 {@link #UNKNOWN}
     */
    public static String resolve(HttpServletRequest request, boolean trustForwardedHeaders) {
        if (request == null) {
            return UNKNOWN;
        }
        if (trustForwardedHeaders) {
            String forwarded = firstForwardedAddress(request.getHeader(X_FORWARDED_FOR));
            if (forwarded != null) {
                return forwarded;
            }
            String realIp = sanitize(request.getHeader(X_REAL_IP));
            if (realIp != null) {
                return realIp;
            }
        }
        String remote = sanitize(request.getRemoteAddr());
        return remote != null ? remote : UNKNOWN;
    }

    /**
     * 取 {@code X-Forwarded-For} 的第一段：形如 {@code client, proxy1, proxy2}，
     * 第一段才是最初发起请求的客户端地址。
     */
    private static String firstForwardedAddress(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int separator = header.indexOf(',');
        String candidate = separator < 0 ? header : header.substring(0, separator);
        return sanitize(candidate);
    }

    /**
     * 清洗候选地址：只保留「像 IP 的」字符，避免把任意请求头内容写进数据库与日志。
     */
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            boolean allowed = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F')
                    || ch == '.' || ch == ':' || ch == '%';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }
}

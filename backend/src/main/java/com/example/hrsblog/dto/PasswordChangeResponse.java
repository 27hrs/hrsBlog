package com.example.hrsblog.dto;

/**
 * 修改密码成功响应：返回新签发的 JWT，前端据此替换本地令牌。
 *
 * <p>旧令牌在有效期届满前仍然可用（无状态 JWT，不做服务端吊销）；
 * 如需立即失效，可扩展令牌版本号或维护黑名单。
 *
 * @param accessToken 新访问令牌
 * @param tokenType   固定为 Bearer
 * @param expiresIn   有效期（秒）
 */
public record PasswordChangeResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}

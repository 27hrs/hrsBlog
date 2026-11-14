package com.example.hrsblog.dto;

/**
 * 登录成功响应：JWT 与最小化的管理员资料（不含密码哈希等敏感字段）。
 *
 * @param accessToken 访问令牌，前端后续请求放在 {@code Authorization: Bearer <token>}
 * @param tokenType   固定为 Bearer
 * @param expiresIn   有效期（秒）
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AdminUserSummary user
) {

    public record AdminUserSummary(Long id, String username, String displayName, String avatarUrl) {
    }
}

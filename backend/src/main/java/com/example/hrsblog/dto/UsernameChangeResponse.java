package com.example.hrsblog.dto;

/**
 * 修改用户名成功响应：返回重新签发的 JWT 与更新后的管理员资料。
 *
 * <p>JWT 的 subject 就是用户名，修改用户名后旧 Token 不再能定位到账号，
 * 因此必须用这里返回的新 Token 覆盖本地令牌。
 *
 * @param accessToken 新访问令牌
 * @param tokenType   固定为 Bearer
 * @param expiresIn   有效期（秒）
 * @param user        更新后的管理员资料
 */
public record UsernameChangeResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        LoginResponse.AdminUserSummary user
) {
}

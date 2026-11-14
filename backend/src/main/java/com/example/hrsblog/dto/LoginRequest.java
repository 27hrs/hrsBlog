package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员登录请求。
 *
 * <p>失败一律返回 401 且文案统一，不以「用户不存在 / 密码错误 / 账号禁用」区分，避免账号枚举。
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度必须在 3-64 之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度必须在 8-64 之间")
        String password
) {
}

package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 *
 * <p>原密码必须与数据库中的 BCrypt 哈希匹配；新密码除长度外还要求同时包含字母和数字，
 * 且不能与原密码相同（服务层二次校验）。旧 Token 不会立即失效，保留至自然过期。
 */
public record PasswordChangeRequest(

        @NotBlank(message = "oldPassword 不能为空")
        @Size(min = 8, max = 64, message = "原密码长度必须在 8-64 之间")
        String oldPassword,

        @NotBlank(message = "newPassword 不能为空")
        @Size(min = 8, max = 64, message = "新密码长度必须在 8-64 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "新密码必须同时包含字母和数字，长度 8-64")
        String newPassword
) {
}

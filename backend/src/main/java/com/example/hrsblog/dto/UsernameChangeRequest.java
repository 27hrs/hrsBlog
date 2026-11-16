package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改用户名请求。
 *
 * <p>新用户名只能包含字母、数字、下划线和中划线，长度 3-32；
 * 服务层再校验一次唯一性（排除当前账号），冲突返回 409。
 *
 * @param newUsername 新的登录用户名
 */
public record UsernameChangeRequest(

        @NotBlank(message = "newUsername 不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$",
                message = "用户名只能包含字母、数字、下划线和中划线，长度 3-32")
        String newUsername
) {
}

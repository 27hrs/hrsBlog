package com.example.hrsblog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 访客提交留言请求（匿名接口）。
 *
 * <p>三个字段：姓名（必填）、邮箱（可选）、留言内容（必填）。
 *
 * <p>{@code email} 只用于两件事：一是派生 QQ 头像（前端预览 + 后端生成公开头像地址），
 * 二是在管理端给站长留一个联系渠道。它<b>绝不</b>出现在访客可见的响应里。
 */
public record GuestMessageSubmitRequest(

        @NotBlank(message = "nickname 不能为空")
        @Size(max = 64, message = "nickname 长度不能超过 64")
        String nickname,

        @Email(message = "email 格式不正确")
        @Size(max = 255, message = "email 长度不能超过 255")
        String email,

        @NotBlank(message = "content 不能为空")
        @Size(max = 500, message = "content 长度不能超过 500")
        String content
) {
}

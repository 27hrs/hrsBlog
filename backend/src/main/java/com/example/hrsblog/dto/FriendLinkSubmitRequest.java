package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 访客提交友链申请请求（匿名接口）。
 *
 * <p>URL 只放行 {@code http://} 与 {@code https://}；空串视为「未填写」，
 * 因此可选字段既可以传 {@code null} 也可以传空字符串。
 *
 * <p>{@code contact}（联系方式）只允许出现在管理端响应里，提交后不会再返回给访客。
 */
public record FriendLinkSubmitRequest(

        @NotBlank(message = "siteName 不能为空")
        @Size(max = 100, message = "siteName 长度不能超过 100")
        String siteName,

        @NotBlank(message = "siteUrl 不能为空")
        @Size(max = 500, message = "siteUrl 长度不能超过 500")
        @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "siteUrl 必须以 http:// 或 https:// 开头")
        String siteUrl,

        @NotBlank(message = "linkPageUrl 不能为空")
        @Size(max = 500, message = "linkPageUrl 长度不能超过 500")
        @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "linkPageUrl 必须以 http:// 或 https:// 开头")
        String linkPageUrl,

        @Size(max = 500, message = "avatarUrl 长度不能超过 500")
        @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "avatarUrl 必须以 http:// 或 https:// 开头")
        String avatarUrl,

        @Size(max = 500, message = "description 长度不能超过 500")
        String description,

        @Size(max = 200, message = "contact 长度不能超过 200")
        String contact
) {
}

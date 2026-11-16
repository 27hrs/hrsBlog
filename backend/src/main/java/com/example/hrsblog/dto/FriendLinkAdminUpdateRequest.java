package com.example.hrsblog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理端更新友链请求（全量字段覆盖）。
 *
 * <p>字段与校验规则与 {@link FriendLinkSubmitRequest} 一致，另外允许调整展示顺序；
 * 状态变更走单独的审核接口（{@code POST /api/admin/friend-links/{id}/review}）。
 */
public record FriendLinkAdminUpdateRequest(

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
        String contact,

        @Min(value = 0, message = "sortOrder 不能小于 0")
        @Max(value = 9999, message = "sortOrder 不能大于 9999")
        Integer sortOrder
) {
}

package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 管理员投递（新建）文章请求。
 *
 * <p>{@code contentFormat} / {@code status} 用受限字符串 + Bean Validation 校验，
 * 而不是直接反序列化成枚举：非法取值会得到字段级 400，而不是笼统的解析失败，
 * 同时避免把领域枚举的演进直接绑死到接口报文上。
 */
public record ArticleCreateRequest(

        @NotBlank(message = "title 不能为空")
        @Size(max = 200, message = "title 长度不能超过 200")
        String title,

        @NotBlank(message = "slug 不能为空")
        @Size(max = 200, message = "slug 长度不能超过 200")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "slug 只能由小写字母、数字和单个连字符组成，且不能以连字符开头或结尾")
        String slug,

        @Size(max = 500, message = "summary 长度不能超过 500")
        String summary,

        @NotBlank(message = "content 不能为空")
        @Size(max = 500_000, message = "content 长度不能超过 500000")
        String content,

        @Pattern(regexp = "MARKDOWN", message = "contentFormat 目前仅支持 MARKDOWN")
        String contentFormat,

        @Size(max = 500, message = "coverUrl 长度不能超过 500")
        @Pattern(regexp = "^(https?://.+|/.+)$", message = "coverUrl 必须是 http(s) 链接或以 / 开头的站内路径")
        String coverUrl,

        @NotNull(message = "status 不能为空")
        @Pattern(regexp = "DRAFT|PUBLISHED", message = "status 只能取 DRAFT 或 PUBLISHED")
        String status,

        @Size(max = 10, message = "标签数量不能超过 10")
        Set<
                @NotBlank(message = "标签名不能为空")
                @Size(max = 50, message = "标签名长度不能超过 50")
                        String> tags
) {
}

package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 管理端审核留言请求：只接受「通过」与「拒绝」两种结论。
 *
 * <p>用受限字符串 + Bean Validation 校验，而不是直接反序列化成枚举：
 * 非法取值会得到字段级 400，而不是笼统的解析失败。
 */
public record GuestMessageReviewRequest(

        @NotBlank(message = "status 不能为空")
        @Pattern(regexp = "APPROVED|REJECTED", message = "status 只能取 APPROVED 或 REJECTED")
        String status
) {
}

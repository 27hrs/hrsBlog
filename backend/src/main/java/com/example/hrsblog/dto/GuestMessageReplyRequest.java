package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端回复留言请求。
 *
 * <p>一条留言至多一条回复：再次提交即覆盖旧回复（upsert 语义）。
 */
public record GuestMessageReplyRequest(

        @NotBlank(message = "content 不能为空")
        @Size(max = 1000, message = "content 长度不能超过 1000")
        String content
) {
}

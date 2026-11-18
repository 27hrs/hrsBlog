package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建说说请求。
 *
 * <p>新建的条目一律是私有草稿（{@code visibility=PRIVATE}）：
 * 只有显式调用发布接口才会出现在访客端，避免「刚写完就公开」。
 */
public record ShuoshuoCreateRequest(

        @NotBlank(message = "content 不能为空")
        @Size(max = 2000, message = "content 长度不能超过 2000")
        String content
) {
}

package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新说说请求。
 *
 * <p>只覆盖正文；可见性与发布时间由发布 / 撤回接口单独维护，
 * 避免「编辑内容」意外改变条目在时间线上的位置。
 */
public record ShuoshuoUpdateRequest(

        @NotBlank(message = "content 不能为空")
        @Size(max = 2000, message = "content 长度不能超过 2000")
        String content
) {
}

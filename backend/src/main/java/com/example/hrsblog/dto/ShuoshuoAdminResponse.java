package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 管理端说说信息：包含可见性与三个时间戳，供列表与详情页使用。
 *
 * <p>{@code visibility} 用字符串承载（PUBLIC / PRIVATE），
 * 与文章、友链的管理端响应保持同一套约定。
 */
public record ShuoshuoAdminResponse(
        Long id,
        String content,
        String visibility,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

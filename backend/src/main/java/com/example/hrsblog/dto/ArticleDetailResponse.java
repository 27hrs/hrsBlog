package com.example.hrsblog.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 访客文章详情：仅 PUBLISHED 文章可获取，包含正文与时间信息。 */
public record ArticleDetailResponse(
        Long id,
        String title,
        String slug,
        String summary,
        String content,
        String contentFormat,
        String coverUrl,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TagResponse> tags
) {
}

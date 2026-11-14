package com.example.hrsblog.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 访客文章列表项：不含正文，避免列表接口传输大字段。 */
public record ArticleSummaryResponse(
        Long id,
        String title,
        String slug,
        String summary,
        String coverUrl,
        LocalDateTime publishedAt,
        List<TagResponse> tags
) {
}

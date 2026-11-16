package com.example.hrsblog.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端文章摘要：包含草稿与已发布文章，供文章管理列表展示与编辑入口取数。
 *
 * <p>与访客 {@link ArticleSummaryResponse} 的差异：多 {@code contentFormat}、
 * {@code createdAt}、{@code updatedAt} 与 {@code status}，便于后台按状态筛选与展示编辑时间。
 */
public record AdminArticleSummaryResponse(
        Long id,
        String title,
        String slug,
        String summary,
        String contentFormat,
        String coverUrl,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TagResponse> tags
) {
}

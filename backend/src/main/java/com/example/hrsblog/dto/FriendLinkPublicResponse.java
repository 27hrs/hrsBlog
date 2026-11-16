package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 访客可见的友链信息。
 *
 * <p><b>刻意不包含 {@code contact}</b>：联系方式只在管理端出现，
 * 访客接口（以及由它构建出来的静态页面）绝不返回该字段，避免被爬虫批量采集。
 */
public record FriendLinkPublicResponse(
        Long id,
        String siteName,
        String siteUrl,
        String linkPageUrl,
        String avatarUrl,
        String description,
        Integer sortOrder,
        LocalDateTime createdAt
) {
}

package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 管理端友链信息：与访客版的差别是多了 {@code contact}（联系方式）与审核痕迹。
 *
 * <p>{@code submittedIp} 同样只在管理端可见，便于排查重复提交与限频命中。
 */
public record FriendLinkAdminResponse(
        Long id,
        String siteName,
        String siteUrl,
        String linkPageUrl,
        String avatarUrl,
        String description,
        String contact,
        String status,
        Integer sortOrder,
        String submittedIp,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

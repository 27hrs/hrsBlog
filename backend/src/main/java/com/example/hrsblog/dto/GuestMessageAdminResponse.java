package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 管理端留言信息：与访客版的差别是多了邮箱、来源 IP、UA 与审核状态。
 *
 * <p>{@code reply} 为 {@code null} 表示这条留言还没有管理员回复；
 * 一条留言至多一条回复，编辑回复即覆盖。
 */
public record GuestMessageAdminResponse(
        Long id,
        String nickname,
        String email,
        String content,
        String ipAddress,
        String userAgent,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        GuestMessageReplyResponse reply
) {
}

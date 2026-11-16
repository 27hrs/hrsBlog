package com.example.hrsblog.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 访客可见的留言信息。
 *
 * <p><b>刻意不包含 {@code email} / {@code ipAddress} / {@code userAgent}</b>：
 * 邮箱只用于派生头像与管理端联系，来源 IP 与 UA 只用于审核与防刷，
 * 三者都只在 {@link GuestMessageAdminResponse} 里出现。
 *
 * <p>{@code avatarUrl} 由服务端依据邮箱派生（QQ 邮箱才有值），不是访客可以直接填写的字段，
 * 因此不存在「访客塞一个任意 URL 骗流量」的问题。
 */
public record GuestMessagePublicResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String content,
        LocalDateTime createdAt,
        List<GuestMessageReplyResponse> replies
) {
}

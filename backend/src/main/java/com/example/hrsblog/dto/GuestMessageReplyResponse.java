package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 管理员回复的公开表示：留言墙里嵌套展示的那一块。
 *
 * <p>只包含回复正文与时间——回复人始终是站长本人，不需要在公开接口里暴露管理员身份。
 */
public record GuestMessageReplyResponse(
        Long id,
        String content,
        LocalDateTime createdAt
) {
}

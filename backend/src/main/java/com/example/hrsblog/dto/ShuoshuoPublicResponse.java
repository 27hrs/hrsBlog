package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 访客可见的说说信息。
 *
 * <p><b>刻意不包含 {@code visibility} / {@code createdAt} / {@code updatedAt}</b>：
 * 访客只关心「说了什么」与「什么时候说的」，草稿痕迹与编辑痕迹一律不外泄。
 */
public record ShuoshuoPublicResponse(
        Long id,
        String content,
        LocalDateTime publishedAt
) {
}

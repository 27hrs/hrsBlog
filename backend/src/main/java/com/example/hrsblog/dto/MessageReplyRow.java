package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 留言回复的只读投影：批量取回复时用它代替实体，避免把懒加载的留言关联拖进内存。
 *
 * @param id        回复 ID
 * @param messageId 被回复的留言 ID
 * @param content   回复正文
 * @param createdAt 回复创建时间
 */
public record MessageReplyRow(
        Long id,
        Long messageId,
        String content,
        LocalDateTime createdAt
) {
}

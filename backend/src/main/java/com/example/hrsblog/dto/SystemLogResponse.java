package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 通用系统日志条目（管理端展示用）。
 *
 * @param id        日志主键
 * @param type      日志类型，例如 {@code DEPLOY_NOTIFY}
 * @param scene     业务场景，例如 {@code blog-content-updated}
 * @param status    结果状态：SUCCESS / FAILED
 * @param attempt   第几次尝试，从 1 开始
 * @param message   结果摘要
 * @param detail    脱敏后的详情
 * @param createdAt 写入时间
 */
public record SystemLogResponse(
        Long id,
        String type,
        String scene,
        String status,
        int attempt,
        String message,
        String detail,
        LocalDateTime createdAt) {
}

package com.example.hrsblog.dto;

import java.time.LocalDateTime;

/**
 * 静态资源响应：数据库只保存链接与元数据，文件本体在存储层。
 *
 * @param url   对外访问链接（public-base-url + storageKey）
 * @param size  文件字节大小
 * @param hash  SHA-256 摘要，可用作去重/秒传校验
 */
public record AssetResponse(
        Long id,
        String url,
        String originalName,
        String mimeType,
        long size,
        String hash,
        String kind,
        String status,
        LocalDateTime createdAt
) {
}

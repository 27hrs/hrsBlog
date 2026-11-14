package com.example.hrsblog.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应体，避免把 Spring Data 的 {@link Page} 内部结构直接暴露成接口契约。
 *
 * <p>{@code page} 从 0 开始计数，与 {@code GET /api/visitors/articles?page=} 的入参一致。
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}

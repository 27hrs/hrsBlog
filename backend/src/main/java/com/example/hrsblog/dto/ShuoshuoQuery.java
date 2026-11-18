package com.example.hrsblog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 说说列表查询参数（用于 {@code @ModelAttribute} 绑定 + Bean Validation）。
 *
 * <p>{@code size} 上限 50，防止一次拉取过多数据；{@code visibility} 只接受两种可见性，
 * 留空表示不筛选。排序不开放给调用方，由服务端固定
 * （访客端按 {@code published_at}、管理端按 {@code created_at} 倒序）。
 *
 * <p>{@code visibility} 仅管理端接口使用（可选，PUBLIC / PRIVATE）；
 * 访客端固定查询 PUBLIC，服务层不读取该字段。
 */
@Getter
@Setter
public class ShuoshuoQuery {

    @Min(value = 0, message = "page 不能小于 0")
    private int page = 0;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 50, message = "size 不能大于 50")
    private int size = 10;

    /** 可选可见性筛选（仅管理端）：PUBLIC 或 PRIVATE。 */
    @Pattern(regexp = "PUBLIC|PRIVATE", message = "visibility 只能取 PUBLIC 或 PRIVATE")
    private String visibility;
}

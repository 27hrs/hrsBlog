package com.example.hrsblog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 访客文章列表查询参数（用于 {@code @ModelAttribute} 绑定 + Bean Validation）。
 *
 * <p>{@code size} 上限 50，防止匿名调用方一次拉取过多数据；{@code tag} 只接受标签 slug。
 * 排序不开放给调用方，由服务端固定按 {@code published_at} 倒序，避免通过排序参数做探测。
 */
@Getter
@Setter
public class ArticleQuery {

    @Min(value = 0, message = "page 不能小于 0")
    private int page = 0;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 50, message = "size 不能大于 50")
    private int size = 10;

    @Size(max = 80, message = "tag 长度不能超过 80")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "tag 只能由小写字母、数字和单个连字符组成")
    private String tag;
}

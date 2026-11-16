package com.example.hrsblog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章列表查询参数（用于 {@code @ModelAttribute} 绑定 + Bean Validation）。
 *
 * <p>{@code size} 上限 50，防止匿名调用方一次拉取过多数据；{@code tag} 只接受标签 slug。
 * 排序不开放给调用方，由服务端固定（访客按 {@code published_at}、管理端按 {@code updated_at} 倒序）。
 *
 * <p>{@code status} 仅管理端接口使用（可选，DRAFT / PUBLISHED）；访客端固定查询
 * PUBLISHED，服务层不读取该字段。
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

    /** 可选状态筛选（仅管理端）：DRAFT 或 PUBLISHED。 */
    @Pattern(regexp = "DRAFT|PUBLISHED", message = "status 只能取 DRAFT 或 PUBLISHED")
    private String status;
}

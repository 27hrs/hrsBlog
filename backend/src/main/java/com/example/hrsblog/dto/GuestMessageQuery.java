package com.example.hrsblog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 留言列表查询参数（用于 {@code @ModelAttribute} 绑定 + Bean Validation）。
 *
 * <p>{@code size} 上限 100，防止一次拉取过多数据；{@code status} 只接受三种审核状态，
 * 留空表示不筛选。排序不开放给调用方，由服务端固定（按提交时间倒序）。
 */
@Getter
@Setter
public class GuestMessageQuery {

    @Min(value = 0, message = "page 不能小于 0")
    private int page = 0;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    private int size = 20;

    @Pattern(regexp = "PENDING|APPROVED|REJECTED",
            message = "status 只能取 PENDING、APPROVED 或 REJECTED")
    private String status;
}

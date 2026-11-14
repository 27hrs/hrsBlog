package com.example.hrsblog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 管理员资源列表查询参数。 */
@Getter
@Setter
public class AssetQuery {

    @Min(value = 0, message = "page 不能小于 0")
    private int page = 0;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    private int size = 20;

    @Pattern(regexp = "IMAGE", message = "kind 目前仅支持 IMAGE")
    private String kind;

    @Pattern(regexp = "ACTIVE|DELETED", message = "status 只能取 ACTIVE 或 DELETED")
    private String status;
}

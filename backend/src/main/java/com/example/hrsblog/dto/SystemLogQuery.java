package com.example.hrsblog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用系统日志查询参数（管理端 {@code GET /api/admin/logs}）。
 *
 * <p>{@code page} 从 0 开始计数，与其它分页接口保持一致；
 * {@code type} 与 {@code status} 均为可选筛选，缺省表示不过滤。
 */
@Getter
@Setter
public class SystemLogQuery {

    @Min(value = 0, message = "page 不能小于 0")
    private int page = 0;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    private int size = 20;

    /** 可选日志类型，例如 {@code DEPLOY_NOTIFY}。 */
    @Size(max = 32, message = "type 长度不能超过 32")
    private String type;

    /** 可选结果状态：SUCCESS 或 FAILED。 */
    @Pattern(regexp = "SUCCESS|FAILED", message = "status 只能取 SUCCESS 或 FAILED")
    private String status;
}

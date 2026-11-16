package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员批量删除文章请求。
 *
 * <p>{@code ids} 为待删除的文章主键列表：
 * <ul>
 *   <li>{@link NotNull} 保证缺少该字段时给出字段级错误，而不是空指针；</li>
 *   <li>{@link NotEmpty} 拒绝空列表，避免产生无意义的批量请求；</li>
 *   <li>{@link Size} 限制单次最多 200 个 id，防止超大请求拖垮数据库。</li>
 * </ul>
 *
 * <p>重复 id 由服务层去重（保留首次出现的顺序），不会重复计数。
 */
public record ArticleBatchDeleteRequest(

        @NotNull(message = "ids 不能为空")
        @NotEmpty(message = "ids 不能为空")
        @Size(max = 200, message = "单次最多删除 200 篇文章")
        List<Long> ids
) {
}

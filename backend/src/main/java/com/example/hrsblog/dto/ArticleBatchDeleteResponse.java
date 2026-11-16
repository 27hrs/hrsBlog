package com.example.hrsblog.dto;

import java.util.List;

/**
 * 管理员批量删除文章结果。
 *
 * <p>批量删除采用「尽力而为」语义：能删的删掉，删不掉的逐条回报原因，
 * 请求整体仍然成功（HTTP 200 / code=0），由前端根据 {@code failed} 给出提示。
 *
 * @param totalCount   本次处理的文章数量（已去重，保留首次出现的顺序）
 * @param successCount 成功软删除的文章数量
 * @param failed       删除失败明细，全部成功时为空列表
 */
public record ArticleBatchDeleteResponse(
        int totalCount,
        int successCount,
        List<FailedItem> failed
) {

    /**
     * 单条删除失败明细。
     *
     * @param id      未能删除的文章主键
     * @param code    失败原因对应的错误码（{@code ErrorCode} 的数值）
     * @param message 失败原因文案
     */
    public record FailedItem(Long id, int code, String message) {
    }
}

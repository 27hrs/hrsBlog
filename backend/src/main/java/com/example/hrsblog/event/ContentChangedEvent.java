package com.example.hrsblog.event;

import java.time.Instant;
import java.util.List;

/**
 * 内容变更事件：站点配置实际发生变化，或文章被发布 / 已发布文章内容被更新。
 *
 * <p>事件在业务事务内发布，由 {@code DeployNotifyService} 以
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 消费 —— 只有事务真正提交
 * 之后才会去通知外部系统，避免回滚了却把「已变更」的事实推给 CI。
 *
 * <p>载荷只携带「变更原因」，不带任何内容：原因列表用于让接收方知道是站点配置变了
 * 还是文章变了，具体内容仍以内容源为准。
 *
 * @param reasons    变更原因集合，例如 {@code settings_updated} / {@code article_published}
 * @param occurredAt 变更发生时间（ISO-8601，合并多个事件时取最早的一个）
 */
public record ContentChangedEvent(List<String> reasons, Instant occurredAt) {

    /** 站点配置实际发生变化。 */
    public static final String REASON_SETTINGS_UPDATED = "settings_updated";

    /** 文章被发布（新建即发布，或草稿首次转为已发布）。 */
    public static final String REASON_ARTICLE_PUBLISHED = "article_published";

    /** 已发布文章的内容被更新。 */
    public static final String REASON_ARTICLE_UPDATED = "article_updated";

    public ContentChangedEvent {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /** 用当前时间构造一个只带给定原因的事件。 */
    public static ContentChangedEvent of(String... reasons) {
        return new ContentChangedEvent(List.of(reasons), Instant.now());
    }
}

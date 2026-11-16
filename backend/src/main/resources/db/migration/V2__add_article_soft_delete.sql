-- 软删除支持：为文章表新增删除时间戳列。
-- 非 NULL 表示文章已被管理员删除；查询层面由 Hibernate @SQLRestriction(deleted_at IS NULL) 自动过滤，
-- 数据行与 article_tag 关联仍保留（软删除），slug 由应用层改写释放以便复用。

ALTER TABLE article
    ADD COLUMN deleted_at DATETIME(3) NULL COMMENT '软删除时间戳：非 NULL 表示文章已删除，对所有业务查询不可见';

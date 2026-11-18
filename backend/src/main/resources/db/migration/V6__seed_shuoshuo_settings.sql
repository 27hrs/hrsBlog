-- 说说模块的站点设置种子数据：预置说说页标题、描述与空态提示三项公开文案，
-- 使前台说说页开箱即有可读的默认文案（管理员保存过的值始终优先）。
--
-- 版本号说明：db/migration 目录里存在两个 V2 前缀文件（V2__create_static_asset.sql 与
-- V2__add_article_soft_delete.sql，历史遗留），Flyway 按「版本号 + 描述」登记迁移，
-- 因此新增迁移统一使用 V6 前缀，避免与既有的 V2 / V3 / V4 / V5 冲突。
--
-- 幂等约定：已存在的 (config_group, config_key) 行不覆盖（ON DUPLICATE KEY UPDATE id = id 为空操作），
-- 管理员已保存过的值始终优先；sort_order 改写按 config_key 定位，可重复执行。
--
-- 为什么要改写 sort_order：SiteSettingCatalog 的 sort_order 是全局自增的，
-- 在 reading 组中间插入说说三项后，其后所有分组（footer / about / seo / integrations / advanced）
-- 的序号整体后移 3 位。若不同步改写，site_setting 表里的 sort_order 会与 catalog 产生
-- 重号（seo 与 footer 撞在 35、36），访客接口按 sort_order 排序时就会把两组的配置交错返回。

-- 1) 预置「说说」三项公开文案（reading 组，与 SiteSettingCatalog 的 sort_order 一致）
INSERT INTO site_setting (config_group, config_key, config_value, value_type, description, is_public, sort_order) VALUES
('reading', 'shuoshuoTitle',       '说说',                                  'STRING', '说说页标题',           TRUE, 29),
('reading', 'shuoshuoDescription', '随手记下的一些片段：想法、片段与近况。', 'STRING', '说说页描述',           TRUE, 30),
('reading', 'shuoshuoEmptyText',   '还没有公开的说说，稍后再来看看。',     'STRING', '说说空状态提示文案',   TRUE, 31)
ON DUPLICATE KEY UPDATE id = id;

-- 2) allowIndexing 归位到 basic 组（V5 的同一条修正，放到这里以便在只执行 V6 的库上也保持一致）
UPDATE site_setting
SET config_group = 'basic',
    is_public = TRUE,
    sort_order = 6,
    description = '是否允许搜索引擎索引（robots.txt 与 noindex 由它驱动）'
WHERE config_key = 'allowIndexing'
  AND config_group = 'advanced';

-- 3) reading 组之后的分组整体后移 3 位（footer 29..33 → 32..36，about 34 → 37，
--    seo 35..39 → 38..42，integrations 40..48 → 43..51，advanced 49..51 → 52..54）
UPDATE site_setting SET sort_order = 32 WHERE config_key = 'footerStartYear';
UPDATE site_setting SET sort_order = 33 WHERE config_key = 'footerNote';
UPDATE site_setting SET sort_order = 34 WHERE config_key = 'icp';
UPDATE site_setting SET sort_order = 35 WHERE config_key = 'icpUrl';
UPDATE site_setting SET sort_order = 36 WHERE config_key = 'footerSyncedAt';
UPDATE site_setting SET sort_order = 37 WHERE config_key = 'aboutContent';
UPDATE site_setting SET sort_order = 38 WHERE config_key = 'titleTemplate';
UPDATE site_setting SET sort_order = 39 WHERE config_key = 'metaDescription';
UPDATE site_setting SET sort_order = 40 WHERE config_key = 'keywords';
UPDATE site_setting SET sort_order = 41 WHERE config_key = 'ogImage';
UPDATE site_setting SET sort_order = 42 WHERE config_key = 'enableSitemap';
UPDATE site_setting SET sort_order = 43 WHERE config_key = 'analyticsProvider';
UPDATE site_setting SET sort_order = 44 WHERE config_key = 'analyticsSiteId';
UPDATE site_setting SET sort_order = 45 WHERE config_key = 'analyticsScriptUrl';
UPDATE site_setting SET sort_order = 46 WHERE config_key = 'webhookUrl';
UPDATE site_setting SET sort_order = 47 WHERE config_key = 'webhookEnabled';
UPDATE site_setting SET sort_order = 48 WHERE config_key = 'webhookSecret';
UPDATE site_setting SET sort_order = 49 WHERE config_key = 'webhookEventType';
UPDATE site_setting SET sort_order = 50 WHERE config_key = 'webhookDebounceSeconds';
UPDATE site_setting SET sort_order = 51 WHERE config_key = 'webhookMaxRetries';
UPDATE site_setting SET sort_order = 52 WHERE config_key = 'siteStatus';
UPDATE site_setting SET sort_order = 53 WHERE config_key = 'timezone';
UPDATE site_setting SET sort_order = 54 WHERE config_key = 'showDebugInfo';

-- 站点设置种子数据：把 blog_frontend/.vitepress/site.config.mjs 的现有默认值预置进 site_setting，
-- 使页脚/作者/站点基础/关于页配置开箱即有值可读。
-- 幂等约定：已存在的 (config_group, config_key) 行不覆盖（ON DUPLICATE KEY UPDATE id = id 为空操作），
-- 管理员已保存过的值始终优先。

-- 1) 冗余键清理：footerText 被 footerNote 取代；robots 与 allowIndexing 语义重复且前台未消费
DELETE FROM site_setting WHERE config_key IN ('footerText', 'robots');

-- 2) allowIndexing 旧定义位于 advanced 组且私有，迁入 basic 组并公开（robots.txt 与 noindex 均消费它）
UPDATE site_setting
SET config_group = 'basic',
    is_public = TRUE,
    sort_order = 6,
    description = '是否允许搜索引擎索引（robots.txt 与 noindex 由它驱动）'
WHERE config_key = 'allowIndexing'
  AND config_group = 'advanced';

-- 3) 预置默认值（全部公开；sort_order 与 SiteSettingCatalog 保持一致）
INSERT INTO site_setting (config_group, config_key, config_value, value_type, description, is_public, sort_order) VALUES
('basic',    'siteName',        'HrsBlog',                                                                                                                    'STRING',  '站点名称',                                    TRUE, 1),
('basic',    'siteTagline',     '保持好奇，持续记录。',                                                                                                          'STRING',  '一句话介绍',                                  TRUE, 2),
('basic',    'siteDescription', '一个关于代码、设计与日常观察的个人数字花园。记录正在学习的事，也保存值得回看的过程。',                                                              'STRING',  '站点描述',                                    TRUE, 3),
('basic',    'siteWordmark',    'HrsBlog',                                                                                                                    'STRING',  '站点文字标识（页眉/页脚品牌文字）',                     TRUE, 5),
('basic',    'allowIndexing',   'true',                                                                                                                       'BOOLEAN', '是否允许搜索引擎索引（robots.txt 与 noindex 由它驱动）', TRUE, 6),
('identity', 'authorName',      'Hrs',                                                                                                                        'STRING',  '作者显示名称',                                  TRUE, 7),
('identity', 'authorRole',      '开发者 / 长期主义者',                                                                                                            'STRING',  '作者身份标签',                                  TRUE, 8),
('identity', 'authorTagline',   '在代码与山水之间，记录正在发生的事。',                                                                                              'STRING',  '作者一句话标语',                                TRUE, 9),
('identity', 'authorIntro',     '这里存放长一点的想法：工程、设计，以及生活里值得回看的瞬间。偶尔也写一些不合时宜的观察。',                                                        'STRING',  '作者简介（资料卡短介绍）',                          TRUE, 10),
('identity', 'authorBio',       '我在代码与文字之间来回，喜欢把复杂问题拆成小块，也喜欢在清晨写一点东西。这个博客不追热点，只希望每次打开都像走进一间安静的屋子。',                          'STRING',  '作者详细自述',                                  TRUE, 11),
('identity', 'avatarUrl',       '/avatar.svg',                                                                                                                'STRING',  '作者头像图片链接',                                TRUE, 12),
('identity', 'location',        '杭州',                                                                                                                        'STRING',  '所在城市',                                     TRUE, 13),
('footer',   'footerStartYear', '2024',                                                                                                                       'NUMBER',  '页脚版权起始年份',                                TRUE, 29),
('footer',   'footerNote',      '保持好奇，持续记录。',                                                                                                          'STRING',  '页脚备注文案',                                  TRUE, 30),
('footer',   'icp',             '京ICP备XXXXXXXX号',                                                                                                            'STRING',  'ICP 备案号，留空则页脚隐藏备案栏',                    TRUE, 31),
('footer',   'icpUrl',          'https://beian.miit.gov.cn/',                                                                                                 'STRING',  'ICP 备案查询链接',                               TRUE, 32),
('footer',   'footerSyncedAt',  '2026.11.15',                                                                                                                 'STRING',  '页脚『最近同步』回退时间文案',                       TRUE, 33),
('about',    'aboutContent',    '这个博客不追热点，也不保证更新频率，只希望每次打开都像走进一间安静的屋子。\n\n## 关于我\n\n白天写 Java 和前端，晚上读一点书。做过几年业务系统，也做过一些没人用的 side project。相比“把东西做出来”，我现在更在意“它是不是值得存在”。\n\n我相信好的软件应该像好的家具：安静、耐用、不需要说明书。也相信写作是一种整理，把散落的想法放回它们该在的位置。\n\n## 关于这个博客\n\n这里存放长一点的想法：工程、设计，以及生活里值得回看的瞬间。它不试图成为某个领域的权威，只是一份公开的笔记。\n\n技术上，它用 VitePress 做静态生成，内容来自一个很小的 Spring Boot 后端。文章在构建时被同步成 Markdown，所以页面是纯静态的：没有数据库查询，也没有客户端渲染的白屏。\n\n### 写什么\n\n- **工程**：后端设计、前端构建、错误处理，以及那些“看起来很简单”的问题；\n- **设计**：排版、颜色、界面里的克制，和照片有关的审美判断；\n- **生活**：阅读、散步、旅行，以及一些没有结论的观察。\n\n不写“三天学会”“2026 最火框架”之类的东西。不是它们没有价值，而是这里更适合放慢一点。\n\n## 一些长期在做的事\n\n- 维护一个足够小的内容后端，把复杂度留在该留的地方；\n- 练习把界面做得更少，但更准确；\n- 每周留半天不安排“产出”；\n- 把开了头的项目做完，允许它们“足够好”。\n\n> 写作不是为了被看见，而是为了把看见的东西留下来。\n\n如果你也在做类似的事，欢迎写信。邮箱和 GitHub 在最下面。', 'STRING', '关于页 Markdown 正文，留空时前台回退本地 about.md', TRUE, 34)
ON DUPLICATE KEY UPDATE id = id;

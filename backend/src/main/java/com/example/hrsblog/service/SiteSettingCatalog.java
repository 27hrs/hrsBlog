package com.example.hrsblog.service;

import com.example.hrsblog.domain.SiteSetting.ValueType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站点配置目录：服务端的唯一事实来源，与 admin 前端
 * {@code admin_frontend/src/config/settingsRegistry.js} 的分组/字段一一对应。
 *
 * <p>职责：
 * <ul>
 *   <li>key 白名单校验（未知配置项直接拒绝，写入 400）；</li>
 *   <li>决定每个配置项的 {@code config_group}、{@code value_type}、{@code is_public}、
 *       {@code sort_order} 与 {@code description}——这些元数据由服务端控制，
 *       不接受前端提交，避免把私密配置误标为公开；</li>
 *   <li>图片类字段强制只允许 {@code http(s)://} 或 {@code /} 开头的链接，
 *       防止 {@code javascript:} 等协议注入。</li>
 * </ul>
 *
 * <p>公开性约定：basic / identity / appearance / footer / about / seo 与 reading.friendLinks /
 * reading.enableGuestbook / reading.guestbookPageSize / reading.shuoshuoTitle /
 * reading.shuoshuoDescription / reading.shuoshuoEmptyText 会被博客前台消费，标记为公开；
 * integrations（统计、webhook）与 advanced 属于运维私密配置，不进入访客接口。
 */
public final class SiteSettingCatalog {

    /** 单个配置项的服务端元数据。 */
    public record FieldSpec(
            String key,
            String group,
            ValueType valueType,
            boolean publicSetting,
            int sortOrder,
            String description,
            Double min,
            Double max) {

        public boolean isImage() {
            return IMAGE_FIELDS.contains(key);
        }
    }

    private static final Map<String, FieldSpec> SPECS = new LinkedHashMap<>();

    private static final Map<String, FieldSpec> UNMODIFIABLE_SPECS = Collections.unmodifiableMap(SPECS);

    /** 图片/Logo 类字段：值为静态资源链接，只允许 http(s) 或站内绝对路径。 */
    private static final java.util.Set<String> IMAGE_FIELDS = java.util.Set.of(
            "siteLogo", "avatarUrl", "backgroundImage", "ogImage");

    static {
        int order = 0;
        // ── basic：基本设置（公开：站点名/标识/描述与搜索引擎开关是前台第一印象） ──
        put(spec("siteName", "basic", ValueType.STRING, true, ++order, "站点名称", 60d));
        put(spec("siteTagline", "basic", ValueType.STRING, true, ++order, "一句话介绍", 80d));
        put(spec("siteDescription", "basic", ValueType.STRING, true, ++order, "站点描述", 300d));
        put(spec("siteLogo", "basic", ValueType.STRING, true, ++order, "站点 Logo 图片链接", 500d));
        put(spec("siteWordmark", "basic", ValueType.STRING, true, ++order, "站点文字标识（页眉/页脚品牌文字）", 60d));
        put(spec("allowIndexing", "basic", ValueType.BOOLEAN, true, ++order, "是否允许搜索引擎索引（robots.txt 与 noindex 由它驱动）"));

        // ── identity：作者资料（公开：关于页与文章署名使用） ──
        put(spec("authorName", "identity", ValueType.STRING, true, ++order, "作者显示名称", 40d));
        put(spec("authorRole", "identity", ValueType.STRING, true, ++order, "作者身份标签", 60d));
        put(spec("authorTagline", "identity", ValueType.STRING, true, ++order, "作者一句话标语", 80d));
        put(spec("authorIntro", "identity", ValueType.STRING, true, ++order, "作者简介（资料卡短介绍）", 500d));
        put(spec("authorBio", "identity", ValueType.STRING, true, ++order, "作者详细自述", 500d));
        put(spec("avatarUrl", "identity", ValueType.STRING, true, ++order, "作者头像图片链接", 500d));
        put(spec("location", "identity", ValueType.STRING, true, ++order, "所在城市", 40d));
        put(spec("email", "identity", ValueType.STRING, true, ++order, "联系邮箱", 120d));
        put(spec("githubUrl", "identity", ValueType.STRING, true, ++order, "GitHub 主页链接", 200d));
        put(spec("xUrl", "identity", ValueType.STRING, true, ++order, "X / Twitter 主页链接", 200d));
        put(spec("rssPath", "identity", ValueType.STRING, true, ++order, "RSS 订阅地址", 200d));

        // ── appearance：外观与背景（公开：直接驱动博客前台渲染） ──
        put(spec("defaultTheme", "appearance", ValueType.STRING, true, ++order, "默认主题 system/light/dark", 16d));
        put(spec("accentColor", "appearance", ValueType.STRING, true, ++order, "品牌强调色（十六进制）", 9d));
        put(spec("showHeroBackdrop", "appearance", ValueType.BOOLEAN, true, ++order, "首页 Hero 是否展示背景图"));
        put(spec("showReadingTime", "appearance", ValueType.BOOLEAN, true, ++order, "是否显示阅读时长"));
        put(spec("articlesPerPage", "appearance", ValueType.NUMBER, true, ++order, "文章列表每页数量", 5d, 50d));
        put(spec("showToc", "appearance", ValueType.BOOLEAN, true, ++order, "是否显示文章目录"));
        put(spec("backgroundImage", "appearance", ValueType.STRING, true, ++order,
                "博客前台全站背景图链接（静态资源地址）", 500d));
        put(spec("backgroundOverlay", "appearance", ValueType.NUMBER, true, ++order,
                "背景图遮罩强度（0-100，数值越大文字底越实）", 0d, 100d));

        // ── reading：阅读体验 ──
        put(spec("friendLinks", "reading", ValueType.JSON, true, ++order, "友情链接 [{name,url}] 列表"));
        put(spec("enableGuestbook", "reading", ValueType.BOOLEAN, true, ++order, "是否展示留言板入口"));
        put(spec("guestbookPageSize", "reading", ValueType.NUMBER, true, ++order, "留言板每页数量", 1d, 50d));

        // ── reading：说说页文案（公开：说说页标题、描述与空态提示由前台消费） ──
        put(spec("shuoshuoTitle", "reading", ValueType.STRING, true, ++order, "说说页标题", 60d));
        put(spec("shuoshuoDescription", "reading", ValueType.STRING, true, ++order, "说说页描述", 300d));
        put(spec("shuoshuoEmptyText", "reading", ValueType.STRING, true, ++order, "说说空状态提示文案", 200d));

        // ── footer：页脚配置（公开：博客页脚版权/备案/同步时间展示） ──
        put(spec("footerStartYear", "footer", ValueType.NUMBER, true, ++order, "页脚版权起始年份", 1900d, 2100d));
        put(spec("footerNote", "footer", ValueType.STRING, true, ++order, "页脚备注文案", 100d));
        put(spec("icp", "footer", ValueType.STRING, true, ++order, "ICP 备案号，留空则页脚隐藏备案栏", 60d));
        put(spec("icpUrl", "footer", ValueType.STRING, true, ++order, "ICP 备案查询链接", 200d));
        put(spec("footerSyncedAt", "footer", ValueType.STRING, true, ++order, "页脚『最近同步』回退时间文案", 40d));

        // ── about：关于页（公开：关于页正文，构建期编译为静态 HTML） ──
        put(spec("aboutContent", "about", ValueType.STRING, true, ++order,
                "关于页 Markdown 正文，留空时前台回退本地 about.md", 20000d));

        // ── seo：SEO 与分享（公开：构建期生成 head/sitemap/RSS 时消费） ──
        put(spec("titleTemplate", "seo", ValueType.STRING, true, ++order, "页面标题模板，:title 为占位符", 100d));
        put(spec("metaDescription", "seo", ValueType.STRING, true, ++order, "默认 SEO 描述", 200d));
        put(spec("keywords", "seo", ValueType.STRING, true, ++order, "站点关键词（英文逗号分隔）", 200d));
        put(spec("ogImage", "seo", ValueType.STRING, true, ++order, "默认分享卡片图链接", 500d));
        put(spec("enableSitemap", "seo", ValueType.BOOLEAN, true, ++order, "构建时是否生成 sitemap"));

        // ── integrations：第三方集成（私密：不暴露给访客） ──
        put(spec("analyticsProvider", "integrations", ValueType.STRING, false, ++order, "访问统计提供方 none/umami/plausible/custom", 16d));
        put(spec("analyticsSiteId", "integrations", ValueType.STRING, false, ++order, "统计站点 ID", 100d));
        put(spec("analyticsScriptUrl", "integrations", ValueType.STRING, false, ++order, "自定义统计脚本地址", 300d));
        put(spec("webhookUrl", "integrations", ValueType.STRING, false, ++order, "发布通知 Webhook 地址", 300d));
        put(spec("webhookEnabled", "integrations", ValueType.BOOLEAN, false, ++order, "是否启用发布通知 Webhook"));
        put(spec("webhookSecret", "integrations", ValueType.STRING, false, ++order, "Webhook 鉴权密钥（Bearer Token）", 300d));
        put(spec("webhookEventType", "integrations", ValueType.STRING, false, ++order, "Webhook 事件类型，默认 blog-content-updated", 64d));
        put(spec("webhookDebounceSeconds", "integrations", ValueType.NUMBER, false, ++order, "Webhook 防抖窗口（秒）", 0d, 600d));
        put(spec("webhookMaxRetries", "integrations", ValueType.NUMBER, false, ++order, "Webhook 失败重试次数", 0d, 10d));

        // ── advanced：高级（私密：站点运维开关） ──
        put(spec("siteStatus", "advanced", ValueType.STRING, false, ++order, "站点状态 online/maintenance", 16d));
        put(spec("timezone", "advanced", ValueType.STRING, false, ++order, "站点时区", 64d));
        put(spec("showDebugInfo", "advanced", ValueType.BOOLEAN, false, ++order, "页脚是否展示构建信息"));
    }

    private SiteSettingCatalog() {
    }

    private static FieldSpec spec(String key, String group, ValueType valueType, boolean publicSetting,
                                  int sortOrder, String description, Double min, Double max) {
        return new FieldSpec(key, group, valueType, publicSetting, sortOrder, description, min, max);
    }

    private static FieldSpec spec(String key, String group, ValueType valueType, boolean publicSetting,
                                  int sortOrder, String description, Double limit) {
        return spec(key, group, valueType, publicSetting, sortOrder, description, null, limit);
    }

    private static FieldSpec spec(String key, String group, ValueType valueType, boolean publicSetting,
                                  int sortOrder, String description) {
        return spec(key, group, valueType, publicSetting, sortOrder, description, null, null);
    }

    private static void put(FieldSpec fieldSpec) {
        SPECS.put(fieldSpec.key(), fieldSpec);
    }

    public static FieldSpec get(String key) {
        return SPECS.get(key);
    }

    public static boolean contains(String key) {
        return SPECS.containsKey(key);
    }

    public static Map<String, FieldSpec> all() {
        return UNMODIFIABLE_SPECS;
    }
}

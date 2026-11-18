/**
 * Local defaults for site identity and editorial configuration.
 * `scripts/sync-settings.mjs` runs before every build: it fetches the public
 * site settings from the backend (`GET /api/visitors/settings`, managed in the
 * admin panel) and writes the merged result to `.vitepress/data/site-settings.json`,
 * which `config.mjs` consumes. Values configured in the admin panel override
 * the defaults below; anything missing or blank keeps these local values.
 */
export const siteConfig = {
  name: 'HrsBlog',
  wordmark: 'HrsBlog',
  tagline: '保持好奇，持续记录。',
  description: '一个关于代码、设计与日常观察的个人数字花园。记录正在学习的事，也保存值得回看的过程。',
  allowIndexing: true,

  author: {
    name: 'Hrs',
    role: '开发者 / 长期主义者',
    tagline: '在代码与山水之间，记录正在发生的事。',
    intro: '这里存放长一点的想法：工程、设计，以及生活里值得回看的瞬间。偶尔也写一些不合时宜的观察。',
    bio: '我在代码与文字之间来回，喜欢把复杂问题拆成小块，也喜欢在清晨写一点东西。这个博客不追热点，只希望每次打开都像走进一间安静的屋子。',
    avatar: '/avatar.svg',
    location: '杭州',
  },

  stack: ['Vue 3', 'VitePress', 'Spring Boot', 'MySQL', 'Docker', 'Figma'],

  social: [
    { label: 'GitHub', href: 'https://github.com/27hrs', icon: 'github' },
    { label: '邮箱', href: 'mailto:hello@example.com', icon: 'mail' },
    { label: 'RSS', href: '/feed.xml', icon: 'rss' },
  ],

  nav: [
    { label: '首页', link: '/' },
    { label: '文章', link: '/articles/' },
    { label: '友链', link: '/links/' },
    { label: '说说', link: '/shuoshuo/' },
    { label: '关于', link: '/about' },
  ],

  /**
   * 说说页配置。
   *
   * 三项文案都能在后台「站点设置」里覆盖（`shuoshuoTitle` / `shuoshuoDescription` /
   * `shuoshuoEmptyText`），构建期读不到时保留这里的本地默认值。
   * 说说正文由 `scripts/sync-shuoshuo.mjs` 在构建期拉取并编译成静态 HTML。
   */
  shuoshuo: {
    title: '说说',
    description: '一些零碎的近况与随手记，不成篇，只记录。',
    emptyText: '还没有留下说说，等第一条出现。',
  },

  /**
   * 留言板配置。
   *
   * `enabled` 默认关闭：构建期读不到后台的 `enableGuestbook` 时保守回退为
   * 「不展示留言入口」（访客直接访问 /guestbook/ 也只会看到未开放提示）。
   * 后台打开开关并重新构建站点后，这里会被覆盖为 true，导航里也会多出「留言」入口。
   */
  guestbook: {
    enabled: false,
    pageSize: 10,
    title: '留言',
    lead: '路过就留一句话。',
  },

  friendLinks: [
    { name: 'VitePress', url: 'https://vitepress.dev', description: '静态站点生成器' },
    { name: 'Vue.js', url: 'https://cn.vuejs.org', description: '渐进式 JavaScript 框架' },
    { name: 'Spring', url: 'https://spring.io', description: 'Java 应用框架' },
  ],

  // Used by the sidebar. If empty, the latest three articles are shown.
  featuredSlugs: [
    'designing-errors-in-spring-boot',
    'a-color-system-that-breathes',
    'writing-by-the-lake',
  ],

  footer: {
    startYear: 2024,
    note: '保持好奇，持续记录。',
    icp: '京ICP备XXXXXXXX号',
    icpUrl: 'https://beian.miit.gov.cn/',
    syncedAt: '2026.11.15',
  },

  homeLimit: 6,
}

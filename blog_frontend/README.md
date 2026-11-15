# HrsBlog · 博客前台

VitePress 1.6.4 + 完全自定义主题的静态博客。内容来自 HrsBlog 的访客 API，在**构建期**同步为 Markdown，再生成纯静态页面；运行期不需要数据库或 Node 服务。

![首页](<../docs/screenshots/home-desktop-light.jpg>)

## 页面

| 路由 | 说明 |
|---|---|
| `/` | 首页：半透明 Hero、最新 6 篇、封面、页脚 |
| `/articles/` | 文章归档：分类/标签筛选、搜索侧栏、热门、友链 |
| `/articles/{slug}` | 静态文章页：封面、正文、右侧目录、阅读进度、上下篇、代码复制 |
| `/about` | 关于：资料卡、Markdown 正文、技术栈、联系方式 |
| `/404.html` | 自定义“这一页漂走了” |

截图在 [`docs/screenshots`](../docs/screenshots)：[归档](<../docs/screenshots/archive-desktop.jpg>)、[文章](<../docs/screenshots/article-desktop.jpg>)、[关于](<../docs/screenshots/about-desktop.jpg>)、[移动端](<../docs/screenshots/archive-mobile.jpg>)、[深色文章](<../docs/screenshots/article-dark.jpg>)。

## 运行

```bash
pnpm dev          # 同步内容 + vitepress dev，端口 27080
pnpm dev:offline  # 使用 fixtures，不请求 API
pnpm seed         # 只用 fixtures 生成 6 篇演示文章
pnpm sync          # 从 API 同步文章、封面、feed、robots（需要 BLOG_API_BASE）
pnpm sync:settings # 只同步后台站点设置
pnpm clean        # 清空 .vitepress/dist
pnpm build        # sync settings + sync（配置了 API 时）+ clean + build + verify
pnpm preview      # 预览构建产物，端口 27080
pnpm verify       # 校验静态产物、sitemap、feed、陈旧页面
```

### 本地环境

```bash
cp .env.example .env.local
```

`.env.local` 已被 Git 忽略。常用变量：

| 变量 | 说明 |
|---|---|
| `BLOG_API_BASE` | 构建期 API 根地址，例如 `http://localhost:27000/api`；留空则使用已有内容/fixtures，不请求网络 |
| `BLOG_SITE_URL` | canonical / sitemap / RSS 使用的公开地址，例如 `https://blog.example.com` |
| `BLOG_ASSET_BASE` | 封面 CDN 前缀；留空使用站内路径 |
| `BLOG_SYNC_STRICT` | `1` 时 API 失败直接构建失败；`0` 时复用缓存或 fixtures |
| `BLOG_CONTENT_LIMIT` | 调试用，限制同步篇数 |
| `BLOG_SYNC_TIMEOUT` | 单次 API 请求超时（毫秒） |

> 生产构建务必设置 `BLOG_API_BASE` 和 `BLOG_SITE_URL`，否则会使用 fixtures、canonical 会指向 localhost。

## 内容管线

```text
GET /api/visitors/articles?page=0&size=50
        │
        ▼
api/articles.js        分页、校验、并发拉详情
        │
        ▼
scripts/sync-content.mjs
        │
        ├─ articles/{slug}.md        正文 + frontmatter（gitignore）
        ├─ .vitepress/data/articles.json   列表/标签/分类元数据（gitignore）
        ├─ public/feed.xml           RSS（gitignore）
        ├─ public/robots.txt         robots（gitignore）
        └─ public/assets/image/**    缓存的封面（gitignore）
        │
        ▼
vitepress build → .vitepress/dist（静态 HTML）
```

- API 失败时：有缓存则复用；无缓存则回退 `scripts/fixtures`；`BLOG_SYNC_STRICT=1` 时直接失败。
- 已发布 → 草稿：同步会删除不再存在的生成页，避免旧文章残留。
- 构建前会执行 `scripts/clean-dist.mjs`，防止上一轮的旧静态页混入 `sitemap`。
- `markdown.headers` 开启，文章 TOC 直接使用 VitePress 的 `page.headers`（含 H2/H3 嵌套）。

## API 封装

`api/` 是浏览器与 Node 都能用的 fetch 层，客户端不依赖第三方库：

| 文件 | 职责 |
|---|---|
| `api/client.js` | `{code,message,data}` 解包、`ApiError`、超时、429/5xx 重试、GET/JSON |
| `api/articles.js` | 列表分页、详情、`listAllArticles`、`getArticleDetails`、slug 校验 |
| `api/settings.js` | 访客站点设置（`GET /visitors/settings`）读取与响应形状校验 |
| `api/assets.js` | 封面 URL 安全校验、绝对地址解析 |
| `api/config.js` | `BLOG_API_BASE` / `BLOG_SITE_URL` / 严格模式等配置解析 |
| `api/index.js` | 统一出口 |

访客接口只读，不需要 token。封面若为 `/assets/image/**`，同步时会下载到 `public/assets/image/**`，让静态站不依赖运行期后端；nginx 也会在文件缺失时回源。

## 关键文件

| 内容 | 文件 |
|---|---|
| 站点身份、社交、友链、精选（本地默认值） | `.vitepress/site.config.mjs` |
| 设置合并（接口值覆盖默认值） | `scripts/sync-settings.mjs`、`scripts/lib/site-settings.mjs` |
| 导航、markdown、sitemap、head | `.vitepress/config.mjs` |
| SEO（canonical/OG/JSON-LD） | `.vitepress/seo.mjs` |
| 首页 | `.vitepress/theme/views/HomeView.vue` |
| 文章归档 | `ArticleListView.vue`、`ArchiveFilters.vue`、`ArchiveSidebar.vue` |
| 文章详情 | `ArticleView.vue`、`ArticleToc.vue`、`ReadingProgress.vue` |
| 关于 | `AboutView.vue`、`about.md` |
| 卡片/画框/壳层 | `components/`、`Layout.vue` |
| 设计 token / 正文排版 | `styles/tokens.css`、`styles/prose.css` |
| 演示内容 | `scripts/fixtures/articles.mjs` |
| API | `api/` |
| 同步/清理/校验 | `scripts/sync-content.mjs`、`clean-dist.mjs`、`verify-build.mjs` |

## 设计

- **概念**：雾湖手记。固定的湖景照片是背景，内容放在半透明纸面上；
- **画框**：1px 细线、四角铜色角标、压在边线上的编号标签；
- **卡片**：桌面右侧 3:2 封面（归档页用紧凑 190px 版），手机 2.4:1 通栏；
- **字体**：Georgia / 系统宋体做标题，系统无衬线正文，等宽字体做元信息；
- **颜色**：松绿、纸白、墨色，铜色只给交互与当前状态；
- **响应式**：1440 / 1200 / 1024 / 768 / 390 / 320 都检查过，无横向滚动。

## 部署

`Dockerfile` 会执行 `pnpm build`，产物从 `.vitepress/dist` 复制到 nginx：

```bash
docker build \
  --build-arg BLOG_API_BASE=https://api.example.com/api \
  --build-arg BLOG_SITE_URL=https://blog.example.com \
  --build-arg BLOG_SYNC_STRICT=1 \
  -t hrsblog-blog .
```

`nginx.conf` 已适配 SSG：

- clean URL `try_files $uri $uri.html $uri/ =404`；
- 真实 404；
- `/assets/` 长缓存、HTML 协商缓存；
- `/feed.xml` 使用 `application/rss+xml`；
- `/assets/image/**` 优先静态文件，缺失时回源后端；
- 预留 `/api/` 反向代理。

未传构建参数时使用 fixtures，便于本地 `docker compose build` 直接看到完整站点。

## 当前演示数据

仓库最终截图和当前构建产物使用 `scripts/fixtures/articles.mjs` 的 6 篇演示文章，保证视觉与截图稳定。

访客 API 已经实测可用：当前数据库返回 7 篇已发布文章，其中包含一篇后台联调用的测试文（无封面）。正式上线前请在后台清理这篇测试文，然后：

```bash
pnpm sync
pnpm build
```

即可切换到真实内容。`featuredSlugs` 指向演示 slug；若真实 slug 不同，侧栏热门会自动回退到最新 3 篇。

## 尚未包含

- 评论、点赞、阅读量（后端暂无接口）；
- 站内全文搜索；
- 后台站点设置的公开读取接口（当前站点资料在 `site.config.mjs`）；
- 发布后自动重建（可接 webhook 或定时任务）。

完整设计/实施计划见 [`docs/blog-frontend-plan.md`](../docs/blog-frontend-plan.md)。

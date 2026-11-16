# HrsBlog Admin Frontend

HrsBlog 的 Vue 3 管理后台，覆盖登录、管理主页、文章投放、文章管理与站点设置五个页面。整体遵循「工具型 / 中性专业」设计：三层 CSS Token、`data-theme` 三档主题、单一低饱和蓝主色、6–8px 圆角与克制的阴影层级。

## 快速开始

```bash
pnpm install
pnpm dev
```

默认地址：<http://localhost:27081>

开发服务器会把 `/api` 和 `/assets/image` 代理到 `http://localhost:27000`。登录使用后端 bootstrap 账号（见 `backend/src/main/resources/application.yaml` 或启动环境变量）。

## 常用脚本

```bash
pnpm dev       # Vite 开发服务器，端口 27081（strictPort）
pnpm build     # 生产构建，产物在 dist/
pnpm preview   # 预览构建产物
pnpm lint      # oxlint + eslint
pnpm format    # Prettier
```

## 环境变量

复制 `.env.example` 为 `.env.local`（可选）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api` | 浏览器请求前缀，生产默认由 nginx 同源反代 |
| `VITE_DEV_BACKEND_TARGET` | `http://localhost:27000` | 仅开发环境 Vite 代理目标 |
| `VITE_BLOG_URL` | 当前主机的 `:27080` | 「访问博客」链接目标 |
| `VITE_DASHBOARD_PREVIEW` | 开发环境自动开启 | 缺失统计接口时是否展示带标识的预览趋势图 |

生产镜像通过 `nginx.conf` 把 `/api/` 和 `/assets/image/` 反代到 `backend:27000`。普通的构建产物位于 `/assets/*`，不会与后台上传图片（`/assets/image/*`）混在一起。

## 页面与路由

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | 登录 | 居中卡片、统一 401 文案、Token 持久化 |
| `/dashboard` | 管理主页 | 统计卡 + Unovis 趋势图 + 最近评论/文章 + 待办 |
| `/articles` | 文章管理 | 搜索、状态/分类/标签筛选、批量操作、分页 |
| `/articles/new` | 文章投放 | md-editor-v3 编辑区 + 右侧设置面板 |
| `/settings` | 站点设置 | 注册表驱动的左侧分类导航 + SchemaForm |

所有业务路由懒加载。路由守卫校验本地会话（token + 过期时间），任意管理 API 返回 401 时由统一客户端清理会话并回到登录页。

## 架构

```text
router + guard
  └── pages (views/)
        ├── business components (components/)
        ├── headless interaction (Reka UI)
        └── Pinia stores (auth / theme / settings / articleDraft / toast)
              └── api client (fetch + token + 401 + error toast)
```

- **API 客户端**：`src/api/client.js` 统一处理 `{code,message,data}` 信封、Bearer token、超时、字段级错误和 401 登出。
- **主题**：`src/styles/theme.css` 是唯一的设计变量来源，分三层：
  1. 原始值：色板、字号、间距、圆角、动效；
  2. 语义值：`--bg-*` / `--text-*` / `--border-*` / 状态色；
  3. 组件值：`--header-height`、`--table-row-hover`、`--focus-ring` 等。
  
  主题模式存于 `localStorage` 的 `hrsblog-theme`（`system | light | dark`），`data-theme` 始终是解析后的 `light/dark`；`data-theme-mode` 保留用户选择。
- **图表**：Unovis `VisXYContainer` + `VisArea` / `VisLine`；颜色在 JS 中按主题切换，网格和轴文字用 CSS 变量。
- **编辑器**：md-editor-v3，表单状态集中在 `articleDraft` store；右侧设置面板与主编辑区共享同一份 draft。

## 站点设置：注册表扩展

新增一个设置分类只需要在 `src/config/settingsRegistry.js` 追加 section，无需改页面。

```js
{
  key: 'analytics',
  label: '访问统计',
  icon: 'trending-up',
  description: '接入统计服务。',
  fields: [
    { key: 'provider', label: '服务商', type: 'select', default: 'none', options: [...] },
    { key: 'siteId', label: '站点 ID', type: 'input', width: 'half' },
    { key: 'enabled', label: '启用', type: 'switch', default: false },
  ],
}
```

支持的字段类型：`input`、`textarea`、`number`、`select`、`switch`、`image`、`color`、`list`。字段支持 `rules`（required/max/min/url/email）、`visibleWhen`、`width` 等声明。

设置页会先尝试 `GET /api/admin/settings`，失败时回退到 `localStorage`；保存时优先调用 `PUT /api/admin/settings`，接口不存在则安全保存到本机并明确提示。后台当前没有这个接口，因此页面会展示「本机保存」状态。

## 与当前后端的接口差异

后端目前只有以下管理接口可用：

- `POST /api/admin/auth/login`
- `PUT /api/admin/auth/password`
- `POST /api/admin/articles`、`GET /api/admin/articles`、`GET /api/admin/articles/{id}`、`PUT /api/admin/articles/{id}`、`DELETE /api/admin/articles/{id}`（列表 / 详情 / 更新 / 软删除均已提供）
- `GET /api/admin/assets`、`POST /api/admin/assets`、`DELETE /api/admin/assets/{id}`

因此前端做了明确、可移除的降级：

| 能力 | 当前行为 |
| --- | --- |
| 管理文章列表 | `GET /api/admin/articles`（草稿 + 已发布，串行翻页拉全量）；接口不可用时回退为「公开文章接口 + 本机草稿缓存」 |
| 草稿 | 通过现有 `POST /api/admin/articles` 新建成功，并把详情缓存到本机以支持列表展示 |
| 文章更新/删除 | `PUT` / `DELETE /api/admin/articles/{id}`：列表提供编辑与删除入口，删除有二次确认弹窗与加载态，成功后自动刷新列表（末页删空自动回退页码），失败按接口返回的 message 展示；接口不可用时回退为复用本机内容新建副本 |
| 分类/置顶/推荐/可见性/定时 | 本机 metadata，会显示「本地」标记；不伪造后端持久化 |
| 统计/评论/访客 | 后端无接口；文章数为真实接口数据，评论/访客显示「—」，趋势图在开发环境使用明确标注的预览数据 |
| 站点设置 | 先探测规划接口，不可用时保存本机并明确提示 |

一旦后端补齐对应接口，API 层和页面会优先使用远端能力，无需重写布局。

## 设计备注

- 登录、主页、编辑、列表、设置均实现桌面与窄屏布局；侧边栏在 1024px 以下切换为抽屉。
- `prefers-reduced-motion` 下关闭所有非必要动效。
- 关键交互（按钮、输入、选择器、对话框、开关）均有 `:focus-visible` 焦点状态。
- 上传封面走现有 `POST /api/admin/assets`，单文件上限 5MB，支持 JPG/PNG/WebP/GIF。
- 图标全部为内联 SVG，不依赖额外图标字体。

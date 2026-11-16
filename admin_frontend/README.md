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
| `/articles` | 文章管理 | 搜索、状态/分类/标签筛选、批量删除、分页 |
| `/articles/new` | 文章投放 | md-editor-v3 编辑区 + 右侧设置面板 |
| `/settings` | 站点设置 | 账号安全（用户名 / 密码）+ 注册表驱动的左侧分类导航 + SchemaForm |

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
- `PUT /api/admin/auth/username`
- `POST /api/admin/articles`、`GET /api/admin/articles`、`GET /api/admin/articles/{id}`、`PUT /api/admin/articles/{id}`、`DELETE /api/admin/articles/{id}`（列表 / 详情 / 更新 / 软删除均已提供）
- `POST /api/admin/articles/batch-delete`（批量软删除，逐条返回成功 / 失败明细）
- `GET /api/admin/assets`、`POST /api/admin/assets`、`DELETE /api/admin/assets/{id}`

因此前端做了明确、可移除的降级：

| 能力 | 当前行为 |
| --- | --- |
| 管理文章列表 | `GET /api/admin/articles`（草稿 + 已发布，串行翻页拉全量）；接口不可用时回退为「公开文章接口 + 本机草稿缓存」 |
| 草稿 | 通过现有 `POST /api/admin/articles` 新建成功，并把详情缓存到本机以支持列表展示 |
| 文章更新/删除 | `PUT` / `DELETE /api/admin/articles/{id}`：列表提供编辑与删除入口，删除有二次确认弹窗与加载态，成功后自动刷新列表（末页删空自动回退页码），失败按接口返回的 message 展示；接口不可用时回退为复用本机内容新建副本 |
| 批量删除 | `POST /api/admin/articles/batch-delete`：一次最多 200 条，逐条返回结果，部分失败不影响已成功的条目 |
| 账号安全 | `PUT /api/admin/auth/username` 与 `PUT /api/admin/auth/password`：成功后由后端换发新令牌，前端同步更新本地会话 |
| 分类/置顶/推荐/可见性/定时 | 本机 metadata，会显示「本地」标记；不伪造后端持久化 |
| 统计/评论/访客 | 后端无接口；文章数为真实接口数据，评论/访客显示「—」，趋势图在开发环境使用明确标注的预览数据 |
| 站点设置 | 先探测规划接口，不可用时保存本机并明确提示 |

一旦后端补齐对应接口，API 层和页面会优先使用远端能力，无需重写布局。

## 批量删除文章

`POST /api/admin/articles/batch-delete`

| 项目 | 说明 |
| --- | --- |
| 请求体 | `{ "ids": [12, 34] }`，`ids` 必填，单次最多 200 条（重复 id 自动去重） |
| 成功响应 | `data` = `{ totalCount, successCount, failed }` |
| `totalCount` | 去重后实际处理的条目数 |
| `successCount` | 成功软删除的条目数 |
| `failed` | 失败明细数组，每项为 `{ id, code, message }`；成功时为空数组 |
| 鉴权失败 | HTTP 401，`code` = 40101 |
| 参数非法 | HTTP 400，`code` = 40000，`data.ids` 给出字段级原因（空数组 / 缺失 / 超过 200 条） |

语义要点：

- 软删除复用单篇删除逻辑——写入 `deleted_at` 并把 slug 改写为 `{slug}-deleted-{id}` 释放唯一键；删除后前台与后台列表、详情均不可见。
- **部分失败不影响成功项**：不存在的 id 记入 `failed` 并继续处理其余条目，不会回滚已成功的删除。
- 选 POST 而非带 body 的 DELETE，避免 fetch / 代理对 DELETE body 的兼容问题。

前端交互（`ArticleListView.vue`）：

| 场景 | 行为 |
| --- | --- |
| 勾选含服务端文章 | 工具条「批量删除」可用，确认弹窗展示所选数量与服务端 / 本地草稿的拆分 |
| 全部成功 | `toast.success('已删除 N 篇文章')`，刷新列表并在当前页超界时回退末页 |
| 部分失败 | `toast.warning('已删除 N 篇，M 篇失败：#id（原因）')`，失败项带 id 与原因 |
| 全部失败 | `toast.error(...)` 并列出失败明细 |
| 仅勾选本地草稿 | 不调用后端，仍走本机记录移除（与原有行为一致） |

## 账号安全（用户名 / 密码）

设置页顶部新增「账号安全」卡片，两个表单各自独立校验与提交。

`PUT /api/admin/auth/username`

| 项目 | 说明 |
| --- | --- |
| 请求体 | `{ "newUsername": "new-name" }`，3-32 位，仅允许字母、数字、下划线与中划线 |
| 成功响应 | `data` = `{ accessToken, tokenType, expiresIn, user }`；`user` 含最新的 `username` |
| 格式非法 | HTTP 400，`code` = 40000，`data.newUsername` 给出字段级原因 |
| 用户名冲突 | HTTP 409，`code` = 40902（`USERNAME_CONFLICT`），`message` = 「用户名已存在」 |

`PUT /api/admin/auth/password`（沿用既有接口）

| 项目 | 说明 |
| --- | --- |
| 请求体 | `{ "oldPassword": "...", "newPassword": "..." }` |
| 成功响应 | `data` = `{ accessToken, tokenType, expiresIn }`（不含 `user`） |
| 原密码错误 | HTTP 400，`code` = 40002（`UNAUTHORIZED_PASSWORD`），`message` = 「原密码错误」 |
| 新密码不合规 | HTTP 400，`code` = 40000，`data.newPassword` 给出字段级原因 |

会话同步要点：

- JWT 的 subject 就是用户名，因此**改完用户名必须换发新令牌**。两个接口的响应都带 `accessToken`，前端通过 `stores/auth.js` 的 `applySession()` 覆盖本地 token 与用户信息，侧边栏 / 顶栏显示的用户名随之更新。
- 前端手写校验：原密码必填、新密码 ≥ 8 位且同时包含字母与数字、两次密码一致、用户名需匹配 `^[a-zA-Z0-9_-]{3,32}$` 且不能与当前用户名相同。
- 提交失败时按 `fieldErrors` 或信封 `message` 行内提示，**不会清空「原密码」输入框**，方便直接重试；密码修改成功后才清空三个字段。

## 设计备注

- 登录、主页、编辑、列表、设置均实现桌面与窄屏布局；侧边栏在 1024px 以下切换为抽屉。
- `prefers-reduced-motion` 下关闭所有非必要动效。
- 关键交互（按钮、输入、选择器、对话框、开关）均有 `:focus-visible` 焦点状态。
- 上传封面走现有 `POST /api/admin/assets`，单文件上限 5MB，支持 JPG/PNG/WebP/GIF。
- 图标全部为内联 SVG，不依赖额外图标字体。

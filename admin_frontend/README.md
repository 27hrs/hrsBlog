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
| `/shuoshuo` | 说说管理 | 可见性筛选、分页列表、发布/撤回/删除、md-editor-v3 编辑弹窗 |
| `/settings` | 站点设置 | 账号安全（用户名 / 密码）+ 注册表驱动的左侧分类导航 + SchemaForm |
| `/logs` | 系统日志 | 通用系统日志列表：type / status 筛选 + 服务端分页 |

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

> **键名必须与后端 `SiteSettingCatalog` 完全一致。** 后端保存走「整体替换」语义：
> 本次没提交的旧键会被直接删除（`SiteSettingService.replaceAll`），而未知键会返回
> 400「未知的配置项」。新增一个设置项要同时改 `settingsRegistry.js` 与
> `backend/.../service/SiteSettingCatalog.java`（键名、分组、类型、长度上限），
> 别只改一边。

「阅读体验」分组里的 `shuoshuoTitle` / `shuoshuoDescription` / `shuoshuoEmptyText`
是说说页的三项公开文案：改动后随站点配置进入博客前台的 `/shuoshuo/` 构建产物，
留空则前台回退本地默认值。

设置页会先尝试 `GET /api/admin/settings`，失败时回退到 `localStorage`；保存时优先调用 `PUT /api/admin/settings`，接口不存在则安全保存到本机并明确提示。后台当前没有这个接口，因此页面会展示「本机保存」状态。

## 与当前后端的接口差异

后端目前只有以下管理接口可用：

- `POST /api/admin/auth/login`
- `PUT /api/admin/auth/password`
- `PUT /api/admin/auth/username`
- `POST /api/admin/articles`、`GET /api/admin/articles`、`GET /api/admin/articles/{id}`、`PUT /api/admin/articles/{id}`、`DELETE /api/admin/articles/{id}`（列表 / 详情 / 更新 / 软删除均已提供）
- `POST /api/admin/articles/batch-delete`（批量软删除，逐条返回成功 / 失败明细）
- `GET /api/admin/assets`、`POST /api/admin/assets`、`DELETE /api/admin/assets/{id}`
- `GET /api/admin/logs`（通用系统日志分页查询，支持 `type` / `status` 筛选）
- `GET/POST /api/admin/shuoshuo`、`GET/PUT/DELETE /api/admin/shuoshuo/{id}`、`POST /api/admin/shuoshuo/{id}/publish|unpublish`（说说管理，见下节）

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

## 说说管理

管理后台「内容 → 说说管理」（`/shuoshuo`）用于撰写与发布说说，发布后进入博客前台
`/shuoshuo/` 的时间线页。

### 接口

`src/api/shuoshuo.js` 封装全部管理端接口，统一 `silent: true`（提示由页面负责，避免双弹）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/shuoshuo?visibility=&page=&size=` | 分页列表；`visibility` 可选 `PUBLIC` / `PRIVATE`，空串=不过滤；`page` 从 0 起，`size` 1–50 |
| GET | `/api/admin/shuoshuo/{id}` | 详情 |
| POST | `/api/admin/shuoshuo` | 新建，后端固定落成 `PRIVATE` 草稿 |
| PUT | `/api/admin/shuoshuo/{id}` | 更新正文（只覆盖 content） |
| POST | `/api/admin/shuoshuo/{id}/publish` | 发布：`published_at` 为空时后端回填当前时间 |
| POST | `/api/admin/shuoshuo/{id}/unpublish` | 撤回：置回 `PRIVATE`，**保留** `published_at` |
| DELETE | `/api/admin/shuoshuo/{id}` | 硬删除，响应 message 为「说说已删除」 |

### 页面交互

| 场景 | 行为 |
| --- | --- |
| 可见性筛选 | 全部说说 / 草稿 / 已发布；切换时页码重置为 1 |
| 行内操作 | 编辑、发布（仅草稿）、撤回（仅已发布）、删除；发布/撤回期间只禁用当前行 |
| 新建 / 编辑 | `AppModal` + md-editor-v3（实时预览、工具栏含预览与全屏）+ 字数计数；底部「保存 / 保存并发布」 |
| 校验 | 正文必填且 ≤ 2000 字符（`SHUOSHUO_CONTENT_LIMIT`，与后端 DTO 一致）；后端 400 的 `fieldErrors.content` 回填到同一个错误对象 |
| 删除确认 | 独立 `AppModal` 二次确认，不用 `window.confirm`；成功后 `toast.success('说说已删除')` 并刷新列表（末页删空自动回退页码） |
| 空态 | `AppEmptyState` 文案随筛选变化（还没有说说 / 没有草稿说说 / 还没有已发布的说说） |

正文以 **Markdown 原文**保存（前端不渲染 HTML）；博客前台在**构建期**用 markdown-it
（`html: false`）编译成静态 HTML。

## 部署通知与系统日志

后台「站点设置 → 集成」分组里的 `integrations` 段落承载「内容变更 → Webhook → CI 重建」链路的配置，
保存后由后端在事务提交后异步投递；投递的每一次尝试都会写进通用日志表，可在「系统日志」页查看。

### 集成分组新增字段

| Key | 标签 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `webhookUrl` | 发布通知 Webhook | input | 空 | GitHub Dispatches 地址或任意通用 Webhook |
| `webhookEnabled` | 启用通知 | switch | `false` | 内容变更后触发一次通知 |
| `webhookSecret` | 鉴权密钥 | input | 空 | 作为 `Authorization: Bearer <密钥>` 发送；留空则不带鉴权头 |
| `webhookEventType` | 事件类型 | input | `blog-content-updated` | 需与 Actions 的 `repository_dispatch.types` 一致 |
| `webhookDebounceSeconds` | 防抖窗口（秒） | number | `10` | 窗口内多次变更合并为一次 |
| `webhookMaxRetries` | 失败重试次数 | number | `2` | 不含首次尝试 |

后端只在配置**实际发生变化**、文章**被发布 / 已发布文章被更新**、
或说说**被发布 / 撤回 / 删除 / 已公开说说被更新**时才发通知；
草稿编辑、保存相同配置都不触发。

### `GET /api/admin/logs`

| 项目 | 说明 |
| --- | --- |
| 入参 | `page`（从 0 开始）、`size`（≤ 100）、`type`（如 `DEPLOY_NOTIFY`）、`status`（`SUCCESS` / `FAILED`） |
| 响应 | `data` = `{ items, page, size, totalElements, totalPages }`；每项含 `type` / `scene` / `status` / `attempt` / `message` / `detail` / `createdAt` |
| 鉴权失败 | HTTP 401，`code` = 40101 |

前端实现：`src/api/logs.js`（请求封装）+ `src/views/LogsView.vue`（表格 + 筛选 + 分页）。
日志详情已脱敏，只包含目标主机路径、状态码与响应片段，鉴权头与密钥不会出现在页面上。

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
- 文章编辑与说说编辑的图片上传共用 `src/services/editorImages.js`：md-editor-v3 的 `onUploadImg`
  接管工具栏「上传图片 / 裁剪上传」、拖入与粘贴图片，逐张上传后把返回的 `/assets/image/**`
  地址以 `![alt](url)` 插入正文；图片与正文一起以 Markdown 原文保存，前台按 Markdown 渲染。
- 图标全部为内联 SVG，不依赖额外图标字体。

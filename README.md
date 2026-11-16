# HrsBlog

一个自托管的个人博客系统：**Spring Boot 后端 + Vue 3 管理后台 + VitePress 博客前台**，
外加一条「内容变更 → Webhook 通知 → GitHub Actions 自动重建前台」的自动化链路。

| 模块 | 目录 | 技术栈 | 默认端口 |
| --- | --- | --- | --- |
| 后端 API | `backend/` | Spring Boot 4.1 / Java 25 / Jetty / MySQL / Flyway / JPA | `27000` |
| 管理后台 | `admin_frontend/` | Vue 3 + Vite + Pinia + vue-router + reka-ui | `27081` |
| 博客前台 | `blog_frontend/` | VitePress 1.6（构建期从后端 API 同步内容） | `27080` |

## 目录结构

```text
HrsBlog/
├── backend/                 # Spring Boot 后端：管理接口、访客接口、静态资源、自动化链路
│   └── src/main/java/com/example/hrsblog/
│       ├── common/          # 统一响应体、错误码、分页封装
│       ├── config/          # 配置属性类（@ConfigurationProperties）
│       ├── controller/      # admin/** 与 visitor/** 两组接口
│       ├── domain/          # 实体：Article / SiteSetting / StaticAsset / SystemLog …
│       ├── dto/             # 请求与响应 DTO
│       ├── event/           # ContentChangedEvent（内容变更事件）
│       ├── repository/      # Spring Data 仓储
│       ├── security/        # JWT 鉴权
│       └── service/         # 业务服务；service/deploy/ 是 Webhook 通知链路
├── admin_frontend/          # 管理后台（Vue 3）
├── blog_frontend/           # 博客前台（VitePress）
├── docs/                    # 设计与运维文档
└── .github/workflows/       # CI：构建镜像 + 构建静态站点并 rsync 到 1Panel 站点目录
```

## 本地开发

### 后端

```bash
cd backend
./mvnw spring-boot:run
```

默认连 MySQL（连接信息见 `backend/src/main/resources/application.yaml`，可用环境变量覆盖）：
`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`。
数据库结构由 Flyway 迁移维护（`backend/src/main/resources/db/migration`），应用启动时自动执行。

首次启动会用 `hrsblog.admin.bootstrap-username` / `bootstrap-password` 创建管理员
（默认 `user` / `admin123`，生产环境请务必覆盖）。

跑测试（内存 H2，不连生产库）：

```bash
cd backend
./mvnw test
```

### 管理后台

```bash
cd admin_frontend
pnpm install
pnpm dev        # http://localhost:27081，/api 代理到 http://localhost:27000
```

### 博客前台

```bash
cd blog_frontend
pnpm install
pnpm dev        # http://localhost:27080
```

构建时需要 `BLOG_API_BASE`（后端 API 地址）与 `BLOG_SITE_URL`（站点对外地址），
详见 `blog_frontend/README.md`。

## 自动化链路：内容变更 → Webhook → CI 重建

后台保存站点设置或发布文章后，后端会自动向一个**通用 Webhook** 发送一条通知；
把该 Webhook 指向 GitHub Dispatches API，GitHub Actions 就会自动重建并发布 VitePress 前台。

```text
AdminArticleController / AdminSettingController / AdminFriendLinkController
        │  事务内埋点（只在「真的变了」时）
        ▼
SiteSettingService / ArticleService / FriendLinkService ──publishEvent──► ContentChangedEvent
        │  AFTER_COMMIT
        ▼
DeployNotifyService（防抖合并：窗口内多次变更只发一次）
        │  组装 event_type + client_payload
        ▼
WebhookClient（RestClient）──POST──► GitHub Dispatches API / 通用 Webhook
        │                                │
        │ 每次尝试落 system_log            ▼
        │                         Actions: blog-content-updated
        ▼                         构建 VitePress → rsync 到站点目录
GET /api/admin/logs（管理端「系统日志」页）
```

- **触发条件**：站点配置实际变化；文章直接发布 / 草稿首发 / 已发布文章内容更新；
  友链审核通过 / 已通过的友链被编辑或删除。草稿编辑、保存相同配置都不触发。
- **防抖**：默认 10 秒窗口，窗口内的多次变更合并成一次投递，只带变更原因、不带内容。
- **重试**：默认失败重试 2 次（退避 5s / 15s），每次尝试都写一条日志；
  最终失败只记 ERROR，**不影响文章 / 配置保存主流程**。
- **脱敏**：日志只保留主机路径、状态码与响应片段，`Authorization` 与密钥永不入库。

配置项（后台「站点设置 → 集成」）：`webhookUrl`、`webhookEnabled`、`webhookSecret`、
`webhookEventType`、`webhookDebounceSeconds`、`webhookMaxRetries`。

完整说明（载荷格式、接口契约、全部验证步骤）见 **[docs/deploy-notification.md](docs/deploy-notification.md)**。

### 手动端到端验证

> 先用 <https://webhook.site> 建一个 Request Bin 独立确认载荷与鉴权头，确认无误再切 GitHub 真实地址。

**准备**：后台「站点设置 → 集成」→ 填入 <https://webhook.site/你的-bin> →
打开「启用通知」→「鉴权密钥」填 `test-token` → 保存。
这次保存本身就是一次变更，约 10 秒（防抖窗口）后应收到第一条通知。

**场景 A — 站点配置变更**

1. 在 webhook.site 的 bin 页面看到一条 `POST`：请求头含
   `Authorization: Bearer test-token`，请求体为
   `{"event_type":"blog-content-updated","client_payload":{"reasons":["settings_updated"],"occurredAt":"…"}}`。
2. 管理端「系统 → 系统日志」出现一条 `DEPLOY_NOTIFY` / `SUCCESS` / `attempt=1` 的记录。
3. 在 10 秒窗口内连续改 3 次站点名称并保存 → 只会收到 **1 条** 新请求（防抖合并）。
4. 不改任何内容直接再点一次保存 → 不应有新请求、日志也不新增（无变化不触发）。

**场景 B — 文章发布**

1. 「文章 → 投放」写一篇并把状态选为「已发布」→ 发布。
2. webhook.site 收到新请求，`reasons` 为 `["article_published"]`；系统日志新增一条 `SUCCESS`。
3. 再新建一篇**草稿**保存 → 不应有新请求（草稿不触发）。

**场景 C — 切到 GitHub Actions**

1. Webhook 地址改为 `https://api.github.com/repos/{owner}/{repo}/dispatches`，
   密钥改成带 `repo` 权限的 GitHub PAT，保存。
2. 发布一篇文章。
3. GitHub 仓库 → Actions → **Build and deploy** 出现一条 `blog-content-updated` 触发的 run，
   run 结束后前台站点内容更新。

**失败路径**

1. Webhook 地址改成不可达地址（如 `http://127.0.0.1:9/unreachable`），保存。
2. 发布一篇文章 → 系统日志出现多条 `FAILED`，`attempt` 依次为 1、2、3（间隔约 5s、15s）。
3. 文章接口仍然返回 **201**、设置接口仍然返回 **200** —— 通知失败不回滚主流程。

## 友链系统

访客在博客前台的「友链」页提交申请，站长在管理后台审核，通过后友链出现在前台列表里。

```text
访客 /links/ 填表
      │  POST /api/visitors/friend-links（匿名，按 IP 限频）
      ▼
FriendLinkService ──► friend_link 表（status = PENDING）
      │
      │  管理后台「内容 → 友链管理」审核通过
      ▼
ContentChangedEvent ──► Webhook ──► CI 重建前台
      │
      ▼
sync-friend-links.mjs（构建期）──► .vitepress/data/friend-links.json ──► /links/ 静态页
```

- **提交表单是前台唯一需要后端在线的接口**；友链内容本身仍是构建期静态同步的，
  表单 JS 失败不影响 `/links/` 页面的静态展示。
- **访客接口绝不返回 `contact`**：联系方式只出现在管理端的
  `GET /api/admin/friend-links`，防止被爬虫批量采集。
- **基础防滥用**：必填项与 `http(s)` URL 校验、长度上限、按 IP 的每日提交限额
  （`hrsblog.friend-link.daily-submit-limit`，默认 5，可设为 0 关闭），不引入验证码。
- **自动重建**：审核通过 / 已通过的友链被编辑或删除时，触发与文章发布同一套
  `ContentChangedEvent` → Webhook → CI 链路。

| 接口 | 说明 |
| --- | --- |
| `POST /api/visitors/friend-links` | 访客提交申请（匿名，按 IP 限频，超限返回 429） |
| `GET /api/visitors/friend-links` | 已通过的友链列表（响应不含 `contact`） |
| `GET /api/admin/friend-links?status=&page=&size=` | 管理端分页列表（支持状态筛选） |
| `POST /api/admin/friend-links/{id}/review` | 审核通过 / 拒绝 |
| `PUT /api/admin/friend-links/{id}` | 编辑友链 |
| `DELETE /api/admin/friend-links/{id}` | 删除友链 |

数据库迁移：`backend/src/main/resources/db/migration/V4__create_friend_link.sql`。

> 注：站点设置里的 `friendLinks`（JSON 列表）是另一条老链路，用于文章列表页侧边栏，
> 与这里的「友链申请 + 审核」相互独立。

## 部署

生产使用 `compose.production.yaml`：后端与后台由镜像提供，博客前台是构建期静态产物，
由 CI 用 `rsync` 同步到 1Panel 的站点目录（不产生镜像、不重启容器）。
部署细节见 `.github/workflows/deploy.yml` 与 `docs/` 下的运维文档。

## 许可

见 [LICENSE.txt](LICENSE.txt)。

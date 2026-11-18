import { existsSync, readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const DIST = path.join(ROOT, '.vitepress', 'dist')
const DATA = path.join(ROOT, '.vitepress', 'data', 'articles.json')
const SHUOSHUO_DATA = path.join(ROOT, '.vitepress', 'data', 'shuoshuo.json')

function fail(message) {
  errors.push(message)
}

function read(file) {
  return readFileSync(file, 'utf8')
}

function htmlEscape(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

const errors = []
const warnings = []

if (!existsSync(DATA)) {
  console.error('[verify] 找不到 .vitepress/data/articles.json，请先运行 pnpm seed 或 pnpm sync')
  process.exit(1)
}

const data = JSON.parse(read(DATA))
const items = Array.isArray(data.items) ? data.items : []

if (!existsSync(path.join(DIST, 'index.html'))) fail('缺少 dist/index.html')
if (!existsSync(path.join(DIST, 'articles', 'index.html'))) fail('缺少文章列表 dist/articles/index.html')
if (!existsSync(path.join(DIST, 'about.html'))) fail('缺少 dist/about.html')
if (!existsSync(path.join(DIST, 'links', 'index.html'))) fail('缺少友链页 dist/links/index.html')
if (!existsSync(path.join(DIST, 'shuoshuo', 'index.html'))) fail('缺少说说页 dist/shuoshuo/index.html')
if (!existsSync(path.join(DIST, 'guestbook', 'index.html'))) fail('缺少留言页 dist/guestbook/index.html')
if (!existsSync(path.join(DIST, '404.html'))) fail('缺少 dist/404.html')

for (const item of items) {
  const file = path.join(DIST, 'articles', `${item.slug}.html`)
  if (!existsSync(file)) {
    fail(`缺少文章静态页 ${item.slug}.html`)
    continue
  }

  const html = read(file)
  if (!html.includes(htmlEscape(item.title))) fail(`文章页缺少标题：${item.slug}`)
  if (!html.includes('article-prose')) fail(`文章页缺少正文容器：${item.slug}`)
  if (!html.includes('<h1')) fail(`文章页缺少 H1：${item.slug}`)

  if (item.cover?.startsWith('/')) {
    const coverFile = path.join(DIST, item.cover.replace(/^\//, ''))
    if (!existsSync(coverFile)) fail(`封面未进入构建产物：${item.cover}`)
  }
}

const sitemapFile = path.join(DIST, 'sitemap.xml')
if (!existsSync(sitemapFile)) {
  fail('缺少 sitemap.xml')
} else {
  const sitemap = read(sitemapFile)
  if (!sitemap.includes('/articles/')) fail('sitemap 中缺少文章列表')
  if (!sitemap.includes('/links/')) fail('sitemap 中缺少友链页')
  if (!sitemap.includes('/shuoshuo/')) fail('sitemap 中缺少说说页')
  if (!sitemap.includes('/guestbook/')) fail('sitemap 中缺少留言页')

  for (const item of items) {
    if (!sitemap.includes(item.url)) fail(`sitemap 中缺少 ${item.url}`)
  }

  const locations = [...sitemap.matchAll(/<loc>(.*?)<\/loc>/g)].map((match) => match[1])
  // 首页 + 文章列表 + 关于 + 友链 + 说说 + 留言
  const expectedLocations = items.length + 6
  if (locations.length !== expectedLocations) {
    fail(`sitemap 页面数量异常：${locations.length}，预期 ${expectedLocations}`)
  }
}

const articleDir = path.join(DIST, 'articles')
const expectedArticlePages = new Set(['index.html', ...items.map((item) => `${item.slug}.html`)])
if (existsSync(articleDir)) {
  for (const file of readdirSync(articleDir)) {
    if (file.endsWith('.html') && !expectedArticlePages.has(file)) {
      fail(`存在陈旧文章页：articles/${file}`)
    }
  }
}

const feedFile = path.join(DIST, 'feed.xml')
if (!existsSync(feedFile)) {
  fail('缺少 feed.xml')
} else {
  const feed = read(feedFile)
  const feedItems = (feed.match(/<item>/g) || []).length
  if (feedItems !== items.length) fail(`feed 文章数量不匹配：${feedItems} != ${items.length}`)
}

const robotsFile = path.join(DIST, 'robots.txt')
if (!existsSync(robotsFile)) {
  fail('缺少 robots.txt')
} else if (!read(robotsFile).includes('Sitemap:')) {
  warnings.push('robots.txt 没有 Sitemap 行（BLOG_SITE_URL 可能为空）')
}

const indexHtml = existsSync(path.join(DIST, 'index.html')) ? read(path.join(DIST, 'index.html')) : ''
const homeCards = (indexHtml.match(/class="post"/g) || []).length
if (items.length > 0 && homeCards === 0) fail('首页没有渲染文章卡片')

const linksFile = path.join(DIST, 'links', 'index.html')
if (existsSync(linksFile)) {
  const linksHtml = read(linksFile)
  if (!linksHtml.includes(htmlEscape('友情链接'))) fail('友链页缺少标题：友情链接')
  if (!linksHtml.includes('申请友链')) fail('友链页缺少申请表单')
}

/**
 * 说说页校验：
 * - 数据文件可解析，且每条说说都带构建期编译好的 `html`；
 * - 页面存在且渲染出时间线结构（有数据时渲染条目与日期标签，无数据时渲染空态）。
 */
const shuoshuoItems = []
if (existsSync(SHUOSHUO_DATA)) {
  try {
    const payload = JSON.parse(read(SHUOSHUO_DATA))
    shuoshuoItems.push(...(Array.isArray(payload.items) ? payload.items : []))
  } catch (error) {
    fail(`说说数据文件解析失败：${error.message}`)
  }
} else {
  fail('缺少 .vitepress/data/shuoshuo.json')
}

for (const item of shuoshuoItems) {
  if (typeof item.html !== 'string') {
    fail('说说数据缺少构建期编译的 html 字段')
    break
  }
}

const shuoshuoFile = path.join(DIST, 'shuoshuo', 'index.html')
if (existsSync(shuoshuoFile)) {
  const shuoshuoHtml = read(shuoshuoFile)

  if (shuoshuoItems.length > 0) {
    if (!shuoshuoHtml.includes('shuoshuo__timeline')) fail('说说页缺少时间线结构')
    if (!shuoshuoHtml.includes('shuoshuo__item')) fail('说说页没有渲染说说条目')

    // 时间标签取最新一条：归一化后的 ISO 串可以直接按字典序比较。
    const newest = shuoshuoItems
      .map((item) => String(item?.publishedAt || ''))
      .filter(Boolean)
      .sort()
      .pop()
    const dateTag = newest ? newest.slice(0, 10).replaceAll('-', '.') : ''
    if (dateTag && !shuoshuoHtml.includes(dateTag)) fail('说说页缺少最新说说的日期标签')
  } else if (!shuoshuoHtml.includes('shuoshuo__empty')) {
    fail('说说页缺少空态提示')
  }
}

const guestbookFile = path.join(DIST, 'guestbook', 'index.html')
if (existsSync(guestbookFile)) {
  const guestbookHtml = read(guestbookFile)
  if (!guestbookHtml.includes(htmlEscape('留言'))) fail('留言页缺少标题：留言')

  // 留言板被后台关闭时，页面只展示「未开放」提示，不渲染提交表单。
  const guestbookEnabled = readGuestbookEnabled()
  if (guestbookEnabled) {
    if (!guestbookHtml.includes('留下留言')) fail('留言页缺少提交表单')
  } else if (!guestbookHtml.includes('留言板暂时没有开放')) {
    fail('留言板关闭时，留言页缺少未开放提示')
  }
}

/**
 * 留言板开关取自构建期落盘的站点配置（与前台运行时读到的是同一份）。
 */
function readGuestbookEnabled() {
  const settingsFile = path.join(ROOT, '.vitepress', 'data', 'site-settings.json')
  if (!existsSync(settingsFile)) return false

  try {
    const payload = JSON.parse(read(settingsFile))
    return payload?.siteConfig?.guestbook?.enabled === true
  } catch {
    return false
  }
}

// 联系方式只属于站长，构建产物里不允许出现 contact 字段。
const friendLinksFile = path.join(ROOT, '.vitepress', 'data', 'friend-links.json')
if (existsSync(friendLinksFile) && read(friendLinksFile).includes('"contact"')) {
  fail('友链数据文件不应包含 contact 字段')
}

if (items.length === 0) warnings.push('当前没有已发布文章，生成的是空站点')
if (warnings.length) warnings.forEach((warning) => console.warn(`[verify] 警告：${warning}`))

if (errors.length) {
  errors.forEach((error) => console.error(`[verify] 失败：${error}`))
  process.exit(1)
}

console.log(`[verify] 通过：${items.length} 篇文章、首页 ${homeCards} 张卡片、sitemap/feed/robots 完整`)

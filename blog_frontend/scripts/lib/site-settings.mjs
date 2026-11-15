/**
 * 把访客站点设置（`GET /api/visitors/settings` 返回的键值 map）合并进
 * `.vitepress/site.config.mjs` 的默认配置。
 *
 * 合并规则：
 * - 只覆盖“有值”的配置项，空字符串/缺失一律保留本地默认，保证接口部分不可用时站点仍完整；
 * - `friendLinks` 等列表字段为空数组时同样保留默认值；
 * - 背景图只接受 `http(s)://` 或 `/` 开头的链接，其余协议丢弃（防 `javascript:` 注入）。
 */

const HEX_COLOR = /^#[0-9a-f]{6}$/i
const THEMES = new Set(['system', 'light', 'dark'])

function safeImage(value) {
  const url = String(value ?? '').trim()
  if (!url) return ''
  return /^https?:\/\//i.test(url) || url.startsWith('/') ? url : ''
}

function isBlank(value) {
  return value === undefined || value === null || String(value).trim() === ''
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value))
}

/** 纯函数：defaults 为 site.config.mjs 的 siteConfig，values 为接口键值 map。 */
export function applySettingsToSiteConfig(defaults, values = {}) {
  const site = cloneJson(defaults)
  const overrides = []

  const text = (key, apply) => {
    const value = values[key]
    if (isBlank(value)) return
    apply(String(value).trim())
    overrides.push(key)
  }

  // ── 基本设置 ──
  text('siteName', (value) => {
    site.name = value
    site.wordmark = value
  })
  text('siteTagline', (value) => {
    site.tagline = value
    site.footer.note = value
  })
  text('siteDescription', (value) => {
    site.description = value
  })

  // ── 作者资料 ──
  text('authorName', (value) => {
    site.author.name = value
  })
  text('authorRole', (value) => {
    site.author.role = value
  })
  text('authorBio', (value) => {
    site.author.bio = value
  })
  text('avatarUrl', (value) => {
    const url = safeImage(value)
    if (url) site.author.avatar = url
  })
  text('location', (value) => {
    site.author.location = value
  })

  // ── 社交入口（GitHub / 邮箱 / RSS 按默认顺序重建） ──
  let hasEmail = false
  let hasGithub = false
  let hasRss = false
  text('email', (value) => {
    site.author.email = value
    hasEmail = true
  })
  text('githubUrl', (value) => {
    site.author.githubUrl = value
    hasGithub = true
  })
  text('rssPath', (value) => {
    site.author.rssPath = value
    hasRss = true
  })
  if (hasEmail || hasGithub || hasRss) {
    const social = []
    if (hasGithub) social.push({ label: 'GitHub', href: site.author.githubUrl, icon: 'github' })
    if (hasEmail) social.push({ label: '邮箱', href: `mailto:${site.author.email}`, icon: 'mail' })
    social.push({
      label: 'RSS',
      href: hasRss ? site.author.rssPath : '/feed.xml',
      icon: 'rss',
    })
    site.social = social
  }

  // ── 外观与背景 ──
  text('backgroundImage', (value) => {
    const url = safeImage(value)
    if (url) site.backgroundImage = url
  })
  if (!isBlank(values.backgroundOverlay)) {
    const overlay = Number(values.backgroundOverlay)
    if (Number.isFinite(overlay) && overlay >= 0 && overlay <= 100) {
      site.backgroundOverlay = overlay
      overrides.push('backgroundOverlay')
    }
  }
  if (!isBlank(values.defaultTheme) && THEMES.has(String(values.defaultTheme))) {
    site.defaultTheme = String(values.defaultTheme)
    overrides.push('defaultTheme')
  }
  if (!isBlank(values.accentColor) && HEX_COLOR.test(String(values.accentColor).trim())) {
    site.accentColor = String(values.accentColor).trim().toLowerCase()
    overrides.push('accentColor')
  }

  // ── 友情链接 ──
  if (Array.isArray(values.friendLinks) && values.friendLinks.length > 0) {
    const links = values.friendLinks
      .filter((item) => item && typeof item === 'object')
      .filter((item) => String(item.name ?? '').trim() && /^https?:\/\//i.test(String(item.url ?? '')))
      .map((item) => ({
        name: String(item.name).trim(),
        url: String(item.url).trim(),
        description: String(item.description ?? '').trim(),
      }))
    if (links.length > 0) {
      site.friendLinks = links
      overrides.push('friendLinks')
    }
  }

  // ── SEO ──
  if (!isBlank(values.titleTemplate) && String(values.titleTemplate).includes(':title')) {
    site.titleTemplate = String(values.titleTemplate).trim()
    overrides.push('titleTemplate')
  }
  text('metaDescription', (value) => {
    site.metaDescription = value
  })

  site.settingsOverrides = overrides
  return site
}

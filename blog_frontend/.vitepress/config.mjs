import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitepress'
import { loadEnv } from '../scripts/lib/load-env.mjs'
import { resolveSiteUrl } from '../api/config.js'
import { siteConfig } from './site.config.mjs'
import { createHeadTransform } from './seo.mjs'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
loadEnv(ROOT)

/**
 * 构建期配置合并：`scripts/sync-settings.mjs` 已把 `GET /api/visitors/settings`
 * 的公开配置合并写入 `data/site-settings.json`。这里读取合并结果作为有效站点配置；
 * 文件不存在（未同步/离线种子）时直接使用 site.config.mjs 默认值。
 */
const SETTINGS_FILE = fileURLToPath(new URL('./data/site-settings.json', import.meta.url))
let effectiveSiteConfig = siteConfig
if (existsSync(SETTINGS_FILE)) {
  try {
    const payload = JSON.parse(readFileSync(SETTINGS_FILE, 'utf8'))
    if (payload?.siteConfig && typeof payload.siteConfig === 'object') {
      // 落盘文件可能来自旧版本（缺少新增的 guestbook / shuoshuo 配置块）：
      // 用本地默认值补齐缺失字段，避免前台读到 undefined。
      effectiveSiteConfig = {
        ...siteConfig,
        ...payload.siteConfig,
        guestbook: { ...(siteConfig.guestbook || {}), ...(payload.siteConfig.guestbook || {}) },
        shuoshuo: { ...(siteConfig.shuoshuo || {}), ...(payload.siteConfig.shuoshuo || {}) },
      }
    }
  } catch (error) {
    console.warn(`[config] site-settings.json 解析失败，使用本地默认配置: ${error.message}`)
  }
}

const siteUrl = resolveSiteUrl()
const effectiveSiteUrl = siteUrl || 'http://localhost:27080'
const withUrl = { ...effectiveSiteConfig, url: effectiveSiteUrl }

const defaultTheme = ['system', 'light', 'dark'].includes(effectiveSiteConfig.defaultTheme)
  ? effectiveSiteConfig.defaultTheme
  : 'system'

/**
 * Runs before first paint so the paper/photo theme never flashes.
 * `?theme=dark|light` is a preview escape hatch used for design review shots;
 * a real toggle persists to localStorage. 未存储偏好时使用后台配置的默认主题。
 */
const themeScript = `(() => {
  try {
    const params = new URLSearchParams(window.location.search)
    const preview = params.get('theme')
    const stored = localStorage.getItem('hrsblog-theme')
    const fallback = ${JSON.stringify(defaultTheme)}
    const mode = preview === 'dark' || preview === 'light' ? preview : (stored || fallback)
    const dark = mode === 'dark' || (mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
    const root = document.documentElement
    root.dataset.theme = dark ? 'dark' : 'light'
    root.dataset.themeMode = mode
    root.classList.toggle('dark', dark)
    root.style.colorScheme = dark ? 'dark' : 'light'
  } catch {
    document.documentElement.dataset.theme = 'light'
  }
})()`

// 后台配置的品牌强调色（合法的 6 位十六进制）覆盖默认铜色。
const accentColor = /^#[0-9a-f]{6}$/i.test(String(effectiveSiteConfig.accentColor || ''))
  ? String(effectiveSiteConfig.accentColor).toLowerCase()
  : ''
const accentStyle = accentColor
  ? `:root{--copper:${accentColor}}html[data-theme='dark']{--copper:${accentColor}}`
  : ''

export default defineConfig({
  lang: 'zh-CN',
  title: effectiveSiteConfig.name,
  titleTemplate: effectiveSiteConfig.titleTemplate || `:title · ${effectiveSiteConfig.name}`,
  description: effectiveSiteConfig.description,
  cleanUrls: true,
  appearance: false,
  lastUpdated: false,
  srcExclude: ['README.md', 'scripts/**', 'api/**'],
  sitemap: { hostname: effectiveSiteUrl },
  transformHead: createHeadTransform({
    siteUrl: effectiveSiteUrl,
    siteConfig: withUrl,
    defaultImage: effectiveSiteConfig.ogImage || '/og-cover.jpg',
  }),
  markdown: {
    html: false,
    headers: {
      level: [2, 3],
    },
    theme: {
      light: 'vitesse-light',
      dark: 'vitesse-dark',
    },
    image: {
      lazyLoading: true,
    },
  },
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' }],
    ['meta', { name: 'theme-color', content: '#f6f2e8', media: '(prefers-color-scheme: light)' }],
    ['meta', { name: 'theme-color', content: '#121914', media: '(prefers-color-scheme: dark)' }],
    ['meta', { name: 'author', content: effectiveSiteConfig.author.name }],
    [
      'link',
      {
        rel: 'alternate',
        type: 'application/rss+xml',
        title: `${effectiveSiteConfig.name} RSS`,
        href: '/feed.xml',
      },
    ],
    // 配置了自定义背景图时预载该图；否则预载默认湖景响应式版本
    effectiveSiteConfig.backgroundImage
      ? [
          'link',
          {
            rel: 'preload',
            as: 'image',
            href: effectiveSiteConfig.backgroundImage,
            fetchpriority: 'high',
          },
        ]
      : [
          'link',
          {
            rel: 'preload',
            as: 'image',
            href: '/images/hero-1600.avif',
            type: 'image/avif',
            imagesrcset: '/images/hero-1600.avif 1600w, /images/hero-2400.avif 2400w',
            imagesizes: '100vw',
            fetchpriority: 'high',
          },
        ],
    ['script', {}, themeScript],
    ['style', {}, 'html{background:#f6f2e8}html[data-theme="dark"]{background:#121914}'],
    ...(accentStyle ? [['style', {}, accentStyle]] : []),
  ],
  themeConfig: {
    site: withUrl,
  },
  vite: {
    server: {
      // Covers are downloaded into public/assets at sync time, but these
      // proxies keep a dev server useful when a cover is not cached yet.
      //
      // 只代理后端真正的两个命名空间（/api/visitors/**、/api/admin/**），
      // 而不是整段 `/api`：仓库里的 `api/` 同时是源码目录（api/client.js、
      // api/friend-links.js …），友链申请表单在浏览器端 import 它们时，
      // Vite 会以 `/api/xxx.js` 的 URL 取模块；若整段代理，这些模块请求会被
      // 转发到后端并拿到 401，页面上的提交也就跟着坏了。
      proxy: {
        '^/api/(?:visitors|admin)/': {
          target: 'http://localhost:27000',
          changeOrigin: true,
        },
        '/assets/image': {
          target: 'http://localhost:27000',
          changeOrigin: true,
        },
      },
      // Atomic-write helpers create transient files; without this, Vite's
      // watcher can trip over the lock on Windows.
      watch: {
        ignored: [
          '**/scripts/**',
          '**/build.log',
          '**/.vitepress/data/**',
          '**/*.tmpdir/**',
          '**/.*.tmpdir/**',
          '**/*.tmp',
        ],
      },
    },
  },
})

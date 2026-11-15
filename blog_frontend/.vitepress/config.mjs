import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitepress'
import { loadEnv } from '../scripts/lib/load-env.mjs'
import { resolveSiteUrl } from '../api/config.js'
import { siteConfig } from './site.config.mjs'
import { createHeadTransform } from './seo.mjs'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
loadEnv(ROOT)

const siteUrl = resolveSiteUrl()
const effectiveSiteUrl = siteUrl || 'http://localhost:27080'
const withUrl = { ...siteConfig, url: effectiveSiteUrl }

/**
 * Runs before first paint so the paper/photo theme never flashes.
 * `?theme=dark|light` is a preview escape hatch used for design review shots;
 * a real toggle persists to localStorage.
 */
const themeScript = `(() => {
  try {
    const params = new URLSearchParams(window.location.search)
    const preview = params.get('theme')
    const stored = localStorage.getItem('hrsblog-theme')
    const mode = preview === 'dark' || preview === 'light' ? preview : (stored || 'system')
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

export default defineConfig({
  lang: 'zh-CN',
  title: siteConfig.name,
  titleTemplate: `:title · ${siteConfig.name}`,
  description: siteConfig.description,
  cleanUrls: true,
  appearance: false,
  lastUpdated: false,
  srcExclude: ['README.md', 'scripts/**', 'api/**'],
  sitemap: siteUrl ? { hostname: siteUrl } : undefined,
  transformHead: createHeadTransform({
    siteUrl: effectiveSiteUrl,
    siteConfig: withUrl,
    defaultImage: '/og-cover.jpg',
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
    ['meta', { name: 'author', content: siteConfig.author.name }],
    [
      'link',
      {
        rel: 'alternate',
        type: 'application/rss+xml',
        title: `${siteConfig.name} RSS`,
        href: '/feed.xml',
      },
    ],
    [
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
  ],
  themeConfig: {
    site: withUrl,
  },
  vite: {
    server: {
      // Covers are downloaded into public/assets at sync time, but these
      // proxies keep a dev server useful when a cover is not cached yet.
      proxy: {
        '/api': {
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

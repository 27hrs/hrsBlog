import { promises as fs } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { siteConfig as defaultSiteConfig } from '../../.vitepress/site.config.mjs'
import { buildArticleRecord, buildIndexData, nowInShanghai, renderFeed, renderRobots } from './content.mjs'

const ROOT = fileURLToPath(new URL('../..', import.meta.url))
const ARTICLES_DIR = path.join(ROOT, 'articles')
const DATA_DIR = path.join(ROOT, '.vitepress', 'data')
const PUBLIC_DIR = path.join(ROOT, 'public')
const DATA_FILE = path.join(DATA_DIR, 'articles.json')
const SETTINGS_FILE = path.join(DATA_DIR, 'site-settings.json')

/**
 * 优先使用 sync-settings.mjs 落盘的合并配置（含后台 allowIndexing 等），
 * 文件缺失或损坏时回退 site.config.mjs 本地默认。
 */
async function resolveSiteConfig() {
  try {
    const payload = JSON.parse(await fs.readFile(SETTINGS_FILE, 'utf8'))
    if (payload?.siteConfig && typeof payload.siteConfig === 'object') {
      return { ...defaultSiteConfig, ...payload.siteConfig }
    }
  } catch {
    // 未同步或文件损坏：用本地默认继续。
  }
  return defaultSiteConfig
}

async function ensureDir(directory) {
  await fs.mkdir(directory, { recursive: true })
}

async function exists(file) {
  try {
    await fs.access(file)
    return true
  } catch {
    return false
  }
}

async function writeAtomic(file, contents) {
  await ensureDir(path.dirname(file))
  const temporary = `${file}.tmp-${process.pid}-${Date.now()}`
  await fs.writeFile(temporary, contents)
  await fs.rename(temporary, file)
}

/**
 * The backend stores covers under /assets/image/**. To keep the generated site
 * fully static, download those files into public/assets/image/** at sync time.
 * If the download fails, keep the original URL so nginx can still proxy it.
 */
async function localizeCover(coverUrl, apiBase) {
  if (!coverUrl || !coverUrl.startsWith('/assets/') || !apiBase) return { coverUrl }

  let origin
  try {
    origin = new URL(apiBase).origin
  } catch {
    return { coverUrl }
  }

  const publicRoot = path.resolve(PUBLIC_DIR)
  const target = path.resolve(PUBLIC_DIR, `.${coverUrl}`)
  if (!target.startsWith(`${publicRoot}${path.sep}`)) return { coverUrl }
  if (await exists(target)) return { coverUrl }

  try {
    const response = await fetch(`${origin}${coverUrl}`, {
      signal: AbortSignal.timeout(10_000),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const buffer = Buffer.from(await response.arrayBuffer())
    await writeAtomic(target, buffer)
    return { coverUrl }
  } catch (error) {
    return { coverUrl, warning: `封面缓存失败 ${coverUrl}: ${error.message}` }
  }
}

async function readGeneratedSlugs() {
  const slugs = new Set()
  let entries = []

  try {
    entries = await fs.readdir(ARTICLES_DIR, { withFileTypes: true })
  } catch {
    return slugs
  }

  for (const entry of entries) {
    if (!entry.isFile() || !entry.name.endsWith('.md') || entry.name === 'index.md') continue
    const file = path.join(ARTICLES_DIR, entry.name)
    const head = (await fs.readFile(file, 'utf8')).slice(0, 8000)
    if (!head.includes('generatedBy: "hrsblog-sync"')) continue
    const slug = head.match(/^slug:\s*"([^"]+)"/m)?.[1]
    if (slug) slugs.add(slug)
  }

  return slugs
}

async function removeStaleArticles(nextSlugs) {
  const existing = await readGeneratedSlugs()
  const removed = []

  for (const slug of existing) {
    if (nextSlugs.has(slug)) continue
    const file = path.join(ARTICLES_DIR, `${slug}.md`)
    try {
      await fs.unlink(file)
      removed.push(slug)
    } catch {
      // A file may already be gone; ignore.
    }
  }

  return removed
}

/**
 * @param {object} options
 * @param {Array<object>} options.details API detail DTOs
 * @param {'api'|'fixtures'|'seed'} options.source
 * @param {string} options.siteUrl
 * @param {string} [options.assetBase]
 * @param {string} [options.apiBase] used to cache /assets/image/** covers
 * @param {object} [options.siteConfig]
 */
export async function writeContent({
  details,
  source,
  siteUrl,
  assetBase = '',
  apiBase = '',
  siteConfig,
}) {
  const effectiveSiteConfig = siteConfig ?? (await resolveSiteConfig())
  await ensureDir(ARTICLES_DIR)
  await ensureDir(DATA_DIR)
  await ensureDir(PUBLIC_DIR)

  const items = []
  const errors = []
  const warnings = []

  for (const detail of details) {
    try {
      const localized = await localizeCover(detail.coverUrl || '', apiBase)
      if (localized.warning) warnings.push(localized.warning)

      const record = buildArticleRecord(
        { ...detail, coverUrl: localized.coverUrl },
        { assetBase, siteUrl },
      )
      const file = path.join(ARTICLES_DIR, `${record.item.slug}.md`)
      await writeAtomic(file, record.markdown)
      items.push(record.item)
    } catch (error) {
      errors.push({ slug: detail?.slug || '(unknown)', reason: error.message })
    }
  }

  items.sort((a, b) => {
    const byDate = b.publishedAt.localeCompare(a.publishedAt)
    return byDate || String(b.id || '').localeCompare(String(a.id || ''))
  })

  const generatedAt = nowInShanghai()
  const index = buildIndexData(items, { source, siteUrl, generatedAt })
  const nextSlugs = new Set(items.map((item) => item.slug))
  const removed = await removeStaleArticles(nextSlugs)

  await writeAtomic(DATA_FILE, `${JSON.stringify(index, null, 2)}\n`)
  await writeAtomic(
    path.join(PUBLIC_DIR, 'feed.xml'),
    renderFeed({ items, siteConfig: effectiveSiteConfig, siteUrl, generatedAt }),
  )
  await writeAtomic(
    path.join(PUBLIC_DIR, 'robots.txt'),
    renderRobots({ siteUrl, allowIndexing: effectiveSiteConfig.allowIndexing !== false }),
  )

  return {
    count: items.length,
    errors,
    warnings,
    removed,
    generatedAt,
    dataFile: DATA_FILE,
  }
}

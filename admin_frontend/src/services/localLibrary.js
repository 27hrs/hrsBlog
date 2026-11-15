import { inferCategoryFromTags } from '@/data/categories.js'

const LIBRARY_KEY = 'hrsblog-admin-article-library'
const MAX_ENTRIES = 120

function safeParse(value, fallback) {
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

function readRawLibrary() {
  const parsed = safeParse(localStorage.getItem(LIBRARY_KEY) || '[]', [])
  return Array.isArray(parsed) ? parsed : []
}

function writeRawLibrary(entries) {
  localStorage.setItem(LIBRARY_KEY, JSON.stringify(entries.slice(0, MAX_ENTRIES)))
  window.dispatchEvent(new CustomEvent('hrsblog:article-library-changed'))
}

function normalizeTags(tags) {
  if (!Array.isArray(tags)) return []
  return tags
    .map((tag) => {
      if (typeof tag === 'string') {
        return { name: tag, slug: tag.toLowerCase().replace(/\s+/g, '-') }
      }
      if (!tag || !tag.name) return null
      return { name: tag.name, slug: tag.slug || tag.name.toLowerCase().replace(/\s+/g, '-') }
    })
    .filter(Boolean)
}

function normalizeEntry(article, meta = {}) {
  const tags = normalizeTags(article.tags)
  return {
    id: article.id ?? null,
    title: article.title || '未命名文章',
    slug: article.slug || '',
    summary: article.summary || '',
    content: article.content || '',
    contentFormat: article.contentFormat || 'MARKDOWN',
    coverUrl: article.coverUrl || '',
    status: article.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    publishedAt: article.publishedAt || null,
    createdAt: article.createdAt || new Date().toISOString(),
    updatedAt: article.updatedAt || new Date().toISOString(),
    tags,
    localMeta: {
      category: meta.category || inferCategoryFromTags(tags),
      visibility: meta.visibility || 'public',
      pinned: Boolean(meta.pinned),
      featured: Boolean(meta.featured),
      scheduledAt: meta.scheduledAt || '',
      savedAt: new Date().toISOString(),
      serverVerified: Boolean(article.id),
    },
  }
}

function entryKey(entry) {
  return entry.id ? `id:${entry.id}` : `slug:${entry.slug}`
}

export function listLocalArticles() {
  return readRawLibrary()
    .filter((entry) => entry && entry.slug)
    .map((entry) => ({
      ...entry,
      tags: normalizeTags(entry.tags),
      localMeta: {
        category: entry.localMeta?.category || inferCategoryFromTags(entry.tags),
        visibility: entry.localMeta?.visibility || 'public',
        pinned: Boolean(entry.localMeta?.pinned),
        featured: Boolean(entry.localMeta?.featured),
        scheduledAt: entry.localMeta?.scheduledAt || '',
        savedAt: entry.localMeta?.savedAt || entry.updatedAt || '',
        serverVerified: Boolean(entry.localMeta?.serverVerified ?? entry.id),
      },
    }))
}

export function rememberArticle(article, meta = {}) {
  if (!article?.slug) return null
  const entry = normalizeEntry(article, meta)
  const key = entryKey(entry)
  const existing = readRawLibrary().filter((item) => entryKey(item) !== key)
  writeRawLibrary([entry, ...existing])
  return entry
}

export function removeLocalArticle(entry) {
  if (!entry?.slug) return
  const key = entryKey(entry)
  writeRawLibrary(readRawLibrary().filter((item) => entryKey(item) !== key))
}

export function getLocalArticle({ id, slug }) {
  return (
    listLocalArticles().find((entry) =>
      id ? String(entry.id) === String(id) : entry.slug === slug,
    ) || null
  )
}

/**
 * Merge server summaries with browser-cached full records.
 * The backend currently exposes only published summaries, so locally cached
 * drafts and richer metadata fill the gap until the admin collection API lands.
 */
export function mergeArticleCollections(serverItems = []) {
  const localEntries = listLocalArticles()
  const byId = new Map()
  const bySlug = new Map()

  localEntries.forEach((entry) => {
    if (entry.id) byId.set(String(entry.id), entry)
    bySlug.set(entry.slug, entry)
  })

  const merged = serverItems.map((item) => {
    const local = byId.get(String(item.id)) || bySlug.get(item.slug)
    const tags = normalizeTags(item.tags)
    return {
      ...item,
      status: 'PUBLISHED',
      content: local?.content || '',
      contentFormat: local?.contentFormat || 'MARKDOWN',
      createdAt: local?.createdAt || item.publishedAt,
      updatedAt: local?.updatedAt || item.publishedAt,
      tags,
      localMeta: {
        category:
          local?.localMeta?.category ||
          inferCategoryFromTags(tags) ||
          (local?.localMeta?.category ?? 'other'),
        visibility: local?.localMeta?.visibility || 'public',
        pinned: Boolean(local?.localMeta?.pinned),
        featured: Boolean(local?.localMeta?.featured),
        scheduledAt: local?.localMeta?.scheduledAt || '',
        savedAt: local?.localMeta?.savedAt || '',
      },
      dataSource: local ? 'server+local' : 'server',
    }
  })

  const seenKeys = new Set(merged.map((item) => `${item.id || ''}:${item.slug}`))
  const seenSlugs = new Set(merged.map((item) => item.slug))

  localEntries.forEach((entry) => {
    const serverMatches =
      (entry.id && merged.some((item) => String(item.id) === String(entry.id))) ||
      merged.some((item) => item.slug === entry.slug)
    if (serverMatches || seenKeys.has(`${entry.id || ''}:${entry.slug}`)) return
    if (seenSlugs.has(entry.slug)) return
    merged.push({
      ...entry,
      dataSource: 'local',
    })
  })

  return merged.sort((left, right) => {
    const leftDate = new Date(left.publishedAt || left.updatedAt || left.createdAt || 0).getTime()
    const rightDate = new Date(right.publishedAt || right.updatedAt || right.createdAt || 0).getTime()
    return rightDate - leftDate
  })
}

export function countLocalDrafts() {
  return listLocalArticles().filter((entry) => entry.status === 'DRAFT').length
}

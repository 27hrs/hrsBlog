import { fetchAllPublishedArticles, tryAdminArticleCollection } from '@/api/articles.js'
import { mergeArticleCollections } from '@/services/localLibrary.js'
import { inferCategoryFromTags } from '@/data/categories.js'

function normalizeAdminItem(item) {
  const tags = Array.isArray(item.tags) ? item.tags : []
  return {
    ...item,
    status: item.status || 'PUBLISHED',
    category: item.category || item.localMeta?.category || inferCategoryFromTags(tags),
    localMeta: {
      category: item.localMeta?.category || inferCategoryFromTags(tags),
      visibility: item.localMeta?.visibility || 'public',
      pinned: Boolean(item.localMeta?.pinned),
      featured: Boolean(item.localMeta?.featured),
      scheduledAt: item.localMeta?.scheduledAt || '',
    },
    dataSource: item.dataSource || 'admin-api',
  }
}

const ADMIN_PAGE_SIZE = 50
const ADMIN_MAX_PAGES = 40

/**
 * 串行翻页拉取管理端文章集合（草稿 + 已发布，后端 size 上限 50）。
 * 管理接口不可用时返回 null，由调用方降级到访客接口 + 本机缓存。
 */
async function fetchAllAdminArticles({ maxPages = ADMIN_MAX_PAGES } = {}) {
  const collected = []

  for (let page = 0; page < maxPages; page += 1) {
    const response = await tryAdminArticleCollection({ page, size: ADMIN_PAGE_SIZE })
    if (!response) return null

    const items = Array.isArray(response?.items) ? response.items : []
    collected.push(...items)
    const totalPages = Number(response?.totalPages) || 0
    if (page + 1 >= totalPages) break
  }

  return collected
}

/**
 * One loading strategy for the management workspace:
 * 1. Prefer the admin collection endpoint (drafts + published) when the backend serves it.
 * 2. Fall back to the public collection plus browser-cached drafts.
 *
 * Filters and pagination stay client-side so the same UI works in both modes.
 */
export async function loadArticleWorkspace() {
  const adminItems = await fetchAllAdminArticles()

  if (adminItems) {
    return {
      items: adminItems.map(normalizeAdminItem),
      source: 'admin-api',
      fetchedAt: new Date(),
    }
  }

  const published = await fetchAllPublishedArticles()
  const items = mergeArticleCollections(published.items).map((article) => ({
    ...article,
    category: article.localMeta?.category || inferCategoryFromTags(article.tags),
  }))

  return {
    items,
    source: 'public+local',
    fetchedAt: new Date(),
  }
}

export function managementStatus(article) {
  if (article.status === 'PUBLISHED') return 'published'
  if (article.localMeta?.scheduledAt) return 'scheduled'
  return 'draft'
}

export function publishedUrl(slug) {
  const configured = import.meta.env.VITE_BLOG_URL
  const base = configured
    ? configured.replace(/\/$/, '')
    : `${window.location.protocol}//${window.location.hostname}:27080`
  return `${base}/articles/${slug}`
}

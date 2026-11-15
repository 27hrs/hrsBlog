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

/**
 * One loading strategy for the management workspace:
 * 1. Prefer the planned admin collection endpoint when the backend ships it.
 * 2. Today, fall back to the public collection plus browser-cached drafts.
 *
 * Filters and pagination stay client-side so the same UI works in both modes.
 */
export async function loadArticleWorkspace() {
  const adminCollection = await tryAdminArticleCollection({ page: 0, size: 100 })

  if (adminCollection?.items?.length || adminCollection?.totalElements === 0) {
    return {
      items: (adminCollection.items || []).map(normalizeAdminItem),
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

import { computed } from 'vue'
import { useData } from 'vitepress'
import { ARTICLE_CATEGORIES } from '../utils/categories.js'

/**
 * VitePress exposes JSON imports as modules. Using import.meta.glob keeps the
 * build working even before the first sync (empty items, not a broken import).
 */
const generated = import.meta.glob('../../data/articles.json', { eager: true })
const rawData = Object.values(generated)[0]?.default || {}

const items = Array.isArray(rawData.items) ? rawData.items : []
const tags = Array.isArray(rawData.tags) ? rawData.tags : []
const categories = Array.isArray(rawData.categories) && rawData.categories.length
  ? rawData.categories
  : ARTICLE_CATEGORIES.map((category) => ({ ...category, count: 0 }))

const bySlug = new Map(items.map((item) => [item.slug, item]))

export const articlesData = {
  generatedAt: rawData.generatedAt || '',
  source: rawData.source || 'empty',
  siteUrl: rawData.siteUrl || '',
  count: items.length,
  items,
  tags,
  categories,
}

export function useArticles() {
  const { theme } = useData()
  const site = computed(() => theme.value.site || {})

  const featured = computed(() => {
    const configured = Array.isArray(site.value.featuredSlugs) ? site.value.featuredSlugs : []
    const picked = configured.map((slug) => bySlug.get(slug)).filter(Boolean)
    return picked.length ? picked : items.slice(0, 3)
  })

  const latest = computed(() => items.slice(0, site.value.homeLimit || 6))

  function getArticle(slug) {
    return bySlug.get(slug) || null
  }

  function getAdjacent(slug) {
    const index = items.findIndex((item) => item.slug === slug)
    if (index < 0) return { previous: null, next: null }

    return {
      previous: index > 0 ? items[index - 1] : null,
      next: index < items.length - 1 ? items[index + 1] : null,
    }
  }

  return {
    ...articlesData,
    items,
    tags,
    categories,
    featured,
    latest,
    getArticle,
    getAdjacent,
  }
}

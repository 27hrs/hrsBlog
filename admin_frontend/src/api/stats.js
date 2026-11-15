import { fetchAllPublishedArticles } from './articles.js'
import { listLocalArticles, mergeArticleCollections } from '@/services/localLibrary.js'

const PREVIEW_ENABLED =
  import.meta.env.VITE_DASHBOARD_PREVIEW === 'true' || import.meta.env.DEV === true

const PREVIEW_PV = [
  186, 212, 197, 244, 268, 231, 287, 312, 296, 348, 331, 374, 402, 389, 431, 467, 452, 498, 521,
  486, 542, 576, 559, 603, 628, 611, 656, 691, 674, 718,
]
const PREVIEW_UV = [
  92, 108, 101, 124, 139, 118, 147, 159, 152, 178, 169, 192, 205, 199, 221, 236, 229, 252, 264, 243,
  277, 294, 286, 307, 318, 311, 336, 352, 341, 366,
]

function startOfToday() {
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  return now
}

function buildPreviewTrend(days) {
  const today = startOfToday()
  const labels = []
  const views = []
  const visitors = []

  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date(today)
    date.setDate(today.getDate() - offset)
    labels.push(`${date.getMonth() + 1}/${date.getDate()}`)
    const index = PREVIEW_PV.length - days + (days - 1 - offset)
    views.push(PREVIEW_PV[Math.max(0, index)] || 0)
    visitors.push(PREVIEW_UV[Math.max(0, index)] || 0)
  }

  return {
    labels,
    series: [
      { key: 'views', label: '访问量', data: views, color: 'brand' },
      { key: 'visitors', label: '访客数', data: visitors, color: 'secondary' },
    ],
    preview: true,
  }
}

function buildArticleTrend(articles, days) {
  const today = startOfToday()
  const labels = []
  const counts = []

  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date(today)
    date.setDate(today.getDate() - offset)
    labels.push(`${date.getMonth() + 1}/${date.getDate()}`)
    const dayStart = new Date(date).getTime()
    const dayEnd = dayStart + 86_400_000
    counts.push(
      articles.filter((article) => {
        const timestamp = new Date(article.publishedAt || 0).getTime()
        return timestamp >= dayStart && timestamp < dayEnd
      }).length,
    )
  }

  return {
    labels,
    series: [{ key: 'published', label: '发布文章', data: counts, color: 'brand' }],
    preview: false,
  }
}

function deriveRecentArticles(articles) {
  return articles.slice(0, 5).map((article) => ({
    id: article.id,
    slug: article.slug,
    title: article.title,
    status: article.status,
    publishedAt: article.publishedAt,
    updatedAt: article.updatedAt,
    coverUrl: article.coverUrl,
    tags: article.tags || [],
    category: article.localMeta?.category,
  }))
}

function deriveTodos(articles, drafts) {
  const todos = []

  if (!articles.length) {
    todos.push({
      id: 'first-post',
      title: '发表第一篇博客文章',
      description: '让访客和搜索引擎看到你的第一篇文章。',
      action: { label: '去写作', to: { name: 'article-create' } },
      tone: 'brand',
    })
  }

  if (drafts > 0) {
    todos.push({
      id: 'drafts',
      title: `完善 ${drafts} 篇本地草稿`,
      description: '草稿记录保存在当前浏览器，可复用内容新建文章。',
      action: { label: '查看草稿', to: { name: 'article-list', query: { status: 'DRAFT' } } },
      tone: 'warning',
    })
  }

  const missingCover = articles.filter((article) => !article.coverUrl).length
  if (missingCover > 0) {
    todos.push({
      id: 'covers',
      title: `${missingCover} 篇文章缺少封面`,
      description: '补上封面可以让文章列表和分享卡片更完整。',
      action: { label: '查看文章', to: { name: 'article-list' } },
      tone: 'info',
    })
  }

  if (articles.length > 0 && !todos.length) {
    todos.push({
      id: 'keep-writing',
      title: '内容状态很健康',
      description: '所有已发布文章都有摘要和封面，继续记录吧。',
      action: { label: '写新文章', to: { name: 'article-create' } },
      tone: 'success',
    })
  }

  return todos.slice(0, 3)
}

/**
 * Dashboard data is assembled from the APIs that exist today. Visitor and
 * comment metrics light up only when their planned endpoints become available;
 * a clearly-labelled preview series keeps the chart useful during local dev.
 */
export async function loadDashboardData() {
  const collection = await fetchAllPublishedArticles()
  const articles = mergeArticleCollections(collection.items)
  const published = articles.filter((article) => article.status === 'PUBLISHED')
  const localDrafts = listLocalArticles().filter((entry) => entry.status === 'DRAFT').length

  const publishedThisMonth = published.filter((article) => {
    const date = new Date(article.publishedAt || 0)
    const now = new Date()
    return date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear()
  }).length

  const trendPreview = PREVIEW_ENABLED
  const trend = trendPreview
    ? buildPreviewTrend(30)
    : buildArticleTrend(published, 30)

  return {
    stats: {
      articles: collection.totalElements ?? published.length,
      comments: null,
      visitors: null,
      drafts: localDrafts,
      publishedThisMonth,
    },
    preview: trendPreview,
    trend,
    recentArticles: deriveRecentArticles(published),
    recentComments: [],
    todos: deriveTodos(published, localDrafts),
    capabilities: {
      stats: false,
      comments: false,
    },
  }
}

export async function loadDashboardTrend(days = 14) {
  const collection = await fetchAllPublishedArticles()
  const articles = collection.items || []
  const published = mergeArticleCollections(articles).filter(
    (article) => article.status === 'PUBLISHED',
  )
  return PREVIEW_ENABLED ? buildPreviewTrend(days) : buildArticleTrend(published, days)
}

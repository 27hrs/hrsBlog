import { ApiError, request } from './client.js'

export const ARTICLE_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
export const MAX_PAGE_SIZE = 50
export const MAX_PAGES = 100

export function assertArticleSlug(slug) {
  if (typeof slug !== 'string' || !ARTICLE_SLUG_PATTERN.test(slug) || slug.length > 200) {
    throw new ApiError('文章 slug 不合法', { code: 'INVALID_SLUG' })
  }
  return slug
}

function isRecord(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function assertPageShape(data) {
  if (!isRecord(data) || !Array.isArray(data.items)) {
    throw new ApiError('文章列表响应格式不正确', { code: 'INVALID_RESPONSE' })
  }

  return {
    items: data.items,
    page: Number.isInteger(data.page) ? data.page : 0,
    size: Number.isInteger(data.size) ? data.size : data.items.length,
    totalElements: Number.isInteger(data.totalElements) ? data.totalElements : data.items.length,
    totalPages: Number.isInteger(data.totalPages) ? data.totalPages : 1,
  }
}

function assertDetailShape(data) {
  if (!isRecord(data) || typeof data.title !== 'string' || typeof data.content !== 'string') {
    throw new ApiError('文章详情响应格式不正确', { code: 'INVALID_RESPONSE' })
  }

  if (data.status && data.status !== 'PUBLISHED') {
    throw new ApiError('文章未发布', { status: 404, code: 'ARTICLE_NOT_FOUND' })
  }

  return data
}

/**
 * GET /visitors/articles
 */
export async function listArticles({
  baseUrl,
  page = 0,
  size = 10,
  tag = '',
  timeout,
  retries,
  signal,
  fetchImpl,
} = {}) {
  if (!Number.isInteger(page) || page < 0) {
    throw new ApiError('page 必须是非负整数', { code: 'INVALID_QUERY' })
  }

  if (!Number.isInteger(size) || size < 1 || size > MAX_PAGE_SIZE) {
    throw new ApiError(`size 必须在 1–${MAX_PAGE_SIZE} 之间`, { code: 'INVALID_QUERY' })
  }

  const data = await request('/visitors/articles', {
    baseUrl,
    query: { page, size, tag },
    timeout,
    retries,
    signal,
    fetchImpl,
  })

  return assertPageShape(data)
}

/**
 * GET /visitors/articles/{slug}
 */
export async function getArticle(
  slug,
  { baseUrl, timeout, retries, signal, fetchImpl } = {},
) {
  assertArticleSlug(slug)

  const data = await request(`/visitors/articles/${encodeURIComponent(slug)}`, {
    baseUrl,
    timeout,
    retries,
    signal,
    fetchImpl,
  })

  return assertDetailShape(data)
}

/**
 * Walk every page and return deduplicated summaries.
 * The backend caps `size` at 50, so this is also the safe page size.
 */
export async function listAllArticles({
  baseUrl,
  pageSize = MAX_PAGE_SIZE,
  limit = 2000,
  tag = '',
  onPage,
  timeout,
  retries,
  signal,
  fetchImpl,
} = {}) {
  const seen = new Set()
  const items = []
  let page = 0
  let totalPages = 1

  while (page < totalPages && page < MAX_PAGES && items.length < limit) {
    const result = await listArticles({
      baseUrl,
      page,
      size: pageSize,
      tag,
      timeout,
      retries,
      signal,
      fetchImpl,
    })

    totalPages = Math.max(1, result.totalPages)

    for (const item of result.items) {
      if (!isRecord(item) || typeof item.slug !== 'string') continue
      if (seen.has(item.slug)) continue
      seen.add(item.slug)
      items.push(item)
      if (items.length >= limit) break
    }

    onPage?.({ page, totalPages, received: result.items.length, total: items.length })
    page += 1
  }

  return items
}

/**
 * Fetch summaries first, then details with bounded concurrency.
 * A 404 while fetching means the article was unpublished between calls; skip it.
 */
export async function getArticleDetails(
  summaries,
  { baseUrl, concurrency = 4, onProgress, timeout, retries, signal, fetchImpl } = {},
) {
  const details = []
  const failures = []
  let cursor = 0

  async function worker() {
    while (cursor < summaries.length) {
      const index = cursor
      cursor += 1
      const summary = summaries[index]

      try {
        const detail = await getArticle(summary.slug, {
          baseUrl,
          timeout,
          retries,
          signal,
          fetchImpl,
        })
        details[index] = detail
      } catch (error) {
        if (error instanceof ApiError && error.isNotFound) {
          failures[index] = { slug: summary.slug, reason: 'not-found' }
          continue
        }
        failures[index] = { slug: summary.slug, reason: error?.message || 'unknown' }
      }

      onProgress?.({ done: details.filter(Boolean).length, total: summaries.length })
    }
  }

  await Promise.all(
    Array.from({ length: Math.max(1, concurrency) }, () => worker()),
  )

  return {
    details: details.filter(Boolean),
    failures: failures.filter(Boolean),
  }
}

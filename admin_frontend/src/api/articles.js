import { request } from './client.js'

const PUBLIC_PAGE_SIZE = 50

function normalizeContentFormat(value) {
  return value === 'MARKDOWN' ? value : 'MARKDOWN'
}

function normalizeTagNames(tags) {
  if (!Array.isArray(tags)) return []
  return [...new Set(tags.map((tag) => String(tag).trim()).filter(Boolean))].slice(0, 10)
}

export async function listPublishedArticles({ page = 0, size = 10, tag } = {}) {
  return request('/visitors/articles', {
    query: { page, size: Math.min(Math.max(Number(size) || 10, 1), PUBLIC_PAGE_SIZE), tag },
    silent: false,
  })
}

export async function getPublishedArticle(slug) {
  return request(`/visitors/articles/${encodeURIComponent(slug)}`, { silent: true })
}

/**
 * Visitor list caps page size at 50. The management fallback needs the complete
 * published set, so it walks pages serially rather than firing a burst of calls.
 */
export async function fetchAllPublishedArticles({ maxPages = 80 } = {}) {
  const collected = []
  let totalPages = 0

  for (let page = 0; page < maxPages; page += 1) {
    const response = await listPublishedArticles({ page, size: PUBLIC_PAGE_SIZE })
    const items = Array.isArray(response?.items) ? response.items : []
    collected.push(...items)
    totalPages = Number(response?.totalPages) || 0
    if (page + 1 >= totalPages) break
  }

  return {
    items: collected,
    totalElements: collected.length,
    reachedPageLimit: totalPages > maxPages,
  }
}

export function createArticle(draft) {
  const payload = {
    title: String(draft.title || '').trim(),
    slug: String(draft.slug || '').trim(),
    summary: draft.summary?.trim() || null,
    content: draft.content || '',
    contentFormat: normalizeContentFormat(draft.contentFormat),
    coverUrl: draft.coverUrl || null,
    status: draft.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    tags: normalizeTagNames(draft.tags),
  }

  return request('/admin/articles', {
    method: 'POST',
    body: payload,
    silent: true,
  })
}

let adminCollectionUnavailable = false

/** Planned admin collection endpoint. Returns null when the API is unavailable. */
export async function tryAdminArticleCollection(query = {}) {
  if (adminCollectionUnavailable) return null

  try {
    const response = await request('/admin/articles', {
      query,
      silent: true,
      timeout: 8000,
    })
    adminCollectionUnavailable = false
    return response
  } catch (error) {
    if ([401].includes(error.status)) throw error
    if ([403, 404, 405].includes(error.status)) adminCollectionUnavailable = true
    return null
  }
}

/** 管理端文章详情（编辑表单取数）：文章不存在或已删除时抛 404 ApiError。 */
export function getAdminArticle(id) {
  return request(`/admin/articles/${encodeURIComponent(id)}`, { silent: true })
}

/** 管理端更新文章：全量字段覆盖，409 为 slug 冲突，404 为文章不存在/已删除。 */
export function updateArticle(id, draft) {
  return request(`/admin/articles/${id}`, {
    method: 'PUT',
    body: {
      title: draft.title,
      slug: draft.slug,
      summary: draft.summary || null,
      content: draft.content,
      contentFormat: normalizeContentFormat(draft.contentFormat),
      coverUrl: draft.coverUrl || null,
      status: draft.status,
      tags: normalizeTagNames(draft.tags),
    },
    silent: true,
  })
}

export function deleteArticle(id) {
  return request(`/admin/articles/${id}`, {
    method: 'DELETE',
    silent: true,
  })
}

/**
 * 批量删除文章（软删除）。返回逐条结果：成功数量与失败明细。
 * 使用 silent，由调用方统一反馈。
 */
export function deleteArticles(ids) {
  return request('/admin/articles/batch-delete', {
    method: 'POST',
    body: { ids: [...ids] },
    silent: true,
  })
}

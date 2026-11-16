import { request } from './client.js'

/** 友链审核状态，与后端 FriendLinkStatus 枚举保持一致。 */
const FRIEND_LINK_STATUSES = ['PENDING', 'APPROVED', 'REJECTED']

const DEFAULT_PAGE_SIZE = 20
const MAX_PAGE_SIZE = 100

function normalizeStatus(status) {
  return FRIEND_LINK_STATUSES.includes(status) ? status : ''
}

function normalizePageSize(size) {
  const value = Number(size)
  if (!Number.isFinite(value) || value <= 0) return DEFAULT_PAGE_SIZE
  return Math.min(Math.max(Math.floor(value), 1), MAX_PAGE_SIZE)
}

function optionalText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/**
 * 友链管理接口（ROLE_ADMIN）：
 * - GET /admin/friend-links → { items, page, size, totalElements, totalPages }
 *   - page 从 0 开始计数；status 可选，缺省表示不过滤。
 * - GET /admin/friend-links/{id} → 单条友链
 * - PUT /admin/friend-links/{id} → 全量字段覆盖，返回更新后的友链
 * - POST /admin/friend-links/{id}/review → 审核（APPROVED / REJECTED）
 * - DELETE /admin/friend-links/{id} → 删除友链
 *
 * 统一使用 silent，由调用方（FriendLinksView）负责提示。
 */
export function listFriendLinks({ status = '', page = 0, size = DEFAULT_PAGE_SIZE } = {}) {
  return request('/admin/friend-links', {
    query: {
      status: normalizeStatus(status),
      page: Math.max(Number(page) || 0, 0),
      size: normalizePageSize(size),
    },
    silent: true,
  })
}

/** 友链详情：用于重新取回一条友链的最新数据。 */
export function getFriendLink(id) {
  return request(`/admin/friend-links/${encodeURIComponent(id)}`, { silent: true })
}

/** 更新友链：全量字段覆盖，siteName / siteUrl / linkPageUrl 为必填。 */
export function updateFriendLink(id, payload) {
  const sortOrder = Number(payload?.sortOrder)

  return request(`/admin/friend-links/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: {
      siteName: optionalText(payload?.siteName),
      siteUrl: optionalText(payload?.siteUrl),
      linkPageUrl: optionalText(payload?.linkPageUrl),
      avatarUrl: optionalText(payload?.avatarUrl) || null,
      description: optionalText(payload?.description) || null,
      contact: optionalText(payload?.contact) || null,
      sortOrder: Number.isFinite(sortOrder) ? Math.trunc(sortOrder) : 0,
    },
    silent: true,
  })
}

/** 审核友链：只接受 APPROVED / REJECTED 两种结果。 */
export function reviewFriendLink(id, status) {
  return request(`/admin/friend-links/${encodeURIComponent(id)}/review`, {
    method: 'POST',
    body: { status: status === 'REJECTED' ? 'REJECTED' : 'APPROVED' },
    silent: true,
  })
}

/** 删除友链：删除后无法在界面中恢复。 */
export function deleteFriendLink(id) {
  return request(`/admin/friend-links/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    silent: true,
  })
}

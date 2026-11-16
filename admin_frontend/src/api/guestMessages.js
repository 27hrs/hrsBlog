import { request } from './client.js'

/** 留言审核状态，与后端 GuestMessage.MessageStatus 枚举保持一致。 */
const GUEST_MESSAGE_STATUSES = ['PENDING', 'APPROVED', 'REJECTED']

const DEFAULT_PAGE_SIZE = 20
const MAX_PAGE_SIZE = 100

/** 管理员回复正文的最大长度，与后端 GuestMessageReplyRequest 保持一致。 */
export const GUEST_MESSAGE_REPLY_LIMIT = 1000

function normalizeStatus(status) {
  return GUEST_MESSAGE_STATUSES.includes(status) ? status : ''
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
 * 留言管理接口（ROLE_ADMIN）：
 * - GET /admin/guest-messages → { items, page, size, totalElements, totalPages }
 *   - page 从 0 开始计数；status 可选，缺省表示不过滤。
 * - GET /admin/guest-messages/{id} → 单条留言（含回复）
 * - POST /admin/guest-messages/{id}/review → 审核（APPROVED / REJECTED）
 * - PUT /admin/guest-messages/{id}/reply → 回复（一条留言一条回复，再次提交即覆盖）
 * - DELETE /admin/guest-messages/{id} → 删除留言（后端会先删掉它的回复）
 *
 * 统一使用 silent，由调用方（GuestbookView）负责提示。
 */
export function listGuestMessages({ status = '', page = 0, size = DEFAULT_PAGE_SIZE } = {}) {
  return request('/admin/guest-messages', {
    query: {
      status: normalizeStatus(status),
      page: Math.max(Number(page) || 0, 0),
      size: normalizePageSize(size),
    },
    silent: true,
  })
}

/** 留言详情：用于重新取回一条留言的最新数据。 */
export function getGuestMessage(id) {
  return request(`/admin/guest-messages/${encodeURIComponent(id)}`, { silent: true })
}

/** 审核留言：只接受 APPROVED / REJECTED 两种结果。 */
export function reviewGuestMessage(id, status) {
  return request(`/admin/guest-messages/${encodeURIComponent(id)}/review`, {
    method: 'POST',
    body: { status: status === 'REJECTED' ? 'REJECTED' : 'APPROVED' },
    silent: true,
  })
}

/** 回复留言：一条留言至多一条回复，再次提交即覆盖旧回复。 */
export function replyGuestMessage(id, content) {
  return request(`/admin/guest-messages/${encodeURIComponent(id)}/reply`, {
    method: 'PUT',
    body: { content: optionalText(content) },
    silent: true,
  })
}

/** 删除留言：删除后无法在界面中恢复。 */
export function deleteGuestMessage(id) {
  return request(`/admin/guest-messages/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    silent: true,
  })
}

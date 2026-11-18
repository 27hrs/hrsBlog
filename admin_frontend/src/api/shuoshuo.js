import { request } from './client.js'

/** 说说可见性，与后端 Shuoshuo.Visibility 枚举保持一致。 */
export const SHUOSHUO_VISIBILITIES = ['PUBLIC', 'PRIVATE']

/** 已发布：会出现在博客前台的说说页。 */
export const SHUOSHUO_VISIBILITY_PUBLIC = 'PUBLIC'

/** 草稿：仅管理端可见。 */
export const SHUOSHUO_VISIBILITY_PRIVATE = 'PRIVATE'

/** 说说正文的最大长度，与后端 ShuoshuoCreateRequest / ShuoshuoUpdateRequest 保持一致。 */
export const SHUOSHUO_CONTENT_LIMIT = 2000

const DEFAULT_PAGE_SIZE = 20
const MAX_PAGE_SIZE = 50

/** 可见性归一化：非法值一律降级为空串（= 不过滤）。 */
export function normalizeShuoshuoVisibility(visibility) {
  return SHUOSHUO_VISIBILITIES.includes(visibility) ? visibility : ''
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
 * 说说管理接口（ROLE_ADMIN）：
 * - GET /admin/shuoshuo → { items, page, size, totalElements, totalPages }
 *   - page 从 0 开始计数；visibility 可选（PUBLIC / PRIVATE），缺省表示不过滤。
 * - GET /admin/shuoshuo/{id} → 单条说说
 * - POST /admin/shuoshuo → 新建（后端固定落成 PRIVATE 草稿）
 * - PUT /admin/shuoshuo/{id} → 更新正文
 * - POST /admin/shuoshuo/{id}/publish → 发布（publishedAt 为空时后端回填当前时间）
 * - POST /admin/shuoshuo/{id}/unpublish → 撤回（保留 publishedAt）
 * - DELETE /admin/shuoshuo/{id} → 删除说说
 *
 * 统一使用 silent，由调用方（ShuoshuoView）负责提示。
 */
export function listShuoshuo({ visibility = '', page = 0, size = DEFAULT_PAGE_SIZE } = {}) {
  return request('/admin/shuoshuo', {
    query: {
      visibility: normalizeShuoshuoVisibility(visibility),
      page: Math.max(Number(page) || 0, 0),
      size: normalizePageSize(size),
    },
    silent: true,
  })
}

/** 说说详情：用于重新取回一条说说的最新数据。 */
export function getShuoshuo(id) {
  return request(`/admin/shuoshuo/${encodeURIComponent(id)}`, { silent: true })
}

/** 新建说说：后端一律落成私有草稿，需要显式调用发布接口才会对外可见。 */
export function createShuoshuo(content) {
  return request('/admin/shuoshuo', {
    method: 'POST',
    body: { content: optionalText(content) },
    silent: true,
  })
}

/** 更新说说正文：只覆盖 content，其余字段（可见性 / 发布时间）保持不变。 */
export function updateShuoshuo(id, content) {
  return request(`/admin/shuoshuo/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: { content: optionalText(content) },
    silent: true,
  })
}

/** 发布说说：置为 PUBLIC；publishedAt 为空时后端回填当前时间。 */
export function publishShuoshuo(id) {
  return request(`/admin/shuoshuo/${encodeURIComponent(id)}/publish`, {
    method: 'POST',
    silent: true,
  })
}

/** 撤回说说：置为 PRIVATE，但保留 publishedAt。 */
export function unpublishShuoshuo(id) {
  return request(`/admin/shuoshuo/${encodeURIComponent(id)}/unpublish`, {
    method: 'POST',
    silent: true,
  })
}

/** 删除说说：硬删除，删除后无法在界面中恢复。 */
export function deleteShuoshuo(id) {
  return request(`/admin/shuoshuo/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    silent: true,
  })
}

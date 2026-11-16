import { ApiError, request } from './client.js'

/**
 * 后端对留言字段的长度限制，表单校验与后端 DTO 保持同一份定义。
 */
export const GUEST_MESSAGE_LIMITS = {
  nickname: 64,
  email: 255,
  content: 500,
}

/** 前台留言分页的每页条数上限，与后端 GuestMessageService 的上限一致。 */
export const GUEST_MESSAGE_MAX_PAGE_SIZE = 50

const REQUIRED_FIELDS = ['nickname', 'content']

/**
 * QQ 邮箱判定：用户名为纯数字、域名为 qq.com（不区分大小写）。
 *
 * 必须与后端 `com.example.hrsblog.common.QqAvatar` 的规则保持一致，
 * 否则前端预览出来的头像和前台展示的头像会对不上。
 */
const QQ_EMAIL_PATTERN = /^(\d+)@qq\.com$/i

/** 邮箱格式校验（与后端 jakarta @Email 的宽松口径接近）。 */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function isRecord(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function asText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/**
 * 由邮箱派生 QQ 公开头像地址；不是 QQ 邮箱时返回 null（前端回退默认头像）。
 *
 * 这是一个纯函数：同样的邮箱永远得到同样的地址，方便在表单里即时预览。
 */
export function deriveAvatarUrl(email) {
  const text = asText(email)
  if (!text) return null

  const matched = QQ_EMAIL_PATTERN.exec(text)
  if (!matched) return null

  return `https://q1.qlogo.cn/headimg_dl?dst_uin=${matched[1]}&spec=100`
}

/** 归一化管理员回复。 */
export function normalizeGuestMessageReply(item) {
  if (!isRecord(item)) return null

  const content = asText(item.content)
  if (!content) return null

  return {
    id: item.id ?? null,
    content,
    createdAt: item.createdAt || '',
  }
}

/** 归一化一条留言：只保留公开字段。 */
export function normalizeGuestMessage(item) {
  if (!isRecord(item)) return null

  const nickname = asText(item.nickname)
  const content = asText(item.content)
  if (!nickname || !content) return null

  const replies = Array.isArray(item.replies)
    ? item.replies.map(normalizeGuestMessageReply).filter(Boolean)
    : []

  return {
    id: item.id ?? null,
    nickname,
    avatarUrl: asText(item.avatarUrl) || null,
    content,
    createdAt: item.createdAt || '',
    replies,
  }
}

/** 分页响应结构校验：与后端 PageResponse 对齐。 */
function assertPageShape(data) {
  if (!isRecord(data) || !Array.isArray(data.items)) {
    throw new ApiError('留言列表响应格式不正确', { code: 'INVALID_RESPONSE' })
  }

  return {
    items: data.items.map(normalizeGuestMessage).filter(Boolean),
    page: Number(data.page) || 0,
    size: Number(data.size) || 0,
    totalElements: Number(data.totalElements) || 0,
    totalPages: Number(data.totalPages) || 0,
  }
}

/**
 * 提交留言前的本地兜底校验，避免把明显不合法的内容发到后端。
 */
export function buildGuestMessagePayload(payload) {
  if (!isRecord(payload)) {
    throw new ApiError('留言内容不完整', { code: 'INVALID_PAYLOAD' })
  }

  const body = {
    nickname: asText(payload.nickname),
    email: asText(payload.email),
    content: asText(payload.content),
  }

  for (const field of REQUIRED_FIELDS) {
    if (!body[field]) {
      throw new ApiError(`留言缺少必填字段：${field}`, { code: 'INVALID_PAYLOAD' })
    }
  }

  for (const field of Object.keys(GUEST_MESSAGE_LIMITS)) {
    if (body[field].length > GUEST_MESSAGE_LIMITS[field]) {
      throw new ApiError(`留言字段 ${field} 超出长度限制`, { code: 'INVALID_PAYLOAD' })
    }
  }

  if (body.email && !EMAIL_PATTERN.test(body.email)) {
    throw new ApiError('留言邮箱格式不正确', { code: 'INVALID_PAYLOAD' })
  }

  return body
}

/**
 * GET /visitors/guest-messages
 *
 * 返回已通过审核的留言分页列表（page 从 0 开始计数）。
 */
export async function fetchGuestMessages({
  baseUrl,
  page = 0,
  size,
  timeout,
  retries = 1,
  signal,
  fetchImpl,
} = {}) {
  const data = await request('/visitors/guest-messages', {
    baseUrl,
    query: { page: Math.max(Number(page) || 0, 0), size },
    timeout,
    retries,
    signal,
    fetchImpl,
  })

  return assertPageShape(data)
}

/**
 * POST /visitors/guest-messages
 *
 * 访客提交留言。失败时抛出 ApiError：
 * - `code === 40000`：`details` 是 `{ 字段名: 中文错误 }`
 * - `code === 42901`：提交过于频繁
 */
export async function submitGuestMessage(payload, { baseUrl, timeout, signal, fetchImpl } = {}) {
  const body = buildGuestMessagePayload(payload)

  return request('/visitors/guest-messages', {
    baseUrl,
    method: 'POST',
    body,
    timeout,
    // 提交不重试：慢响应时重复提交会产生重复留言。
    retries: 0,
    signal,
    fetchImpl,
  })
}

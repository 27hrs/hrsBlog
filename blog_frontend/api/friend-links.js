import { ApiError, request } from './client.js'

/**
 * 后端对友链字段的长度限制，表单校验与同步脚本共用同一份定义。
 */
export const FRIEND_LINK_LIMITS = {
  siteName: 100,
  siteUrl: 500,
  linkPageUrl: 500,
  avatarUrl: 500,
  description: 500,
  contact: 200,
}

const REQUIRED_FIELDS = ['siteName', 'siteUrl', 'linkPageUrl']

function isRecord(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function asText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/**
 * 只保留公开字段。`contact` 属于站长私有信息，
 * 公开列表接口不会返回它，这里也不允许它进入构建产物。
 */
export function normalizeFriendLink(item) {
  if (!isRecord(item)) return null

  const siteName = asText(item.siteName)
  const siteUrl = asText(item.siteUrl)
  if (!siteName || !siteUrl) return null

  return {
    siteName,
    siteUrl,
    linkPageUrl: asText(item.linkPageUrl),
    avatarUrl: asText(item.avatarUrl) || null,
    description: asText(item.description),
  }
}

function assertListShape(data) {
  if (!Array.isArray(data)) {
    throw new ApiError('友链列表响应格式不正确', { code: 'INVALID_RESPONSE' })
  }

  return data.map(normalizeFriendLink).filter(Boolean)
}

/**
 * 后端只接受 http:// 与 https:// 地址。
 */
export function isHttpUrl(value) {
  try {
    const url = new URL(String(value || '').trim())
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

/**
 * GET /visitors/friend-links
 *
 * 返回已通过审核的友链列表；元素已归一化为公开字段。
 */
export async function fetchPublicFriendLinks({
  baseUrl,
  timeout,
  retries,
  signal,
  fetchImpl,
} = {}) {
  const data = await request('/visitors/friend-links', {
    baseUrl,
    timeout,
    retries,
    signal,
    fetchImpl,
  })

  return assertListShape(data)
}

/**
 * 提交友链申请前的本地兜底校验，避免把明显不合法的内容发到后端。
 */
export function buildFriendLinkPayload(payload) {
  if (!isRecord(payload)) {
    throw new ApiError('友链申请内容不完整', { code: 'INVALID_PAYLOAD' })
  }

  const body = {
    siteName: asText(payload.siteName),
    siteUrl: asText(payload.siteUrl),
    linkPageUrl: asText(payload.linkPageUrl),
    avatarUrl: asText(payload.avatarUrl),
    description: asText(payload.description),
    contact: asText(payload.contact),
  }

  for (const field of REQUIRED_FIELDS) {
    if (!body[field]) {
      throw new ApiError(`友链申请缺少必填字段：${field}`, { code: 'INVALID_PAYLOAD' })
    }
  }

  for (const field of ['siteUrl', 'linkPageUrl']) {
    if (!isHttpUrl(body[field])) {
      throw new ApiError(`友链申请字段 ${field} 不是合法的 http(s) 地址`, {
        code: 'INVALID_PAYLOAD',
      })
    }
  }

  return body
}

/**
 * POST /visitors/friend-links
 *
 * 访客提交友链申请。失败时抛出 ApiError：
 * - `code === 40000`：`details` 是 `{ 字段名: 中文错误 }`
 * - `code === 42900`：提交过于频繁
 */
export async function submitFriendLink(
  payload,
  { baseUrl, timeout, signal, fetchImpl } = {},
) {
  const body = buildFriendLinkPayload(payload)

  return request('/visitors/friend-links', {
    baseUrl,
    method: 'POST',
    body,
    timeout,
    // 提交不重试：慢响应时重复提交会产生重复记录。
    retries: 0,
    signal,
    fetchImpl,
  })
}

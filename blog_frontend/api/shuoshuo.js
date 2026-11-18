import { ApiError, request } from './client.js'

/** 说说列表每页条数：与后端分页上限保持一致。 */
export const SHUOSHUO_PAGE_SIZE = 50

/** 兜底的最大页数：防止后端一直返回满页时把构建卡死。 */
const MAX_PAGES = 200

/** Asia/Shanghai 自 1991 年起固定为 UTC+8，构建期统一按这个偏移解读后端时间。 */
const SHANGHAI_OFFSET = '+08:00'

/** 已经带时区/偏移（Z 或 ±HH:MM）的串原样保留，不再做二次换算。 */
const HAS_TIME_ZONE = /(?:Z|[+-]\d{2}:?\d{2})$/i

/** 后端 LocalDateTime：`2026-11-18T22:50:14`（可能省略秒，也可能只到日期）。 */
const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{1,2}):(\d{2})(?::(\d{2}))?)?/

function isRecord(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function asText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/**
 * 把后端的 LocalDateTime 归一化成带 +08:00 的 ISO 串。
 *
 * 后端存的是不带时区的本地时间，构建期一律按 Asia/Shanghai 解读；
 * 若串本身已经带时区/偏移（例如 `2026-11-18T22:50:14Z`）则原样保留。
 * 无法解析时返回空串，调用方据此跳过这条数据。
 */
export function toShanghaiIso(value) {
  const text = String(value ?? '').trim()
  if (!text) return ''

  if (HAS_TIME_ZONE.test(text)) return text

  const matched = LOCAL_DATE_TIME.exec(text)
  if (!matched) return ''

  const [, year, month, day, hour = '00', minute = '00', second = '00'] = matched
  const normalized = `${year}-${month}-${day}T${String(hour).padStart(2, '0')}:${minute}:${second}${SHANGHAI_OFFSET}`

  // 用 Date 兜一次校验：非法日期（例如 13 月）会得到 Invalid Date。
  if (Number.isNaN(new Date(normalized).getTime())) return ''

  return normalized
}

/** 归一化一条说说：只保留公开字段 id / content / publishedAt。 */
export function normalizeShuoshuo(item) {
  if (!isRecord(item)) return null

  const content = asText(item.content)
  if (!content) return null

  return {
    id: item.id ?? null,
    content,
    publishedAt: toShanghaiIso(item.publishedAt),
  }
}

/** 分页响应结构校验：与后端 PageResponse 对齐。 */
function assertPageShape(data) {
  if (!isRecord(data) || !Array.isArray(data.items)) {
    throw new ApiError('说说列表响应格式不正确', { code: 'INVALID_RESPONSE' })
  }

  return {
    items: data.items.map(normalizeShuoshuo).filter(Boolean),
    page: Number(data.page) || 0,
    size: Number(data.size) || 0,
    totalElements: Number(data.totalElements) || 0,
    totalPages: Number(data.totalPages) || 0,
  }
}

/**
 * GET /visitors/shuoshuo
 *
 * 返回公开说说的分页列表（page 从 0 开始计数）。
 *
 * 构建期需要完整的说说列表，所以这里内部循环翻页并聚合成一个数组：
 * 每页 size 条，直到某一页返回条数不足一页，或已经取满 totalElements。
 * 元素已归一化为公开字段，时间统一成 +08:00 的 ISO 串。
 */
export async function fetchPublicShuoshuo({
  baseUrl,
  timeout,
  retries,
  signal,
  fetchImpl,
  pageSize = SHUOSHUO_PAGE_SIZE,
} = {}) {
  const size = Math.max(Number(pageSize) || SHUOSHUO_PAGE_SIZE, 1)
  const collected = []

  for (let page = 0; page < MAX_PAGES; page += 1) {
    const data = await request('/visitors/shuoshuo', {
      baseUrl,
      query: { page, size },
      timeout,
      retries,
      signal,
      fetchImpl,
    })

    const result = assertPageShape(data)
    collected.push(...result.items)

    // 这一页没填满：说明已经是最后一页。
    if (result.items.length < size) break
    // 已经取满总量：不必再翻页。
    if (result.totalElements > 0 && collected.length >= result.totalElements) break
  }

  return collected
}

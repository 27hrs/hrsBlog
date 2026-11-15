import { ApiError, request } from './client.js'

function isRecord(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function assertSettingsShape(data) {
  if (!isRecord(data)) {
    throw new ApiError('站点设置响应格式不正确', { code: 'INVALID_RESPONSE' })
  }

  // 兼容 `{ values: {...} }` 与直接扁平 map 两种响应形态
  const values = isRecord(data.values) ? data.values : data

  return values
}

/**
 * GET /visitors/settings
 *
 * 返回 `is_public=true` 的站点配置键值（值已按 value_type 反序列化）。
 */
export async function fetchPublicSettings({
  baseUrl,
  timeout,
  retries,
  signal,
  fetchImpl,
} = {}) {
  const data = await request('/visitors/settings', {
    baseUrl,
    timeout,
    retries,
    signal,
    fetchImpl,
  })
  return assertSettingsShape(data)
}

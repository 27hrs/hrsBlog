import { request } from './client.js'

/**
 * 站点设置接口对接：
 * - GET /admin/settings → `{ values: {key: value} }`，读取全部配置（含私密项）；
 * - PUT /admin/settings，body `{ settings: {key: value} }`，整体替换保存；
 *   未知 key 或值不合法时返回 400，data 中带字段级错误。
 *
 * 404/405（旧后端未提供接口）按“远端不可用”处理，store 会回退 localStorage，
 * 不视为致命错误。
 */
export async function fetchSiteSettings() {
  try {
    return await request('/admin/settings', { silent: true, timeout: 7000 })
  } catch (error) {
    if (error.status === 401) throw error
    return null
  }
}

export function saveSiteSettings(settings) {
  return request('/admin/settings', {
    method: 'PUT',
    body: { settings },
    silent: true,
    timeout: 10000,
  })
}

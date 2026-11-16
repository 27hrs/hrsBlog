import { request } from './client.js'

/**
 * 通用系统日志接口：
 * - GET /admin/logs → { items, page, size, totalElements, totalPages }
 *   - page 从 0 开始计数；
 *   - type / status 为可选筛选，缺省表示不过滤。
 *
 * 日志由后端在每次自动化动作（如部署通知 Webhook 投递）后写入，
 * 页面只读，不提供删除与修改。
 */
export function listSystemLogs({ page = 0, size = 20, type = '', status = '' } = {}) {
  return request('/admin/logs', {
    query: { page, size, type, status },
    silent: true,
  })
}

import { request } from './client.js'

export function uploadAsset(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request('/admin/assets', {
    method: 'POST',
    body: formData,
    silent: true,
    timeout: 45000,
  })
}

export function listAssets({ page = 0, size = 20, kind = 'IMAGE', status = 'ACTIVE' } = {}) {
  return request('/admin/assets', {
    query: { page, size, kind, status },
    silent: true,
  })
}

export function deleteAsset(id, { hard = false } = {}) {
  return request(`/admin/assets/${id}`, {
    method: 'DELETE',
    query: { hard },
    silent: true,
  })
}

export function validateImageFile(file, maxBytes = 5 * 1024 * 1024) {
  const allowed = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!file) return '请选择图片文件'
  if (!allowed.includes(file.type)) return '仅支持 JPG、PNG、WebP 或 GIF 图片'
  if (file.size > maxBytes) return '图片大小不能超过 5 MB'
  return ''
}

import { request } from './client.js'

export function login(credentials) {
  return request('/admin/auth/login', {
    method: 'POST',
    body: credentials,
    skipAuth: true,
    silent: true,
    timeout: 12000,
  })
}

export function changePassword(payload) {
  return request('/admin/auth/password', {
    method: 'PUT',
    body: payload,
    silent: true,
  })
}

/** 修改管理员用户名：成功后返回新的访问令牌与用户信息。 */
export function updateUsername(payload) {
  return request('/admin/auth/username', {
    method: 'PUT',
    body: payload,
    silent: true,
  })
}

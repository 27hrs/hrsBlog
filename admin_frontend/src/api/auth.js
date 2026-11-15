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

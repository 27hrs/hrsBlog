import { request } from './client.js'

/**
 * Settings are designed to be backend-agnostic. The current backend has no
 * settings controller, so callers persist the same shape to localStorage and
 * treat a 404/405 as "remote unavailable" rather than a fatal error.
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

import { clearSession, getAuthToken } from './session.js'
import { notify } from './notify.js'

const RAW_API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')
export const API_BASE = RAW_API_BASE || '/api'

/**
 * Resolve backend-relative assets such as `/assets/image/ab/...`.
 * When the admin uses the nginx/Vite proxy the asset lives on this origin.
 * When an absolute API base is configured, the asset is served by that origin.
 */
export function resolveAssetUrl(url) {
  if (!url) return ''
  if (/^(?:https?:)?\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) {
    return url
  }

  const origin = /^https?:\/\//i.test(API_BASE) ? new URL(API_BASE).origin : window.location.origin
  return `${origin}${url.startsWith('/') ? url : `/${url}`}`
}

export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = options.code ?? null
    this.status = options.status ?? 0
    this.fieldErrors = options.fieldErrors || null
    this.data = options.data ?? null
  }
}

export function isFeatureUnavailable(error) {
  return error instanceof ApiError && (error.status === 404 || error.status === 405)
}

let unauthorizedHandler = null
let unauthorizedDispatched = false

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === 'function' ? handler : null
}

function dispatchUnauthorized() {
  if (unauthorizedDispatched) return
  unauthorizedDispatched = true
  clearSession()
  unauthorizedHandler?.()
  // Allow the next authenticated request to surface a fresh 401 after re-login.
  setTimeout(() => {
    unauthorizedDispatched = false
  }, 1200)
}

function buildQuery(query) {
  if (!query) return ''
  const search = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    search.set(key, String(value))
  })
  const serialized = search.toString()
  return serialized ? `?${serialized}` : ''
}

function fieldErrorsFrom(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return null
  const entries = Object.entries(data).filter(([, value]) => typeof value === 'string')
  return entries.length ? Object.fromEntries(entries) : null
}

/**
 * Thin fetch client around the backend `{code, message, data}` envelope.
 * - injects Bearer token
 * - turns every failure into ApiError
 * - routes 401 through one central handler
 * - emits user-facing toasts unless `silent` is explicitly requested
 */
export async function request(
  path,
  {
    method = 'GET',
    body,
    query,
    headers: extraHeaders,
    timeout = 15000,
    silent = false,
    skipAuth = false,
  } = {},
) {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)
  const token = skipAuth ? '' : getAuthToken()
  const headers = {
    Accept: 'application/json',
    ...extraHeaders,
  }

  let payload
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  try {
    const options = {
      method,
      headers,
      signal: controller.signal,
    }
    if (payload !== undefined) options.body = payload

    const response = await fetch(`${API_BASE}${path}${buildQuery(query)}`, options)

    const text = await response.text()
    let envelope = null
    if (text) {
      try {
        envelope = JSON.parse(text)
      } catch {
        envelope = null
      }
    }

    if (!response.ok || (envelope && envelope.code !== 0)) {
      const message =
        envelope?.message ||
        (response.status === 401
          ? '登录状态已失效，请重新登录'
          : response.status === 403
            ? '没有执行此操作的权限'
            : response.status === 413
              ? '文件超过大小限制'
              : response.status === 415
                ? '不支持的文件类型'
                : `请求失败（HTTP ${response.status}）`)
      const error = new ApiError(message, {
        code: envelope?.code,
        status: response.status,
        fieldErrors: fieldErrorsFrom(envelope?.data),
        data: envelope?.data,
      })

      if (response.status === 401 && !skipAuth) {
        dispatchUnauthorized()
      } else if (!silent) {
        notify(message, 'error')
      }
      throw error
    }

    return envelope?.data ?? null
  } catch (error) {
    if (error instanceof ApiError) throw error

    const aborted = error?.name === 'AbortError'
    const message = aborted ? '请求超时，请检查后端服务' : '网络连接失败，请检查后端服务'
    const apiError = new ApiError(message, { status: 0 })
    if (!silent) notify(message, 'error')
    throw apiError
  } finally {
    clearTimeout(timeoutId)
  }
}

export { notify }

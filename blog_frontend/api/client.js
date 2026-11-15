/**
 * Small, dependency-free fetch client for the HrsBlog visitor API.
 *
 * It is used by the build-time content sync (Node 22+) and can also run in the
 * browser unchanged. The backend wraps every response in `{ code, message, data }`,
 * so the client unwraps `data` on success and turns every failure into ApiError.
 */

export class ApiError extends Error {
  constructor(message, { status = 0, code = null, details = null, url = '', cause } = {}) {
    super(message, cause ? { cause } : undefined)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
    this.url = url
  }

  get isNotFound() {
    return this.status === 404
  }
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

function parseJson(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function httpMessage(status) {
  if (status === 400) return '请求参数不合法'
  if (status === 401) return '登录状态已失效'
  if (status === 403) return '没有访问权限'
  if (status === 404) return '资源不存在'
  if (status === 413) return '文件超过大小限制'
  if (status === 415) return '不支持的文件类型'
  if (status === 429) return '请求过于频繁，请稍后再试'
  if (status >= 500) return '服务暂时不可用'
  return `请求失败（HTTP ${status}）`
}

export function normalizeBaseUrl(baseUrl) {
  return String(baseUrl || '').trim().replace(/\/+$/, '')
}

export function buildUrl(baseUrl, path, query) {
  const base = normalizeBaseUrl(baseUrl)
  if (!base) throw new ApiError('API 地址未配置')

  const suffix = path.startsWith('/') ? path : `/${path}`
  const params = new URLSearchParams()

  if (query && typeof query === 'object') {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue
      params.set(key, String(value))
    }
  }

  const serialized = params.toString()
  return serialized ? `${base}${suffix}?${serialized}` : `${base}${suffix}`
}

/**
 * @param {string} path API path below /api, e.g. `/visitors/articles`
 * @param {object} options
 * @param {string} options.baseUrl e.g. http://localhost:27000/api
 * @param {number} [options.timeout] per-attempt timeout in ms
 * @param {number} [options.retries] retries for network / 429 / 5xx failures
 * @param {AbortSignal} [options.signal]
 * @param {Function} [options.fetchImpl] injectable for tests
 */
export async function request(path, options = {}) {
  const {
    baseUrl,
    method = 'GET',
    query,
    body,
    headers: extraHeaders,
    timeout = 8000,
    retries = 1,
    signal,
    fetchImpl = globalThis.fetch,
  } = options

  if (typeof fetchImpl !== 'function') {
    throw new ApiError('当前运行环境没有 fetch')
  }

  const url = buildUrl(baseUrl, path, query)
  let lastError = null

  for (let attempt = 0; attempt <= retries; attempt++) {
    const controller = new AbortController()
    let timer = null
    let forwardAbort = null

    try {
      if (signal) {
        if (signal.aborted) throw new ApiError('请求已取消', { url, cause: signal.reason })
        forwardAbort = () => controller.abort(signal.reason)
        signal.addEventListener('abort', forwardAbort, { once: true })
      }

      timer = setTimeout(() => controller.abort(new Error('timeout')), timeout)

      const headers = { Accept: 'application/json', ...extraHeaders }
      let payload = body
      if (body !== undefined && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json'
        payload = JSON.stringify(body)
      }

      const response = await fetchImpl(url, {
        method,
        headers,
        body: payload,
        signal: controller.signal,
      })

      const text = await response.text()
      const envelope = parseJson(text)

      if (!response.ok) {
        const error = new ApiError(envelope?.message || httpMessage(response.status), {
          status: response.status,
          code: envelope?.code ?? null,
          details: envelope?.data ?? null,
          url,
        })

        if ((response.status === 429 || response.status >= 500) && attempt < retries) {
          lastError = error
          await sleep(250 * (attempt + 1))
          continue
        }

        throw error
      }

      if (!envelope || typeof envelope !== 'object') {
        throw new ApiError('响应不是有效的 JSON', { status: response.status, url })
      }

      if (envelope.code !== 0) {
        throw new ApiError(envelope.message || '请求失败', {
          status: response.status,
          code: envelope.code,
          details: envelope.data,
          url,
        })
      }

      return envelope.data ?? null
    } catch (error) {
      if (signal?.aborted) {
        throw new ApiError('请求已取消', { url, cause: error })
      }

      if (error instanceof ApiError) {
        if (error.status === 0 && attempt < retries) {
          lastError = error
          await sleep(250 * (attempt + 1))
          continue
        }
        throw error
      }

      const aborted = controller.signal.aborted
      lastError = new ApiError(aborted ? '请求超时' : '网络连接失败', { url, cause: error })

      if (attempt < retries) {
        await sleep(250 * (attempt + 1))
        continue
      }

      throw lastError
    } finally {
      if (timer) clearTimeout(timer)
      if (signal && forwardAbort) signal.removeEventListener('abort', forwardAbort)
    }
  }

  throw lastError || new ApiError('请求失败', { url })
}

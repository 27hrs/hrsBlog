/** URL helpers shared by cover images, OG tags and the content sync. */

const SAFE_PROTOCOLS = /^(?:https?:|data:image\/|blob:)/i

export function isSafeAssetUrl(url) {
  if (typeof url !== 'string') return false
  const value = url.trim()
  if (!value) return false
  if (value.startsWith('/')) return !value.startsWith('//')
  return SAFE_PROTOCOLS.test(value)
}

export function resolveAssetUrl(url, assetBase = '') {
  if (!isSafeAssetUrl(url)) return ''

  const value = url.trim()
  if (/^(?:https?:|data:image\/|blob:)/i.test(value)) return value

  const base = String(assetBase || '').trim().replace(/\/+$/, '')
  const path = value.startsWith('/') ? value : `/${value}`
  return base ? `${base}${path}` : path
}

export function toAbsoluteUrl(url, siteUrl = '') {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const base = String(siteUrl || '').trim().replace(/\/+$/, '')
  if (!base) return url
  return `${base}${url.startsWith('/') ? url : `/${url}`}`
}

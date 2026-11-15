/** Resolve runtime configuration without assuming Node or Vite. */

function readEnv(name) {
  if (typeof process !== 'undefined' && process.env) {
    return process.env[name]
  }
  return undefined
}

export function resolveApiBase(explicit, fallback = '') {
  if (explicit !== undefined) return String(explicit).trim()
  return String(readEnv('BLOG_API_BASE') ?? fallback).trim()
}

export function resolveSiteUrl(explicit, fallback = 'http://localhost:27080') {
  const raw = explicit !== undefined ? explicit : readEnv('BLOG_SITE_URL')
  const value = raw === undefined || raw === null || String(raw).trim() === '' ? fallback : raw
  return String(value).trim().replace(/\/+$/, '')
}

export function resolveAssetBase(explicit, fallback = '') {
  const value = explicit !== undefined ? explicit : (readEnv('BLOG_ASSET_BASE') ?? fallback)
  return String(value).trim().replace(/\/+$/, '')
}

export function isTruthyFlag(value) {
  return value === true || value === '1' || value === 'true' || value === 'yes'
}

export function toPositiveInt(value, fallback) {
  const number = Number.parseInt(String(value ?? ''), 10)
  return Number.isFinite(number) && number > 0 ? number : fallback
}

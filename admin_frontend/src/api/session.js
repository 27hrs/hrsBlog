export const SESSION_STORAGE_KEY = 'hrsblog-admin-session'

function normalizeSession(value) {
  if (!value || typeof value !== 'object' || typeof value.token !== 'string') {
    return null
  }

  return {
    token: value.token,
    tokenType: value.tokenType || 'Bearer',
    expiresAt: Number.isFinite(value.expiresAt) ? value.expiresAt : 0,
    user: value.user && typeof value.user === 'object' ? value.user : null,
  }
}

export function readSession() {
  try {
    return normalizeSession(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY) || 'null'))
  } catch {
    return null
  }
}

export function writeSession(session) {
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearSession() {
  localStorage.removeItem(SESSION_STORAGE_KEY)
}

export function getAuthToken() {
  return readSession()?.token || ''
}

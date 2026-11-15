import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginRequest } from '@/api/auth.js'
import {
  clearSession as clearStoredSession,
  readSession,
  writeSession,
} from '@/api/session.js'

export const useAuthStore = defineStore('auth', () => {
  const initial = readSession()

  const token = ref(initial?.token || '')
  const tokenType = ref(initial?.tokenType || 'Bearer')
  const expiresAt = ref(initial?.expiresAt || 0)
  const user = ref(initial?.user || null)
  const lastError = ref('')

  const isAuthenticated = computed(() => Boolean(token.value))
  const displayName = computed(() => user.value?.displayName || user.value?.username || '管理员')
  const username = computed(() => user.value?.username || '')
  const initials = computed(() => {
    const name = displayName.value.trim()
    if (!name) return 'H'
    return /[\u3400-\u9fff]/.test(name) ? name.slice(-1) : name.slice(0, 2).toUpperCase()
  })

  function hasValidSession() {
    if (!token.value) return false
    if (expiresAt.value && Date.now() >= expiresAt.value) {
      clearSession()
      return false
    }
    return true
  }

  async function login(credentials) {
    lastError.value = ''
    const payload = await loginRequest(credentials)
    if (!payload?.accessToken) {
      throw new Error('登录响应缺少访问令牌')
    }

    token.value = payload.accessToken
    tokenType.value = payload.tokenType || 'Bearer'
    expiresAt.value = payload.expiresIn ? Date.now() + Number(payload.expiresIn) * 1000 : 0
    user.value = payload.user || null

    writeSession({
      token: token.value,
      tokenType: tokenType.value,
      expiresAt: expiresAt.value,
      user: user.value,
    })

    return payload
  }

  function clearSession() {
    token.value = ''
    tokenType.value = 'Bearer'
    expiresAt.value = 0
    user.value = null
    lastError.value = ''
    clearStoredSession()
  }

  return {
    token,
    tokenType,
    expiresAt,
    user,
    lastError,
    isAuthenticated,
    displayName,
    username,
    initials,
    hasValidSession,
    login,
    clearSession,
  }
})

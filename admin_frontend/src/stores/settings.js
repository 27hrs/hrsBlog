import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchSiteSettings, saveSiteSettings } from '@/api/settings.js'
import {
  createDefaultSettings,
  settingSections,
  validateSettings,
} from '@/config/settingsRegistry.js'

export const SETTINGS_STORAGE_KEY = 'hrsblog-admin-site-settings'

function clone(value) {
  return JSON.parse(JSON.stringify(value ?? null))
}

function serialize(values) {
  return JSON.stringify(values)
}

function mergeWithDefaults(candidate = {}) {
  const defaults = createDefaultSettings()
  const merged = { ...defaults }
  Object.keys(defaults).forEach((key) => {
    if (candidate[key] !== undefined) {
      merged[key] = Array.isArray(candidate[key]) ? clone(candidate[key]) : candidate[key]
    }
  })
  return merged
}

function parseStored(candidate) {
  return candidate && typeof candidate === 'object' ? candidate : {}
}

function extractSettingsPayload(payload) {
  if (!payload || typeof payload !== 'object') return null
  if (payload.values && typeof payload.values === 'object') return payload.values
  if (payload.settings && !Array.isArray(payload.settings)) return payload.settings
  if (Array.isArray(payload)) {
    const result = {}
    payload.forEach((row) => {
      if (!row || typeof row !== 'object') return
      const key = row.key || row.configKey
      if (!key) return
      let value = row.value ?? row.configValue
      if (row.valueType === 'NUMBER') value = Number(value)
      if (row.valueType === 'BOOLEAN') value = String(value) === 'true'
      if (row.valueType === 'JSON') {
        try {
          value = JSON.parse(value)
        } catch {
          value = null
        }
      }
      result[key] = value
    })
    return result
  }
  if (Array.isArray(payload.items)) return extractSettingsPayload(payload.items)
  return payload
}

function readLocalSettings() {
  try {
    const parsed = JSON.parse(localStorage.getItem(SETTINGS_STORAGE_KEY) || 'null')
    if (!parsed) return null
    if (parsed.values && typeof parsed.values === 'object') {
      return {
        values: parsed.values,
        savedAt: parsed.savedAt || null,
        source: parsed.source || 'local',
      }
    }
    return { values: parseStored(parsed), savedAt: null, source: 'local' }
  } catch {
    return null
  }
}

function writeLocalSettings(values, source) {
  const savedAt = new Date().toISOString()
  try {
    localStorage.setItem(
      SETTINGS_STORAGE_KEY,
      JSON.stringify({ values: clone(values), savedAt, source }),
    )
  } catch {
    // Storage can be unavailable in strict privacy modes; the in-memory form still works.
  }
  return savedAt
}

export const useSettingsStore = defineStore('settings', () => {
  const values = reactive(createDefaultSettings())
  const source = ref('local')
  const loading = ref(false)
  const saving = ref(false)
  const initialized = ref(false)
  const lastSavedAt = ref(null)
  const loadError = ref('')
  const errors = reactive({})
  const savedSnapshot = ref(serialize(values))

  const isDirty = computed(() => serialize(values) !== savedSnapshot.value)
  const errorCount = computed(() => Object.keys(errors).length)
  const dirtyFieldCount = computed(() => {
    const saved = JSON.parse(savedSnapshot.value || '{}')
    let count = 0
    Object.keys(values).forEach((key) => {
      if (serialize(values[key]) !== serialize(saved[key])) count += 1
    })
    return count
  })
  const sourceLabel = computed(() =>
    source.value === 'remote' ? '服务端同步' : '保存在本机',
  )

  function replaceValues(next) {
    Object.assign(values, mergeWithDefaults(next))
  }

  function clearErrors() {
    Object.keys(errors).forEach((key) => delete errors[key])
  }

  function clearFieldError(key) {
    if (errors[key]) delete errors[key]
    Object.keys(errors).forEach((errorKey) => {
      if (errorKey.startsWith(`${key}.`)) delete errors[errorKey]
    })
  }

  function updateField(key, value) {
    values[key] = value
    clearFieldError(key)
  }

  function snapshot() {
    savedSnapshot.value = serialize(values)
    lastSavedAt.value = new Date().toISOString()
  }

  async function load() {
    if (loading.value) return
    loading.value = true
    loadError.value = ''
    const localSnapshot = readLocalSettings()

    try {
      const remote = extractSettingsPayload(await fetchSiteSettings())
      if (remote && Object.keys(remote).length) {
        replaceValues(remote)
        source.value = 'remote'
      } else if (localSnapshot) {
        replaceValues(localSnapshot.values)
        source.value = 'local'
        lastSavedAt.value = localSnapshot.savedAt
      } else {
        replaceValues(createDefaultSettings())
        source.value = 'local'
      }
    } catch (error) {
      loadError.value = error.message || '远端设置读取失败，已切换到本机缓存'
      if (localSnapshot) {
        replaceValues(localSnapshot.values)
        lastSavedAt.value = localSnapshot.savedAt
      }
      source.value = 'local'
    } finally {
      clearErrors()
      snapshot()
      if (!lastSavedAt.value) lastSavedAt.value = new Date().toISOString()
      loading.value = false
      initialized.value = true
    }
  }

  async function saveAll() {
    if (saving.value) return { ok: false, errors: { ...errors }, message: '正在保存' }
    const validationErrors = validateSettings(values)
    clearErrors()
    if (Object.keys(validationErrors).length) {
      Object.assign(errors, validationErrors)
      return { ok: false, errors: validationErrors, message: '设置中存在未完善的内容' }
    }

    saving.value = true
    let localOnly
    let failure = null

    try {
      await saveSiteSettings(clone(values))
      source.value = 'remote'
      localOnly = false
    } catch (error) {
      if (error.status === 401) {
        saving.value = false
        return { ok: false, unauthorized: true, message: error.message }
      }
      if (error.status === 400 && error.fieldErrors) {
        Object.assign(errors, error.fieldErrors)
        saving.value = false
        return { ok: false, errors: error.fieldErrors, message: error.message }
      }
      localOnly = true
      source.value = 'local'
      failure = error
    }

    const savedAt = writeLocalSettings(values, source.value)
    lastSavedAt.value = savedAt
    savedSnapshot.value = serialize(values)
    saving.value = false

    return {
      ok: true,
      localOnly,
      error: failure,
      savedAt,
      message: localOnly ? '设置已保存到当前浏览器' : '设置已同步到服务端',
    }
  }

  function resetToDefaults() {
    replaceValues(createDefaultSettings())
    clearErrors()
  }

  function sectionDirty(sectionKey) {
    const section = settingSections.find((item) => item.key === sectionKey)
    if (!section) return false
    const saved = JSON.parse(savedSnapshot.value || '{}')
    return section.fields.some((field) => serialize(values[field.key]) !== serialize(saved[field.key]))
  }

  return {
    values,
    source,
    sourceLabel,
    loading,
    saving,
    initialized,
    lastSavedAt,
    loadError,
    errors,
    isDirty,
    errorCount,
    dirtyFieldCount,
    load,
    saveAll,
    updateField,
    clearErrors,
    clearFieldError,
    resetToDefaults,
    sectionDirty,
  }
})

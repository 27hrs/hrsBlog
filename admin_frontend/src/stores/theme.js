import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const THEME_KEY = 'hrsblog-theme'
const MODES = ['system', 'light', 'dark']

function readMode() {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    return MODES.includes(saved) ? saved : 'system'
  } catch {
    return 'system'
  }
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref(readMode())
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemPrefersDark = ref(media.matches)
  const initialized = ref(false)

  const resolvedTheme = computed(() => {
    if (mode.value === 'system') return systemPrefersDark.value ? 'dark' : 'light'
    return mode.value
  })

  const isDark = computed(() => resolvedTheme.value === 'dark')
  const modeLabel = computed(
    () => ({ system: '跟随系统', light: '浅色', dark: '深色' })[mode.value] || '跟随系统',
  )
  const modeIcon = computed(
    () => ({ system: 'monitor', light: 'sun', dark: 'moon' })[mode.value] || 'monitor',
  )

  function applyTheme() {
    const root = document.documentElement
    root.dataset.theme = resolvedTheme.value
    root.dataset.themeMode = mode.value
    root.style.colorScheme = resolvedTheme.value
  }

  function setMode(nextMode) {
    if (!MODES.includes(nextMode)) return
    const previous = mode.value
    mode.value = nextMode
    try {
      localStorage.setItem(THEME_KEY, nextMode)
    } catch {
      // Private mode can reject storage; the in-memory preference still applies.
    }

    if (previous === nextMode) return

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (document.startViewTransition && !prefersReducedMotion) {
      document.startViewTransition(() => applyTheme())
    } else {
      applyTheme()
    }
  }

  function handleSystemChange(event) {
    systemPrefersDark.value = event.matches
    if (mode.value === 'system') applyTheme()
  }

  function handleStorage(event) {
    if (event.key !== THEME_KEY) return
    const next = MODES.includes(event.newValue) ? event.newValue : 'system'
    if (next !== mode.value) {
      mode.value = next
      applyTheme()
    }
  }

  function init() {
    if (initialized.value) return
    initialized.value = true
    applyTheme()
    media.addEventListener('change', handleSystemChange)
    window.addEventListener('storage', handleStorage)
  }

  applyTheme()

  return {
    mode,
    resolvedTheme,
    isDark,
    modeLabel,
    modeIcon,
    init,
    setMode,
  }
})

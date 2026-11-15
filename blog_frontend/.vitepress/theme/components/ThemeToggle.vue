<script setup>
import { computed, onMounted, ref } from 'vue'
import AppIcon from './AppIcon.vue'

const isDark = ref(false)
const ready = ref(false)

onMounted(() => {
  isDark.value = document.documentElement.dataset.theme === 'dark'
  ready.value = true
})

const label = computed(() => (isDark.value ? '切换到浅色模式' : '切换到深色模式'))

function toggle() {
  const next = isDark.value ? 'light' : 'dark'
  const root = document.documentElement

  root.dataset.theme = next
  root.classList.toggle('dark', next === 'dark')
  root.style.colorScheme = next

  try {
    localStorage.setItem('hrsblog-theme', next)
  } catch {
    // Private mode: switching still works for this page view.
  }

  isDark.value = next === 'dark'
}
</script>

<template>
  <button
    class="icon-button icon-button--dark theme-toggle"
    type="button"
    :aria-label="label"
    :title="label"
    :aria-pressed="ready ? isDark : undefined"
    @click="toggle"
  >
    <AppIcon :name="isDark ? 'sun' : 'moon'" :size="17" />
  </button>
</template>

<style scoped>
.theme-toggle {
  transition:
    color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease),
    background-color var(--dur-fast) var(--ease),
    transform var(--dur) var(--ease);
}

.theme-toggle:hover {
  transform: rotate(-8deg);
}
</style>

import { ref } from 'vue'
import { defineStore } from 'pinia'

let sequence = 0
const timers = new Map()

export const useToastStore = defineStore('toast', () => {
  const items = ref([])

  function dismiss(id) {
    const index = items.value.findIndex((item) => item.id === id)
    if (index >= 0) {
      items.value[index].leaving = true
      const timer = timers.get(id)
      if (timer) clearTimeout(timer)
      timers.delete(id)
      setTimeout(() => {
        items.value = items.value.filter((item) => item.id !== id)
      }, 180)
    }
  }

  function push(message, options = {}) {
    if (!message) return null
    const id = ++sequence
    const type = options.type || 'info'
    const duration = options.duration ?? (type === 'error' ? 5200 : type === 'warning' ? 4200 : 3200)
    const item = { id, message, type, title: options.title || '' }
    items.value.push(item)

    if (duration > 0) {
      const timer = setTimeout(() => dismiss(id), duration)
      timers.set(id, timer)
    }
    return id
  }

  function success(message, options = {}) {
    return push(message, { ...options, type: 'success' })
  }

  function error(message, options = {}) {
    return push(message, { ...options, type: 'error' })
  }

  function warning(message, options = {}) {
    return push(message, { ...options, type: 'warning' })
  }

  function info(message, options = {}) {
    return push(message, { ...options, type: 'info' })
  }

  function clear() {
    timers.forEach((timer) => clearTimeout(timer))
    timers.clear()
    items.value = []
  }

  return { items, push, dismiss, success, error, warning, info, clear }
})

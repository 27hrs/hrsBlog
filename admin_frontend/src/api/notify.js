let notifier = null

export function setNotifier(handler) {
  notifier = typeof handler === 'function' ? handler : null
}

export function notify(message, type = 'info') {
  if (notifier) {
    notifier(message, type)
    return
  }
  if (import.meta.env.DEV) {
    // The notifier is registered by main.js. This branch only helps early boot errors.
    console.warn(`[notify:${type}] ${message}`)
  }
}

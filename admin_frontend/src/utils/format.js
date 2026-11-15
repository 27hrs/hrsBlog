const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const shortDateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: 'short',
  day: 'numeric',
})

const weekdayFormatter = new Intl.DateTimeFormat('zh-CN', { weekday: 'long' })

export function toDate(value) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value) {
  const date = toDate(value)
  return date ? dateFormatter.format(date).replaceAll('/', '-') : '—'
}

export function formatDateTime(value) {
  const date = toDate(value)
  return date ? dateTimeFormatter.format(date).replaceAll('/', '-') : '—'
}

export function formatShortDate(value) {
  const date = toDate(value)
  return date ? shortDateFormatter.format(date) : '—'
}

export function formatWeekday(value = new Date()) {
  const date = toDate(value)
  return date ? weekdayFormatter.format(date) : ''
}

export function formatRelative(value) {
  const date = toDate(value)
  if (!date) return '—'

  const diff = Date.now() - date.getTime()
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff >= 0 && diff < minute) return '刚刚'
  if (diff >= 0 && diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff >= 0 && diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff >= 0 && diff < day * 7) return `${Math.floor(diff / day)} 天前`
  return formatDate(date)
}

export function formatNumber(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return new Intl.NumberFormat('zh-CN').format(number)
}

export function formatCompactNumber(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  if (Math.abs(number) < 1000) return String(number)
  return new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(number)
}

export function formatBytes(bytes) {
  const size = Number(bytes)
  if (!Number.isFinite(size) || size < 0) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function readingTime(markdown = '') {
  const text = String(markdown).replace(/```[\s\S]*?```/g, ' ')
  const cjk = (text.match(/[\u3400-\u9fff]/g) || []).length
  const words = (text.replace(/[\u3400-\u9fff]/g, ' ').match(/[a-z0-9]+/gi) || []).length
  const minutes = Math.max(1, Math.ceil(cjk / 350 + words / 220))
  return `${minutes} 分钟阅读`
}

export function greetingFor(date = new Date()) {
  const hour = date.getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

export function isSameDay(left, right) {
  const a = toDate(left)
  const b = toDate(right)
  if (!a || !b) return false
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

export function daysUntil(value) {
  const date = toDate(value)
  if (!date) return null
  return Math.ceil((date.getTime() - Date.now()) / 86_400_000)
}

export function startOfDay(value = new Date()) {
  const date = toDate(value) || new Date()
  const clone = new Date(date)
  clone.setHours(0, 0, 0, 0)
  return clone
}

export function isoLocalDate(value = new Date()) {
  const date = toDate(value) || new Date()
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

import { computed } from 'vue'

/**
 * VitePress exposes JSON imports as modules. Using import.meta.glob keeps the
 * build working even before the first sync (empty items, not a broken import).
 */
const generated = import.meta.glob('../../data/friend-links.json', { eager: true })
const rawData = Object.values(generated)[0]?.default || {}

const items = Array.isArray(rawData.items) ? rawData.items : []

export const friendLinksData = {
  generatedAt: rawData.generatedAt || '',
  source: rawData.source || 'empty',
  count: items.length,
  items,
}

export function useFriendLinks() {
  const count = computed(() => items.length)

  return {
    ...friendLinksData,
    items,
    count,
  }
}

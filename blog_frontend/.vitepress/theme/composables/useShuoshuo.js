import { computed } from 'vue'

/**
 * VitePress exposes JSON imports as modules. Using import.meta.glob keeps the
 * build working even before the first sync (empty items, not a broken import).
 *
 * 说说正文在构建期已经被 markdown-it 编译成静态 HTML（`html` 字段），
 * 浏览器端只负责渲染，不再解析 Markdown。
 */
const generated = import.meta.glob('../../data/shuoshuo.json', { eager: true })
const rawData = Object.values(generated)[0]?.default || {}

const items = Array.isArray(rawData.items) ? rawData.items : []

export const shuoshuoData = {
  generatedAt: rawData.generatedAt || '',
  source: rawData.source || 'empty',
  count: items.length,
  items,
}

export function useShuoshuo() {
  const count = computed(() => items.length)

  return {
    ...shuoshuoData,
    items,
    count,
  }
}

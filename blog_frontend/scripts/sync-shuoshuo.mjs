import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { fetchPublicShuoshuo } from '../api/shuoshuo.js'
import { isTruthyFlag, resolveApiBase, toPositiveInt } from '../api/config.js'
import { loadEnv } from './lib/load-env.mjs'
import { renderMarkdown } from './lib/markdown.mjs'
import { nowInShanghai } from './lib/content.mjs'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const DATA_DIR = path.join(ROOT, '.vitepress', 'data')
const OUTPUT_FILE = path.join(DATA_DIR, 'shuoshuo.json')

loadEnv(ROOT)

const apiBase = resolveApiBase()
const strict = isTruthyFlag(process.env.BLOG_SYNC_STRICT)
const timeout = toPositiveInt(process.env.BLOG_SYNC_TIMEOUT, 5000)

function log(message) {
  console.log(`[shuoshuo] ${message}`)
}

/**
 * `.vitepress/data/shuoshuo.json` 是公开说说的唯一落盘处：
 * `useShuoshuo.js` 在构建期读取它并渲染 `/shuoshuo/` 页面。
 * 文件写入采用“临时文件 + rename”，避免中断留下半个 JSON。
 */
async function writeAtomically(payload) {
  await mkdir(DATA_DIR, { recursive: true })
  const tmp = `${OUTPUT_FILE}.${process.pid}.tmp`
  await writeFile(tmp, `${JSON.stringify(payload, null, 2)}\n`, 'utf8')
  await rename(tmp, OUTPUT_FILE)
}

async function readExisting() {
  try {
    return JSON.parse(await readFile(OUTPUT_FILE, 'utf8'))
  } catch {
    return null
  }
}

function cachedItems(existing) {
  return Array.isArray(existing?.items) ? existing.items : []
}

/**
 * 说说的正文是 Markdown：构建期编译成静态 HTML（html:false 防注入），
 * 浏览器端直接用 v-html 渲染，不再做客户端 Markdown 解析。
 */
function buildItems(raw) {
  return raw
    .map((item) => ({
      id: item.id ?? null,
      content: item.content,
      publishedAt: item.publishedAt,
      html: renderMarkdown(item.content),
    }))
    // 时间新的在前；没有时间的排在最后。
    .sort((a, b) => {
      if (!a.publishedAt && !b.publishedAt) return 0
      if (!a.publishedAt) return 1
      if (!b.publishedAt) return -1
      const byTime = b.publishedAt.localeCompare(a.publishedAt)
      return byTime || String(b.id ?? '').localeCompare(String(a.id ?? ''))
    })
}

async function main() {
  if (!apiBase) {
    log('BLOG_API_BASE 为空，跳过说说同步（保留已有数据）')
    const items = cachedItems(await readExisting())
    await writeAtomically({
      generatedAt: nowInShanghai(),
      source: 'local',
      count: items.length,
      items,
    })
    return
  }

  try {
    log(`从 ${apiBase} 读取公开说说`)
    const raw = await fetchPublicShuoshuo({ baseUrl: apiBase, timeout, retries: 1 })
    const items = buildItems(raw)
    await writeAtomically({
      generatedAt: nowInShanghai(),
      source: items.length > 0 ? 'api' : 'api-empty',
      count: items.length,
      items,
    })
    log(`已写入 ${items.length} 条说说`)
  } catch (error) {
    if (strict) {
      throw new Error(`说说同步失败（BLOG_SYNC_STRICT=1）：${error.message}`)
    }
    log(`同步失败，回退本地缓存：${error.message}`)
    const items = cachedItems(await readExisting())
    await writeAtomically({
      generatedAt: nowInShanghai(),
      source: 'fallback',
      count: items.length,
      items,
    })
  }
}

main().catch((error) => {
  console.error(`[shuoshuo] ${error.message}`)
  process.exit(1)
})

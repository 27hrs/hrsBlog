import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { fetchPublicFriendLinks } from '../api/friend-links.js'
import { isTruthyFlag, resolveApiBase, toPositiveInt } from '../api/config.js'
import { loadEnv } from './lib/load-env.mjs'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const DATA_DIR = path.join(ROOT, '.vitepress', 'data')
const OUTPUT_FILE = path.join(DATA_DIR, 'friend-links.json')

loadEnv(ROOT)

const apiBase = resolveApiBase()
const strict = isTruthyFlag(process.env.BLOG_SYNC_STRICT)
const timeout = toPositiveInt(process.env.BLOG_SYNC_TIMEOUT, 5000)

function log(message) {
  console.log(`[friend-links] ${message}`)
}

/**
 * `.vitepress/data/friend-links.json` 是公开友链的唯一落盘处：
 * `useFriendLinks.js` 在构建期读取它并渲染 `/links/` 页面。
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

async function main() {
  if (!apiBase) {
    log('BLOG_API_BASE 为空，跳过友链同步（保留已有数据）')
    const items = cachedItems(await readExisting())
    await writeAtomically({
      generatedAt: new Date().toISOString(),
      source: 'local',
      count: items.length,
      items,
    })
    return
  }

  try {
    log(`从 ${apiBase} 读取公开友链`)
    const items = await fetchPublicFriendLinks({ baseUrl: apiBase, timeout, retries: 1 })
    await writeAtomically({
      generatedAt: new Date().toISOString(),
      source: items.length > 0 ? 'api' : 'api-empty',
      count: items.length,
      items,
    })
    log(`已写入 ${items.length} 条友链`)
  } catch (error) {
    if (strict) {
      throw new Error(`友链同步失败（BLOG_SYNC_STRICT=1）：${error.message}`)
    }
    log(`同步失败，回退本地缓存：${error.message}`)
    const items = cachedItems(await readExisting())
    await writeAtomically({
      generatedAt: new Date().toISOString(),
      source: 'fallback',
      count: items.length,
      items,
    })
  }
}

main().catch((error) => {
  console.error(`[friend-links] ${error.message}`)
  process.exit(1)
})

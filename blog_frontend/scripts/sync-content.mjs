import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { getArticleDetails, listAllArticles } from '../api/articles.js'
import { isTruthyFlag, resolveApiBase, resolveAssetBase, resolveSiteUrl, toPositiveInt } from '../api/config.js'
import { fixtureArticles } from './fixtures/articles.mjs'
import { loadEnv } from './lib/load-env.mjs'
import { writeContent } from './lib/writer.mjs'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const DATA_FILE = path.join(ROOT, '.vitepress', 'data', 'articles.json')
const ARTICLES_DIR = path.join(ROOT, 'articles')

loadEnv(ROOT)

const apiBase = resolveApiBase()
const siteUrl = resolveSiteUrl()
const assetBase = resolveAssetBase()
const strict = isTruthyFlag(process.env.BLOG_SYNC_STRICT)
const limit = toPositiveInt(process.env.BLOG_CONTENT_LIMIT, 2000)
const timeout = toPositiveInt(process.env.BLOG_SYNC_TIMEOUT, 5000)

function log(message) {
  console.log(`[sync] ${message}`)
}

async function readExistingIndex() {
  try {
    const data = JSON.parse(await readFile(DATA_FILE, 'utf8'))
    if (!Array.isArray(data.items) || data.items.length === 0) return null
    return data
  } catch {
    return null
  }
}

async function writeFixtures(reason) {
  log(`使用本地 fixtures（${reason}）`)
  const result = await writeContent({
    details: fixtureArticles,
    source: 'fixtures',
    siteUrl,
    assetBase,
  })

  if (result.errors.length) {
    for (const error of result.errors) log(`跳过 ${error.slug}：${error.reason}`)
  }

  log(`已写入 ${result.count} 篇 fixture 文章、封面索引与 feed`)
  return result
}

async function syncFromApi() {
  log(`从 ${apiBase} 读取访客文章`)

  const summaries = await listAllArticles({
    baseUrl: apiBase,
    limit,
    timeout,
    retries: 1,
    onPage: ({ page, totalPages, total }) => {
      log(`列表第 ${page + 1}/${totalPages} 页，累计 ${total} 篇`)
    },
  })

  log(`列表返回 ${summaries.length} 篇`)

  if (summaries.length === 0) {
    const result = await writeContent({
      details: [],
      source: 'api',
      siteUrl,
      assetBase,
    })
    log('API 中没有已发布文章，生成空站点索引')
    return result
  }

  const { details, failures } = await getArticleDetails(summaries, {
    baseUrl: apiBase,
    concurrency: 4,
    timeout,
    retries: 1,
  })

  if (details.length === 0) {
    throw new Error(`文章详情全部失败（${failures.length} 篇）`)
  }

  const failureRatio = failures.length / summaries.length
  if (failureRatio > 0.2) {
    throw new Error(`文章详情失败率过高：${failures.length}/${summaries.length}`)
  }

  for (const failure of failures) {
    log(`跳过 ${failure.slug}：${failure.reason}`)
  }

  const result = await writeContent({
    details,
    source: 'api',
    siteUrl,
    assetBase,
    apiBase,
  })

  if (result.warnings?.length) {
    for (const warning of result.warnings) log(warning)
  }

  if (result.errors.length) {
    for (const error of result.errors) log(`构建内容失败 ${error.slug}：${error.reason}`)
  }

  log(`已写入 ${result.count} 篇文章，同步时间 ${result.generatedAt}`)
  return result
}

async function main() {
  if (!apiBase) {
    if (strict) {
      throw new Error('BLOG_SYNC_STRICT=1 但 BLOG_API_BASE 为空')
    }

    const existing = await readExistingIndex()
    if (existing) {
      log(`BLOG_API_BASE 为空，保留已有的 ${existing.items.length} 篇文章`)
      return
    }

    await writeFixtures('BLOG_API_BASE 为空且没有缓存')
    return
  }

  try {
    await syncFromApi()
  } catch (error) {
    if (strict) throw error

    const existing = await readExistingIndex()
    if (existing) {
      log(`API 同步失败，保留缓存：${error.message}`)
      return
    }

    await writeFixtures(`API 不可用：${error.message}`)
  }
}

main().catch((error) => {
  console.error(`[sync] 失败：${error.message}`)
  if (error.details) console.error(JSON.stringify(error.details, null, 2))
  process.exitCode = 1
})

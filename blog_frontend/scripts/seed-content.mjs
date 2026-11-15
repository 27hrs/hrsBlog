import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveAssetBase, resolveSiteUrl } from '../api/config.js'
import { fixtureArticles } from './fixtures/articles.mjs'
import { loadEnv } from './lib/load-env.mjs'
import { writeContent } from './lib/writer.mjs'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
loadEnv(ROOT)

const siteUrl = resolveSiteUrl()
const assetBase = resolveAssetBase()

writeContent({
  details: fixtureArticles,
  source: 'seed',
  siteUrl,
  assetBase,
})
  .then((result) => {
    if (result.errors.length) {
      for (const error of result.errors) {
        console.warn(`[seed] 跳过 ${error.slug}：${error.reason}`)
      }
    }
    console.log(`[seed] 已写入 ${result.count} 篇演示文章到 articles/ 与 .vitepress/data/articles.json`)
    console.log(`[seed] feed 与 robots 已生成到 public/`)
  })
  .catch((error) => {
    console.error(`[seed] 失败：${error.message}`)
    process.exitCode = 1
  })

import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import MarkdownIt from 'markdown-it'
import { fetchPublicSettings } from '../api/settings.js'
import { isTruthyFlag, resolveApiBase, toPositiveInt } from '../api/config.js'
import { loadEnv } from './lib/load-env.mjs'
import { applySettingsToSiteConfig } from './lib/site-settings.mjs'
import { siteConfig } from '../.vitepress/site.config.mjs'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const DATA_DIR = path.join(ROOT, '.vitepress', 'data')
const OUTPUT_FILE = path.join(DATA_DIR, 'site-settings.json')

loadEnv(ROOT)

// 关于页正文构建期编译：html:false 禁用原始 HTML（与文章管线同策略，防注入）。
const markdown = new MarkdownIt({ html: false, linkify: true })

// 站外链接统一补 target/rel，避免跳失与 window.opener 风险。
const defaultLinkOpen =
  markdown.renderer.rules.link_open ||
  ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = token.attrGet('href') || ''
  if (/^https?:\/\//i.test(href)) {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener noreferrer')
  }
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/** 合并接口值并把 aboutContent 编译为 aboutHtml（SSG 预渲染进静态 HTML）。 */
function buildSiteConfig(values) {
  const merged = applySettingsToSiteConfig(siteConfig, values)
  const content = merged.about?.content
  merged.about = { content: content || '', html: content ? markdown.render(content) : '' }
  return merged
}

const apiBase = resolveApiBase()
const strict = isTruthyFlag(process.env.BLOG_SYNC_STRICT)
const timeout = toPositiveInt(process.env.BLOG_SYNC_TIMEOUT, 5000)

function log(message) {
  console.log(`[settings] ${message}`)
}

/**
 * `.vitepress/data/site-settings.json` 是“接口配置的唯一落盘处”：
 * `config.mjs` 在构建期读取它并与 site.config.mjs 默认值合并。
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

async function main() {
  if (!apiBase) {
    log('BLOG_API_BASE 为空，跳过站点设置同步（保留 site.config.mjs 默认值）')
    const existing = await readExisting()
    await writeAtomically({
      generatedAt: new Date().toISOString(),
      source: 'local',
      values: existing?.values ?? {},
      siteConfig: buildSiteConfig(existing?.values ?? {}),
    })
    return
  }

  try {
    log(`从 ${apiBase} 读取公开站点设置`)
    const values = await fetchPublicSettings({ baseUrl: apiBase, timeout, retries: 1 })
    const valueCount = Object.keys(values).length
    const merged = buildSiteConfig(values)
    await writeAtomically({
      generatedAt: new Date().toISOString(),
      source: valueCount > 0 ? 'api' : 'api-empty',
      values,
      siteConfig: merged,
    })
    log(`已应用 ${merged.settingsOverrides.length} 项配置覆盖（接口返回 ${valueCount} 项）`)
  } catch (error) {
    if (strict) {
      throw new Error(`站点设置同步失败（BLOG_SYNC_STRICT=1）：${error.message}`)
    }
    log(`同步失败，回退本地默认配置：${error.message}`)
    const existing = await readExisting()
    await writeAtomically({
      generatedAt: new Date().toISOString(),
      source: 'fallback',
      values: existing?.values ?? {},
      siteConfig: buildSiteConfig(existing?.values ?? {}),
    })
  }
}

main().catch((error) => {
  console.error(`[settings] ${error.message}`)
  process.exit(1)
})

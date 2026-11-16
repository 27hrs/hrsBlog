import { createHash } from 'node:crypto'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 计算「内容指纹」，供 watch-build.sh 判断是否需要重新构建。
 *
 * 只纳入真正影响产物的东西：
 *   - articles/*.md：sync-content.mjs 从后端拉取后生成的正文与 frontmatter
 *   - site-settings.json 的 values：站点设置的实际取值
 *
 * 刻意排除 generatedAt / source 这类每次同步都会变的字段，否则指纹永远变化，
 * 构建就退化成「每轮都全量重建」。
 */
const root = process.cwd()
const hash = createHash('sha256')

const articlesDir = join(root, 'articles')
try {
  const files = readdirSync(articlesDir).filter((name) => name.endsWith('.md')).sort()
  for (const name of files) {
    hash.update(name)
    hash.update(readFileSync(join(articlesDir, name)))
  }
} catch {
  // 目录还不存在：当作空内容
}

try {
  const payload = JSON.parse(readFileSync(join(root, '.vitepress/data/site-settings.json'), 'utf8'))
  hash.update(JSON.stringify(payload.values ?? {}))
} catch {
  // 设置文件尚未生成：忽略
}

process.stdout.write(hash.digest('hex'))

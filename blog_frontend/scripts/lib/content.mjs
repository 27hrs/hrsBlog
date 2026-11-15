import { inferCategoryFromTags, ARTICLE_CATEGORIES, categoryLabel } from '../../.vitepress/theme/utils/categories.js'
import { resolveAssetUrl, toAbsoluteUrl } from '../../api/assets.js'

const SHANGHAI_TIME_ZONE = 'Asia/Shanghai'
const TZ_OFFSET = '+08:00'

export function parseApiDateTime(value) {
  if (!value) return null

  const text = String(value).trim()
  if (!text) return null

  const hasTimeZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
  const hasTime = text.includes('T')
  const normalized = hasTimeZone
    ? text
    : hasTime
      ? `${text}${TZ_OFFSET}`
      : `${text}T00:00:00${TZ_OFFSET}`

  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function shanghaiParts(date) {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: SHANGHAI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })

  const parts = Object.fromEntries(
    formatter.formatToParts(date).filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]),
  )

  return {
    year: parts.year,
    month: parts.month,
    day: parts.day,
    hour: parts.hour === '24' ? '00' : parts.hour,
    minute: parts.minute,
    second: parts.second,
  }
}

export function toShanghaiIso(value) {
  const date = value instanceof Date ? value : parseApiDateTime(value)
  if (!date) return ''

  const parts = shanghaiParts(date)
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}${TZ_OFFSET}`
}

export function toDateLabel(value) {
  const iso = toShanghaiIso(value)
  if (!iso) return ''
  return iso.slice(0, 10).replaceAll('-', '.')
}

export function nowInShanghai() {
  return toShanghaiIso(new Date())
}

export function escapeXml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;')
}

export function stripMarkdown(markdown) {
  return String(markdown || '')
    .replace(/~~~[\s\S]*?~~~/g, ' ')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^>\s?/gm, '')
    .replace(/^[-*+]\s+/gm, '')
    .replace(/^\d+\.\s+/gm, '')
    .replace(/^\|.*\|$/gm, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[*_~]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

export function truncateText(value, maxLength) {
  const characters = Array.from(String(value || '').trim())
  if (characters.length <= maxLength) return characters.join('')
  return `${characters.slice(0, maxLength).join('').replace(/[，、；：,.;:\s]+$/, '')}…`
}

export function estimateReadingTime(markdown) {
  const text = stripMarkdown(markdown)
  const chinese = (text.match(/[\u3400-\u9fff]/g) || []).length
  const latinWords = (text.match(/[A-Za-z0-9]+/g) || []).length
  const codeLines = (markdown.match(/^(?:~~~|```)/gm) || []).length * 6
  const minutes = chinese / 350 + latinWords / 220 + codeLines * 0.08
  return Math.max(1, Math.round(minutes || 1))
}

export function countWords(markdown) {
  const text = stripMarkdown(markdown)
  const chinese = (text.match(/[\u3400-\u9fff]/g) || []).length
  const latinWords = (text.match(/[A-Za-z0-9]+/g) || []).length
  return chinese + latinWords
}

export function normalizeMarkdown(markdown, title) {
  let content = String(markdown || '').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim()

  content = content.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
  content = content.replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, '')

  content = content.replace(/<img\b[^>]*>/gi, (tag) => {
    const src = tag.match(/\bsrc=["']([^"']+)["']/i)?.[1]
    const alt = tag.match(/\balt=["']([^"']*)["']/i)?.[1] || ''
    return src ? `![${alt}](${src})` : ''
  })

  const lines = content.split('\n')
  if (lines.length && /^#\s+/.test(lines[0]) && lines[0].replace(/^#\s+/, '').trim() === title.trim()) {
    lines.shift()
  }
  content = lines.join('\n')

  content = content.replace(/^#(?!#)\s+/gm, '## ').trim()

  return content
}

function uniquePairs(pairs) {
  const seen = new Set()
  return pairs.filter((pair) => {
    if (!pair?.slug || seen.has(pair.slug)) return false
    seen.add(pair.slug)
    return true
  })
}

export function normalizeTags(tags) {
  if (!Array.isArray(tags)) return []

  return uniquePairs(
    tags
      .map((tag) => {
        if (typeof tag === 'string') {
          return { name: tag.trim(), slug: tag.trim().toLowerCase().replace(/\s+/g, '-') }
        }
        if (!tag || typeof tag !== 'object') return null
        const name = String(tag.name || '').trim()
        const slug = String(tag.slug || '').trim()
        return name && slug ? { name, slug } : null
      })
      .filter(Boolean),
  )
}

function yamlString(value) {
  return JSON.stringify(String(value ?? ''))
}

export function buildArticleRecord(detail, { assetBase = '', siteUrl = '' } = {}) {
  if (!detail || typeof detail !== 'object') throw new Error('文章详情为空')
  if (detail.contentFormat && detail.contentFormat !== 'MARKDOWN') {
    throw new Error(`不支持的正文格式：${detail.contentFormat}`)
  }

  const title = String(detail.title || '').trim()
  const slug = String(detail.slug || '').trim()
  if (!title) throw new Error('文章标题为空')
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) || slug.length > 200) {
    throw new Error(`slug 不合法：${slug}`)
  }

  const body = normalizeMarkdown(detail.content, title)
  if (!body) throw new Error('文章正文为空')

  const published = parseApiDateTime(detail.publishedAt || detail.createdAt)
  if (!published) throw new Error('文章发布时间无效')

  const updated = parseApiDateTime(detail.updatedAt) || published
  const text = stripMarkdown(body)
  const summary = truncateText(
    String(detail.summary || '').trim() || text,
    110,
  )
  const description = truncateText(summary, 160)
  const tags = normalizeTags(detail.tags)
  const category = inferCategoryFromTags(tags)
  const readingTime = estimateReadingTime(body)
  const wordCount = countWords(body)
  const cover = resolveAssetUrl(detail.coverUrl || '', assetBase)
  const coverAlt = String(detail.coverAlt || '').trim()
  const publishedAt = toShanghaiIso(published)
  const updatedAt = toShanghaiIso(updated)
  const url = `/articles/${slug}`

  const meta = {
    layout: 'article',
    title,
    slug,
    id: detail.id ?? null,
    date: publishedAt,
    publishedAt,
    updatedAt,
    description,
    summary,
    cover,
    coverAlt,
    category,
    readingTime,
    wordCount,
    tags,
    generatedBy: 'hrsblog-sync',
  }

  const markdown = [
    '---',
    `layout: article`,
    `title: ${yamlString(title)}`,
    `slug: ${yamlString(slug)}`,
    `id: ${detail.id ?? 'null'}`,
    `date: ${yamlString(publishedAt)}`,
    `publishedAt: ${yamlString(publishedAt)}`,
    `updatedAt: ${yamlString(updatedAt)}`,
    `description: ${yamlString(description)}`,
    `summary: ${yamlString(summary)}`,
    `cover: ${yamlString(cover)}`,
    `coverAlt: ${yamlString(coverAlt)}`,
    `category: ${yamlString(category)}`,
    `readingTime: ${readingTime}`,
    `wordCount: ${wordCount}`,
    `tags: ${JSON.stringify(tags)}`,
    `generatedBy: "hrsblog-sync"`,
    '---',
    '',
    body,
    '',
  ].join('\n')

  const item = {
    id: detail.id ?? null,
    title,
    slug,
    summary,
    cover,
    coverAlt,
    publishedAt,
    updatedAt,
    dateLabel: toDateLabel(published),
    tags,
    category,
    categoryLabel: categoryLabel(category),
    readingTime,
    wordCount,
    url,
  }

  return { meta, markdown, item, absoluteUrl: toAbsoluteUrl(url, siteUrl) }
}

export function buildIndexData(items, { source, siteUrl, generatedAt }) {
  const tagCounts = new Map()
  for (const item of items) {
    for (const tag of item.tags) {
      const current = tagCounts.get(tag.slug) || { name: tag.name, slug: tag.slug, count: 0 }
      current.count += 1
      tagCounts.set(tag.slug, current)
    }
  }

  const tags = [...tagCounts.values()].sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, 'zh-CN'))

  const categories = ARTICLE_CATEGORIES.map((category) => ({
    ...category,
    count: items.filter((item) => item.category === category.id).length,
  }))

  return {
    generatedAt,
    source,
    siteUrl,
    count: items.length,
    tags,
    categories,
    items,
  }
}

export function renderFeed({ items, siteConfig, siteUrl, generatedAt }) {
  const title = escapeXml(siteConfig.name)
  const description = escapeXml(siteConfig.description)
  const link = `${siteUrl}/`
  const feedUrl = `${siteUrl}/feed.xml`
  const lastBuildDate = new Date(generatedAt).toUTCString()

  const entries = items
    .map((item) => {
      const url = `${siteUrl}${item.url}`
      const categories = item.tags
        .map((tag) => `      <category>${escapeXml(tag.name)}</category>`)
        .join('\n')

      return [
        '    <item>',
        `      <title>${escapeXml(item.title)}</title>`,
        `      <link>${escapeXml(url)}</link>`,
        `      <guid isPermaLink="true">${escapeXml(url)}</guid>`,
        `      <pubDate>${new Date(item.publishedAt).toUTCString()}</pubDate>`,
        `      <description>${escapeXml(item.summary)}</description>`,
        categories,
        '    </item>',
      ]
        .filter(Boolean)
        .join('\n')
    })
    .join('\n')

  return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>${title}</title>
    <link>${escapeXml(link)}</link>
    <description>${description}</description>
    <language>zh-CN</language>
    <lastBuildDate>${lastBuildDate}</lastBuildDate>
    <atom:link href="${escapeXml(feedUrl)}" rel="self" type="application/rss+xml"/>
${entries}
  </channel>
</rss>
`
}

export function renderRobots({ siteUrl, allowIndexing = true }) {
  if (!allowIndexing) {
    return 'User-agent: *\nDisallow: /\n'
  }

  return `User-agent: *
Allow: /

Sitemap: ${siteUrl}/sitemap.xml
`
}

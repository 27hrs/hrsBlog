import { toAbsoluteUrl } from '../api/assets.js'
import { categoryLabel } from './theme/utils/categories.js'

function normalizePath(relativePath) {
  if (!relativePath || relativePath === 'index.md') return '/'
  if (relativePath === 'articles/index.md') return '/articles/'

  return `/${relativePath
    .replace(/\.md$/, '')
    .replace(/\/index$/, '/')}`
}

function jsonLd(value) {
  return JSON.stringify(value).replaceAll('<', '\\u003c')
}

function meta(property, content) {
  return ['meta', { property, content }]
}

function nameMeta(name, content) {
  return ['meta', { name, content }]
}

/**
 * Adds canonical, Open Graph, Twitter and JSON-LD without duplicating the
 * description/title tags VitePress already renders from frontmatter.
 */
export function createHeadTransform({ siteUrl, siteConfig, defaultImage = '/og-cover.jpg' }) {
  const base = String(siteUrl || '').replace(/\/+$/, '')
  const siteName = siteConfig.name
  const authorName = siteConfig.author?.name || siteName

  return function transformHead({ pageData }) {
    const frontmatter = pageData.frontmatter || {}
    const isArticle = frontmatter.layout === 'article'
    const isAbout = frontmatter.layout === 'about'
    const isNotFound = pageData.isNotFound === true || frontmatter.layout === 'notfound'
    const path = normalizePath(pageData.relativePath)
    const canonical = isNotFound ? '' : `${base}${path}`
    const title = frontmatter.title || pageData.title || siteName
    const description = frontmatter.description || pageData.description || siteConfig.description
    const cover = frontmatter.cover && /\.(?:jpe?g|png)(?:$|\?)/i.test(frontmatter.cover)
      ? frontmatter.cover
      : defaultImage
    const image = toAbsoluteUrl(cover, base)
    const tags = Array.isArray(frontmatter.tags) ? frontmatter.tags : []

    const head = [
      nameMeta('twitter:card', image ? 'summary_large_image' : 'summary'),
      nameMeta('twitter:title', title),
      nameMeta('twitter:description', description),
      meta('og:site_name', siteName),
      meta('og:title', title),
      meta('og:description', description),
      meta('og:type', isArticle ? 'article' : 'website'),
      meta('og:locale', 'zh_CN'),
    ]

    if (canonical) {
      head.push(['link', { rel: 'canonical', href: canonical }])
      head.push(meta('og:url', canonical))
    }

    if (image) {
      head.push(meta('og:image', image))
      head.push(nameMeta('twitter:image', image))
    }

    if (isArticle) {
      const published = frontmatter.publishedAt || frontmatter.date
      const modified = frontmatter.updatedAt || published

      if (published) head.push(meta('article:published_time', published))
      if (modified) head.push(meta('article:modified_time', modified))
      head.push(meta('article:author', authorName))

      for (const tag of tags) {
        if (tag?.name) head.push(meta('article:tag', tag.name))
      }

      const articleUrl = canonical || `${base}${path}`
      const json = {
        '@context': 'https://schema.org',
        '@type': 'BlogPosting',
        headline: title,
        description,
        url: articleUrl,
        mainEntityOfPage: { '@type': 'WebPage', '@id': articleUrl },
        datePublished: published,
        dateModified: modified,
        inLanguage: 'zh-CN',
        image: image ? [image] : undefined,
        author: {
          '@type': 'Person',
          name: authorName,
          url: base || undefined,
        },
        publisher: {
          '@type': 'Organization',
          name: siteName,
        },
        keywords: tags.map((tag) => tag.name).join(', ') || undefined,
        articleSection: frontmatter.category ? categoryLabel(frontmatter.category) : undefined,
      }

      head.push(['script', { type: 'application/ld+json' }, jsonLd(json)])
    } else if (!isNotFound) {
      const json = isAbout
        ? {
            '@context': 'https://schema.org',
            '@type': 'ProfilePage',
            url: canonical,
            mainEntity: {
              '@type': 'Person',
              name: authorName,
              jobTitle: siteConfig.author?.role,
              description: siteConfig.author?.bio || siteConfig.author?.intro,
              image: toAbsoluteUrl(siteConfig.author?.avatar || '', base) || undefined,
              url: base || undefined,
            },
          }
        : {
            '@context': 'https://schema.org',
            '@type': 'WebSite',
            name: siteName,
            url: base || undefined,
            description: siteConfig.description,
            inLanguage: 'zh-CN',
            publisher: { '@type': 'Organization', name: siteName },
          }

      head.push(['script', { type: 'application/ld+json' }, jsonLd(json)])
    }

    if (isNotFound || siteConfig.allowIndexing === false) {
      head.push(nameMeta('robots', 'noindex, nofollow'))
    }

    return head
  }
}

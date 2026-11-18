import MarkdownIt from 'markdown-it'

/**
 * 共享的 Markdown 渲染器（构建期使用）。
 *
 * 站点里所有“构建期编译”的正文都走这一份实现：
 * - 关于页正文（`aboutContent`，由 sync-settings 编译）；
 * - 说说正文（由 sync-shuoshuo 编译成 `html` 字段）。
 *
 * 统一的安全口径：
 * - `html: false`：禁用原始 HTML，防止正文里的标签被当成页面结构渲染；
 * - 站外链接自动补 `target="_blank"` 与 `rel="noopener noreferrer"`。
 *
 * 浏览器端不再渲染 Markdown：编译结果直接以静态 HTML 预渲染进构建产物。
 */
export const markdown = new MarkdownIt({ html: false, linkify: true })

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

/**
 * 把 Markdown 编译成静态 HTML。
 * 空内容返回空串，调用方可以直接判断是否需要渲染容器。
 */
export function renderMarkdown(source) {
  const text = String(source ?? '')
  return text ? markdown.render(text) : ''
}

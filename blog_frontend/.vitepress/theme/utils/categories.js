/**
 * Shared with admin_frontend/src/data/categories.js.
 * Categories are derived from tags because the visitor API has no category field.
 */
export const ARTICLE_CATEGORIES = [
  { id: 'frontend', label: '前端工程', short: '前端' },
  { id: 'backend', label: '后端架构', short: '后端' },
  { id: 'design', label: '设计工程', short: '设计' },
  { id: 'life', label: '生活随笔', short: '生活' },
  { id: 'notes', label: '阅读笔记', short: '笔记' },
  { id: 'other', label: '未分类', short: '其他' },
]

export const ARTICLE_CATEGORY_MAP = Object.fromEntries(
  ARTICLE_CATEGORIES.map((category) => [category.id, category]),
)

const FRONTEND_TAGS = [
  'vue',
  'react',
  'css',
  'html',
  'javascript',
  'typescript',
  'vite',
  'vitepress',
  '前端',
  '可访问性',
  '可视化',
  '性能',
]

const DESIGN_TAGS = ['设计', '配色', '排版', '字体', '视觉', '色彩', '界面']

const BACKEND_TAGS = [
  'java',
  'spring',
  'spring boot',
  'mysql',
  'redis',
  'docker',
  '后端',
  '架构',
  '数据库',
  '微服务',
  '运维',
  'nginx',
]

const LIFE_TAGS = ['随笔', '生活', '效率', '阅读', '读书', '旅行', '年度', '摄影', '日常']
const NOTES_TAGS = ['笔记', '读书笔记', '方法论', '思考', '学习']

function tagNames(tags = []) {
  return tags
    .map((tag) => (typeof tag === 'string' ? tag : tag?.name))
    .filter(Boolean)
    .map((name) => name.toLowerCase())
}

export function inferCategoryFromTags(tags = []) {
  const names = tagNames(tags)

  // Order matters: notes/life are checked before tool tags so a "思考"
  // essay that happens to mention CSS still lands in 阅读笔记.
  if (names.some((name) => NOTES_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'notes'
  }
  if (names.some((name) => LIFE_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'life'
  }
  if (names.some((name) => DESIGN_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'design'
  }
  if (names.some((name) => FRONTEND_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'frontend'
  }
  if (names.some((name) => BACKEND_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'backend'
  }
  return 'other'
}

export function categoryLabel(id) {
  return ARTICLE_CATEGORY_MAP[id]?.label || ARTICLE_CATEGORY_MAP.other.label
}

export function categoryShortLabel(id) {
  return ARTICLE_CATEGORY_MAP[id]?.short || ARTICLE_CATEGORY_MAP.other.short
}

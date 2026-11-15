export const ARTICLE_CATEGORIES = [
  { value: 'frontend', label: '前端工程', short: '前端' },
  { value: 'backend', label: '后端架构', short: '后端' },
  { value: 'design', label: '设计工程', short: '设计' },
  { value: 'life', label: '生活随笔', short: '生活' },
  { value: 'notes', label: '阅读笔记', short: '笔记' },
  { value: 'other', label: '未分类', short: '其他' },
]

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
  '设计',
  '可访问性',
  '可视化',
  '性能',
]

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

export const ARTICLE_CATEGORY_MAP = Object.fromEntries(
  ARTICLE_CATEGORIES.map((category) => [category.value, category]),
)

export function categoryLabel(value) {
  return ARTICLE_CATEGORY_MAP[value]?.label || ARTICLE_CATEGORY_MAP.other.label
}

export function categoryShortLabel(value) {
  return ARTICLE_CATEGORY_MAP[value]?.short || ARTICLE_CATEGORY_MAP.other.short
}

export function inferCategoryFromTags(tags = []) {
  const names = tags
    .map((tag) => (typeof tag === 'string' ? tag : tag?.name))
    .filter(Boolean)
    .map((name) => name.toLowerCase())

  if (names.some((name) => FRONTEND_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'frontend'
  }
  if (names.some((name) => BACKEND_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'backend'
  }
  if (names.some((name) => LIFE_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'life'
  }
  if (names.some((name) => NOTES_TAGS.some((keyword) => name.includes(keyword)))) {
    return 'notes'
  }
  return 'other'
}

/**
 * Site settings are schema-driven.
 *
 * Adding a new settings group is a one-file change: append a section object to
 * `settingSections`. The left navigation, field rendering, validation and
 * persistence all follow the schema — no page code needs to change.
 */
export const settingSections = [
  {
    key: 'basic',
    label: '基本设置',
    icon: 'settings',
    description: '站点名称、简介与品牌标识，构成访客对这里的第一印象。',
    fields: [
      {
        key: 'siteName',
        label: '站点名称',
        type: 'input',
        default: 'HrsBlog',
        placeholder: '例如：HrsBlog',
        width: 'half',
        rules: { required: true, max: 60 },
      },
      {
        key: 'siteTagline',
        label: '一句话介绍',
        type: 'input',
        default: '保持好奇，持续记录。',
        placeholder: '用一句话说明这个站点',
        width: 'half',
        rules: { max: 80 },
      },
      {
        key: 'siteDescription',
        label: '站点描述',
        type: 'textarea',
        default:
          '一个关于代码、设计与日常观察的个人数字花园。记录正在学习的事，也保存值得回看的过程。',
        placeholder: '用于首页与搜索引擎的站点介绍',
        width: 'full',
        rules: { max: 300 },
      },
      {
        key: 'siteLogo',
        label: '站点 Logo',
        type: 'image',
        default: '',
        help: '建议使用透明背景的 PNG 或 SVG 导出图，会保存为资源链接。',
        width: 'half',
      },
      {
        key: 'footerText',
        label: '页脚文案',
        type: 'input',
        default: '内容由 HrsBlog 维护 · 保持好奇，持续记录',
        placeholder: '显示在博客页脚的一句话',
        width: 'half',
        rules: { max: 100 },
      },
    ],
  },
  {
    key: 'identity',
    label: '作者资料',
    icon: 'user',
    description: '在关于页和文章署名处展示的身份信息与社交入口。',
    fields: [
      {
        key: 'authorName',
        label: '显示名称',
        type: 'input',
        default: 'Hrs',
        placeholder: '你的名字或昵称',
        width: 'half',
        rules: { required: true, max: 40 },
      },
      {
        key: 'authorRole',
        label: '身份标签',
        type: 'input',
        default: '开发者 / 长期主义者',
        placeholder: '例如：前端工程师',
        width: 'half',
        rules: { max: 60 },
      },
      {
        key: 'authorBio',
        label: '个人简介',
        type: 'textarea',
        default:
          '在代码与文字之间寻找秩序。喜欢把复杂问题拆成小块，也喜欢在清晨写一点东西。',
        placeholder: '关于你的一段介绍',
        width: 'full',
        rules: { max: 500 },
      },
      {
        key: 'avatarUrl',
        label: '头像',
        type: 'image',
        default: '',
        help: '建议使用正方形图片，博客前台会以圆形呈现。',
        width: 'half',
      },
      {
        key: 'location',
        label: '所在城市',
        type: 'input',
        default: '中国',
        placeholder: '例如：杭州',
        width: 'half',
        rules: { max: 40 },
      },
      {
        key: 'email',
        label: '联系邮箱',
        type: 'input',
        default: '',
        placeholder: 'hello@example.com',
        width: 'half',
        rules: { email: true, max: 120 },
      },
      {
        key: 'githubUrl',
        label: 'GitHub',
        type: 'input',
        default: '',
        placeholder: 'https://github.com/your-name',
        width: 'half',
        rules: { url: true, max: 200 },
      },
      {
        key: 'xUrl',
        label: 'X / Twitter',
        type: 'input',
        default: '',
        placeholder: 'https://x.com/your-name',
        width: 'half',
        rules: { url: true, max: 200 },
      },
      {
        key: 'rssPath',
        label: 'RSS 地址',
        type: 'input',
        default: '/feed.xml',
        placeholder: '/feed.xml',
        width: 'half',
        rules: { max: 200 },
      },
    ],
  },
  {
    key: 'appearance',
    label: '外观与交互',
    icon: 'palette',
    description: '控制博客前台的默认主题、强调色和阅读密度。',
    fields: [
      {
        key: 'defaultTheme',
        label: '默认主题',
        type: 'select',
        default: 'system',
        width: 'half',
        options: [
          { value: 'system', label: '跟随系统' },
          { value: 'light', label: '浅色' },
          { value: 'dark', label: '深色' },
        ],
      },
      {
        key: 'accentColor',
        label: '品牌强调色',
        type: 'color',
        default: '#3b82f6',
        help: '用于链接、按钮与选中状态。',
        width: 'half',
      },
      {
        key: 'showHeroBackdrop',
        label: '首页 Hero 背景图',
        type: 'switch',
        default: true,
        description: '在首页顶部展示图片底图和渐变遮罩。',
        width: 'half',
      },
      {
        key: 'showReadingTime',
        label: '显示阅读时长',
        type: 'switch',
        default: true,
        description: '在文章卡片和详情页估算阅读时间。',
        width: 'half',
      },
      {
        key: 'articlesPerPage',
        label: '每页文章数',
        type: 'number',
        default: 10,
        min: 5,
        max: 50,
        unit: '篇',
        width: 'half',
        rules: { min: 5, max: 50 },
      },
      {
        key: 'showToc',
        label: '显示文章目录',
        type: 'switch',
        default: true,
        description: '在文章详情页右侧显示当前文章大纲。',
        width: 'half',
      },
      {
        key: 'backgroundImage',
        label: '全站背景图',
        type: 'image',
        default: '',
        help: '博客前台的全屏背景照片，上传后保存为静态资源链接；留空使用主题默认背景。',
        width: 'half',
      },
      {
        key: 'backgroundOverlay',
        label: '背景遮罩强度',
        type: 'number',
        default: 40,
        min: 0,
        max: 100,
        unit: '%',
        help: '数值越大，背景照片上的纸面越实、文字越易读。',
        width: 'half',
        rules: { min: 0, max: 100 },
      },
    ],
  },
  {
    key: 'reading',
    label: '阅读体验',
    icon: 'book-open',
    description: '留言板、友链和首页内容密度等读者可感知的细节。',
    fields: [
      {
        key: 'enableGuestbook',
        label: '留言板入口',
        type: 'switch',
        default: true,
        description: '关闭后，博客前台不再展示留言页入口与留言墙。',
        width: 'half',
      },
      {
        key: 'guestbookPageSize',
        label: '留言每页数量',
        type: 'number',
        default: 10,
        min: 1,
        max: 50,
        step: 1,
        unit: '条',
        help: '留言墙每页展示的留言条数，取值 1 ~ 50。',
        width: 'half',
        rules: { min: 1, max: 50 },
      },
      {
        key: 'friendLinks',
        label: '友情链接',
        type: 'list',
        default: [],
        width: 'full',
        addLabel: '添加友链',
        itemLabelKey: 'name',
        itemFields: [
          {
            key: 'name',
            label: '名称',
            type: 'input',
            placeholder: '站点名称',
            rules: { required: true, max: 50 },
          },
          {
            key: 'url',
            label: '链接',
            type: 'input',
            placeholder: 'https://example.com',
            rules: { required: true, url: true, max: 200 },
          },
        ],
        help: '显示在博客文章列表页的侧边栏。',
      },
    ],
  },
  {
    key: 'seo',
    label: 'SEO 与分享',
    icon: 'globe',
    description: '让搜索引擎和分享卡片更准确地理解你的内容。',
    fields: [
      {
        key: 'titleTemplate',
        label: '标题模板',
        type: 'input',
        default: ':title · HrsBlog',
        placeholder: ':title · HrsBlog',
        help: '使用 :title 代表当前页面标题。',
        width: 'half',
        rules: { max: 100 },
      },
      {
        key: 'metaDescription',
        label: '默认 SEO 描述',
        type: 'textarea',
        default: '记录代码、设计与日常观察的个人博客。',
        placeholder: '160 字以内更容易被完整展示',
        width: 'full',
        rules: { max: 200 },
      },
      {
        key: 'keywords',
        label: '关键词',
        type: 'input',
        default: 'HrsBlog, 前端, 后端, 设计, 随笔',
        placeholder: '使用英文逗号分隔',
        width: 'full',
        rules: { max: 200 },
      },
      {
        key: 'ogImage',
        label: '默认分享图',
        type: 'image',
        default: '',
        help: '文章没有封面时，会使用这张图作为社交分享卡片。',
        width: 'half',
      },
      {
        key: 'robots',
        label: '搜索引擎索引',
        type: 'select',
        default: 'index',
        width: 'half',
        options: [
          { value: 'index', label: '允许索引' },
          { value: 'noindex', label: '暂不索引' },
        ],
      },
      {
        key: 'enableSitemap',
        label: '生成 Sitemap',
        type: 'switch',
        default: true,
        description: '构建时为搜索引擎生成站点地图。',
        width: 'half',
      },
    ],
  },
  {
    key: 'integrations',
    label: '集成',
    icon: 'share',
    description: '分析服务与外部通知的连接参数。非必要可以留空。',
    fields: [
      {
        key: 'analyticsProvider',
        label: '访问统计',
        type: 'select',
        default: 'none',
        width: 'half',
        options: [
          { value: 'none', label: '暂不接入' },
          { value: 'umami', label: 'Umami' },
          { value: 'plausible', label: 'Plausible' },
          { value: 'custom', label: '自定义' },
        ],
      },
      {
        key: 'analyticsSiteId',
        label: '统计站点 ID',
        type: 'input',
        default: '',
        placeholder: '例如：a1b2c3d4',
        width: 'half',
        rules: { max: 100 },
        visibleWhen: { key: 'analyticsProvider', notEquals: 'none' },
      },
      {
        key: 'analyticsScriptUrl',
        label: '统计脚本地址',
        type: 'input',
        default: '',
        placeholder: 'https://analytics.example.com/script.js',
        width: 'full',
        rules: { url: true, max: 300 },
        visibleWhen: { key: 'analyticsProvider', equals: 'custom' },
      },
      {
        key: 'webhookUrl',
        label: '发布通知 Webhook',
        type: 'input',
        default: '',
        placeholder: 'https://example.com/webhook',
        help: '文章发布后向该地址发送一条通知。填 GitHub Dispatches 地址时形如 https://api.github.com/repos/{owner}/{repo}/dispatches。',
        width: 'full',
        rules: { url: true, max: 300 },
      },
      {
        key: 'webhookEnabled',
        label: '启用通知',
        type: 'switch',
        default: false,
        description: '内容变更（站点配置实际变化、文章发布）后触发一次通知。',
        width: 'half',
      },
      {
        key: 'webhookSecret',
        label: '鉴权密钥',
        type: 'input',
        default: '',
        placeholder: 'Bearer Token',
        help: '作为 Authorization: Bearer <密钥> 请求头发送；留空则不带鉴权头。',
        width: 'half',
        rules: { max: 300 },
      },
      {
        key: 'webhookEventType',
        label: '事件类型',
        type: 'input',
        default: 'blog-content-updated',
        placeholder: 'blog-content-updated',
        help: '写入载荷的 event_type，需与 GitHub Actions 的 repository_dispatch types 一致。',
        width: 'half',
        rules: { max: 64 },
      },
      {
        key: 'webhookDebounceSeconds',
        label: '防抖窗口（秒）',
        type: 'number',
        default: 10,
        description: '窗口内的多次变更合并成一次通知，新的变更会重新计时。',
        width: 'half',
        rules: { min: 0, max: 600 },
      },
      {
        key: 'webhookMaxRetries',
        label: '失败重试次数',
        type: 'number',
        default: 2,
        description: '投递失败后的重试次数（不含首次），按 5s / 15s 退避。',
        width: 'half',
        rules: { min: 0, max: 10 },
      },
    ],
  },
  {
    key: 'advanced',
    label: '高级',
    icon: 'sliders',
    description: '站点状态、时区与日常维护相关的底层选项。',
    fields: [
      {
        key: 'siteStatus',
        label: '站点状态',
        type: 'select',
        default: 'online',
        width: 'half',
        options: [
          { value: 'online', label: '正常访问' },
          { value: 'maintenance', label: '维护模式' },
        ],
      },
      {
        key: 'timezone',
        label: '站点时区',
        type: 'select',
        default: 'Asia/Shanghai',
        width: 'half',
        options: [
          { value: 'Asia/Shanghai', label: 'Asia/Shanghai (UTC+8)' },
          { value: 'UTC', label: 'UTC' },
          { value: 'America/New_York', label: 'America/New_York' },
        ],
      },
      {
        key: 'allowIndexing',
        label: '允许搜索引擎抓取',
        type: 'switch',
        default: true,
        description: '关闭后会在页面头部输出 noindex。',
        width: 'half',
      },
      {
        key: 'showDebugInfo',
        label: '显示构建信息',
        type: 'switch',
        default: false,
        description: '在页脚展示构建时间和版本信息。',
        width: 'half',
      },
    ],
  },
  {
    key: 'account',
    label: '账号安全',
    icon: 'lock',
    description: '修改管理员用户名与密码。保存令牌会立即签发，当前会话无需重新登录。',
    custom: 'account',
    fields: [],
  },
]

export const settingSectionMap = Object.fromEntries(
  settingSections.map((section) => [section.key, section]),
)

export function createDefaultSettings() {
  const defaults = {}
  settingSections.forEach((section) => {
    section.fields.forEach((field) => {
      defaults[field.key] = Array.isArray(field.default)
        ? field.default.map((item) => ({ ...item }))
        : (field.default ?? '')
    })
  })
  return defaults
}

export function isFieldVisible(field, values) {
  if (!field.visibleWhen) return true
  const { key, equals, notEquals } = field.visibleWhen
  if (equals !== undefined) return values[key] === equals
  if (notEquals !== undefined) return values[key] !== notEquals
  return true
}

function isEmpty(value) {
  return value === undefined || value === null || (typeof value === 'string' && !value.trim())
}

function validateValue(field, value) {
  const rules = field.rules || {}
  if (rules.required && isEmpty(value)) return `请填写${field.label}`

  if (isEmpty(value)) return ''

  if (rules.max !== undefined && String(value).length > rules.max) {
    return `${field.label}不能超过 ${rules.max} 个字符`
  }
  if (rules.min !== undefined && Number(value) < rules.min) {
    return `${field.label}不能小于 ${rules.min}`
  }
  if (rules.max !== undefined && field.type === 'number' && Number(value) > rules.max) {
    return `${field.label}不能大于 ${rules.max}`
  }
  if (rules.email) {
    const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!pattern.test(String(value))) return '请输入有效的邮箱地址'
  }
  if (rules.url) {
    try {
      const url = new URL(String(value))
      if (!['http:', 'https:'].includes(url.protocol)) return '请输入以 http(s) 开头的链接'
    } catch {
      return '请输入有效的链接地址'
    }
  }
  if (field.type === 'color' && !/^#[0-9a-f]{6}$/i.test(String(value))) {
    return '请输入 6 位十六进制颜色值，例如 #3b82f6'
  }
  return ''
}

/**
 * Validate every visible field across every section.
 * Returns a flat error map compatible with SchemaForm.
 */
export function validateSettings(values) {
  const errors = {}

  settingSections.forEach((section) => {
    section.fields.forEach((field) => {
      if (!isFieldVisible(field, values)) return

      if (field.type === 'list') {
        const items = Array.isArray(values[field.key]) ? values[field.key] : []
        items.forEach((item, index) => {
          ;(field.itemFields || []).forEach((subField) => {
            const error = validateValue(
              { ...subField, label: `${field.label}第 ${index + 1} 项的${subField.label}` },
              item?.[subField.key],
            )
            if (error) errors[`${field.key}.${index}.${subField.key}`] = error
          })
        })
        return
      }

      const error = validateValue(field, values[field.key])
      if (error) errors[field.key] = error
    })
  })

  return errors
}

export function firstSectionWithErrors(errors) {
  if (!Object.keys(errors || {}).length) return null
  return (
    settingSections.find((section) =>
      section.fields.some((field) => {
        if (field.type === 'list') {
          return Object.keys(errors).some((key) => key.startsWith(`${field.key}.`))
        }
        return Boolean(errors[field.key])
      }),
    )?.key || null
  )
}

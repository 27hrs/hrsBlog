import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { generateSlug, isValidSlug } from '@/utils/slug.js'
import { inferCategoryFromTags } from '@/data/categories.js'

export function createEmptyDraft() {
  return {
    title: '',
    slug: '',
    summary: '',
    content: '',
    contentFormat: 'MARKDOWN',
    coverUrl: '',
    status: 'DRAFT',
    tags: [],
    category: 'frontend',
    visibility: 'public',
    scheduledAt: '',
    pinned: false,
    featured: false,
  }
}

export const useArticleDraftStore = defineStore('articleDraft', () => {
  const form = reactive(createEmptyDraft())
  const slugTouched = ref(false)
  const isEditing = ref(false)
  const sourceId = ref(null)
  const baselineSlug = ref('')
  const lastSavedAt = ref(null)

  const isScheduled = computed(() => Boolean(form.scheduledAt))
  const effectiveStatus = computed(() => {
    if (form.status === 'PUBLISHED' && form.scheduledAt) return 'SCHEDULED'
    return form.status
  })
  const wordCount = computed(() => {
    const plain = form.content
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/[#>*_`~[\]()!-]/g, ' ')
      .trim()
    return plain ? plain.length : 0
  })

  function reset(next = {}) {
    Object.assign(form, createEmptyDraft(), next)
    slugTouched.value = Boolean(next.slug)
    isEditing.value = false
    sourceId.value = null
    baselineSlug.value = next.slug || ''
    lastSavedAt.value = null
  }

  function setTitle(value) {
    form.title = value
    if (!slugTouched.value) {
      form.slug = generateSlug(value)
    }
  }

  function setSlug(value) {
    slugTouched.value = true
    form.slug = value.toLowerCase().replace(/\s+/g, '-')
  }

  function addTag(rawName) {
    const name = String(rawName || '').trim()
    if (!name) return false
    if (form.tags.length >= 10) return false
    if (form.tags.some((tag) => tag.toLowerCase() === name.toLowerCase())) return false
    if (name.length > 50) return false
    form.tags.push(name)
    return true
  }

  function removeTag(name) {
    form.tags = form.tags.filter((tag) => tag !== name)
  }

  function hydrateFromArticle(article, options = {}) {
    const tags = (article.tags || []).map((tag) => (typeof tag === 'string' ? tag : tag.name))
    Object.assign(form, createEmptyDraft(), {
      title: article.title || '',
      slug: options.newSlug || article.slug || '',
      summary: article.summary || '',
      content: article.content || '',
      contentFormat: article.contentFormat || 'MARKDOWN',
      coverUrl: article.coverUrl || '',
      status: options.status || article.status || 'DRAFT',
      tags,
      category: article.localMeta?.category || options.category || inferCategoryFromTags(tags),
    })
    slugTouched.value = true
    isEditing.value = Boolean(options.isEditing)
    sourceId.value = options.isEditing ? (article.id ?? null) : null
    baselineSlug.value = options.isEditing ? article.slug || '' : ''
  }

  function validate() {
    const errors = {}

    if (!form.title.trim()) errors.title = '请输入文章标题'
    else if (form.title.trim().length > 200) errors.title = '标题不能超过 200 个字符'

    if (!form.slug.trim()) errors.slug = '请输入文章 slug'
    else if (!isValidSlug(form.slug.trim())) {
      errors.slug = 'slug 仅允许小写字母、数字和单个连字符'
    }

    if (!form.content.trim()) errors.content = '正文不能为空'
    else if (form.content.length > 500_000) errors.content = '正文不能超过 500,000 个字符'

    if (form.summary.length > 500) errors.summary = '摘要不能超过 500 个字符'
    if (form.tags.length > 10) errors.tags = '最多添加 10 个标签'
    if (form.tags.some((tag) => tag.length > 50)) errors.tags = '单个标签不能超过 50 个字符'
    if (form.coverUrl.length > 500) errors.coverUrl = '封面链接过长'
    if (form.scheduledAt && new Date(form.scheduledAt).getTime() <= Date.now()) {
      errors.scheduledAt = '定时发布时间必须晚于当前时间'
    }

    return errors
  }

  function toApiPayload(status = form.status) {
    return {
      title: form.title.trim(),
      slug: form.slug.trim(),
      summary: form.summary.trim() || null,
      content: form.content,
      contentFormat: 'MARKDOWN',
      coverUrl: form.coverUrl || null,
      status,
      tags: [...form.tags],
    }
  }

  function metadata() {
    return {
      category: form.category,
      visibility: form.visibility,
      pinned: form.pinned,
      featured: form.featured,
      scheduledAt: form.scheduledAt,
    }
  }

  return {
    form,
    slugTouched,
    isEditing,
    sourceId,
    baselineSlug,
    lastSavedAt,
    isScheduled,
    effectiveStatus,
    wordCount,
    reset,
    setTitle,
    setSlug,
    addTag,
    removeTag,
    hydrateFromArticle,
    validate,
    toApiPayload,
    metadata,
  }
})

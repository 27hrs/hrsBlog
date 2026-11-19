<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import AppIcon from '@/components/common/AppIcon.vue'
import AppModal from '@/components/common/AppModal.vue'
import ArticleSettingsPanel from '@/components/editor/ArticleSettingsPanel.vue'
import { createArticle, getAdminArticle, getPublishedArticle, updateArticle } from '@/api/articles.js'
import { uploadEditorImages } from '@/services/editorImages.js'
import { getLocalArticle, rememberArticle } from '@/services/localLibrary.js'
import { useArticleDraftStore } from '@/stores/articleDraft.js'
import { useThemeStore } from '@/stores/theme.js'
import { useToastStore } from '@/stores/toast.js'
import { generateSlug } from '@/utils/slug.js'

const route = useRoute()
const router = useRouter()
const draft = useArticleDraftStore()
const theme = useThemeStore()
const toast = useToastStore()

const initializing = ref(false)
const savingAction = ref('')
const errors = reactive({})
const submitError = ref('')
const copyNotice = ref('')
const scheduleModalOpen = ref(false)

const toolbars = [
  'bold',
  'underline',
  'italic',
  'strikeThrough',
  '-',
  'title',
  'quote',
  '-',
  'unorderedList',
  'orderedList',
  'task',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  '-',
  'revoke',
  'next',
  '=',
  'preview',
  'pageFullscreen',
  'catalog',
]

const saving = computed(() => Boolean(savingAction.value))
const isDraftSaving = computed(() => savingAction.value === 'draft')
const isPublishing = computed(() => savingAction.value === 'publish')
const charCount = computed(() => draft.form.content.length)
const titleLength = computed(() => draft.form.title.length)
const hasScheduled = computed(() => Boolean(draft.form.scheduledAt))
const editing = computed(() => Boolean(draft.sourceId))

const editingId = computed(() => {
  const id = Number(route.params.id)
  return Number.isInteger(id) && id > 0 ? id : null
})

const statusLabel = computed(() => {
  if (draft.form.status === 'PUBLISHED') return editing.value ? '已发布' : '已就绪'
  if (hasScheduled.value) return '计划草稿'
  return editing.value ? '草稿' : '新文章'
})

function clearError(key) {
  if (errors[key]) delete errors[key]
  submitError.value = ''
}

watch(
  () => draft.form.title,
  () => clearError('title'),
)
watch(
  () => draft.form.slug,
  () => clearError('slug'),
)
watch(
  () => draft.form.content,
  () => clearError('content'),
)
watch(
  () => draft.form.visibility,
  () => {
    if (draft.form.visibility === 'public') submitError.value = ''
  },
)

function copySlug(slug) {
  const base = `${slug || 'article'}-copy`
  return base.length > 200 ? `${base.slice(0, 195)}-copy` : base
}

async function hydrateSource() {
  const sourceSlug = typeof route.query.from === 'string' ? route.query.from : ''
  const localSlug = typeof route.query.fromLocal === 'string' ? route.query.fromLocal : ''

  if (!sourceSlug && !localSlug) return

  initializing.value = true
  try {
    let article = null
    if (sourceSlug) {
      article = await getPublishedArticle(sourceSlug)
    } else {
      article = getLocalArticle({ slug: localSlug })
    }

    if (!article) {
      toast.warning('找不到要复用的文章，已打开空白编辑页')
      return
    }

    draft.hydrateFromArticle(article, {
      newSlug: copySlug(article.slug),
      status: 'DRAFT',
      category: article.localMeta?.category,
    })
    copyNotice.value = `已载入《${article.title}》作为新草稿，保存时会创建一篇新文章。`
    toast.info('原文内容已载入，可修改后重新发布')
  } catch (error) {
    toast.warning(error.message || '载入原文失败，已打开空白编辑页')
  } finally {
    initializing.value = false
  }
}

/** 编辑模式：按路由参数加载已有文章填充表单（管理端详情接口）。 */
async function loadForEdit(id) {
  initializing.value = true
  try {
    const article = await getAdminArticle(id)
    draft.hydrateFromArticle(article, { isEditing: true })
  } catch (error) {
    if (error.status === 404) {
      toast.error('文章不存在或已被删除')
    } else {
      toast.error(error.message || '载入文章失败')
    }
    await router.replace({ name: 'article-list' })
  } finally {
    initializing.value = false
  }
}

onMounted(async () => {
  draft.reset()
  if (editingId.value) {
    await loadForEdit(editingId.value)
  } else {
    await hydrateSource()
  }
})

function regenerateSlug() {
  draft.form.slug = generateSlug(draft.form.title || 'post')
  draft.slugTouched = true
  clearError('slug')
}

function validateForm() {
  Object.keys(errors).forEach((key) => delete errors[key])
  const nextErrors = draft.validate()
  Object.assign(errors, nextErrors)
  submitError.value = ''
  if (Object.keys(nextErrors).length) {
    toast.warning('还有几项内容需要完善')
    return false
  }
  if (draft.form.visibility !== 'public' && draft.form.status === 'PUBLISHED') {
    submitError.value = '当前后端仅支持公开文章，请将可见性改为「公开」后再发布。'
    toast.warning('非公开文章无法通过当前后端发布')
    return false
  }
  return true
}

async function performSave(status) {
  if (saving.value) return
  draft.form.status = status
  submitError.value = ''

  if (!validateForm()) return

  savingAction.value = status === 'DRAFT' ? 'draft' : 'publish'
  try {
    const payload = draft.toApiPayload(status)
    const saved = editing.value
      ? await updateArticle(draft.sourceId, payload)
      : await createArticle(payload)
    rememberArticle(saved, {
      ...draft.metadata(),
      scheduledAt: status === 'DRAFT' ? draft.form.scheduledAt : '',
    })
    toast.success(
      editing.value
        ? status === 'PUBLISHED'
          ? '文章已更新并发布'
          : '文章已保存'
        : status === 'PUBLISHED'
          ? '文章已发布'
          : '草稿已保存',
    )
    await router.push({
      name: 'article-list',
      query: { status: status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT' },
    })
  } catch (error) {
    if (error.status === 404) {
      toast.error('文章不存在或已被删除，无法保存')
      await router.replace({ name: 'article-list' })
      return
    }
    if (error.fieldErrors) {
      Object.entries(error.fieldErrors).forEach(([key, message]) => {
        if (['title', 'slug', 'summary', 'content', 'coverUrl', 'tags'].includes(key)) {
          errors[key] = message
        }
      })
    }
    if (error.status === 409) {
      errors.slug = error.message || 'slug 已存在，请更换一个'
    }
    submitError.value = error.message || '保存失败，请稍后重试'
    toast.error(error.message || '保存失败，请稍后重试')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } finally {
    savingAction.value = ''
  }
}

function saveDraft() {
  if (saving.value) return
  draft.form.status = 'DRAFT'
  performSave('DRAFT')
}

function requestPublish() {
  if (saving.value) return
  if (draft.form.visibility !== 'public') {
    submitError.value = '当前后端仅支持公开文章，请将可见性改为「公开」后再发布。'
    toast.warning('请先将文章可见性设为公开')
    document.querySelector('#article-visibility')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  if (hasScheduled.value) {
    scheduleModalOpen.value = true
    return
  }
  performSave('PUBLISHED')
}

function confirmScheduledPublish() {
  scheduleModalOpen.value = false
  performSave('PUBLISHED')
}

function keepAsDraft() {
  scheduleModalOpen.value = false
  performSave('DRAFT')
}
</script>

<template>
  <div class="article-editor page-reveal">
    <header class="article-editor__head">
      <div class="article-editor__heading">
        <RouterLink class="editor-backlink" :to="{ name: 'article-list' }">
          <AppIcon name="arrow-left" :size="13" />
          文章管理
        </RouterLink>
        <div class="article-editor__title-row">
          <h1 class="article-editor__title">{{ editing ? '编辑文章' : '投放新文章' }}</h1>
          <span class="editor-status"><span class="editor-status__dot" />{{ statusLabel }}</span>
        </div>
        <p class="article-editor__lead">把散落的念头，整理成一篇可以抵达读者的文章。</p>
      </div>

      <div class="article-editor__actions">
        <button
          class="btn"
          type="button"
          :disabled="saving || initializing"
          @click="saveDraft"
        >
          <span v-if="isDraftSaving" class="btn__spinner" />
          <AppIcon v-else name="save" :size="15" />
          {{ isDraftSaving ? '保存中…' : editing ? '存为草稿' : '保存草稿' }}
        </button>
        <button
          class="btn btn--primary"
          type="button"
          :disabled="saving || initializing"
          @click="requestPublish"
        >
          <span v-if="isPublishing" class="btn__spinner" />
          <AppIcon v-else name="send" :size="14" />
          {{ isPublishing ? (editing ? '更新中…' : '发布中…') : editing ? '保存并发布' : '发布文章' }}
        </button>
      </div>
    </header>

    <div v-if="copyNotice" class="callout callout--info editor-notice">
      <AppIcon class="callout__icon" name="copy" :size="15" />
      <span>{{ copyNotice }}</span>
    </div>

    <div v-if="submitError" class="callout callout--danger editor-notice" role="alert">
      <AppIcon class="callout__icon" name="alert-circle" :size="15" />
      <span>{{ submitError }}</span>
    </div>

    <div v-if="initializing" class="editor-loading panel">
      <div class="editor-loading__copy">
        <span class="skeleton skeleton--title" />
        <span class="skeleton skeleton--text" style="width: 62%" />
      </div>
      <span class="skeleton editor-loading__canvas" />
    </div>

    <div v-else class="editor-layout">
      <div class="editor-main">
        <section class="panel editor-title-card">
          <div class="editor-title-card__top">
            <span class="editor-kicker">
              <span class="editor-kicker__mark" />
              TITLE
            </span>
            <span class="form-counter">{{ titleLength }} / 200</span>
          </div>
          <textarea
            class="editor-title-input"
            :class="{ 'editor-title-input--invalid': errors.title }"
            :value="draft.form.title"
            rows="1"
            maxlength="200"
            placeholder="在这里写下标题…"
            aria-label="文章标题"
            @input="draft.setTitle($event.target.value)"
          />
          <p v-if="errors.title" class="form-error editor-field-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ errors.title }}
          </p>

          <div class="editor-slug-row">
            <span class="editor-slug-row__label">永久链接</span>
            <div class="editor-slug" :class="{ 'editor-slug--invalid': errors.slug }">
              <span class="editor-slug__prefix">/articles/</span>
              <input
                class="editor-slug__input"
                :value="draft.form.slug"
                type="text"
                maxlength="200"
                spellcheck="false"
                placeholder="article-slug"
                aria-label="文章 slug"
                @input="draft.setSlug($event.target.value)"
              />
              <button class="editor-slug__refresh" type="button" title="根据标题重新生成" @click="regenerateSlug">
                <AppIcon name="refresh" :size="13" />
              </button>
            </div>
          </div>
          <p v-if="errors.slug" class="form-error editor-field-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ errors.slug }}
          </p>
        </section>

        <section class="panel markdown-panel">
          <header class="markdown-panel__head">
            <div class="markdown-panel__title">
              <AppIcon name="pen-line" :size="14" />
              <span>正文</span>
              <span class="markdown-panel__format">Markdown</span>
            </div>
            <div class="markdown-panel__meta">
              <span>{{ charCount.toLocaleString() }} 字符</span>
              <span class="markdown-panel__separator" />
              <span class="markdown-panel__saved">
                <AppIcon name="check-circle" :size="12" />
                边写边预览
              </span>
            </div>
          </header>
          <div class="markdown-shell" :class="{ 'markdown-shell--invalid': errors.content }">
            <MdEditor
              v-model="draft.form.content"
              :theme="theme.isDark ? 'dark' : 'light'"
              :toolbars="toolbars"
              :preview="true"
              :on-upload-img="uploadEditorImages"
              language="zh-CN"
              placeholder="从这里开始写作。支持 Markdown、代码块、表格与图片上传…"
            />
          </div>
          <p v-if="errors.content" class="form-error markdown-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ errors.content }}
          </p>
        </section>

        <div class="editor-footnote">
          <span>
            <AppIcon name="info" :size="12" />
            {{
              editing
                ? '编辑会直接更新服务器上的文章；slug 冲突或校验失败时不会覆盖原有内容。'
                : '文章保存后可随时回到文章管理中继续编辑或删除。'
            }}
          </span>
          <span class="mono">MD / UTF-8</span>
        </div>
      </div>

      <aside class="editor-side">
        <div class="editor-side__sticky">
          <ArticleSettingsPanel :errors="errors" />
        </div>
      </aside>
    </div>

    <AppModal
      v-model:open="scheduleModalOpen"
      title="定时发布尚未启用"
      description="当前后端没有定时任务接口。计划时间会保存在本机草稿记录中，但不会自动触发发布。"
      size="sm"
    >
      <div class="schedule-modal__note">
        <AppIcon name="calendar" :size="17" />
        <span>你可以先保存为草稿，等后端支持后再启用自动发布；也可以现在立即发布。</span>
      </div>
      <template #footer>
        <button class="btn" type="button" @click="scheduleModalOpen = false">取消</button>
        <button class="btn" type="button" @click="keepAsDraft">保存为草稿</button>
        <button class="btn btn--primary" type="button" @click="confirmScheduledPublish">仍然立即发布</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.article-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.article-editor__head {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
}

.article-editor__heading {
  min-width: 0;
}

.editor-backlink {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  margin-bottom: 9px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  text-decoration: none;
}

.editor-backlink:hover {
  color: var(--brand-text);
  text-decoration: none;
}

.article-editor__title-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.article-editor__title {
  font-size: clamp(22px, 2.2vw, 27px);
  font-weight: var(--weight-semibold);
  line-height: 1.2;
  color: var(--text-primary);
  letter-spacing: -0.04em;
}

.editor-status {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 21px;
  padding: 0 7px;
  font-size: 10px;
  color: var(--text-tertiary);
  white-space: nowrap;
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
}

.editor-status__dot {
  width: 4px;
  height: 4px;
  background: var(--brand);
  border-radius: 50%;
}

.article-editor__lead {
  margin-top: 7px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.article-editor__actions {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
}

.editor-notice {
  align-items: flex-start;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 312px;
  gap: 18px;
  align-items: start;
}

.editor-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.editor-title-card {
  position: relative;
  padding: 15px 19px 14px;
  overflow: hidden;
}

.editor-title-card__top {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.editor-kicker {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
}

.editor-kicker__mark {
  width: 5px;
  height: 5px;
  background: var(--brand);
  border-radius: 1px;
}

.editor-title-input {
  display: block;
  width: 100%;
  min-height: 39px;
  max-height: 110px;
  padding: 0;
  overflow: hidden;
  font-family: var(--font-sans);
  font-size: clamp(21px, 2.1vw, 26px);
  font-weight: var(--weight-semibold);
  line-height: 1.32;
  color: var(--text-primary);
  letter-spacing: -0.035em;
  resize: none;
  background: transparent;
  border: 0;
  outline: none;
}

.editor-title-input::placeholder {
  color: var(--text-disabled);
}

.editor-title-input--invalid {
  color: var(--danger);
}

.editor-field-error {
  margin-top: 7px;
}

.editor-slug-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-top: 12px;
  margin-top: 11px;
  border-top: 1px solid var(--border-subtle);
}

.editor-slug-row__label {
  flex: none;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.editor-slug {
  display: flex;
  flex: 1;
  gap: 0;
  align-items: center;
  min-width: 0;
  height: 28px;
  padding: 0 3px 0 8px;
  background: var(--bg-inset);
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  transition: border-color var(--duration-fast) var(--ease-out);
}

.editor-slug:focus-within {
  border-color: var(--brand);
}

.editor-slug--invalid {
  border-color: var(--danger);
}

.editor-slug__prefix {
  flex: none;
  font-family: var(--font-mono);
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.editor-slug__input {
  flex: 1;
  min-width: 0;
  padding: 0 5px;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-secondary);
  background: transparent;
  border: 0;
  outline: none;
}

.editor-slug__refresh {
  display: grid;
  flex: none;
  width: 24px;
  height: 22px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-xs);
  place-items: center;
}

.editor-slug__refresh:hover {
  color: var(--text-primary);
  background: var(--bg-surface);
}

.markdown-panel {
  overflow: hidden;
}

.markdown-panel__head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 15px;
  background: var(--bg-inset);
  border-bottom: 1px solid var(--border-subtle);
}

.markdown-panel__title {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
}

.markdown-panel__title > .app-icon {
  color: var(--text-tertiary);
}

.markdown-panel__format {
  padding-left: 4px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
}

.markdown-panel__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 10px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.markdown-panel__separator {
  width: 1px;
  height: 9px;
  background: var(--border);
}

.markdown-panel__saved {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  color: var(--text-tertiary);
}

.markdown-shell {
  height: calc(100dvh - 348px);
  min-height: 470px;
  overflow: hidden;
}

.markdown-shell--invalid {
  box-shadow: inset 0 0 0 1px var(--danger);
}

.markdown-error {
  padding: 8px 15px 10px;
  background: var(--danger-soft);
  border-top: 1px solid color-mix(in srgb, var(--danger) 15%, transparent);
}

.editor-footnote {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 0 3px;
  font-size: 10px;
  line-height: 1.6;
  color: var(--text-tertiary);
}

.editor-footnote > span:first-child {
  display: inline-flex;
  gap: 5px;
  align-items: flex-start;
}

.editor-footnote .app-icon {
  flex: none;
  margin-top: 1px;
}

.editor-side {
  min-width: 0;
}

.editor-side__sticky {
  position: static;
  max-height: none;
  padding-right: 2px;
  overflow: visible;
}

.editor-loading {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 26px;
}

.editor-loading__copy {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.editor-loading__canvas {
  height: 480px;
  border-radius: var(--radius-sm);
}

.schedule-modal__note {
  display: flex;
  gap: 10px;
  padding: 12px;
  font-size: var(--text-xs);
  line-height: 1.65;
  color: var(--text-secondary);
  background: var(--bg-inset);
  border-radius: var(--radius-sm);
}

.schedule-modal__note .app-icon {
  flex: none;
  margin-top: 1px;
  color: var(--brand-text);
}

/* md-editor-v3 is styled through our semantic tokens. */
.markdown-shell :deep(.md-editor) {
  --md-color: var(--text-primary);
  --md-hover-color: var(--text-primary);
  --md-bk-color: var(--bg-surface);
  --md-bk-color-outstand: var(--bg-inset);
  --md-bk-hover-color: var(--bg-hover);
  --md-border-color: var(--border-subtle);
  --md-border-hover-color: var(--border-strong);
  --md-border-active-color: var(--brand);
  --md-modal-mask: var(--bg-backdrop);
  --md-scrollbar-bg-color: transparent;
  --md-scrollbar-thumb-color: var(--scrollbar-thumb);
  height: 100%;
  font-family: var(--font-sans);
  border: 0;
  border-radius: 0;
  background: var(--bg-surface);
}

.markdown-shell :deep(.md-editor-toolbar-wrapper) {
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
}

.markdown-shell :deep(.md-editor-toolbar) {
  padding: 3px 7px;
}

.markdown-shell :deep(.md-editor-toolbar-item) {
  color: var(--text-secondary);
  border-radius: var(--radius-xs);
}

.markdown-shell :deep(.md-editor-toolbar-item:hover) {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.markdown-shell :deep(.md-editor-content) {
  background: var(--bg-surface);
}

.markdown-shell :deep(.cm-editor) {
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.75;
  background: var(--bg-surface);
}

.markdown-shell :deep(.cm-editor.cm-focused) {
  outline: none;
}

.markdown-shell :deep(.cm-scroller) {
  padding: 10px 4px;
}

.markdown-shell :deep(.md-editor-preview) {
  padding: 26px 32px 60px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
}

.markdown-shell :deep(.md-editor-preview h1),
.markdown-shell :deep(.md-editor-preview h2),
.markdown-shell :deep(.md-editor-preview h3) {
  letter-spacing: -0.025em;
}

.markdown-shell :deep(.md-editor-preview img) {
  border-radius: var(--radius-sm);
}

.markdown-shell :deep(.md-editor-footer) {
  display: none;
}

.markdown-shell :deep(.md-editor-preview-wrapper) {
  border-left-color: var(--border-subtle);
}

@media (max-width: 1200px) {
  .editor-layout {
    grid-template-columns: minmax(0, 1fr) 292px;
  }
}

@media (max-width: 980px) {
  .article-editor__head {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .editor-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .editor-side {
    order: 2;
  }

  .editor-side__sticky {
    position: static;
    max-height: none;
    overflow: visible;
  }

  .markdown-shell {
    height: 560px;
    min-height: 0;
  }

  .settings-stack {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }

  .settings-note {
    grid-column: span 2;
  }
}

@media (max-width: 640px) {
  .article-editor__actions {
    width: 100%;
  }

  .article-editor__actions .btn {
    flex: 1;
  }

  .editor-title-card {
    padding: 13px 14px;
  }

  .editor-slug-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .editor-slug {
    width: 100%;
  }

  .markdown-panel__head {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .markdown-shell {
    height: 480px;
  }

  .settings-stack {
    display: flex;
  }

  .markdown-shell :deep(.md-editor-preview) {
    display: none;
  }

  .markdown-shell :deep(.md-editor-preview-wrapper) {
    display: none;
  }

  .markdown-shell :deep(.md-editor-content) {
    width: 100%;
  }
}
</style>

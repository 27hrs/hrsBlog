<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import AppIcon from '@/components/common/AppIcon.vue'
import AppTable from '@/components/common/AppTable.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import {
  SHUOSHUO_CONTENT_LIMIT,
  SHUOSHUO_VISIBILITY_PRIVATE,
  SHUOSHUO_VISIBILITY_PUBLIC,
  createShuoshuo,
  deleteShuoshuo,
  listShuoshuo,
  publishShuoshuo,
  unpublishShuoshuo,
  updateShuoshuo,
} from '@/api/shuoshuo.js'
import { formatDateTime, formatRelative } from '@/utils/format.js'
import { uploadEditorImages } from '@/services/editorImages.js'
import { useThemeStore } from '@/stores/theme.js'
import { useToastStore } from '@/stores/toast.js'

const route = useRoute()
const theme = useThemeStore()
const toast = useToastStore()

const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const items = ref([])
const total = ref(0)

/** 筛选哨兵：ALL 表示不过滤，请求时转成空串。 */
const VISIBILITY_ALL = 'ALL'
const visibilityFilter = ref(VISIBILITY_ALL)
const page = ref(1)
const pageSize = ref(20)

const actingId = ref(null)
const removing = ref(false)
const removalOpen = ref(false)
const removalTarget = ref(null)

const editorOpen = ref(false)
const editorIsNew = ref(false)
const editorTarget = ref(null)
const editorContent = ref('')
const editorErrors = ref({})
const editorSubmitError = ref('')
const savingEditor = ref(false)
/** 当前进行中的保存动作：save = 仅保存，publish = 保存并发布。 */
const savingAction = ref('')

const VISIBILITY_OPTIONS = [
  { value: VISIBILITY_ALL, label: '全部说说' },
  { value: SHUOSHUO_VISIBILITY_PRIVATE, label: '草稿' },
  { value: SHUOSHUO_VISIBILITY_PUBLIC, label: '已发布' },
]

const toolbars = [
  'bold',
  'italic',
  'strikeThrough',
  '-',
  'quote',
  'unorderedList',
  'orderedList',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  '-',
  'revoke',
  'next',
  '=',
  'preview',
  'pageFullscreen',
]

const columns = [
  { key: 'content', label: '内容', width: '44%' },
  { key: 'visibility', label: '可见性', width: '92px' },
  { key: 'publishedAt', label: '发布时间', width: '132px' },
  { key: 'updatedAt', label: '更新时间', width: '132px' },
  { key: 'actions', label: '', width: '108px', align: 'right' },
]

const visibilityOptions = computed(() => VISIBILITY_OPTIONS)

const contentLength = computed(() => editorContent.value.length)

const editorTitle = computed(() => (editorIsNew.value ? '新建说说' : '编辑说说'))

const editorDescription = computed(() =>
  editorIsNew.value
    ? '新建的说说先保存为草稿，发布后才会出现在博客前台。'
    : '正文以 Markdown 原文保存；发布与撤回可以在列表中随时切换。',
)

const removalCopy = computed(() => {
  const item = removalTarget.value
  const visibility = item?.visibility === SHUOSHUO_VISIBILITY_PUBLIC ? '已发布' : '草稿'
  return {
    title: '删除这条说说？',
    description: `这条${visibility}说说会立即从列表中移除，删除后无法在界面中恢复。`,
    note: '此操作会调用后端删除接口，删除服务器上的说说记录。',
    confirm: '删除说说',
  }
})

const emptyTitle = computed(() => {
  if (loading.value) return '正在加载说说'
  if (visibilityFilter.value === VISIBILITY_ALL) return '还没有说说'
  if (visibilityFilter.value === SHUOSHUO_VISIBILITY_PRIVATE) return '没有草稿说说'
  return '还没有已发布的说说'
})

const emptyDescription = computed(() => {
  if (visibilityFilter.value === VISIBILITY_ALL) {
    return '写下第一条说说，发布后会展示在博客前台的说说页。'
  }
  if (visibilityFilter.value === SHUOSHUO_VISIBILITY_PRIVATE) {
    return '所有说说都已发布；新建的说说会先落成草稿。'
  }
  return '草稿可以先保存下来，准备好后再发布到前台。'
})

/** 去掉 Markdown 标记后的纯文本摘要，用于列表展示。 */
function summarize(content, limit = 90) {
  const text = String(content || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
    .replace(/[#>*_`~|-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!text) return '（空内容）'
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

function visibilityMeta(visibility) {
  if (visibility === SHUOSHUO_VISIBILITY_PUBLIC) {
    return { label: '已发布', className: 'status--published' }
  }
  return { label: '草稿', className: 'status--draft' }
}

function isPublic(item) {
  return item?.visibility === SHUOSHUO_VISIBILITY_PUBLIC
}

function selectVisibility(value) {
  if (value === visibilityFilter.value) return
  visibilityFilter.value = value
  page.value = 1
  load()
}

function onPageChange(next) {
  if (next === page.value) return
  page.value = next
  load({ silent: true })
}

function onPageSizeChange(size) {
  if (size === pageSize.value) return
  pageSize.value = size
  page.value = 1
  load({ silent: true })
}

async function load({ silent = false } = {}) {
  if (silent) refreshing.value = true
  else loading.value = true
  error.value = ''

  try {
    const response = await listShuoshuo({
      visibility: visibilityFilter.value === VISIBILITY_ALL ? '' : visibilityFilter.value,
      page: Math.max(page.value - 1, 0),
      size: pageSize.value,
    })
    items.value = Array.isArray(response?.items) ? response.items : []
    total.value = Number(response?.totalElements) || 0
  } catch (loadError) {
    error.value = loadError.message || '说说列表加载失败'
    items.value = []
    total.value = 0
    toast.error(error.value)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

/** 变更后刷新当前列表；末页删空时回退一页。 */
async function refreshAfterMutation() {
  await load({ silent: true })
  if (!items.value.length && page.value > 1) {
    page.value -= 1
    await load({ silent: true })
  }
}

/** 发布 / 撤回：切换可见性，撤回会保留发布时间。 */
async function toggleVisibility(item, publish) {
  if (!item || actingId.value) return
  actingId.value = item.id
  try {
    if (publish) {
      await publishShuoshuo(item.id)
      toast.success('说说已发布')
    } else {
      await unpublishShuoshuo(item.id)
      toast.warning('说说已撤回')
    }
    await refreshAfterMutation()
  } catch (actionError) {
    toast.error(actionError.message || (publish ? '发布失败，请稍后重试' : '撤回失败，请稍后重试'))
  } finally {
    actingId.value = null
  }
}

function askRemove(item) {
  removalTarget.value = item
  removalOpen.value = true
}

async function confirmRemove() {
  const target = removalTarget.value
  if (!target || removing.value) return

  removing.value = true
  try {
    await deleteShuoshuo(target.id)
    removalOpen.value = false
    removalTarget.value = null
    toast.success('说说已删除')
    await refreshAfterMutation()
  } catch (deleteError) {
    toast.error(deleteError.message || '删除失败，请稍后重试')
  } finally {
    removing.value = false
  }
}

function openCreate() {
  editorIsNew.value = true
  editorTarget.value = null
  editorContent.value = ''
  editorErrors.value = {}
  editorSubmitError.value = ''
  editorOpen.value = true
}

function openEditor(item) {
  editorIsNew.value = false
  editorTarget.value = item
  editorContent.value = item?.content || ''
  editorErrors.value = {}
  editorSubmitError.value = ''
  editorOpen.value = true
}

function clearEditError(key) {
  if (editorErrors.value[key]) {
    const next = { ...editorErrors.value }
    delete next[key]
    editorErrors.value = next
  }
  editorSubmitError.value = ''
}

/** 表单校验：正文必填，且长度不超过后端上限。 */
function validateEditorForm(content) {
  const errors = {}
  const text = typeof content === 'string' ? content : ''

  if (!text.trim()) errors.content = '请输入说说内容'
  else if (text.length > SHUOSHUO_CONTENT_LIMIT)
    errors.content = `说说内容不能超过 ${SHUOSHUO_CONTENT_LIMIT} 个字符`

  return errors
}

async function submitEditor(publish) {
  if (savingEditor.value) return

  editorSubmitError.value = ''
  const errors = validateEditorForm(editorContent.value)
  editorErrors.value = errors
  if (Object.keys(errors).length) {
    toast.warning('还有几项内容需要完善')
    return
  }

  const content = editorContent.value.trim()
  savingEditor.value = true
  savingAction.value = publish ? 'publish' : 'save'
  try {
    let target = editorTarget.value
    if (target) {
      await updateShuoshuo(target.id, content)
    } else {
      target = await createShuoshuo(content)
      // 新建成功后回填 id：即使后续发布失败，再次提交也是更新而不是重复新建。
      editorTarget.value = target
      editorIsNew.value = false
    }

    if (publish && target && target.visibility !== SHUOSHUO_VISIBILITY_PUBLIC) {
      await publishShuoshuo(target.id)
    }

    editorOpen.value = false
    toast.success(publish ? '说说已保存并发布' : '说说已保存')
    await refreshAfterMutation()
  } catch (saveError) {
    if (saveError.fieldErrors?.content) {
      editorErrors.value = { ...editorErrors.value, content: String(saveError.fieldErrors.content) }
    }
    editorSubmitError.value = saveError.message || '保存失败，请稍后重试'
    toast.error(editorSubmitError.value)
  } finally {
    savingEditor.value = false
    savingAction.value = ''
  }
}

watch(editorContent, () => clearEditError('content'))

const initialVisibility = typeof route.query.visibility === 'string' ? route.query.visibility : ''
if (VISIBILITY_OPTIONS.some((option) => option.value === initialVisibility)) {
  visibilityFilter.value = initialVisibility
}

onMounted(() => {
  load()
})

watch(
  () => route.query.visibility,
  (next) => {
    if (typeof next !== 'string') return
    if (VISIBILITY_OPTIONS.some((option) => option.value === next)) selectVisibility(next)
  },
)
</script>

<template>
  <div class="shuoshuo-page page-reveal">
    <header class="list-head">
      <div class="list-head__copy">
        <p class="list-head__eyebrow">
          CONTENT / SHUOSHUO
          <span class="list-head__eyebrow-rule" aria-hidden="true" />
          {{ total }} 条记录
        </p>
        <h1 class="list-head__title">说说管理</h1>
        <p class="list-head__lead">记录随手的想法与近况，发布后会展示在博客前台。</p>
      </div>

      <div class="list-head__actions">
        <button class="btn btn--primary" type="button" @click="openCreate">
          <AppIcon name="plus" :size="15" />
          写说说
        </button>
        <button
          class="btn"
          type="button"
          :disabled="refreshing || loading"
          @click="load({ silent: true })"
        >
          <span v-if="refreshing" class="btn__spinner" />
          <AppIcon v-else name="refresh" :size="14" />
          刷新
        </button>
      </div>
    </header>

    <div v-if="error" class="callout callout--danger" role="alert">
      <AppIcon class="callout__icon" name="alert-circle" :size="16" />
      <span>{{ error }}</span>
      <button class="inline-retry" type="button" @click="load()">重试</button>
    </div>

    <section class="panel list-panel">
      <div class="filter-bar">
        <div class="filter-bar__select">
          <AppSelect
            :model-value="visibilityFilter"
            :options="visibilityOptions"
            aria-label="按可见性筛选"
            @update:model-value="selectVisibility"
          />
        </div>

        <div class="filter-bar__spacer" />

        <span class="source-pill" title="数据来自管理端说说接口">
          <span class="source-pill__dot" />
          管理端接口
        </span>
      </div>

      <AppTable
        :columns="columns"
        :rows="items"
        row-key="id"
        :loading="loading"
        :empty-title="emptyTitle"
        :empty-description="emptyDescription"
        aria-label="说说管理列表"
      >
        <template #empty>
          <AppEmptyState icon="feather" :title="emptyTitle" :description="emptyDescription">
            <button
              v-if="visibilityFilter !== VISIBILITY_ALL"
              class="btn btn--sm"
              type="button"
              @click="selectVisibility(VISIBILITY_ALL)"
            >
              查看全部说说
            </button>
            <button v-else class="btn btn--sm" type="button" @click="openCreate">写第一条说说</button>
          </AppEmptyState>
        </template>

        <template #cell-content="{ row }">
          <span class="shuoshuo-cell__content" :title="row.content || ''">
            {{ summarize(row.content) }}
          </span>
        </template>

        <template #cell-visibility="{ row }">
          <span class="status" :class="visibilityMeta(row.visibility).className">
            {{ visibilityMeta(row.visibility).label }}
          </span>
        </template>

        <template #cell-publishedAt="{ row }">
          <div v-if="row.publishedAt" class="date-cell">
            <span>{{ formatDateTime(row.publishedAt) }}</span>
            <small>{{ formatRelative(row.publishedAt) }}</small>
          </div>
          <span v-else class="cell-muted">—</span>
        </template>

        <template #cell-updatedAt="{ row }">
          <div class="date-cell">
            <span>{{ formatDateTime(row.updatedAt) }}</span>
            <small>{{ formatRelative(row.updatedAt) }}</small>
          </div>
        </template>

        <template #cell-actions="{ row }">
          <div class="row-actions">
            <button
              class="row-action"
              type="button"
              title="编辑说说"
              aria-label="编辑说说"
              @click="openEditor(row)"
            >
              <AppIcon name="edit" :size="14" />
            </button>
            <button
              v-if="!isPublic(row)"
              class="row-action"
              type="button"
              title="发布说说"
              aria-label="发布说说"
              :disabled="actingId === row.id"
              @click="toggleVisibility(row, true)"
            >
              <AppIcon name="check-circle" :size="14" />
            </button>
            <button
              v-else
              class="row-action row-action--warn"
              type="button"
              title="撤回说说"
              aria-label="撤回说说"
              :disabled="actingId === row.id"
              @click="toggleVisibility(row, false)"
            >
              <AppIcon name="close" :size="14" />
            </button>
            <button
              class="row-action row-action--danger"
              type="button"
              title="删除说说"
              aria-label="删除说说"
              @click="askRemove(row)"
            >
              <AppIcon name="trash" :size="14" />
            </button>
          </div>
        </template>
      </AppTable>

      <footer class="list-panel__footer">
        <span class="list-panel__meta">已发布的说说会展示在博客前台的说说页</span>
        <AppPagination
          :page="page"
          :page-size="pageSize"
          :total="total"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </footer>
    </section>

    <p class="list-footnote">
      <AppIcon name="info" :size="12" />
      <span>说说以 Markdown 原文保存；撤回只会隐藏内容，不会清空发布时间。</span>
    </p>

    <AppModal
      v-model:open="editorOpen"
      :title="editorTitle"
      :description="editorDescription"
      size="lg"
    >
      <div class="editor-form">
        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label">内容<span class="form-label__required">*</span></label>
            <span class="form-label__optional">Markdown</span>
          </div>

          <div class="markdown-shell" :class="{ 'markdown-shell--invalid': editorErrors.content }">
            <MdEditor
              v-model="editorContent"
              :theme="theme.isDark ? 'dark' : 'light'"
              :toolbars="toolbars"
              :preview="true"
              :max-length="SHUOSHUO_CONTENT_LIMIT"
              :disabled="savingEditor"
              :on-upload-img="uploadEditorImages"
              language="zh-CN"
              placeholder="写下此刻的想法…支持 Markdown、代码块、列表、图片与链接。"
            />
          </div>

          <p v-if="editorErrors.content" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editorErrors.content }}
          </p>
          <p v-else class="form-help">正文最多 {{ SHUOSHUO_CONTENT_LIMIT }} 个字符，支持 Markdown 语法。</p>
        </div>

        <div class="editor-form__meta">
          <span class="editor-form__count">
            字数 {{ contentLength }} / {{ SHUOSHUO_CONTENT_LIMIT }}
          </span>
          <span>保存为 Markdown 原文</span>
        </div>

        <div v-if="editorSubmitError" class="callout callout--danger editor-form__error" role="alert">
          <AppIcon class="callout__icon" name="alert-circle" :size="15" />
          <span>{{ editorSubmitError }}</span>
        </div>
      </div>

      <template #footer>
        <button class="btn" type="button" :disabled="savingEditor" @click="editorOpen = false">
          取消
        </button>
        <button class="btn" type="button" :disabled="savingEditor" @click="submitEditor(false)">
          <span v-if="savingAction === 'save'" class="btn__spinner" />
          {{ savingAction === 'save' ? '保存中…' : '保存' }}
        </button>
        <button
          class="btn btn--primary"
          type="button"
          :disabled="savingEditor"
          @click="submitEditor(true)"
        >
          <AppIcon v-if="savingAction !== 'publish'" name="send" :size="14" />
          <span v-else class="btn__spinner" />
          {{ savingAction === 'publish' ? '发布中…' : '保存并发布' }}
        </button>
      </template>
    </AppModal>

    <AppModal
      v-model:open="removalOpen"
      :title="removalCopy.title"
      :description="removalCopy.description"
      size="sm"
    >
      <div class="remove-modal__note">
        <AppIcon name="alert-triangle" :size="17" />
        <span>{{ removalCopy.note }}</span>
      </div>
      <template #footer>
        <button class="btn" type="button" @click="removalOpen = false">取消</button>
        <button class="btn btn--danger" type="button" :disabled="removing" @click="confirmRemove">
          <span v-if="removing" class="btn__spinner" />
          {{ removalCopy.confirm }}
        </button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.shuoshuo-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.list-head {
  display: flex;
  gap: 22px;
  align-items: flex-end;
  justify-content: space-between;
}

.list-head__copy {
  min-width: 0;
}

.list-head__eyebrow {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 11px;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

.list-head__eyebrow-rule {
  width: 18px;
  height: 1px;
  background: var(--border-strong);
}

.list-head__title {
  font-size: clamp(23px, 2.4vw, 28px);
  font-weight: var(--weight-semibold);
  line-height: 1.2;
  color: var(--text-primary);
  letter-spacing: -0.04em;
}

.list-head__lead {
  margin-top: 7px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.list-head__actions {
  display: flex;
  flex: none;
  gap: 7px;
}

.list-panel {
  overflow: hidden;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.filter-bar__spacer {
  flex: 1;
}

.filter-bar__select {
  width: 132px;
}

.filter-bar__select :deep(.select-trigger) {
  height: 34px;
  font-size: var(--text-xs);
  background: var(--bg-inset);
  border-color: transparent;
}

.source-pill {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 23px;
  padding: 0 8px;
  font-size: 10px;
  color: var(--text-tertiary);
  white-space: nowrap;
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
}

.source-pill__dot {
  width: 4px;
  height: 4px;
  background: var(--success);
  border-radius: 50%;
}

.shuoshuo-cell__content {
  display: -webkit-box;
  overflow: hidden;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.cell-muted {
  font-size: var(--text-xs);
  color: var(--text-disabled);
}

.date-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: var(--text-2xs);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.date-cell small {
  font-size: 10px;
  color: var(--text-tertiary);
}

.row-actions {
  display: flex;
  gap: 2px;
  align-items: center;
  justify-content: flex-end;
}

.row-action {
  display: grid;
  width: 25px;
  height: 25px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  place-items: center;
}

.row-action:hover:not(:disabled) {
  color: var(--text-primary);
  background: var(--bg-surface);
  border-color: var(--border);
}

.row-action--warn:hover:not(:disabled) {
  color: var(--warning-text);
  background: var(--warning-soft);
  border-color: transparent;
}

.row-action--danger:hover:not(:disabled) {
  color: var(--danger-text);
  background: var(--danger-soft);
  border-color: transparent;
}

.row-action:disabled {
  color: var(--text-disabled);
  cursor: not-allowed;
}

.list-panel__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding: 13px 16px;
  border-top: 1px solid var(--border-subtle);
}

.list-panel__meta {
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.list-panel__footer :deep(.pagination) {
  width: auto;
  flex: 1;
}

.list-footnote {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding: 0 2px;
  font-size: 10px;
  line-height: 1.6;
  color: var(--text-tertiary);
}

.list-footnote .app-icon {
  flex: none;
  margin-top: 1px;
}

.inline-retry {
  margin-left: auto;
  padding: 0;
  font-size: var(--text-xs);
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.editor-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.editor-form__meta {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.editor-form__count {
  font-variant-numeric: tabular-nums;
}

.editor-form__error {
  align-items: flex-start;
}

.markdown-shell {
  height: 340px;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.markdown-shell--invalid {
  border-color: var(--danger);
  box-shadow: inset 0 0 0 1px var(--danger);
}

.remove-modal__note {
  display: flex;
  gap: 9px;
  padding: 11px 12px;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  background: var(--bg-inset);
  border-radius: var(--radius-sm);
}

.remove-modal__note .app-icon {
  flex: none;
  margin-top: 1px;
  color: var(--warning);
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
  padding: 18px 20px 40px;
  font-size: 13px;
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

@media (max-width: 1024px) {
  .filter-bar__spacer {
    display: none;
  }

  .source-pill {
    margin-left: auto;
  }
}

@media (max-width: 860px) {
  .list-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .list-panel__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .list-panel__footer :deep(.pagination) {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .filter-bar__select {
    width: 100%;
  }

  .source-pill {
    width: 100%;
    justify-content: center;
    margin-left: 0;
  }

  .list-footnote {
    display: none;
  }
}
</style>

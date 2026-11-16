<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppTable from '@/components/common/AppTable.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import AppModal from '@/components/common/AppModal.vue'
import { resolveAssetUrl } from '@/api/client.js'
import { deleteArticle } from '@/api/articles.js'
import { loadArticleWorkspace, managementStatus, publishedUrl } from '@/services/articleAdmin.js'
import { removeLocalArticle } from '@/services/localLibrary.js'
import { ARTICLE_CATEGORIES, categoryLabel, inferCategoryFromTags } from '@/data/categories.js'
import { formatDate, formatDateTime, formatRelative } from '@/utils/format.js'
import { useToastStore } from '@/stores/toast.js'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()

const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const articles = ref([])
const source = ref('')
const fetchedAt = ref(null)

const search = ref('')
const statusFilter = ref('ALL')
const categoryFilter = ref('ALL')
const tagFilter = ref('ALL')
const page = ref(1)
const pageSize = ref(10)
const selected = ref([])

const removalOpen = ref(false)
const removalMode = ref('single')
const removalItems = ref([])
const removing = ref(false)

const initialStatus = typeof route.query.status === 'string' ? route.query.status : ''
if (['ALL', 'PUBLISHED', 'DRAFT', 'SCHEDULED'].includes(initialStatus)) {
  statusFilter.value = initialStatus
}

const columns = [
  { key: 'article', label: '文章', width: '43%' },
  { key: 'status', label: '状态', width: '100px' },
  { key: 'category', label: '分类', width: '106px' },
  { key: 'tags', label: '标签', width: '180px' },
  { key: 'date', label: '时间', width: '142px' },
  { key: 'actions', label: '', width: '112px', align: 'right' },
]

const enriched = computed(() =>
  articles.value.map((article) => ({
    ...article,
    category: article.localMeta?.category || article.category || inferCategoryFromTags(article.tags),
    uiStatus: managementStatus(article),
  })),
)

const statusCounts = computed(() => ({
  all: enriched.value.length,
  published: enriched.value.filter((article) => article.uiStatus === 'published').length,
  draft: enriched.value.filter((article) => article.uiStatus === 'draft').length,
  scheduled: enriched.value.filter((article) => article.uiStatus === 'scheduled').length,
}))

const statusOptions = computed(() => [
  { value: 'ALL', label: '全部状态' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'SCHEDULED', label: '计划草稿' },
])

const categoryOptions = computed(() => {
  const counts = new Map()
  enriched.value.forEach((article) => {
    counts.set(article.category, (counts.get(article.category) || 0) + 1)
  })
  return [
    { value: 'ALL', label: '全部分类' },
    ...ARTICLE_CATEGORIES.filter((category) => counts.has(category.value)).map((category) => ({
      value: category.value,
      label: `${category.label} (${counts.get(category.value)})`,
    })),
  ]
})

const tagOptions = computed(() => {
  const counts = new Map()
  enriched.value.forEach((article) => {
    ;(article.tags || []).forEach((tag) => {
      const name = typeof tag === 'string' ? tag : tag.name
      if (!name) return
      counts.set(name, (counts.get(name) || 0) + 1)
    })
  })
  return [
    { value: 'ALL', label: '全部标签' },
    ...[...counts.entries()]
      .sort((left, right) => left[0].localeCompare(right[0], 'zh-CN'))
      .map(([name, count]) => ({ value: name, label: `${name} (${count})` })),
  ]
})

const filtered = computed(() => {
  const keyword = search.value.trim().toLowerCase()

  return enriched.value.filter((article) => {
    if (statusFilter.value !== 'ALL' && article.uiStatus !== statusFilter.value.toLowerCase()) {
      return false
    }
    if (categoryFilter.value !== 'ALL' && article.category !== categoryFilter.value) return false

    if (tagFilter.value !== 'ALL') {
      const names = (article.tags || []).map((tag) => (typeof tag === 'string' ? tag : tag.name))
      if (!names.includes(tagFilter.value)) return false
    }

    if (!keyword) return true

    const haystack = [
      article.title,
      article.slug,
      article.summary,
      ...(article.tags || []).map((tag) => (typeof tag === 'string' ? tag : tag.name)),
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()

    return haystack.includes(keyword)
  })
})

const total = computed(() => filtered.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const currentPage = computed(() => Math.min(page.value, totalPages.value))
const pageRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

const filtersActive = computed(
  () =>
    Boolean(search.value) ||
    statusFilter.value !== 'ALL' ||
    categoryFilter.value !== 'ALL' ||
    tagFilter.value !== 'ALL',
)

const selectedArticles = computed(() =>
  selected.value
    .map((key) => articles.value.find((article) => articleKey(article) === key))
    .filter(Boolean),
)

const selectedRemovable = computed(
  () =>
    selectedArticles.value.length > 0 &&
    selectedArticles.value.every((article) => isLocalDraft(article)),
)

const selectedPublished = computed(() =>
  selectedArticles.value.filter((article) => article.status === 'PUBLISHED'),
)

const removalCopy = computed(() => {
  if (removalMode.value === 'bulk') {
    return {
      title: `移除 ${removalItems.value.length} 篇本地草稿？`,
      description:
        '这些草稿只存在于当前浏览器。移除后列表中将不再显示；服务器上的记录不会被删除。',
      note: '此操作只影响当前浏览器中的文章列表，不会调用后端删除接口。',
      confirm: '移除本地记录',
    }
  }
  const item = removalItems.value[0]
  if (item?.id) {
    return {
      title: `删除《${item?.title || '这篇文章'}》？`,
      description: '文章将从前台与管理列表中移除（软删除）。删除后无法在界面中恢复，请谨慎确认。',
      note: '此操作会调用后端删除接口，删除服务器上的文章（软删除，保留数据库记录）。',
      confirm: '删除文章',
    }
  }
  return {
    title: `移除《${item?.title || '这篇草稿'}》？`,
    description: '这篇草稿的本地记录将被移除，且无法从当前浏览器恢复；服务器上的记录不会被删除。',
    note: '此操作只影响当前浏览器中的文章列表，不会调用后端删除接口。',
    confirm: '移除本地记录',
  }
})

function articleKey(article) {
  return article.id ? `id:${article.id}` : `slug:${article.slug}`
}

function isLocalDraft(article) {
  return article?.dataSource === 'local' && article.status === 'DRAFT'
}

function displayTagNames(article) {
  return (article.tags || [])
    .map((tag) => (typeof tag === 'string' ? tag : tag.name))
    .filter(Boolean)
}

function articleDateValue(article) {
  return (
    article.publishedAt ||
    article.localMeta?.savedAt ||
    article.updatedAt ||
    article.createdAt
  )
}

function articleTimeLabel(article) {
  return formatDate(articleDateValue(article))
}

async function load({ silent = false } = {}) {
  if (silent) refreshing.value = true
  else loading.value = true
  error.value = ''

  try {
    const workspace = await loadArticleWorkspace()
    articles.value = workspace.items
    source.value = workspace.source
    fetchedAt.value = workspace.fetchedAt
    selected.value = []
  } catch (loadError) {
    error.value = loadError.message || '文章列表加载失败'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(load)

watch(
  [search, statusFilter, categoryFilter, tagFilter],
  () => {
    page.value = 1
    selected.value = []
  },
  { flush: 'post' },
)

watch(page, () => {
  selected.value = []
})

watch(
  () => route.query.status,
  (next) => {
    if (typeof next !== 'string') return
    if (['ALL', 'PUBLISHED', 'DRAFT', 'SCHEDULED'].includes(next)) {
      statusFilter.value = next
    }
  },
)

function resetFilters() {
  search.value = ''
  statusFilter.value = 'ALL'
  categoryFilter.value = 'ALL'
  tagFilter.value = 'ALL'
  page.value = 1
}

function onPageChange(next) {
  page.value = next
}

function onPageSizeChange(size) {
  pageSize.value = size
  page.value = 1
}

function openArticle(article) {
  if (article.status !== 'PUBLISHED') return
  window.open(publishedUrl(article.slug), '_blank', 'noopener,noreferrer')
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const success = document.execCommand('copy')
    textarea.remove()
    return success
  }
}

async function copyLink(article) {
  if (article.status !== 'PUBLISHED') {
    toast.warning('草稿还没有公开链接')
    return
  }
  const success = await copyText(publishedUrl(article.slug))
  if (success) toast.success('文章链接已复制')
  else toast.error('复制失败，请手动复制浏览器地址')
}

async function copySelectedLinks() {
  if (!selectedPublished.value.length) return
  const success = await copyText(selectedPublished.value.map((article) => publishedUrl(article.slug)).join('\n'))
  if (success) toast.success(`已复制 ${selectedPublished.value.length} 个文章链接`)
  else toast.error('复制失败，请重试')
}

function reuseArticle(article) {
  if (article.status === 'PUBLISHED') {
    router.push({ name: 'article-create', query: { from: article.slug } })
    return
  }
  if (isLocalDraft(article)) {
    router.push({ name: 'article-create', query: { fromLocal: article.slug } })
    return
  }
  toast.info('这篇文章没有可复用的本地正文')
}

function askRemove(article) {
  if (!isLocalDraft(article) && !article.id) return
  removalMode.value = 'single'
  removalItems.value = [article]
  removalOpen.value = true
}

function askRemoveSelected() {
  if (!selectedRemovable.value) return
  removalMode.value = 'bulk'
  removalItems.value = [...selectedArticles.value]
  removalOpen.value = true
}

async function confirmRemove() {
  if (!removalItems.value.length || removing.value) return
  removing.value = true

  // 有 id 的为服务端文章（调用后端删除接口），无 id 的为本地草稿（仅清理本机记录）
  const serverItems = removalItems.value.filter((article) => Boolean(article.id))
  const localItems = removalItems.value.filter((article) => !article.id)

  try {
    for (const article of serverItems) {
      await deleteArticle(article.id)
    }

    if (localItems.length) {
      localItems.forEach((article) => {
        removeLocalArticle({ id: article.id, slug: article.slug })
      })
      const removedKeys = new Set(removalItems.value.map((article) => articleKey(article)))
      articles.value = articles.value.filter((article) => !removedKeys.has(articleKey(article)))
      selected.value = selected.value.filter((key) => !removedKeys.has(key))
    }

    removalOpen.value = false

    if (serverItems.length) {
      toast.success(serverItems.length > 1 ? `已删除 ${serverItems.length} 篇文章` : '文章已删除')
      // 删除成功后重新拉取列表，并在末页删空时回退页码
      await load({ silent: true })
      const maxPage = Math.max(1, Math.ceil(filtered.value.length / pageSize.value))
      if (page.value > maxPage) {
        page.value = maxPage
      }
    } else {
      toast.success(removalMode.value === 'bulk' ? '本地草稿已移除' : '本地草稿记录已移除')
    }
  } catch (error) {
    toast.error(error.message || '删除失败，请稍后重试')
    if (serverItems.length) {
      // 批量部分成功时同步一次列表，保持界面与服务端一致
      await load({ silent: true })
    }
  } finally {
    removing.value = false
  }
}

function statusMeta(article) {
  if (article.uiStatus === 'published') {
    return { label: '已发布', className: 'status--published' }
  }
  if (article.uiStatus === 'scheduled') {
    return { label: '计划草稿', className: 'status--scheduled' }
  }
  return { label: '草稿', className: 'status--draft' }
}

const emptyTitle = computed(() => {
  if (loading.value) return '正在加载文章'
  if (!articles.value.length) return '还没有文章'
  return '没有找到匹配的文章'
})

const emptyDescription = computed(() => {
  if (!articles.value.length) return '从一篇文章开始，让这里慢慢长出内容。'
  return '试试更换关键词，或清除当前筛选条件。'
})
</script>

<template>
  <div class="article-list-page page-reveal">
    <header class="list-head">
      <div class="list-head__copy">
        <p class="list-head__eyebrow">
          CONTENT / ARTICLES
          <span class="list-head__eyebrow-rule" aria-hidden="true" />
          {{ statusCounts.all }} 篇记录
        </p>
        <h1 class="list-head__title">文章管理</h1>
        <p class="list-head__lead">整理发布节奏，也让旧文章继续被看见。</p>
      </div>

      <div class="list-head__actions">
        <button class="btn" type="button" :disabled="refreshing || loading" @click="load({ silent: true })">
          <span v-if="refreshing" class="btn__spinner" />
          <AppIcon v-else name="refresh" :size="14" />
          刷新
        </button>
        <button class="btn btn--primary" type="button" @click="router.push({ name: 'article-create' })">
          <AppIcon name="plus" :size="15" />
          写文章
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
        <div class="filter-bar__search input-group">
          <span class="input-group__icon">
            <AppIcon name="search" :size="16" />
          </span>
          <input
            v-model="search"
            class="input"
            type="search"
            placeholder="搜索标题、slug 或标签…"
            aria-label="搜索文章"
            @keydown.esc="search = ''"
          />
          <button
            v-if="search"
            class="filter-bar__clear"
            type="button"
            aria-label="清除搜索"
            @click="search = ''"
          >
            <AppIcon name="close" :size="12" />
          </button>
        </div>

        <div class="filter-bar__select">
          <AppSelect
            v-model="statusFilter"
            :options="statusOptions"
            aria-label="按状态筛选"
          />
        </div>
        <div class="filter-bar__select">
          <AppSelect
            v-model="categoryFilter"
            :options="categoryOptions"
            aria-label="按分类筛选"
          />
        </div>
        <div class="filter-bar__select filter-bar__select--tag">
          <AppSelect
            v-model="tagFilter"
            :options="tagOptions"
            aria-label="按标签筛选"
          />
        </div>

        <button
          v-if="filtersActive"
          class="filter-bar__reset"
          type="button"
          @click="resetFilters"
        >
          <AppIcon name="rotate-ccw" :size="12" />
          重置
        </button>

        <div class="filter-bar__spacer" />
        <span
          class="source-pill"
          :title="source === 'public+local'
            ? '后端管理端文章集合接口暂未提供，已回退为公开文章 + 本机草稿数据'
            : '数据来自管理端文章集合接口'"
        >
          <span class="source-pill__dot" />
          {{ source === 'public+local' ? '公开接口 + 本机缓存' : source === 'admin-api' ? '管理端接口' : '等待数据' }}
        </span>
      </div>

      <div class="bulk-bar" :class="{ 'is-active': selected.length > 0 }">
        <div class="bulk-bar__selection">
          <span class="bulk-bar__count">{{ selected.length }}</span>
          <span>已选择</span>
          <button v-if="selected.length" class="bulk-bar__clear" type="button" @click="selected = []">
            取消选择
          </button>
        </div>
        <div class="bulk-bar__actions">
          <button
            class="btn btn--sm btn--ghost"
            type="button"
            :disabled="!selectedPublished.length"
            title="复制选中文章的公开链接"
            @click="copySelectedLinks"
          >
            <AppIcon name="link" :size="13" />
            复制链接
          </button>
          <button
            class="btn btn--sm btn--ghost bulk-bar__danger"
            type="button"
            :disabled="!selectedRemovable"
            :title="selectedRemovable ? '只移除本机草稿记录，不删除服务器端数据' : '仅全部为本地草稿时可批量移除'"
            @click="askRemoveSelected"
          >
            <AppIcon name="trash" :size="13" />
            批量移除
          </button>
        </div>
      </div>

      <AppTable
        v-model:selected="selected"
        :columns="columns"
        :rows="pageRows"
        :row-key="articleKey"
        :loading="loading"
        selectable
        :empty-title="emptyTitle"
        :empty-description="emptyDescription"
        aria-label="文章管理列表"
      >
        <template #cell-article="{ row }">
          <div class="article-cell">
            <span class="article-cell__cover">
              <img v-if="row.coverUrl" :src="resolveAssetUrl(row.coverUrl)" alt="" />
              <AppIcon v-else name="article" :size="16" />
            </span>
            <span class="article-cell__copy">
              <span class="article-cell__title-row">
                <button
                  v-if="row.status === 'PUBLISHED'"
                  class="article-cell__title article-cell__title--link"
                  type="button"
                  @click="openArticle(row)"
                >
                  {{ row.title }}
                </button>
                <span v-else class="article-cell__title">{{ row.title }}</span>
                <span v-if="row.localMeta?.pinned" class="article-cell__pin" title="置顶">
                  <AppIcon name="pin" :size="10" />
                </span>
                <span v-if="row.dataSource === 'local'" class="article-cell__local">本地</span>
              </span>
              <span class="article-cell__slug mono">/{{ row.slug }}</span>
            </span>
          </div>
        </template>

        <template #cell-status="{ row }">
          <span class="status" :class="statusMeta(row).className">{{ statusMeta(row).label }}</span>
        </template>

        <template #cell-category="{ row }">
          <span class="category-name">{{ categoryLabel(row.category) }}</span>
        </template>

        <template #cell-tags="{ row }">
          <div v-if="displayTagNames(row).length" class="tag-list">
            <span v-for="tag in displayTagNames(row).slice(0, 2)" :key="tag" class="tag">{{ tag }}</span>
            <span v-if="displayTagNames(row).length > 2" class="tag-more">
              +{{ displayTagNames(row).length - 2 }}
            </span>
          </div>
          <span v-else class="cell-muted">—</span>
        </template>

        <template #cell-date="{ row }">
          <div class="date-cell">
            <span>{{ articleTimeLabel(row) }}</span>
            <small>{{ formatRelative(articleDateValue(row)) }}</small>
          </div>
        </template>

        <template #cell-actions="{ row }">
          <div class="row-actions">
            <button
              v-if="row.status === 'PUBLISHED'"
              class="row-action"
              type="button"
              title="访问文章"
              aria-label="访问文章"
              @click="openArticle(row)"
            >
              <AppIcon name="external-link" :size="14" />
            </button>
            <button
              v-if="row.status === 'PUBLISHED'"
              class="row-action"
              type="button"
              title="复制公开链接"
              aria-label="复制公开链接"
              @click="copyLink(row)"
            >
              <AppIcon name="link" :size="14" />
            </button>
            <button
              v-if="row.id"
              class="row-action"
              type="button"
              title="编辑文章"
              aria-label="编辑文章"
              @click="router.push({ name: 'article-edit', params: { id: row.id } })"
            >
              <AppIcon name="edit" :size="14" />
            </button>
            <button
              v-if="row.status === 'PUBLISHED' || isLocalDraft(row)"
              class="row-action"
              type="button"
              title="以此为副本新建"
              aria-label="以此为副本新建"
              @click="reuseArticle(row)"
            >
              <AppIcon name="copy" :size="14" />
            </button>
            <button
              v-if="isLocalDraft(row) || Boolean(row.id)"
              class="row-action row-action--danger"
              type="button"
              :title="isLocalDraft(row) ? '移除本地草稿' : '删除文章'"
              :aria-label="isLocalDraft(row) ? '移除本地草稿' : '删除文章'"
              @click="askRemove(row)"
            >
              <AppIcon name="trash" :size="14" />
            </button>
          </div>
        </template>

        <template #empty-actions>
          <button
            v-if="articles.length"
            class="btn btn--sm"
            type="button"
            @click="resetFilters"
          >
            清除筛选
          </button>
          <button
            v-else
            class="btn btn--primary btn--sm"
            type="button"
            @click="router.push({ name: 'article-create' })"
          >
            <AppIcon name="plus" :size="13" />
            写第一篇文章
          </button>
        </template>
      </AppTable>

      <footer class="list-panel__footer">
        <span class="list-panel__meta">
          <span v-if="fetchedAt">更新于 {{ formatDateTime(fetchedAt) }}</span>
          <span v-else>等待首次加载</span>
        </span>
        <AppPagination
          :page="currentPage"
          :page-size="pageSize"
          :total="total"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </footer>
    </section>

    <p class="list-footnote">
      <AppIcon name="info" :size="12" />
      <span>
        {{
          source === 'admin-api'
            ? '文章与草稿来自管理端接口，支持在线编辑与删除（软删除）；分类、置顶等组织字段为本机视图设置。'
            : '后端管理端接口暂不可用，公开文章来自访客接口，草稿保存在本机浏览器。分类、置顶等组织字段同样为本地视图设置。'
        }}
      </span>
    </p>

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
.article-list-page {
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

.filter-bar__search {
  position: relative;
  width: min(278px, 100%);
}

.filter-bar__search .input {
  height: 34px;
  padding-right: 30px;
  font-size: var(--text-xs);
  background: var(--bg-inset);
  border-color: transparent;
}

.filter-bar__search .input:focus {
  background: var(--bg-surface);
}

.filter-bar__search .input-group__icon {
  left: 10px;
}

.filter-bar__clear {
  position: absolute;
  right: 5px;
  display: grid;
  width: 23px;
  height: 23px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-xs);
  place-items: center;
}

.filter-bar__clear:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
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

.filter-bar__select--tag {
  width: 138px;
}

.filter-bar__reset {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 30px;
  padding: 0 7px;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-xs);
}

.filter-bar__reset:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.filter-bar__spacer {
  flex: 1;
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
  background: var(--warning);
  border-radius: 50%;
}

.bulk-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 7px 16px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
  transition: background-color var(--duration-fast) var(--ease-out);
}

.bulk-bar.is-active {
  background: var(--brand-soft);
}

.bulk-bar__selection {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.bulk-bar.is-active .bulk-bar__selection {
  color: var(--brand-text);
}

.bulk-bar__count {
  display: inline-grid;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  font-size: 10px;
  font-weight: var(--weight-semibold);
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border-radius: var(--radius-xs);
  place-items: center;
}

.bulk-bar.is-active .bulk-bar__count {
  color: var(--brand-text);
  background: var(--bg-surface);
}

.bulk-bar__clear {
  padding: 0;
  margin-left: 4px;
  font-size: 10px;
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  background: transparent;
  border: 0;
  opacity: 0.75;
}

.bulk-bar__actions {
  display: flex;
  gap: 5px;
  align-items: center;
}

.bulk-bar__danger {
  color: var(--danger-text);
}

.article-cell {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.article-cell__cover {
  display: grid;
  flex: none;
  width: 34px;
  height: 34px;
  overflow: hidden;
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xs);
  place-items: center;
}

.article-cell__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.article-cell__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.article-cell__title-row {
  display: flex;
  gap: 5px;
  align-items: center;
  min-width: 0;
}

.article-cell__title {
  min-width: 0;
  overflow: hidden;
  padding: 0;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  border: 0;
}

.article-cell__title--link {
  cursor: pointer;
}

.article-cell__title--link:hover {
  color: var(--brand-text);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.article-cell__pin {
  display: grid;
  flex: none;
  width: 16px;
  height: 16px;
  color: var(--warning-text);
  background: var(--warning-soft);
  border-radius: 3px;
  place-items: center;
}

.article-cell__local {
  flex: none;
  padding: 1px 4px;
  font-size: 8px;
  color: var(--text-tertiary);
  letter-spacing: 0.02em;
  background: var(--bg-inset);
  border-radius: 2px;
}

.article-cell__slug {
  overflow: hidden;
  font-size: 10px;
  color: var(--text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-name {
  font-size: var(--text-xs);
  color: var(--text-secondary);
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

.cell-muted {
  font-size: var(--text-xs);
  color: var(--text-disabled);
}

.tag-more {
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

.row-action:hover {
  color: var(--text-primary);
  background: var(--bg-surface);
  border-color: var(--border);
}

.row-action--danger:hover {
  color: var(--danger-text);
  background: var(--danger-soft);
  border-color: transparent;
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

@media (max-width: 1240px) {
  .filter-bar__select {
    width: 120px;
  }

  .filter-bar__select--tag {
    width: 126px;
  }
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

  .filter-bar__search {
    width: 100%;
  }

  .filter-bar__select,
  .filter-bar__select--tag {
    flex: 1;
    width: auto;
    min-width: 120px;
  }

  .bulk-bar {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
    padding: 9px 16px;
  }

  .bulk-bar__actions {
    width: 100%;
    padding-left: 26px;
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
  .list-head__actions {
    width: 100%;
  }

  .list-head__actions .btn {
    flex: 1;
  }

  .source-pill {
    width: 100%;
    justify-content: center;
    margin-left: 0;
  }

  .filter-bar__select,
  .filter-bar__select--tag {
    flex: 1 1 45%;
  }

  .list-footnote {
    display: none;
  }
}
</style>

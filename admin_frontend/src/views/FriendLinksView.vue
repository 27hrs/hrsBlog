<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppTable from '@/components/common/AppTable.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppModal from '@/components/common/AppModal.vue'
import { resolveAssetUrl } from '@/api/client.js'
import {
  deleteFriendLink,
  listFriendLinks,
  reviewFriendLink,
  updateFriendLink,
} from '@/api/friendLinks.js'
import { formatDateTime, formatRelative } from '@/utils/format.js'
import { useToastStore } from '@/stores/toast.js'

const route = useRoute()
const toast = useToastStore()

const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const links = ref([])
const total = ref(0)
const counts = ref({ ALL: 0, PENDING: 0, APPROVED: 0, REJECTED: 0 })

const statusFilter = ref('ALL')
const page = ref(1)
const pageSize = ref(20)

const reviewingId = ref(null)
const removing = ref(false)
const removalOpen = ref(false)
const removalTarget = ref(null)

const editOpen = ref(false)
const editTarget = ref(null)
const editForm = ref({
  siteName: '',
  siteUrl: '',
  linkPageUrl: '',
  avatarUrl: '',
  description: '',
  contact: '',
  sortOrder: '0',
})
const editErrors = ref({})
const editSubmitError = ref('')
const savingEdit = ref(false)

const STATUS_TABS = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
]

const FIELD_LIMITS = {
  siteName: 100,
  siteUrl: 500,
  linkPageUrl: 500,
  avatarUrl: 500,
  description: 500,
  contact: 200,
}

// 与后端 FriendLinkAdminUpdateRequest 的 sortOrder 取值范围保持一致
const SORT_ORDER_MIN = 0
const SORT_ORDER_MAX = 9999

const columns = [
  { key: 'site', label: '站点名称', width: '17%' },
  { key: 'siteUrl', label: '站点地址', width: '17%' },
  { key: 'linkPageUrl', label: '友链页地址', width: '17%' },
  { key: 'description', label: '简介', width: '15%' },
  { key: 'contact', label: '联系方式', width: '14%' },
  { key: 'status', label: '状态', width: '88px' },
  { key: 'submittedAt', label: '提交时间', width: '128px' },
  { key: 'actions', label: '', width: '108px', align: 'right' },
]

const statusTabs = computed(() =>
  STATUS_TABS.map((tab) => ({
    ...tab,
    count: counts.value[tab.value] ?? 0,
  })),
)

const removalCopy = computed(() => {
  const item = removalTarget.value
  return {
    title: `删除友链《${item?.siteName || '这个站点'}》？`,
    description: '该友链会从访客可见的友链列表中移除，删除后无法在界面中恢复。',
    note: '此操作会调用后端删除接口，删除服务器上的友链记录。',
    confirm: '删除友链',
  }
})

const emptyTitle = computed(() => {
  if (loading.value) return '正在加载友链'
  if (statusFilter.value === 'ALL') return '还没有友链申请'
  return '没有符合条件的友链'
})

const emptyDescription = computed(() => {
  if (statusFilter.value === 'ALL') {
    return '访客在前台提交的友链申请会出现在这里，等待审核。'
  }
  return '试试切换到其他状态，看看还有哪些申请等待处理。'
})

/** http/https 网址校验：与后端保持一致，只接受完整网址。 */
function isHttpUrl(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function statusMeta(status) {
  if (status === 'APPROVED') return { label: '已通过', className: 'status--published' }
  if (status === 'REJECTED') return { label: '已拒绝', className: 'status--rejected' }
  return { label: '待审核', className: 'status--pending' }
}

function selectStatus(status) {
  if (status === statusFilter.value) return
  statusFilter.value = status
  page.value = 1
  load({ withCounts: true })
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

/** 各状态的数量徽标：分别取一次总数（只取 1 条数据），「全部」为三者之和。 */
async function loadCounts() {
  const results = await Promise.all(
    STATUS_TABS.filter((tab) => tab.value !== 'ALL').map(async (tab) => {
      try {
        const response = await listFriendLinks({ status: tab.value, page: 0, size: 1 })
        return [tab.value, Number(response?.totalElements) || 0]
      } catch {
        return [tab.value, 0]
      }
    }),
  )

  const next = { ALL: 0, PENDING: 0, APPROVED: 0, REJECTED: 0 }
  results.forEach(([key, value]) => {
    next[key] = value
  })
  next.ALL = next.PENDING + next.APPROVED + next.REJECTED
  counts.value = next
}

async function load({ silent = false, withCounts = false } = {}) {
  if (silent) refreshing.value = true
  else loading.value = true
  error.value = ''

  try {
    const response = await listFriendLinks({
      status: statusFilter.value === 'ALL' ? '' : statusFilter.value,
      page: Math.max(page.value - 1, 0),
      size: pageSize.value,
    })
    links.value = Array.isArray(response?.items) ? response.items : []
    total.value = Number(response?.totalElements) || 0
  } catch (loadError) {
    error.value = loadError.message || '友链列表加载失败'
    links.value = []
    total.value = 0
    toast.error(error.value)
  } finally {
    loading.value = false
    refreshing.value = false
  }

  if (withCounts) await loadCounts()
}

/** 变更后刷新当前列表；末页删空时回退一页。 */
async function refreshAfterMutation() {
  await load({ silent: true, withCounts: true })
  if (!links.value.length && page.value > 1) {
    page.value -= 1
    await load({ silent: true })
  }
}

async function review(link, status) {
  if (reviewingId.value) return
  reviewingId.value = link.id
  try {
    await reviewFriendLink(link.id, status)
    toast.success(status === 'APPROVED' ? '友链申请已通过' : '友链申请已拒绝')
    await refreshAfterMutation()
  } catch (reviewError) {
    toast.error(reviewError.message || '审核失败，请稍后重试')
  } finally {
    reviewingId.value = null
  }
}

function askRemove(link) {
  removalTarget.value = link
  removalOpen.value = true
}

async function confirmRemove() {
  const target = removalTarget.value
  if (!target || removing.value) return

  removing.value = true
  try {
    await deleteFriendLink(target.id)
    removalOpen.value = false
    removalTarget.value = null
    toast.success('友链已删除')
    await refreshAfterMutation()
  } catch (deleteError) {
    toast.error(deleteError.message || '删除失败，请稍后重试')
  } finally {
    removing.value = false
  }
}

function openEditor(link) {
  editTarget.value = link
  editErrors.value = {}
  editSubmitError.value = ''
  editForm.value = {
    siteName: link.siteName || '',
    siteUrl: link.siteUrl || '',
    linkPageUrl: link.linkPageUrl || '',
    avatarUrl: link.avatarUrl || '',
    description: link.description || '',
    contact: link.contact || '',
    sortOrder: String(link.sortOrder ?? 0),
  }
  editOpen.value = true
}

function clearEditError(key) {
  if (editErrors.value[key]) {
    const next = { ...editErrors.value }
    delete next[key]
    editErrors.value = next
  }
  editSubmitError.value = ''
}

/** 表单校验：必填项、http/https 网址格式与后端长度限制。 */
function validateEditForm(form) {
  const errors = {}
  const siteName = form.siteName.trim()
  const siteUrl = form.siteUrl.trim()
  const linkPageUrl = form.linkPageUrl.trim()
  const avatarUrl = form.avatarUrl.trim()
  const description = form.description.trim()
  const contact = form.contact.trim()
  const sortOrder = form.sortOrder.trim()

  if (!siteName) errors.siteName = '请输入站点名称'
  else if (siteName.length > FIELD_LIMITS.siteName)
    errors.siteName = `站点名称不能超过 ${FIELD_LIMITS.siteName} 个字符`

  if (!siteUrl) errors.siteUrl = '请输入站点地址'
  else if (siteUrl.length > FIELD_LIMITS.siteUrl)
    errors.siteUrl = `站点地址不能超过 ${FIELD_LIMITS.siteUrl} 个字符`
  else if (!isHttpUrl(siteUrl)) errors.siteUrl = '站点地址必须是 http 或 https 开头的完整网址'

  if (!linkPageUrl) errors.linkPageUrl = '请输入友链页地址'
  else if (linkPageUrl.length > FIELD_LIMITS.linkPageUrl)
    errors.linkPageUrl = `友链页地址不能超过 ${FIELD_LIMITS.linkPageUrl} 个字符`
  else if (!isHttpUrl(linkPageUrl))
    errors.linkPageUrl = '友链页地址必须是 http 或 https 开头的完整网址'

  if (avatarUrl) {
    if (avatarUrl.length > FIELD_LIMITS.avatarUrl)
      errors.avatarUrl = `图标地址不能超过 ${FIELD_LIMITS.avatarUrl} 个字符`
    else if (!isHttpUrl(avatarUrl))
      errors.avatarUrl = '图标地址必须是 http 或 https 开头的完整网址'
  }

  if (description.length > FIELD_LIMITS.description)
    errors.description = `站点简介不能超过 ${FIELD_LIMITS.description} 个字符`

  if (contact.length > FIELD_LIMITS.contact)
    errors.contact = `联系方式不能超过 ${FIELD_LIMITS.contact} 个字符`

  if (sortOrder) {
    const value = Number(sortOrder)
    if (!Number.isInteger(value)) errors.sortOrder = '排序值必须是整数'
    else if (value < SORT_ORDER_MIN) errors.sortOrder = `排序值不能小于 ${SORT_ORDER_MIN}`
    else if (value > SORT_ORDER_MAX) errors.sortOrder = `排序值不能大于 ${SORT_ORDER_MAX}`
  }

  return errors
}

async function submitEdit() {
  const target = editTarget.value
  if (!target || savingEdit.value) return

  editSubmitError.value = ''
  const errors = validateEditForm(editForm.value)
  editErrors.value = errors
  if (Object.keys(errors).length) {
    toast.warning('还有几项内容需要完善')
    return
  }

  const sortOrder = editForm.value.sortOrder.trim()

  savingEdit.value = true
  try {
    await updateFriendLink(target.id, {
      siteName: editForm.value.siteName.trim(),
      siteUrl: editForm.value.siteUrl.trim(),
      linkPageUrl: editForm.value.linkPageUrl.trim(),
      avatarUrl: editForm.value.avatarUrl.trim(),
      description: editForm.value.description.trim(),
      contact: editForm.value.contact.trim(),
      sortOrder: sortOrder === '' ? 0 : Number(sortOrder),
    })
    editOpen.value = false
    toast.success('友链已更新')
    await refreshAfterMutation()
  } catch (saveError) {
    if (saveError.fieldErrors) {
      const next = { ...editErrors.value }
      Object.entries(saveError.fieldErrors).forEach(([key, message]) => {
        if (key in FIELD_LIMITS || key === 'sortOrder') next[key] = message
      })
      editErrors.value = next
    }
    editSubmitError.value = saveError.message || '保存失败，请稍后重试'
    toast.error(editSubmitError.value)
  } finally {
    savingEdit.value = false
  }
}

const initialStatus = typeof route.query.status === 'string' ? route.query.status : ''
if (STATUS_TABS.some((tab) => tab.value === initialStatus)) {
  statusFilter.value = initialStatus
}

onMounted(() => {
  load({ withCounts: true })
})

watch(
  () => route.query.status,
  (next) => {
    if (typeof next !== 'string') return
    if (STATUS_TABS.some((tab) => tab.value === next)) selectStatus(next)
  },
)
</script>

<template>
  <div class="friend-links-page page-reveal">
    <header class="list-head">
      <div class="list-head__copy">
        <p class="list-head__eyebrow">
          CONTENT / FRIEND LINKS
          <span class="list-head__eyebrow-rule" aria-hidden="true" />
          {{ total }} 条记录
        </p>
        <h1 class="list-head__title">友链管理</h1>
        <p class="list-head__lead">审核访客提交的友链申请，也让老朋友保持可见。</p>
      </div>

      <div class="list-head__actions">
        <button
          class="btn"
          type="button"
          :disabled="refreshing || loading"
          @click="load({ silent: true, withCounts: true })"
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
      <button class="inline-retry" type="button" @click="load({ withCounts: true })">重试</button>
    </div>

    <section class="panel list-panel">
      <div class="filter-bar">
        <div class="segmented status-tabs" role="group" aria-label="按状态筛选">
          <button
            v-for="tab in statusTabs"
            :key="tab.value"
            class="segmented__item"
            :class="{ 'is-active': statusFilter === tab.value }"
            type="button"
            :aria-pressed="statusFilter === tab.value"
            @click="selectStatus(tab.value)"
          >
            <span>{{ tab.label }}</span>
            <span class="status-tabs__count">{{ tab.count }}</span>
          </button>
        </div>

        <div class="filter-bar__spacer" />

        <span class="source-pill" title="数据来自管理端友链接口">
          <span class="source-pill__dot" />
          管理端接口
        </span>
      </div>

      <AppTable
        :columns="columns"
        :rows="links"
        row-key="id"
        :loading="loading"
        :empty-title="emptyTitle"
        :empty-description="emptyDescription"
        aria-label="友链管理列表"
      >
        <template #empty>
          <AppEmptyState icon="link" :title="emptyTitle" :description="emptyDescription">
            <button
              v-if="statusFilter !== 'ALL'"
              class="btn btn--sm"
              type="button"
              @click="selectStatus('ALL')"
            >
              查看全部友链
            </button>
          </AppEmptyState>
        </template>

        <template #cell-site="{ row }">
          <div class="link-cell">
            <span class="link-cell__avatar">
              <img v-if="row.avatarUrl" :src="resolveAssetUrl(row.avatarUrl)" alt="" />
              <AppIcon v-else name="globe" :size="16" />
            </span>
            <span class="link-cell__copy">
              <span class="link-cell__name">{{ row.siteName }}</span>
              <span class="link-cell__sort mono">排序 {{ row.sortOrder ?? 0 }}</span>
            </span>
          </div>
        </template>

        <template #cell-siteUrl="{ row }">
          <a
            class="link-cell__url"
            :href="row.siteUrl"
            target="_blank"
            rel="noreferrer noopener"
            :title="row.siteUrl"
          >
            {{ row.siteUrl }}
          </a>
        </template>

        <template #cell-linkPageUrl="{ row }">
          <a
            class="link-cell__url"
            :href="row.linkPageUrl"
            target="_blank"
            rel="noreferrer noopener"
            :title="row.linkPageUrl"
          >
            {{ row.linkPageUrl }}
          </a>
        </template>

        <template #cell-description="{ row }">
          <span class="link-cell__desc" :title="row.description || ''">{{ row.description || '—' }}</span>
        </template>

        <template #cell-contact="{ row }">
          <span v-if="row.contact" class="link-cell__contact">{{ row.contact }}</span>
          <span v-else class="cell-muted">—</span>
        </template>

        <template #cell-status="{ row }">
          <span class="status" :class="statusMeta(row.status).className">
            {{ statusMeta(row.status).label }}
          </span>
        </template>

        <template #cell-submittedAt="{ row }">
          <div class="date-cell">
            <span>{{ formatDateTime(row.submittedAt) }}</span>
            <small>{{ formatRelative(row.submittedAt) }}</small>
          </div>
        </template>

        <template #cell-actions="{ row }">
          <div class="row-actions">
            <button
              v-if="row.status !== 'APPROVED'"
              class="row-action"
              type="button"
              title="通过友链申请"
              aria-label="通过友链申请"
              :disabled="reviewingId === row.id"
              @click="review(row, 'APPROVED')"
            >
              <AppIcon name="check-circle" :size="14" />
            </button>
            <button
              v-if="row.status !== 'REJECTED'"
              class="row-action row-action--reject"
              type="button"
              title="拒绝友链申请"
              aria-label="拒绝友链申请"
              :disabled="reviewingId === row.id"
              @click="review(row, 'REJECTED')"
            >
              <AppIcon name="close" :size="14" />
            </button>
            <button
              class="row-action"
              type="button"
              title="编辑友链"
              aria-label="编辑友链"
              @click="openEditor(row)"
            >
              <AppIcon name="edit" :size="14" />
            </button>
            <button
              class="row-action row-action--danger"
              type="button"
              title="删除友链"
              aria-label="删除友链"
              @click="askRemove(row)"
            >
              <AppIcon name="trash" :size="14" />
            </button>
          </div>
        </template>
      </AppTable>

      <footer class="list-panel__footer">
        <span class="list-panel__meta">已通过的友链会展示在博客前台</span>
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
      <span>联系方式仅在管理端可见，用于与站点站长保持联系，不会出现在前台。</span>
    </p>

    <AppModal
      v-model:open="editOpen"
      title="编辑友链"
      description="修改后会立即同步到访客可见的友链页面。"
      size="lg"
    >
      <form class="edit-form" novalidate @submit.prevent="submitEdit">
        <div class="form-field edit-form__field--wide">
          <div class="form-label-row">
            <label class="form-label" for="friend-site-name">
              站点名称<span class="form-label__required">*</span>
            </label>
            <span class="form-label__optional">Site Name</span>
          </div>
          <input
            id="friend-site-name"
            v-model="editForm.siteName"
            class="input"
            :class="{ 'input--invalid': editErrors.siteName }"
            type="text"
            maxlength="100"
            placeholder="站点名称"
            :disabled="savingEdit"
            @input="clearEditError('siteName')"
          />
          <p v-if="editErrors.siteName" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editErrors.siteName }}
          </p>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="friend-site-url">
              站点地址<span class="form-label__required">*</span>
            </label>
            <span class="form-label__optional">Site URL</span>
          </div>
          <input
            id="friend-site-url"
            v-model="editForm.siteUrl"
            class="input"
            :class="{ 'input--invalid': editErrors.siteUrl }"
            type="url"
            maxlength="500"
            placeholder="https://example.com"
            :disabled="savingEdit"
            @input="clearEditError('siteUrl')"
          />
          <p v-if="editErrors.siteUrl" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editErrors.siteUrl }}
          </p>
          <p v-else class="form-help">以 http 或 https 开头的完整网址。</p>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="friend-link-page-url">
              友链页地址<span class="form-label__required">*</span>
            </label>
            <span class="form-label__optional">Links Page</span>
          </div>
          <input
            id="friend-link-page-url"
            v-model="editForm.linkPageUrl"
            class="input"
            :class="{ 'input--invalid': editErrors.linkPageUrl }"
            type="url"
            maxlength="500"
            placeholder="https://example.com/links"
            :disabled="savingEdit"
            @input="clearEditError('linkPageUrl')"
          />
          <p v-if="editErrors.linkPageUrl" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editErrors.linkPageUrl }}
          </p>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="friend-avatar-url">头像/站点图标 URL</label>
            <span class="form-label__optional">Avatar</span>
          </div>
          <input
            id="friend-avatar-url"
            v-model="editForm.avatarUrl"
            class="input"
            :class="{ 'input--invalid': editErrors.avatarUrl }"
            type="url"
            maxlength="500"
            placeholder="https://example.com/avatar.png"
            :disabled="savingEdit"
            @input="clearEditError('avatarUrl')"
          />
          <p v-if="editErrors.avatarUrl" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editErrors.avatarUrl }}
          </p>
          <p v-else class="form-help">留空时使用站点图标占位。</p>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="friend-sort-order">排序值</label>
            <span class="form-label__optional">Sort Order</span>
          </div>
          <input
            id="friend-sort-order"
            v-model="editForm.sortOrder"
            class="input"
            :class="{ 'input--invalid': editErrors.sortOrder }"
            type="number"
            step="1"
            :min="SORT_ORDER_MIN"
            :max="SORT_ORDER_MAX"
            placeholder="0"
            :disabled="savingEdit"
            @input="clearEditError('sortOrder')"
          />
          <p v-if="editErrors.sortOrder" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editErrors.sortOrder }}
          </p>
          <p v-else class="form-help">数值越小越靠前，默认为 0。</p>
        </div>

        <div class="form-field edit-form__field--wide">
          <div class="form-label-row">
            <label class="form-label" for="friend-description">站点简介</label>
            <span class="form-label__optional">Description</span>
          </div>
          <textarea
            id="friend-description"
            v-model="editForm.description"
            class="textarea"
            :class="{ 'textarea--invalid': editErrors.description }"
            rows="3"
            maxlength="500"
            placeholder="用一两句话介绍这个站点…"
            :disabled="savingEdit"
            @input="clearEditError('description')"
          />
          <p v-if="editErrors.description" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editErrors.description }}
          </p>
        </div>

        <div class="form-field edit-form__field--wide">
          <div class="form-label-row">
            <label class="form-label" for="friend-contact">联系方式</label>
            <span class="form-label__optional">Contact</span>
          </div>
          <input
            id="friend-contact"
            v-model="editForm.contact"
            class="input"
            :class="{ 'input--invalid': editErrors.contact }"
            type="text"
            maxlength="200"
            placeholder="someone@example.com"
            :disabled="savingEdit"
            @input="clearEditError('contact')"
          />
          <p v-if="editErrors.contact" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ editErrors.contact }}
          </p>
          <p v-else class="form-help">仅管理员可见，用于与站长联系。</p>
        </div>
      </form>

      <div v-if="editSubmitError" class="callout callout--danger edit-form__error" role="alert">
        <AppIcon class="callout__icon" name="alert-circle" :size="15" />
        <span>{{ editSubmitError }}</span>
      </div>

      <template #footer>
        <button class="btn" type="button" :disabled="savingEdit" @click="editOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="savingEdit" @click="submitEdit">
          <span v-if="savingEdit" class="btn__spinner" />
          {{ savingEdit ? '保存中…' : '保存修改' }}
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
.friend-links-page {
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

.status-tabs {
  flex-wrap: wrap;
}

.status-tabs .segmented__item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.status-tabs__count {
  display: inline-grid;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  color: var(--text-tertiary);
  background: var(--bg-surface);
  border-radius: 3px;
  place-items: center;
}

.segmented__item.is-active .status-tabs__count {
  color: var(--brand-text);
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

.link-cell {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.link-cell__avatar {
  display: grid;
  flex: none;
  width: 32px;
  height: 32px;
  overflow: hidden;
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xs);
  place-items: center;
}

.link-cell__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.link-cell__copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.link-cell__name {
  overflow: hidden;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-cell__sort {
  font-size: 10px;
  color: var(--text-tertiary);
}

.link-cell__url {
  display: block;
  max-width: 100%;
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--text-link);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-cell__desc {
  display: -webkit-box;
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.link-cell__contact {
  display: block;
  max-width: 100%;
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
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

.status--pending {
  color: var(--warning-text);
  background: var(--warning-soft);
}

.status--rejected {
  color: var(--danger-text);
  background: var(--danger-soft);
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

.row-action--reject:hover:not(:disabled) {
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

.edit-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.edit-form__field--wide {
  grid-column: 1 / -1;
}

.edit-form__error {
  align-items: flex-start;
  margin-top: 14px;
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

  .edit-form {
    grid-template-columns: minmax(0, 1fr);
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

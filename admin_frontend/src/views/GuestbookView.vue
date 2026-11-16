<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppTable from '@/components/common/AppTable.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppModal from '@/components/common/AppModal.vue'
import {
  GUEST_MESSAGE_REPLY_LIMIT,
  deleteGuestMessage,
  listGuestMessages,
  replyGuestMessage,
  reviewGuestMessage,
} from '@/api/guestMessages.js'
import { formatDateTime, formatRelative } from '@/utils/format.js'
import { useToastStore } from '@/stores/toast.js'

const route = useRoute()
const toast = useToastStore()

const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const messages = ref([])
const total = ref(0)
const counts = ref({ ALL: 0, PENDING: 0, APPROVED: 0, REJECTED: 0 })

const statusFilter = ref('ALL')
const page = ref(1)
const pageSize = ref(20)

const reviewingId = ref(null)
const removing = ref(false)
const removalOpen = ref(false)
const removalTarget = ref(null)

const replyOpen = ref(false)
const replyTarget = ref(null)
const replyContent = ref('')
const replyError = ref('')
const replySubmitError = ref('')
const savingReply = ref(false)

const STATUS_TABS = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
]

const columns = [
  { key: 'nickname', label: '留言者', width: '18%' },
  { key: 'content', label: '留言内容', width: '26%' },
  { key: 'email', label: '邮箱', width: '16%' },
  { key: 'reply', label: '站长回复', width: '18%' },
  { key: 'status', label: '状态', width: '88px' },
  { key: 'createdAt', label: '提交时间', width: '128px' },
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
    title: `删除「${item?.nickname || '这条留言'}」的留言？`,
    description: '该留言会从访客可见的留言墙中移除，管理员回复也会一并删除。',
    note: '此操作会调用后端删除接口，删除服务器上的留言记录。',
    confirm: '删除留言',
  }
})

const emptyTitle = computed(() => {
  if (loading.value) return '正在加载留言'
  if (statusFilter.value === 'ALL') return '还没有留言'
  return '没有符合条件的留言'
})

const emptyDescription = computed(() => {
  if (statusFilter.value === 'ALL') {
    return '访客在前台提交的留言会出现在这里，等待审核。'
  }
  return '试试切换到其他状态，看看还有哪些留言等待处理。'
})

/** 昵称首字：没有头像时用作占位字母。 */
function initialOf(nickname) {
  const text = String(nickname || '').trim()
  return text ? text.slice(0, 1).toUpperCase() : '?'
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
        const response = await listGuestMessages({ status: tab.value, page: 0, size: 1 })
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
    const response = await listGuestMessages({
      status: statusFilter.value === 'ALL' ? '' : statusFilter.value,
      page: Math.max(page.value - 1, 0),
      size: pageSize.value,
    })
    messages.value = Array.isArray(response?.items) ? response.items : []
    total.value = Number(response?.totalElements) || 0
  } catch (loadError) {
    error.value = loadError.message || '留言列表加载失败'
    messages.value = []
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
  if (!messages.value.length && page.value > 1) {
    page.value -= 1
    await load({ silent: true })
  }
}

async function review(message, status) {
  if (reviewingId.value) return
  reviewingId.value = message.id
  try {
    await reviewGuestMessage(message.id, status)
    toast.success(status === 'APPROVED' ? '留言已通过' : '留言已拒绝')
    await refreshAfterMutation()
  } catch (reviewError) {
    toast.error(reviewError.message || '审核失败，请稍后重试')
  } finally {
    reviewingId.value = null
  }
}

function askRemove(message) {
  removalTarget.value = message
  removalOpen.value = true
}

async function confirmRemove() {
  const target = removalTarget.value
  if (!target || removing.value) return

  removing.value = true
  try {
    await deleteGuestMessage(target.id)
    removalOpen.value = false
    removalTarget.value = null
    toast.success('留言已删除')
    await refreshAfterMutation()
  } catch (deleteError) {
    toast.error(deleteError.message || '删除失败，请稍后重试')
  } finally {
    removing.value = false
  }
}

function openReply(message) {
  replyTarget.value = message
  replyContent.value = message.reply?.content || ''
  replyError.value = ''
  replySubmitError.value = ''
  replyOpen.value = true
}

function clearReplyError() {
  replyError.value = ''
  replySubmitError.value = ''
}

async function submitReply() {
  const target = replyTarget.value
  if (!target || savingReply.value) return

  const content = replyContent.value.trim()
  replyError.value = ''
  replySubmitError.value = ''

  if (!content) {
    replyError.value = '回复内容不能为空'
    return
  }
  if (content.length > GUEST_MESSAGE_REPLY_LIMIT) {
    replyError.value = `回复内容不能超过 ${GUEST_MESSAGE_REPLY_LIMIT} 个字符`
    return
  }

  savingReply.value = true
  try {
    await replyGuestMessage(target.id, content)
    replyOpen.value = false
    toast.success(target.reply ? '回复已更新' : '回复已发布')
    await refreshAfterMutation()
  } catch (saveError) {
    if (saveError.fieldErrors?.content) {
      replyError.value = String(saveError.fieldErrors.content)
    }
    replySubmitError.value = saveError.message || '回复失败，请稍后重试'
    toast.error(replySubmitError.value)
  } finally {
    savingReply.value = false
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
  <div class="guestbook-page page-reveal">
    <header class="list-head">
      <div class="list-head__copy">
        <p class="list-head__eyebrow">
          CONTENT / GUESTBOOK
          <span class="list-head__eyebrow-rule" aria-hidden="true" />
          {{ total }} 条记录
        </p>
        <h1 class="list-head__title">留言管理</h1>
        <p class="list-head__lead">审核访客留下的留言，也在这里回复他们。</p>
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

        <span class="source-pill" title="数据来自管理端留言接口">
          <span class="source-pill__dot" />
          管理端接口
        </span>
      </div>

      <AppTable
        :columns="columns"
        :rows="messages"
        row-key="id"
        :loading="loading"
        :empty-title="emptyTitle"
        :empty-description="emptyDescription"
        aria-label="留言管理列表"
      >
        <template #empty>
          <AppEmptyState icon="message-square" :title="emptyTitle" :description="emptyDescription">
            <button
              v-if="statusFilter !== 'ALL'"
              class="btn btn--sm"
              type="button"
              @click="selectStatus('ALL')"
            >
              查看全部留言
            </button>
          </AppEmptyState>
        </template>

        <template #cell-nickname="{ row }">
          <div class="message-cell">
            <span class="message-cell__avatar">{{ initialOf(row.nickname) }}</span>
            <span class="message-cell__copy">
              <span class="message-cell__name" :title="row.nickname">{{ row.nickname }}</span>
              <span class="message-cell__ip mono">{{ row.ipAddress || '—' }}</span>
            </span>
          </div>
        </template>

        <template #cell-content="{ row }">
          <span class="message-cell__content" :title="row.content || ''">{{ row.content || '—' }}</span>
        </template>

        <template #cell-email="{ row }">
          <span v-if="row.email" class="message-cell__email" :title="row.email">{{ row.email }}</span>
          <span v-else class="cell-muted">—</span>
        </template>

        <template #cell-reply="{ row }">
          <span v-if="row.reply" class="message-cell__reply" :title="row.reply.content">
            {{ row.reply.content }}
          </span>
          <span v-else class="cell-muted">未回复</span>
        </template>

        <template #cell-status="{ row }">
          <span class="status" :class="statusMeta(row.status).className">
            {{ statusMeta(row.status).label }}
          </span>
        </template>

        <template #cell-createdAt="{ row }">
          <div class="date-cell">
            <span>{{ formatDateTime(row.createdAt) }}</span>
            <small>{{ formatRelative(row.createdAt) }}</small>
          </div>
        </template>

        <template #cell-actions="{ row }">
          <div class="row-actions">
            <button
              v-if="row.status !== 'APPROVED'"
              class="row-action"
              type="button"
              title="通过留言"
              aria-label="通过留言"
              :disabled="reviewingId === row.id"
              @click="review(row, 'APPROVED')"
            >
              <AppIcon name="check-circle" :size="14" />
            </button>
            <button
              v-if="row.status !== 'REJECTED'"
              class="row-action row-action--reject"
              type="button"
              title="拒绝留言"
              aria-label="拒绝留言"
              :disabled="reviewingId === row.id"
              @click="review(row, 'REJECTED')"
            >
              <AppIcon name="close" :size="14" />
            </button>
            <button
              class="row-action"
              type="button"
              title="回复留言"
              aria-label="回复留言"
              @click="openReply(row)"
            >
              <AppIcon name="message-square" :size="14" />
            </button>
            <button
              class="row-action row-action--danger"
              type="button"
              title="删除留言"
              aria-label="删除留言"
              @click="askRemove(row)"
            >
              <AppIcon name="trash" :size="14" />
            </button>
          </div>
        </template>
      </AppTable>

      <footer class="list-panel__footer">
        <span class="list-panel__meta">已通过的留言会展示在博客前台的留言墙</span>
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
      <span>邮箱与来源 IP 仅在管理端可见，不会出现在前台留言墙。</span>
    </p>

    <AppModal
      v-model:open="replyOpen"
      title="回复留言"
      description="回复会随这条留言一起展示在博客前台；再次回复即覆盖旧内容。"
      size="lg"
    >
      <div class="reply-form">
        <div class="reply-form__quote">
          <AppIcon name="message-square" :size="14" />
          <span>{{ replyTarget?.nickname }}：{{ replyTarget?.content }}</span>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="guest-message-reply">回复内容</label>
            <span class="form-label__optional">Reply</span>
          </div>
          <textarea
            id="guest-message-reply"
            v-model="replyContent"
            class="textarea"
            :class="{ 'textarea--invalid': replyError }"
            rows="5"
            :maxlength="GUEST_MESSAGE_REPLY_LIMIT"
            placeholder="写点什么回复这位读者…"
            :disabled="savingReply"
            @input="clearReplyError"
          />
          <p v-if="replyError" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ replyError }}
          </p>
          <p v-else class="form-help">回复以「站长回复」的形式嵌在留言下方。</p>
        </div>
      </div>

      <div v-if="replySubmitError" class="callout callout--danger reply-form__error" role="alert">
        <AppIcon class="callout__icon" name="alert-circle" :size="15" />
        <span>{{ replySubmitError }}</span>
      </div>

      <template #footer>
        <button class="btn" type="button" :disabled="savingReply" @click="replyOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="savingReply" @click="submitReply">
          <span v-if="savingReply" class="btn__spinner" />
          {{ savingReply ? '保存中…' : '发布回复' }}
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
.guestbook-page {
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

.message-cell {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.message-cell__avatar {
  display: grid;
  flex: none;
  width: 32px;
  height: 32px;
  font-size: 13px;
  color: var(--brand-text);
  background: var(--brand-soft);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
  place-items: center;
}

.message-cell__copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.message-cell__name {
  overflow: hidden;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-cell__ip {
  font-size: 10px;
  color: var(--text-tertiary);
}

.message-cell__content {
  display: -webkit-box;
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.message-cell__email {
  display: block;
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-cell__reply {
  display: -webkit-box;
  overflow: hidden;
  font-size: var(--text-xs);
  color: var(--brand-text);
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

.reply-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.reply-form__quote {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  padding: 11px 12px;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  background: var(--bg-inset);
  border-radius: var(--radius-sm);
}

.reply-form__quote .app-icon {
  flex: none;
  margin-top: 1px;
  color: var(--text-tertiary);
}

.reply-form__error {
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

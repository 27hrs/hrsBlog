<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AppTable from '@/components/common/AppTable.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import { listSystemLogs } from '@/api/logs.js'
import { formatDateTime, formatRelative } from '@/utils/format.js'

const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const logs = ref([])
const total = ref(0)

// reka-ui 的 SelectItem 不接受空字符串，用 ALL 表示「不筛选」
const typeFilter = ref('ALL')
const statusFilter = ref('ALL')
const page = ref(1)
const pageSize = ref(20)

const columns = [
  { key: 'time', label: '时间', width: '150px' },
  { key: 'type', label: '类型', width: '150px' },
  { key: 'scene', label: '场景', width: '190px' },
  { key: 'status', label: '结果', width: '92px' },
  { key: 'attempt', label: '尝试', width: '68px' },
  { key: 'message', label: '摘要' },
  { key: 'detail', label: '详情' },
]

const typeOptions = [
  { value: 'ALL', label: '全部类型' },
  { value: 'DEPLOY_NOTIFY', label: '部署通知' },
]

const statusOptions = [
  { value: 'ALL', label: '全部结果' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
]

const filtersActive = computed(() => typeFilter.value !== 'ALL' || statusFilter.value !== 'ALL')

const emptyTitle = computed(() => {
  if (loading.value) return '正在加载日志'
  if (!logs.value.length) return '还没有日志记录'
  return '没有匹配的日志'
})

const emptyDescription = computed(() => {
  if (filtersActive.value) return '试试更换筛选条件，或清除当前筛选。'
  return '发布文章或修改站点设置后，自动化链路的每次尝试都会记录在这里。'
})

async function load({ silent = false } = {}) {
  if (silent) refreshing.value = true
  else loading.value = true
  error.value = ''

  try {
    const response = await listSystemLogs({
      page: Math.max(page.value - 1, 0),
      size: pageSize.value,
      type: typeFilter.value === 'ALL' ? '' : typeFilter.value,
      status: statusFilter.value === 'ALL' ? '' : statusFilter.value,
    })
    logs.value = Array.isArray(response?.items) ? response.items : []
    total.value = Number(response?.totalElements) || 0
  } catch (loadError) {
    error.value = loadError.message || '日志加载失败'
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(load)

watch([typeFilter, statusFilter], () => {
  page.value = 1
  load()
})

watch(page, () => load({ silent: true }))

watch(pageSize, () => {
  page.value = 1
  load({ silent: true })
})

function resetFilters() {
  typeFilter.value = 'ALL'
  statusFilter.value = 'ALL'
}

function onPageChange(next) {
  page.value = next
}

function onPageSizeChange(size) {
  pageSize.value = size
}

function statusMeta(status) {
  if (status === 'SUCCESS') {
    return { label: '成功', className: 'status--published' }
  }
  return { label: '失败', className: 'status--draft' }
}

function typeLabel(type) {
  if (type === 'DEPLOY_NOTIFY') return '部署通知'
  return type || '—'
}
</script>

<template>
  <div class="logs-page page-reveal">
    <header class="list-head">
      <div class="list-head__copy">
        <p class="list-head__eyebrow">
          SYSTEM / LOGS
          <span class="list-head__eyebrow-rule" aria-hidden="true" />
          {{ total }} 条记录
        </p>
        <h1 class="list-head__title">系统日志</h1>
        <p class="list-head__lead">自动化链路的每一次尝试都会留痕，失败可以逐条回看。</p>
      </div>

      <div class="list-head__actions">
        <button class="btn" type="button" :disabled="refreshing || loading" @click="load({ silent: true })">
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
          <AppSelect v-model="typeFilter" :options="typeOptions" aria-label="按日志类型筛选" />
        </div>
        <div class="filter-bar__select">
          <AppSelect v-model="statusFilter" :options="statusOptions" aria-label="按结果筛选" />
        </div>

        <button v-if="filtersActive" class="filter-bar__reset" type="button" @click="resetFilters">
          <AppIcon name="rotate-ccw" :size="12" />
          重置
        </button>

        <div class="filter-bar__spacer" />
      </div>

      <AppTable
        :columns="columns"
        :rows="logs"
        row-key="id"
        :loading="loading"
        :empty-title="emptyTitle"
        :empty-description="emptyDescription"
        aria-label="系统日志列表"
      >
        <template #cell-time="{ row }">
          <div class="date-cell">
            <span>{{ formatDateTime(row.createdAt) }}</span>
            <small>{{ formatRelative(row.createdAt) }}</small>
          </div>
        </template>

        <template #cell-type="{ row }">
          <div class="date-cell">
            <span>{{ typeLabel(row.type) }}</span>
            <small class="mono">{{ row.type }}</small>
          </div>
        </template>

        <template #cell-scene="{ row }">
          <span class="scene-name mono">{{ row.scene || '—' }}</span>
        </template>

        <template #cell-status="{ row }">
          <span class="status" :class="statusMeta(row.status).className">
            {{ statusMeta(row.status).label }}
          </span>
        </template>

        <template #cell-attempt="{ row }">
          <span class="attempt-count">#{{ row.attempt }}</span>
        </template>

        <template #cell-message="{ row }">
          <span class="message-cell">{{ row.message || '—' }}</span>
        </template>

        <template #cell-detail="{ row }">
          <span class="detail-cell mono">{{ row.detail || '—' }}</span>
        </template>

        <template #empty-actions>
          <button v-if="filtersActive" class="btn btn--sm" type="button" @click="resetFilters">
            清除筛选
          </button>
        </template>
      </AppTable>

      <footer class="list-panel__footer">
        <span class="list-panel__meta">最新记录在前</span>
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
      <span>日志只记录脱敏后的结果：目标主机路径、状态码与响应片段，鉴权头与密钥不会入库。</span>
    </p>
  </div>
</template>

<style scoped>
.logs-page {
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

.filter-bar__select {
  width: 138px;
}

.filter-bar__select :deep(.select-trigger) {
  height: 34px;
  font-size: var(--text-xs);
  background: var(--bg-inset);
  border-color: transparent;
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

.type-name {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.scene-name {
  font-size: 10px;
  color: var(--text-tertiary);
}

.attempt-count {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.message-cell {
  font-size: var(--text-xs);
  color: var(--text-primary);
}

.detail-cell {
  display: block;
  max-width: 420px;
  overflow: hidden;
  font-size: 10px;
  color: var(--text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
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

@media (max-width: 1024px) {
  .filter-bar__spacer {
    display: none;
  }
}

@media (max-width: 860px) {
  .list-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .filter-bar__select {
    flex: 1;
    width: auto;
    min-width: 120px;
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
  .detail-cell {
    max-width: 180px;
  }

  .list-footnote {
    display: none;
  }
}
</style>

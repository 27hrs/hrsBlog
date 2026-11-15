<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  pageSizeOptions: { type: Array, default: () => [10, 20, 50] },
  showSizeChanger: { type: Boolean, default: true },
})

const emit = defineEmits(['update:page', 'update:pageSize'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(props.pageSize, 1))))
const safePage = computed(() => Math.min(Math.max(props.page, 1), totalPages.value))
const start = computed(() => (props.total ? (safePage.value - 1) * props.pageSize + 1 : 0))
const end = computed(() => Math.min(safePage.value * props.pageSize, props.total))

const pageItems = computed(() => {
  const count = totalPages.value
  if (count <= 7) return Array.from({ length: count }, (_, index) => index + 1)

  const pages = new Set([1, count, safePage.value, safePage.value - 1, safePage.value + 1])
  if (safePage.value <= 3) {
    pages.add(2)
    pages.add(3)
    pages.add(4)
  }
  if (safePage.value >= count - 2) {
    pages.add(count - 1)
    pages.add(count - 2)
    pages.add(count - 3)
  }

  const sorted = [...pages].filter((page) => page >= 1 && page <= count).sort((a, b) => a - b)
  const items = []
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) {
      items.push(`ellipsis-${page}`)
    }
    items.push(page)
  })
  return items
})

function goTo(page) {
  const next = Math.min(Math.max(page, 1), totalPages.value)
  if (next !== props.page) emit('update:page', next)
}

function changePageSize(event) {
  const size = Number(event.target.value)
  emit('update:pageSize', size)
  emit('update:page', 1)
}
</script>

<template>
  <div class="pagination">
    <div class="pagination__meta">
      <span class="pagination__summary">
        <template v-if="total">第 {{ start }}–{{ end }} 条，共 {{ total }} 条</template>
        <template v-else>暂无数据</template>
      </span>
      <label v-if="showSizeChanger" class="pagination__size">
        <span class="sr-only">每页条数</span>
        <select class="pagination__select" :value="pageSize" @change="changePageSize">
          <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条 / 页</option>
        </select>
      </label>
    </div>

    <div v-if="totalPages > 1" class="pagination__pages" aria-label="分页">
      <button
        class="pagination__button"
        type="button"
        aria-label="上一页"
        :disabled="safePage <= 1"
        @click="goTo(safePage - 1)"
      >
        <AppIcon name="chevron-left" :size="14" />
      </button>

      <template v-for="item in pageItems" :key="item">
        <span v-if="typeof item === 'string'" class="pagination__ellipsis">···</span>
        <button
          v-else
          class="pagination__button"
          type="button"
          :aria-current="item === safePage ? 'page' : undefined"
          @click="goTo(item)"
        >
          {{ item }}
        </button>
      </template>

      <button
        class="pagination__button"
        type="button"
        aria-label="下一页"
        :disabled="safePage >= totalPages"
        @click="goTo(safePage + 1)"
      >
        <AppIcon name="chevron-right" :size="14" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.pagination__meta {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.pagination__size {
  position: relative;
}

.pagination__select {
  height: 26px;
  padding: 0 22px 0 7px;
  font-size: var(--text-2xs);
  color: var(--text-secondary);
  cursor: pointer;
  appearance: none;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
}

.pagination__select:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

@media (max-width: 640px) {
  .pagination {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-3);
  }

  .pagination__pages {
    width: 100%;
    overflow-x: auto;
  }
}
</style>

<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: [String, Function], default: 'id' },
  selectable: { type: Boolean, default: false },
  selected: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  skeletonRows: { type: Number, default: 6 },
  emptyTitle: { type: String, default: '暂无数据' },
  emptyDescription: { type: String, default: '' },
  ariaLabel: { type: String, default: '' },
})

const emit = defineEmits(['update:selected', 'row-click'])

function rowId(row) {
  return typeof props.rowKey === 'function' ? props.rowKey(row) : row[props.rowKey]
}

const selectedSet = computed(() => new Set(props.selected.map(String)))
const allSelected = computed(
  () => props.rows.length > 0 && props.rows.every((row) => selectedSet.value.has(String(rowId(row)))),
)
const someSelected = computed(
  () =>
    props.rows.some((row) => selectedSet.value.has(String(rowId(row)))) &&
    !allSelected.value,
)

function toggleRow(row) {
  const id = String(rowId(row))
  const next = new Set(selectedSet.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  emit('update:selected', [...next])
}

function toggleAll() {
  if (allSelected.value) {
    emit('update:selected', [])
    return
  }
  emit('update:selected', props.rows.map((row) => String(rowId(row))))
}

function onRowClick(row, event) {
  if (event.target.closest('button, a, input, label, [data-row-action]')) return
  emit('row-click', row)
}
</script>

<template>
  <div class="table-wrap">
    <table class="data-table" :aria-label="ariaLabel || undefined">
      <thead>
        <tr>
          <th v-if="selectable" class="table-select-cell">
            <input
              class="table-checkbox"
              type="checkbox"
              :checked="allSelected"
              :indeterminate.prop="someSelected"
              :disabled="!rows.length || loading"
              aria-label="选择当前页全部行"
              @change="toggleAll"
            />
          </th>
          <th
            v-for="column in columns"
            :key="column.key"
            :style="{ width: column.width || undefined, textAlign: column.align || 'left' }"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>

      <tbody v-if="loading">
        <tr v-for="index in skeletonRows" :key="`skeleton-${index}`">
          <td v-if="selectable">
            <span class="skeleton skeleton--text" style="width: 15px" />
          </td>
          <td v-for="(column, colIndex) in columns" :key="column.key">
            <span
              class="skeleton"
              :class="{ 'skeleton--title': colIndex === 0 }"
              :style="{ width: colIndex === 0 ? '42%' : `${34 + ((index + colIndex) % 3) * 12}%` }"
            />
          </td>
        </tr>
      </tbody>

      <tbody v-else-if="rows.length">
        <tr
          v-for="(row, index) in rows"
          :key="rowId(row)"
          @click="onRowClick(row, $event)"
        >
          <td v-if="selectable" class="table-select-cell" data-row-action>
            <input
              class="table-checkbox"
              type="checkbox"
              :checked="selectedSet.has(String(rowId(row)))"
              :aria-label="`选择第 ${index + 1} 行`"
              @change="toggleRow(row)"
            />
          </td>
          <td
            v-for="column in columns"
            :key="column.key"
            :style="{ textAlign: column.align || 'left' }"
            :class="column.class"
          >
            <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]" :index="index">
              {{ row[column.key] ?? '—' }}
            </slot>
          </td>
        </tr>
      </tbody>

      <tbody v-else>
        <tr>
          <td :colspan="columns.length + (selectable ? 1 : 0)">
            <slot name="empty">
              <div class="empty-state">
                <span class="empty-state__visual">
                  <AppIcon name="inbox" :size="21" />
                </span>
                <p class="empty-state__title">{{ emptyTitle }}</p>
                <p v-if="emptyDescription" class="empty-state__description">
                  {{ emptyDescription }}
                </p>
                <div v-if="$slots['empty-actions']" class="empty-state__actions">
                  <slot name="empty-actions" />
                </div>
              </div>
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-select-cell {
  width: 46px;
  padding-right: 0 !important;
}

tbody tr {
  cursor: default;
}
</style>

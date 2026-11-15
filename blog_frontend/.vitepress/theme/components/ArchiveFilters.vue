<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  activeCategory: { type: String, default: '' },
  activeTag: { type: String, default: '' },
  filteredCount: { type: Number, default: 0 },
  totalCount: { type: Number, default: 0 },
})

const emit = defineEmits(['select-category', 'select-tag', 'clear'])

const activeTagName = computed(
  () => props.tags.find((tag) => tag.slug === props.activeTag)?.name || '',
)

const hasFilter = computed(() => Boolean(props.activeCategory || props.activeTag))
</script>

<template>
  <div class="filters">
    <div class="filters__row">
      <span class="filters__label">分类</span>
      <button
        class="filter-chip"
        :class="{ 'is-active': !activeCategory }"
        type="button"
        :aria-pressed="!activeCategory"
        @click="emit('select-category', '')"
      >
        全部
        <span class="filter-chip__count">{{ totalCount }}</span>
      </button>
      <button
        v-for="category in categories"
        :key="category.id"
        class="filter-chip"
        :class="{ 'is-active': activeCategory === category.id }"
        type="button"
        :aria-pressed="activeCategory === category.id"
        @click="emit('select-category', activeCategory === category.id ? '' : category.id)"
      >
        {{ category.short }}
        <span class="filter-chip__count">{{ category.count }}</span>
      </button>
    </div>

    <div class="filters__status">
      <p class="filters__result" aria-live="polite">
        匹配 <strong>{{ filteredCount }}</strong> 篇
        <span v-if="hasFilter" class="filters__active">
          <span v-if="activeCategory">· {{ categories.find((item) => item.id === activeCategory)?.label }}</span>
          <span v-if="activeTag">· #{{ activeTagName }}</span>
        </span>
      </p>
      <button v-if="hasFilter" class="filters__clear" type="button" @click="emit('clear')">
        清除筛选
        <AppIcon name="close" :size="11" />
      </button>
    </div>

    <details v-if="tags.length" class="filters__tags" :open="Boolean(activeTag)">
      <summary>
        按标签筛选
        <span v-if="activeTag" class="filters__summary-tag">#{{ activeTagName }}</span>
      </summary>
      <div class="filters__tag-list">
        <button
          v-for="tag in tags"
          :key="tag.slug"
          class="filter-chip filter-chip--tag"
          :class="{ 'is-active': activeTag === tag.slug }"
          type="button"
          :aria-pressed="activeTag === tag.slug"
          @click="emit('select-tag', activeTag === tag.slug ? '' : tag.slug)"
        >
          {{ tag.name }}
          <span class="filter-chip__count">{{ tag.count }}</span>
        </button>
      </div>
    </details>
  </div>
</template>

<style scoped>
.filters {
  padding-bottom: 22px;
  border-bottom: 1px solid var(--line);
}

.filters__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.filters__label {
  margin-right: 6px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
  line-height: 1;
  cursor: pointer;
  transition:
    color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease),
    background-color var(--dur-fast) var(--ease);
}

.filter-chip:hover {
  color: var(--copper);
  border-color: var(--copper);
}

.filter-chip.is-active {
  border-color: var(--copper);
  background: var(--copper);
  color: var(--paper);
}

.filter-chip__count {
  opacity: 0.62;
  font-size: 9px;
}

.filter-chip.is-active .filter-chip__count {
  opacity: 0.8;
}

.filters__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 18px;
}

.filters__result {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  line-height: 1.5;
}

.filters__result strong {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 18px;
  font-style: italic;
  font-weight: 400;
}

.filters__active {
  color: var(--ink-soft);
}

.filters__clear {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
  cursor: pointer;
}

.filters__clear:hover {
  color: var(--copper);
}

.filters__tags {
  display: none;
  margin-top: 16px;
}

.filters__tags summary {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  line-height: 1.5;
  cursor: pointer;
  list-style: none;
}

.filters__tags summary::-webkit-details-marker {
  display: none;
}

.filters__tags summary::before {
  content: '+';
  color: var(--copper);
  font-size: 13px;
  line-height: 1;
}

.filters__tags[open] summary::before {
  content: '−';
}

.filters__summary-tag {
  color: var(--copper);
}

.filters__tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding-top: 12px;
}

.filter-chip--tag {
  border-radius: 2px;
}

@media (max-width: 1180px) {
  .filters__tags {
    display: block;
  }
}

@media (max-width: 620px) {
  .filters__row {
    gap: 6px;
  }

  .filter-chip {
    padding: 7px 10px;
    font-size: 9.5px;
  }

  .filters__status {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}
</style>

<script setup>
import AppIcon from '@/components/common/AppIcon.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  icon: { type: String, default: 'article' },
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyTitle: { type: String, default: '暂无内容' },
  emptyDescription: { type: String, default: '' },
  emptyIcon: { type: String, default: 'inbox' },
  linkLabel: { type: String, default: '' },
  linkTo: { type: [Object, String], default: null },
  unavailable: { type: Boolean, default: false },
})
</script>

<template>
  <section class="panel recent-panel">
    <header class="panel__header recent-panel__header">
      <div class="recent-panel__heading">
        <span class="recent-panel__icon">
          <AppIcon :name="icon" :size="15" />
        </span>
        <div>
          <h2 class="panel__title">{{ title }}</h2>
          <p v-if="subtitle" class="panel__subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <RouterLink v-if="linkTo && linkLabel" class="recent-panel__link" :to="linkTo">
        {{ linkLabel }}
        <AppIcon name="arrow-right" :size="13" />
      </RouterLink>
      <span v-else-if="unavailable" class="tag">接口待接入</span>
    </header>

    <div v-if="loading" class="recent-panel__loading">
      <div v-for="index in 3" :key="index" class="recent-skeleton">
        <span class="skeleton recent-skeleton__thumb" />
        <div class="recent-skeleton__copy">
          <span class="skeleton skeleton--text" style="width: 76%" />
          <span class="skeleton skeleton--text" style="width: 44%" />
        </div>
      </div>
    </div>

    <ul v-else-if="items.length" class="recent-list">
      <li v-for="(item, index) in items" :key="item.id || item.key || index" class="recent-list__item">
        <slot name="item" :item="item" :index="index">
          <span class="recent-list__bullet" />
          <div class="recent-list__copy">
            <p class="recent-list__title">{{ item.title }}</p>
            <p class="recent-list__meta">{{ item.meta }}</p>
          </div>
        </slot>
      </li>
    </ul>

    <AppEmptyState
      v-else
      compact
      :icon="emptyIcon"
      :title="emptyTitle"
      :description="emptyDescription"
    >
      <slot name="empty-actions" />
    </AppEmptyState>
  </section>
</template>

<style scoped>
.recent-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.recent-panel > :deep(.empty-state) {
  flex: 1;
}

.recent-panel__header {
  align-items: center;
  padding-bottom: 14px;
}

.recent-panel__heading {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.recent-panel__icon {
  display: grid;
  flex: none;
  width: 29px;
  height: 29px;
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  place-items: center;
}

.recent-panel__link {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  flex: none;
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  text-decoration: none;
}

.recent-panel__link:hover {
  color: var(--brand-text);
  text-decoration: none;
}

.recent-list {
  display: flex;
  flex-direction: column;
  margin: 0 20px;
  padding: 0;
  list-style: none;
}

.recent-list__item {
  border-top: 1px solid var(--border-subtle);
}

.recent-list__item:first-child {
  border-top: 0;
}

.recent-list__item :deep(a) {
  text-decoration: none;
}

.recent-panel__loading {
  padding: 0 20px;
}

.recent-skeleton {
  display: flex;
  gap: 11px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--border-subtle);
}

.recent-skeleton:first-child {
  border-top: 0;
}

.recent-skeleton__thumb {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-xs);
}

.recent-skeleton__copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 7px;
}

.recent-list__bullet {
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--border-strong);
  border-radius: 50%;
}

.recent-list__copy {
  min-width: 0;
}

.recent-list__title {
  overflow: hidden;
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-list__meta {
  margin-top: 3px;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}
</style>

<script setup>
import AppIcon from '@/components/common/AppIcon.vue'

defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const toneIcon = {
  brand: 'pen-line',
  warning: 'file-edit',
  info: 'image',
  success: 'check',
}
</script>

<template>
  <section class="panel todo-panel">
    <header class="panel__header todo-panel__header">
      <div class="todo-panel__heading">
        <span class="todo-panel__icon">
          <AppIcon name="check-circle" :size="15" />
        </span>
        <div>
          <h2 class="panel__title">待办</h2>
          <p class="panel__subtitle">把下一件小事做完。</p>
        </div>
      </div>
      <span v-if="items.length" class="todo-panel__count">{{ items.length }}</span>
    </header>

    <div v-if="loading" class="todo-panel__loading">
      <div v-for="index in 3" :key="index" class="todo-skeleton">
        <span class="skeleton skeleton--circle" style="width: 26px; height: 26px" />
        <div class="todo-skeleton__copy">
          <span class="skeleton skeleton--text" style="width: 62%" />
          <span class="skeleton skeleton--text" style="width: 84%" />
        </div>
      </div>
    </div>

    <ul v-else-if="items.length" class="todo-list">
      <li v-for="item in items" :key="item.id" class="todo-item">
        <span class="todo-item__icon" :class="`todo-item__icon--${item.tone}`">
          <AppIcon :name="toneIcon[item.tone] || 'sparkles'" :size="14" />
        </span>
        <div class="todo-item__copy">
          <p class="todo-item__title">{{ item.title }}</p>
          <p class="todo-item__description">{{ item.description }}</p>
        </div>
        <RouterLink
          v-if="item.action?.to"
          class="todo-item__action"
          :to="item.action.to"
          :aria-label="item.action.label"
        >
          <AppIcon name="arrow-up-right" :size="13" />
        </RouterLink>
      </li>
    </ul>

    <div v-else class="todo-panel__empty">
      <AppIcon name="check-circle" :size="19" />
      <p>没有需要处理的事项</p>
      <span>保持这个节奏，继续写下去。</span>
    </div>
  </section>
</template>

<style scoped>
.todo-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.todo-panel__header {
  align-items: center;
  padding-bottom: 14px;
}

.todo-panel__heading {
  display: flex;
  gap: 10px;
  align-items: center;
}

.todo-panel__icon {
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

.todo-panel__count {
  display: inline-grid;
  min-width: 21px;
  height: 21px;
  padding: 0 6px;
  font-size: 10px;
  font-weight: var(--weight-semibold);
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border-radius: var(--radius-full);
  place-items: center;
}

.todo-panel__loading {
  padding: 0 20px;
}

.todo-skeleton {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 0;
  border-top: 1px solid var(--border-subtle);
}

.todo-skeleton:first-child {
  border-top: 0;
}

.todo-skeleton__copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 7px;
  padding-top: 2px;
}

.todo-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin: 0 20px 13px;
  padding: 0;
  list-style: none;
}

.todo-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 13px 0;
  border-top: 1px solid var(--border-subtle);
}

.todo-item:first-child {
  border-top: 0;
}

.todo-item__icon {
  display: grid;
  flex: none;
  width: 27px;
  height: 27px;
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border-radius: var(--radius-xs);
  place-items: center;
}

.todo-item__icon--brand {
  color: var(--brand-text);
  background: var(--brand-soft);
}

.todo-item__icon--warning {
  color: var(--warning-text);
  background: var(--warning-soft);
}

.todo-item__icon--info {
  color: var(--info-text);
  background: var(--info-soft);
}

.todo-item__icon--success {
  color: var(--success-text);
  background: var(--success-soft);
}

.todo-item__copy {
  flex: 1;
  min-width: 0;
}

.todo-item__title {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
}

.todo-item__description {
  margin-top: 3px;
  font-size: var(--text-2xs);
  line-height: 1.55;
  color: var(--text-tertiary);
}

.todo-item__action {
  display: grid;
  flex: none;
  width: 24px;
  height: 24px;
  margin-top: 1px;
  color: var(--text-tertiary);
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  place-items: center;
}

.todo-item__action:hover {
  color: var(--brand-text);
  text-decoration: none;
  background: var(--brand-soft);
}

.todo-item__action:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.todo-panel__empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 158px;
  padding: 22px;
  color: var(--success);
  text-align: center;
}

.todo-panel__empty p {
  margin-top: 8px;
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
}

.todo-panel__empty span {
  margin-top: 3px;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}
</style>

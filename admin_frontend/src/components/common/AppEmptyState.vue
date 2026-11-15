<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  icon: { type: String, default: 'inbox' },
  title: { type: String, default: '这里还没有内容' },
  description: { type: String, default: '' },
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <div class="empty-state" :class="{ 'empty-state--compact': compact }">
    <span class="empty-state__visual">
      <AppIcon :name="icon" :size="21" />
    </span>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state--compact {
  padding: 28px 18px;
}

.empty-state--compact .empty-state__visual {
  width: 38px;
  height: 38px;
  margin-bottom: 11px;
}

.empty-state--compact .empty-state__description {
  max-width: 260px;
}
</style>

<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()

const iconForType = computed(() => ({
  success: 'check-circle',
  error: 'alert-circle',
  warning: 'alert-triangle',
  info: 'info',
}))

function iconName(type) {
  return iconForType.value[type] || 'info'
}
</script>

<template>
  <div class="toast-viewport" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="toast-list">
      <div
        v-for="item in toast.items"
        :key="item.id"
        class="toast"
        :class="[`toast--${item.type}`, { 'toast--leaving': item.leaving }]"
        role="status"
      >
        <span class="toast__icon">
          <AppIcon :name="iconName(item.type)" :size="17" />
        </span>
        <div class="toast__content">
          <p v-if="item.title" class="toast__title">{{ item.title }}</p>
          <p class="toast__message">{{ item.message }}</p>
        </div>
        <button class="toast__close" type="button" aria-label="关闭提示" @click="toast.dismiss(item.id)">
          <AppIcon name="close" :size="13" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast__title {
  margin-bottom: 2px;
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.toast-list-leave-active {
  position: absolute;
  width: 100%;
}
</style>

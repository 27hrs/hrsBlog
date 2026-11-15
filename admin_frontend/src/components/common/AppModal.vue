<script setup>
import {
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
  VisuallyHidden,
} from 'reka-ui'
import AppIcon from './AppIcon.vue'

defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  size: { type: String, default: 'md' },
  closeLabel: { type: String, default: '关闭' },
})

const emit = defineEmits(['update:open'])
</script>

<template>
  <DialogRoot :open="open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="dialog-overlay" />
      <DialogContent class="dialog-content" :class="`dialog-content--${size}`">
        <div class="dialog-header">
          <DialogTitle class="dialog-title">{{ title }}</DialogTitle>
          <DialogDescription v-if="description" class="dialog-description">
            {{ description }}
          </DialogDescription>
          <VisuallyHidden v-else>
            <DialogDescription>操作确认</DialogDescription>
          </VisuallyHidden>
          <DialogClose class="dialog-close" type="button" :aria-label="closeLabel">
            <AppIcon name="close" :size="16" />
          </DialogClose>
        </div>
        <div v-if="$slots.default" class="dialog-body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="dialog-footer">
          <slot name="footer" />
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style scoped>
.dialog-content--sm {
  width: min(400px, calc(100vw - 32px));
}

.dialog-content--lg {
  width: min(640px, calc(100vw - 32px));
}

.dialog-close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: grid;
  width: 28px;
  height: 28px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-xs);
  place-items: center;
}

.dialog-close:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.dialog-close:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}
</style>

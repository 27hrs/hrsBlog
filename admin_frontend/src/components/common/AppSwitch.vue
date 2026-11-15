<script setup>
import { SwitchRoot, SwitchThumb } from 'reka-ui'

defineProps({
  modelValue: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  ariaLabel: { type: String, default: '切换开关' },
  id: { type: String, default: undefined },
})

const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <SwitchRoot
    :id="id"
    class="switch"
    :model-value="modelValue"
    :disabled="disabled"
    :aria-label="ariaLabel"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <SwitchThumb class="switch__thumb" />
  </SwitchRoot>
</template>

<style scoped>
.switch {
  position: relative;
  display: inline-flex;
  flex: none;
  align-items: center;
  width: 36px;
  height: 20px;
  padding: 0;
  cursor: pointer;
  background: var(--border-strong);
  border: 0;
  border-radius: var(--radius-full);
  transition: background-color var(--duration-fast) var(--ease-out);
}

.switch[data-state='checked'] {
  background: var(--brand);
}

.switch[data-disabled] {
  cursor: not-allowed;
  opacity: 0.55;
}

.switch:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.switch__thumb {
  display: block;
  width: 16px;
  height: 16px;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 2px rgb(16 24 40 / 20%);
  transform: translateX(2px);
  transition: transform var(--duration-fast) var(--ease-out);
  will-change: transform;
}

.switch[data-state='checked'] .switch__thumb {
  transform: translateX(18px);
}
</style>

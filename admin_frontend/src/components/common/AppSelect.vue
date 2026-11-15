<script setup>
import {
  SelectContent,
  SelectIcon,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectPortal,
  SelectRoot,
  SelectTrigger,
  SelectValue,
  SelectViewport,
} from 'reka-ui'
import AppIcon from './AppIcon.vue'

defineProps({
  modelValue: { type: [String, Number, Boolean], default: undefined },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false },
  ariaLabel: { type: String, default: '' },
  id: { type: String, default: undefined },
})

const emit = defineEmits(['update:modelValue'])

function onUpdate(value) {
  emit('update:modelValue', value)
}
</script>

<template>
  <SelectRoot :model-value="modelValue" :disabled="disabled" @update:model-value="onUpdate">
    <SelectTrigger :id="id" class="select-trigger" :aria-label="ariaLabel || undefined">
      <SelectValue class="select-trigger__value" :placeholder="placeholder" />
      <SelectIcon class="select-trigger__icon">
        <AppIcon name="chevron-down" :size="15" />
      </SelectIcon>
    </SelectTrigger>
    <SelectPortal>
      <SelectContent class="select-content" position="popper" :side-offset="4" align="start">
        <SelectViewport class="select-viewport">
          <SelectItem
            v-for="option in options"
            :key="String(option.value)"
            :value="option.value"
            :disabled="option.disabled"
            class="select-item"
          >
            <SelectItemIndicator class="select-item__indicator">
              <AppIcon name="check" :size="13" />
            </SelectItemIndicator>
            <SelectItemText>{{ option.label }}</SelectItemText>
          </SelectItem>
          <p v-if="!options.length" class="select-empty">暂无可选项</p>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>

<style scoped>
.select-trigger {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: var(--control-height);
  padding: 0 10px 0 11px;
  font-size: var(--text-sm);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: var(--control-radius);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.select-trigger:hover {
  border-color: var(--border-strong);
}

.select-trigger:focus-visible,
.select-trigger[data-state='open'] {
  border-color: var(--brand);
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.select-trigger[data-disabled] {
  color: var(--text-disabled);
  cursor: not-allowed;
  background: var(--bg-inset);
}

.select-trigger__value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-trigger__value[data-placeholder] {
  color: var(--text-tertiary);
}

.select-trigger__icon {
  flex: none;
  color: var(--text-tertiary);
}

.select-empty {
  padding: 12px 10px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  text-align: center;
}
</style>

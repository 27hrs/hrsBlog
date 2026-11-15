<script setup>
import { computed } from 'vue'
import {
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuPortal,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuRoot,
  DropdownMenuTrigger,
} from 'reka-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import { useThemeStore } from '@/stores/theme.js'

const theme = useThemeStore()

const options = [
  { value: 'system', label: '跟随系统', icon: 'monitor', hint: '自动匹配设备外观' },
  { value: 'light', label: '浅色', icon: 'sun', hint: '始终使用浅色' },
  { value: 'dark', label: '深色', icon: 'moon', hint: '始终使用深色' },
]

const currentLabel = computed(() => theme.modeLabel)

function onChange(value) {
  theme.setMode(value)
}
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <button
        type="button"
        class="theme-trigger"
        :aria-label="`主题：${currentLabel}，点击切换`"
        :title="`主题：${currentLabel}`"
      >
        <AppIcon :name="theme.modeIcon" :size="17" />
        <span class="theme-trigger__label">{{ currentLabel }}</span>
        <AppIcon class="theme-trigger__chevron" name="chevron-down" :size="13" />
      </button>
    </DropdownMenuTrigger>

    <DropdownMenuPortal>
      <DropdownMenuContent class="dropdown-menu theme-menu" :side-offset="6" align="end">
        <DropdownMenuLabel class="dropdown-label">界面主题</DropdownMenuLabel>
        <DropdownMenuRadioGroup :model-value="theme.mode" @update:model-value="onChange">
          <DropdownMenuRadioItem
            v-for="option in options"
            :key="option.value"
            :value="option.value"
            class="dropdown-item theme-option"
          >
            <span class="theme-option__icon">
              <AppIcon :name="option.icon" :size="16" />
            </span>
            <span class="theme-option__copy">
              <span>{{ option.label }}</span>
              <small>{{ option.hint }}</small>
            </span>
            <span
              class="theme-option__check"
              :class="{ 'is-visible': theme.mode === option.value }"
              aria-hidden="true"
            >
              <AppIcon name="check" :size="13" />
            </span>
          </DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<style scoped>
.theme-trigger {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  height: 32px;
  padding: 0 9px;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.theme-trigger:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
  border-color: var(--border-subtle);
}

.theme-trigger:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.theme-trigger__label {
  max-width: 68px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.theme-trigger__chevron {
  color: var(--text-tertiary);
}

.theme-menu {
  min-width: 220px;
}

.theme-option {
  min-height: 42px;
  gap: 10px;
}

.theme-option__icon {
  display: grid;
  flex: none;
  width: 28px;
  height: 28px;
  color: var(--text-secondary);
  background: var(--bg-inset);
  border-radius: var(--radius-xs);
  place-items: center;
}

.theme-option__copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.theme-option__copy small {
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.theme-option__check {
  display: grid;
  width: 16px;
  height: 16px;
  margin-left: auto;
  color: var(--brand);
  opacity: 0;
  place-items: center;
}

.theme-option__check.is-visible {
  opacity: 1;
}

.theme-option[data-state='checked'] {
  color: var(--text-primary);
}

@media (max-width: 760px) {
  .theme-trigger__label {
    display: none;
  }

  .theme-trigger {
    width: 32px;
    padding: 0;
    justify-content: center;
  }

  .theme-trigger__chevron {
    display: none;
  }
}
</style>

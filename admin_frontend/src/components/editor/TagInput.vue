<script setup>
import { nextTick, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 10 },
  maxLength: { type: Number, default: 50 },
  disabled: { type: Boolean, default: false },
  error: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])
const inputRef = ref(null)
const draft = ref('')
const hint = ref('')

function commit(rawValue) {
  const values = String(rawValue || '')
    .split(/[,，]/)
    .map((value) => value.trim())
    .filter(Boolean)

  if (!values.length) return
  const next = [...props.modelValue]

  for (const value of values) {
    if (next.length >= props.max) {
      hint.value = `最多添加 ${props.max} 个标签`
      break
    }
    if (value.length > props.maxLength) {
      hint.value = `单个标签不能超过 ${props.maxLength} 个字符`
      break
    }
    if (next.some((tag) => tag.toLowerCase() === value.toLowerCase())) {
      hint.value = '这个标签已经添加过了'
      continue
    }
    next.push(value)
  }

  emit('update:modelValue', next)
  draft.value = ''
}

function onKeydown(event) {
  if (event.isComposing) return
  if (event.key === 'Enter' || event.key === ',' || event.key === '，') {
    event.preventDefault()
    commit(draft.value)
    return
  }
  if (event.key === 'Backspace' && !draft.value && props.modelValue.length) {
    emit('update:modelValue', props.modelValue.slice(0, -1))
  }
}

function removeTag(tag) {
  emit(
    'update:modelValue',
    props.modelValue.filter((item) => item !== tag),
  )
  hint.value = ''
}

function focusInput() {
  nextTick(() => inputRef.value?.focus())
}
</script>

<template>
  <div class="tag-input" :class="{ 'tag-input--invalid': error, 'is-disabled': disabled }">
    <div class="tag-input__control" @click="focusInput">
      <span v-for="tag in modelValue" :key="tag" class="tag-input__chip">
        {{ tag }}
        <button
          class="tag-input__remove"
          type="button"
          :aria-label="`移除标签 ${tag}`"
          @click.stop="removeTag(tag)"
        >
          <AppIcon name="close" :size="10" />
        </button>
      </span>
      <input
        ref="inputRef"
        v-model="draft"
        class="tag-input__input"
        type="text"
        :disabled="disabled"
        :placeholder="modelValue.length ? '继续添加…' : '输入标签后按 Enter'"
        :maxlength="maxLength"
        @keydown="onKeydown"
        @blur="commit(draft)"
        @paste.prevent="commit(($event.clipboardData || window.clipboardData).getData('text'))"
      />
    </div>
    <div class="tag-input__meta">
      <span v-if="error || hint" class="form-error">{{ error || hint }}</span>
      <span v-else class="form-help">使用标签组织内容，最多 {{ max }} 个</span>
      <span class="form-counter">{{ modelValue.length }} / {{ max }}</span>
    </div>
  </div>
</template>

<style scoped>
.tag-input {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tag-input__control {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
  min-height: var(--control-height);
  padding: 5px 8px;
  cursor: text;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: var(--control-radius);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.tag-input__control:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.tag-input--invalid .tag-input__control {
  border-color: var(--danger);
}

.tag-input.is-disabled .tag-input__control {
  cursor: not-allowed;
  background: var(--bg-inset);
}

.tag-input__chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  max-width: 100%;
  height: 22px;
  padding: 0 5px 0 8px;
  overflow: hidden;
  font-size: var(--text-2xs);
  color: var(--brand-text);
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--brand-soft);
  border-radius: var(--radius-xs);
}

.tag-input__remove {
  display: grid;
  width: 14px;
  height: 14px;
  padding: 0;
  color: currentcolor;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 2px;
  opacity: 0.65;
  place-items: center;
}

.tag-input__remove:hover {
  background: var(--brand-soft-strong);
  opacity: 1;
}

.tag-input__input {
  flex: 1;
  min-width: 90px;
  height: 22px;
  padding: 0;
  font-size: var(--text-sm);
  color: var(--text-primary);
  background: transparent;
  border: 0;
  outline: none;
}

.tag-input__input::placeholder {
  color: var(--text-tertiary);
}

.tag-input__meta {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 15px;
}

.tag-input__meta .form-error {
  flex: 1;
}
</style>

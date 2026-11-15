<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import AppSwitch from '@/components/common/AppSwitch.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import { isFieldVisible } from '@/config/settingsRegistry.js'

const props = defineProps({
  section: { type: Object, required: true },
  modelValue: { type: Object, required: true },
  errors: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update-field'])

const visibleFields = computed(() =>
  props.section.fields.filter((field) => isFieldVisible(field, props.modelValue)),
)

function fieldId(field) {
  return `setting-${field.key}`
}

function spanClass(field) {
  return field.width === 'full' ? 'schema-field--full' : ''
}

function fieldError(field) {
  return props.errors[field.key] || ''
}

function setValue(field, value) {
  emit('update-field', { key: field.key, value })
}

function inputValue(field) {
  const value = props.modelValue[field.key]
  return value ?? ''
}

function onTextInput(field, event) {
  setValue(field, event.target.value)
}

function onNumberInput(field, event) {
  const raw = event.target.value
  setValue(field, raw === '' ? '' : Number(raw))
}

function listItems(field) {
  return Array.isArray(props.modelValue[field.key]) ? props.modelValue[field.key] : []
}

function listItemError(field, index, subField) {
  return props.errors[`${field.key}.${index}.${subField.key}`] || ''
}

function updateListItem(field, index, subField, value) {
  const next = listItems(field).map((item, itemIndex) =>
    itemIndex === index ? { ...item, [subField.key]: value } : item,
  )
  setValue(field, next)
}

function addListItem(field) {
  const emptyItem = Object.fromEntries(
    (field.itemFields || []).map((subField) => [subField.key, '']),
  )
  setValue(field, [...listItems(field), emptyItem])
}

function removeListItem(field, index) {
  setValue(
    field,
    listItems(field).filter((_, itemIndex) => itemIndex !== index),
  )
}

function colorPickerValue(field) {
  const value = String(props.modelValue[field.key] || '')
  return /^#[0-9a-f]{6}$/i.test(value) ? value : '#3b82f6'
}
</script>

<template>
  <div class="schema-grid">
    <div
      v-for="field in visibleFields"
      :key="field.key"
      class="schema-field"
      :class="spanClass(field)"
    >
      <template v-if="field.type === 'switch'">
        <div class="schema-switch">
          <div class="schema-switch__copy">
            <label class="form-label" :for="fieldId(field)">{{ field.label }}</label>
            <p v-if="field.description" class="form-help">{{ field.description }}</p>
            <p v-else-if="field.help" class="form-help">{{ field.help }}</p>
          </div>
          <AppSwitch
            :id="fieldId(field)"
            :model-value="Boolean(modelValue[field.key])"
            :aria-label="field.label"
            @update:model-value="setValue(field, $event)"
          />
        </div>
      </template>

      <template v-else-if="field.type === 'image'">
        <div class="form-label-row">
          <label class="form-label" :for="fieldId(field)">{{ field.label }}</label>
          <span v-if="field.rules?.required" class="form-label__required">*</span>
        </div>
        <ImageUploader
          :id="fieldId(field)"
          :model-value="inputValue(field)"
          compact
          :label="`上传${field.label}`"
          @update:model-value="setValue(field, $event)"
        />
        <p v-if="fieldError(field)" class="form-error">{{ fieldError(field) }}</p>
        <p v-else-if="field.help" class="form-help">{{ field.help }}</p>
      </template>

      <template v-else-if="field.type === 'list'">
        <div class="schema-list__head">
          <div>
            <div class="form-label-row">
              <span class="form-label">{{ field.label }}</span>
              <span class="form-counter">{{ listItems(field).length }} 项</span>
            </div>
            <p v-if="field.help" class="form-help">{{ field.help }}</p>
          </div>
          <button class="btn btn--soft btn--sm" type="button" @click="addListItem(field)">
            <AppIcon name="plus" :size="12" />
            {{ field.addLabel || '添加一项' }}
          </button>
        </div>

        <div v-if="listItems(field).length" class="schema-list">
          <div
            v-for="(item, index) in listItems(field)"
            :key="index"
            class="schema-list__item"
          >
            <div class="schema-list__item-head">
              <span class="schema-list__index">
                {{ field.itemLabelKey && item[field.itemLabelKey] ? item[field.itemLabelKey] : `第 ${index + 1} 项` }}
              </span>
              <button
                class="schema-list__remove"
                type="button"
                :aria-label="`移除${field.label}第 ${index + 1} 项`"
                @click="removeListItem(field, index)"
              >
                <AppIcon name="trash" :size="13" />
              </button>
            </div>
            <div class="schema-list__grid">
              <div v-for="subField in field.itemFields" :key="subField.key" class="form-field">
                <label class="form-label" :for="`${fieldId(field)}-${index}-${subField.key}`">
                  {{ subField.label }}
                  <span v-if="subField.rules?.required" class="form-label__required">*</span>
                </label>
                <input
                  :id="`${fieldId(field)}-${index}-${subField.key}`"
                  class="input"
                  :class="{ 'input--invalid': listItemError(field, index, subField) }"
                  :value="item[subField.key] ?? ''"
                  :placeholder="subField.placeholder"
                  :maxlength="subField.rules?.max || undefined"
                  @input="updateListItem(field, index, subField, $event.target.value)"
                />
                <p v-if="listItemError(field, index, subField)" class="form-error">
                  {{ listItemError(field, index, subField) }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <button
          v-else
          class="schema-list__empty"
          type="button"
          @click="addListItem(field)"
        >
          <AppIcon name="plus" :size="15" />
          暂无{{ field.label }}，点击添加第一项
        </button>
      </template>

      <template v-else-if="field.type === 'color'">
        <div class="form-label-row">
          <label class="form-label" :for="fieldId(field)">{{ field.label }}</label>
          <span v-if="field.rules?.required" class="form-label__required">*</span>
        </div>
        <div class="schema-color" :class="{ 'schema-color--invalid': fieldError(field) }">
          <span class="schema-color__swatch-wrap">
            <input
              class="schema-color__picker"
              type="color"
              :value="colorPickerValue(field)"
              aria-label="选择颜色"
              @input="onTextInput(field, $event)"
            />
          </span>
          <input
            :id="fieldId(field)"
            class="input schema-color__input mono"
            :value="inputValue(field)"
            placeholder="#3b82f6"
            maxlength="7"
            @input="onTextInput(field, $event)"
          />
          <span class="schema-color__format">HEX</span>
        </div>
        <p v-if="fieldError(field)" class="form-error">{{ fieldError(field) }}</p>
        <p v-else-if="field.help" class="form-help">{{ field.help }}</p>
      </template>

      <template v-else-if="field.type === 'select'">
        <div class="form-label-row">
          <label class="form-label" :for="fieldId(field)">{{ field.label }}</label>
          <span v-if="field.rules?.required" class="form-label__required">*</span>
        </div>
        <AppSelect
          :id="fieldId(field)"
          :model-value="modelValue[field.key]"
          :options="field.options || []"
          :aria-label="field.label"
          @update:model-value="setValue(field, $event)"
        />
        <p v-if="fieldError(field)" class="form-error">{{ fieldError(field) }}</p>
        <p v-else-if="field.help" class="form-help">{{ field.help }}</p>
      </template>

      <template v-else-if="field.type === 'textarea'">
        <div class="form-label-row">
          <label class="form-label" :for="fieldId(field)">
            {{ field.label }}
            <span v-if="field.rules?.required" class="form-label__required">*</span>
          </label>
          <span v-if="field.rules?.max" class="form-counter">
            {{ String(inputValue(field)).length }} / {{ field.rules.max }}
          </span>
        </div>
        <textarea
          :id="fieldId(field)"
          class="textarea"
          :class="{ 'textarea--invalid': fieldError(field) }"
          :value="inputValue(field)"
          :placeholder="field.placeholder"
          :maxlength="field.rules?.max || undefined"
          rows="4"
          @input="onTextInput(field, $event)"
        />
        <p v-if="fieldError(field)" class="form-error">{{ fieldError(field) }}</p>
        <p v-else-if="field.help" class="form-help">{{ field.help }}</p>
      </template>

      <template v-else-if="field.type === 'number'">
        <div class="form-label-row">
          <label class="form-label" :for="fieldId(field)">{{ field.label }}</label>
          <span class="form-label__optional">{{ field.unit || '' }}</span>
        </div>
        <div class="schema-number">
          <input
            :id="fieldId(field)"
            class="input"
            :class="{ 'input--invalid': fieldError(field) }"
            type="number"
            :value="inputValue(field)"
            :min="field.min"
            :max="field.max"
            :step="field.step || 1"
            @input="onNumberInput(field, $event)"
          />
          <span v-if="field.unit" class="schema-number__unit">{{ field.unit }}</span>
        </div>
        <p v-if="fieldError(field)" class="form-error">{{ fieldError(field) }}</p>
        <p v-else-if="field.help" class="form-help">{{ field.help }}</p>
      </template>

      <template v-else>
        <div class="form-label-row">
          <label class="form-label" :for="fieldId(field)">
            {{ field.label }}
            <span v-if="field.rules?.required" class="form-label__required">*</span>
          </label>
          <span v-if="field.rules?.max" class="form-counter">
            {{ String(inputValue(field)).length }} / {{ field.rules.max }}
          </span>
        </div>
        <input
          :id="fieldId(field)"
          class="input"
          :class="{ 'input--invalid': fieldError(field) }"
          type="text"
          :value="inputValue(field)"
          :placeholder="field.placeholder"
          :maxlength="field.rules?.max || undefined"
          @input="onTextInput(field, $event)"
        />
        <p v-if="fieldError(field)" class="form-error">{{ fieldError(field) }}</p>
        <p v-else-if="field.help" class="form-help">{{ field.help }}</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.schema-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px 20px;
}

.schema-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.schema-field--full {
  grid-column: 1 / -1;
}

.schema-switch {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 48px;
  padding: 10px 11px;
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.schema-switch__copy {
  min-width: 0;
}

.schema-switch__copy .form-help {
  margin-top: 4px;
}

.schema-list__head {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  justify-content: space-between;
}

.schema-list__head > div {
  min-width: 0;
}

.schema-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.schema-list__item {
  padding: 10px 11px;
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.schema-list__item-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
}

.schema-list__index {
  overflow: hidden;
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schema-list__remove {
  display: grid;
  width: 24px;
  height: 22px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-xs);
  place-items: center;
}

.schema-list__remove:hover {
  color: var(--danger-text);
  background: var(--danger-soft);
}

.schema-list__grid {
  display: grid;
  grid-template-columns: minmax(120px, 0.8fr) minmax(0, 1.4fr);
  gap: 10px;
}

.schema-list__empty {
  display: flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-height: 64px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  cursor: pointer;
  background: var(--bg-inset);
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
}

.schema-list__empty:hover {
  color: var(--brand-text);
  border-color: var(--brand);
}

.schema-color {
  display: flex;
  gap: 0;
  align-items: center;
  height: var(--control-height);
  overflow: hidden;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: var(--control-radius);
}

.schema-color:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.schema-color--invalid {
  border-color: var(--danger);
}

.schema-color__swatch-wrap {
  position: relative;
  flex: none;
  width: 35px;
  height: 100%;
  padding: 8px 0 8px 8px;
}

.schema-color__picker {
  width: 100%;
  height: 100%;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 3px;
}

.schema-color__input {
  flex: 1;
  height: 100%;
  padding: 0 6px;
  font-size: var(--text-xs);
  text-transform: uppercase;
  background: transparent;
  border: 0;
}

.schema-color__input:focus {
  box-shadow: none;
}

.schema-color__format {
  padding-right: 9px;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
}

.schema-number {
  position: relative;
}

.schema-number .input {
  padding-right: 38px;
}

.schema-number__unit {
  position: absolute;
  top: 50%;
  right: 11px;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
  pointer-events: none;
  transform: translateY(-50%);
}

@media (max-width: 760px) {
  .schema-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .schema-field--full {
    grid-column: auto;
  }

  .schema-list__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .schema-list__head {
    flex-direction: column;
    gap: 8px;
  }
}
</style>

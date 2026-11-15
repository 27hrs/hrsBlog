<script setup>
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { resolveAssetUrl } from '@/api/client.js'
import { uploadAsset, validateImageFile } from '@/api/assets.js'
import { notify } from '@/api/notify.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  allowUrl: { type: Boolean, default: true },
  label: { type: String, default: '上传图片' },
})

const emit = defineEmits(['update:modelValue', 'uploaded'])

const input = ref(null)
const uploading = ref(false)
const dragActive = ref(false)
const error = ref('')
const showUrlField = ref(false)

const previewUrl = computed(() => resolveAssetUrl(props.modelValue))

function openPicker() {
  if (uploading.value) return
  input.value?.click()
}

async function handleFile(file) {
  error.value = ''
  const validation = validateImageFile(file)
  if (validation) {
    error.value = validation
    return
  }

  uploading.value = true
  try {
    const asset = await uploadAsset(file)
    if (!asset?.url) throw new Error('上传响应缺少图片地址')
    emit('update:modelValue', asset.url)
    emit('uploaded', asset)
    notify('图片上传成功', 'success')
  } catch (uploadError) {
    error.value = uploadError.message || '图片上传失败'
  } finally {
    uploading.value = false
    if (input.value) input.value.value = ''
  }
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  if (file) handleFile(file)
}

function onDrop(event) {
  dragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

function onDragOver() {
  if (!uploading.value) dragActive.value = true
}

function removeImage() {
  emit('update:modelValue', '')
  error.value = ''
}

function onUrlInput(event) {
  emit('update:modelValue', event.target.value.trim())
}
</script>

<template>
  <div class="image-uploader" :class="{ 'image-uploader--compact': compact }">
    <input
      ref="input"
      class="sr-only"
      type="file"
      accept="image/jpeg,image/png,image/webp,image/gif"
      @change="onFileChange"
    />

    <div v-if="modelValue" class="image-uploader__preview">
      <img :src="previewUrl" alt="已上传图片预览" />
      <div class="image-uploader__actions">
        <button class="btn btn--sm image-uploader__button" type="button" @click="openPicker">
          <AppIcon name="refresh" :size="13" />
          替换
        </button>
        <button
          class="btn btn--sm btn--danger image-uploader__button"
          type="button"
          @click="removeImage"
        >
          <AppIcon name="trash" :size="13" />
          移除
        </button>
      </div>
    </div>

    <button
      v-else
      class="image-uploader__dropzone"
      :class="{ 'is-dragging': dragActive }"
      type="button"
      :disabled="uploading"
      @click="openPicker"
      @dragover.prevent="onDragOver"
      @dragleave.prevent="dragActive = false"
      @drop.prevent="onDrop"
    >
      <span class="image-uploader__icon">
        <span v-if="uploading" class="btn__spinner" />
        <AppIcon v-else name="image" :size="20" />
      </span>
      <span class="image-uploader__copy">
        <span class="image-uploader__title">
          {{ uploading ? '正在上传…' : label }}
        </span>
        <span class="image-uploader__hint">JPG / PNG / WebP / GIF，最大 5 MB</span>
      </span>
    </button>

    <div v-if="modelValue" class="image-uploader__url">
      <span class="image-uploader__url-label">图片地址</span>
      <span class="image-uploader__url-value" :title="modelValue">{{ modelValue }}</span>
    </div>

    <div v-if="allowUrl && !modelValue" class="image-uploader__alternate">
      <button
        v-if="!showUrlField"
        class="image-uploader__link"
        type="button"
        @click="showUrlField = true"
      >
        或粘贴外部图片链接
      </button>
      <input
        v-else
        class="input image-uploader__url-input"
        type="url"
        placeholder="https://images.example.com/cover.jpg"
        :value="modelValue"
        @input="onUrlInput"
      />
    </div>

    <p v-if="error" class="form-error">
      <AppIcon name="alert-circle" :size="13" />
      {{ error }}
    </p>
  </div>
</template>

<style scoped>
.image-uploader {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-uploader__preview {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: var(--bg-checker);
  background-position:
    0 0,
    0 5px,
    5px -5px,
    -5px 0;
  background-size: 10px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.image-uploader__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-uploader__actions {
  position: absolute;
  right: 8px;
  bottom: 8px;
  display: flex;
  gap: 5px;
  opacity: 0;
  transform: translateY(4px);
  transition:
    opacity var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.image-uploader__preview:hover .image-uploader__actions,
.image-uploader__preview:focus-within .image-uploader__actions {
  opacity: 1;
  transform: translateY(0);
}

.image-uploader__button {
  --btn-bg: rgb(255 255 255 / 92%);
  --btn-border: transparent;
  --btn-color: #1d222c;

  backdrop-filter: blur(8px);
}

.image-uploader__button.btn--danger {
  --btn-bg: rgb(254 242 242 / 94%);
  --btn-color: #b91c1c;
}

.image-uploader__dropzone {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  min-height: 96px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  background: var(--bg-inset);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-sm);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.image-uploader__dropzone:hover,
.image-uploader__dropzone.is-dragging {
  background: var(--brand-soft);
  border-color: var(--brand);
}

.image-uploader__dropzone:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.image-uploader__dropzone:disabled {
  cursor: progress;
}

.image-uploader__icon {
  display: grid;
  flex: none;
  width: 36px;
  height: 36px;
  color: var(--text-tertiary);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  place-items: center;
}

.image-uploader__copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.image-uploader__title {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
}

.image-uploader__hint {
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.image-uploader__url {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 6px 8px;
  background: var(--bg-inset);
  border-radius: var(--radius-xs);
}

.image-uploader__url-label {
  flex: none;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.image-uploader__url-value {
  min-width: 0;
  overflow: hidden;
  font-family: var(--font-mono);
  font-size: var(--text-2xs);
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-uploader__alternate {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.image-uploader__link {
  align-self: flex-start;
  padding: 0;
  font-size: var(--text-2xs);
  color: var(--brand-text);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.image-uploader__link:hover {
  text-decoration: underline;
}

.image-uploader__url-input {
  height: 32px;
  font-size: var(--text-xs);
}

.image-uploader--compact .image-uploader__preview {
  aspect-ratio: 3 / 1;
}

.image-uploader--compact .image-uploader__dropzone {
  min-height: 64px;
  padding: 10px 12px;
}

.image-uploader--compact .image-uploader__icon {
  width: 28px;
  height: 28px;
}
</style>

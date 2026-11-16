<script setup>
import { computed, reactive, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { ApiError } from '../../../api/client.js'
import {
  EMAIL_PATTERN,
  GUEST_MESSAGE_LIMITS,
  deriveAvatarUrl,
  submitGuestMessage,
} from '../../../api/guest-messages.js'

const fields = [
  {
    name: 'nickname',
    label: '你的称呼',
    required: true,
    type: 'text',
    placeholder: '例如：湖边的读者',
    maxlength: GUEST_MESSAGE_LIMITS.nickname,
  },
  {
    name: 'email',
    label: '邮箱',
    required: false,
    type: 'email',
    placeholder: '选填：用于 QQ 头像展示',
    maxlength: GUEST_MESSAGE_LIMITS.email,
    hint: '填 QQ 邮箱（纯数字@qq.com）会显示你的 QQ 头像；邮箱不会出现在留言墙上。',
  },
  {
    name: 'content',
    label: '留言内容',
    required: true,
    type: 'textarea',
    placeholder: '想说点什么？',
    maxlength: GUEST_MESSAGE_LIMITS.content,
    wide: true,
  },
]

const form = reactive({ nickname: '', email: '', content: '' })

const errors = reactive({})
const submitting = ref(false)
const status = ref('') // '' | 'success' | 'error'
const message = ref('')

/** 邮箱是 QQ 邮箱时即时预览头像；加载失败时回退到昵称首字。 */
const avatarFailed = ref(false)
const avatarPreview = computed(() => (avatarFailed.value ? null : deriveAvatarUrl(form.email)))

watch(
  () => form.email,
  () => {
    avatarFailed.value = false
  },
)

const previewLetter = computed(() => {
  const nickname = String(form.nickname || '').trim()
  return nickname ? nickname.slice(0, 1) : '·'
})

function fieldError(field) {
  const value = String(form[field.name] || '').trim()
  const limit = field.maxlength

  if (!value) return field.required ? '这一项需要填写' : ''
  if (value.length > limit) return `不能超过 ${limit} 个字符`
  if (field.type === 'email' && !EMAIL_PATTERN.test(value)) {
    return '请输入有效的邮箱地址'
  }

  return ''
}

function validate() {
  const next = {}
  for (const field of fields) {
    const error = fieldError(field)
    if (error) next[field.name] = error
  }
  return next
}

function clearError(name) {
  if (errors[name]) delete errors[name]
}

function clearErrors() {
  for (const key of Object.keys(errors)) delete errors[key]
}

function resetForm() {
  form.nickname = ''
  form.email = ''
  form.content = ''
  clearErrors()
}

/** 后端 40000 的 details 是 { 字段名: 中文错误 } */
function applyServerErrors(error) {
  if (!(error instanceof ApiError)) return
  if (error.code !== 40000) return

  const details = error.details
  if (!details || typeof details !== 'object' || Array.isArray(details)) return

  for (const [key, value] of Object.entries(details)) {
    if (key in form) errors[key] = String(value)
  }
}

function focusField(name) {
  if (typeof document === 'undefined') return
  document.getElementById(`guest-message-${name}`)?.focus()
}

async function onSubmit() {
  if (submitting.value) return

  const next = validate()
  clearErrors()
  Object.assign(errors, next)

  if (Object.keys(next).length) {
    status.value = 'error'
    message.value = '还有几项需要补充。'
    focusField(Object.keys(next)[0])
    return
  }

  submitting.value = true
  status.value = ''
  message.value = ''

  try {
    await submitGuestMessage({ ...form }, { timeout: 10000 })
    status.value = 'success'
    message.value = '已提交，等待站长审核'
    resetForm()
  } catch (error) {
    status.value = 'error'
    message.value =
      error?.code === 'INVALID_PAYLOAD'
        ? '留言内容填写不完整，请检查后重试。'
        : error?.message || '提交失败，请稍后再试'
    applyServerErrors(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="guest-form" novalidate @submit.prevent="onSubmit">
    <div class="guest-form__grid">
      <div
        v-for="field in fields"
        :key="field.name"
        class="guest-form__field"
        :class="{ 'guest-form__field--wide': field.wide }"
      >
        <label class="guest-form__label" :for="`guest-message-${field.name}`">
          <span>{{ field.label }}</span>
          <span v-if="field.required" class="guest-form__required" aria-hidden="true">*</span>
          <span v-else class="guest-form__optional">选填</span>
        </label>

        <textarea
          v-if="field.type === 'textarea'"
          :id="`guest-message-${field.name}`"
          v-model="form[field.name]"
          class="guest-form__input guest-form__input--textarea"
          :class="{ 'is-invalid': errors[field.name] }"
          :rows="4"
          :maxlength="field.maxlength"
          :placeholder="field.placeholder"
          :aria-invalid="errors[field.name] ? 'true' : 'false'"
          :aria-describedby="errors[field.name] ? `guest-message-${field.name}-error` : undefined"
          @input="clearError(field.name)"
        />
        <input
          v-else
          :id="`guest-message-${field.name}`"
          v-model="form[field.name]"
          class="guest-form__input"
          :class="{ 'is-invalid': errors[field.name] }"
          :type="field.type"
          :maxlength="field.maxlength"
          :placeholder="field.placeholder"
          :aria-invalid="errors[field.name] ? 'true' : 'false'"
          :aria-describedby="errors[field.name] ? `guest-message-${field.name}-error` : undefined"
          @input="clearError(field.name)"
        />

        <p
          v-if="errors[field.name]"
          :id="`guest-message-${field.name}-error`"
          class="guest-form__error"
        >
          {{ errors[field.name] }}
        </p>
        <p v-else-if="field.hint" class="guest-form__hint">{{ field.hint }}</p>
      </div>
    </div>

    <div class="guest-form__preview">
      <span class="guest-form__preview-avatar">
        <span class="guest-form__preview-letter" aria-hidden="true">{{ previewLetter }}</span>
        <img
          v-if="avatarPreview"
          class="guest-form__preview-img"
          :src="avatarPreview"
          alt=""
          width="40"
          height="40"
          loading="lazy"
          decoding="async"
          referrerpolicy="no-referrer"
          @error="avatarFailed = true"
        />
      </span>
      <span class="guest-form__preview-copy">
        <span class="guest-form__preview-label">头像预览</span>
        <span class="guest-form__preview-text">
          {{
            avatarPreview
              ? '识别为 QQ 邮箱，将展示你的 QQ 头像。'
              : '填写 QQ 邮箱即可展示 QQ 头像。'
          }}
        </span>
      </span>
    </div>

    <div class="guest-form__foot">
      <button class="guest-form__submit" type="submit" :disabled="submitting">
        <span>{{ submitting ? '提交中…' : '留下留言' }}</span>
        <AppIcon v-if="!submitting" name="arrow-right" :size="14" />
      </button>
      <p class="guest-form__note">提交后需要站长人工审核，通过后才会出现在上面的留言墙里。</p>
    </div>

    <p
      v-if="message"
      class="guest-form__status"
      :class="status === 'success' ? 'guest-form__status--success' : 'guest-form__status--error'"
      role="status"
    >
      <AppIcon v-if="status === 'success'" name="check" :size="15" />
      <AppIcon v-else name="close" :size="15" />
      <span>{{ message }}</span>
    </p>

    <noscript>
      <p class="guest-form__note">留言表单需要 JavaScript 才能提交，你也可以直接写信给我。</p>
    </noscript>
  </form>
</template>

<style scoped>
.guest-form {
  display: flex;
  flex-direction: column;
}

.guest-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 24px;
}

.guest-form__field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.guest-form__field--wide {
  grid-column: 1 / -1;
}

.guest-form__label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--ink);
  font-size: 13px;
  line-height: 1.4;
}

.guest-form__required {
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1;
}

.guest-form__optional {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  line-height: 1;
  text-transform: uppercase;
}

.guest-form__input {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-solid);
  color: var(--ink);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
  transition:
    border-color var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease);
}

.guest-form__input::placeholder {
  color: var(--muted);
  opacity: 0.7;
}

.guest-form__input:focus {
  border-color: var(--copper);
  box-shadow: 0 0 0 3px var(--copper-soft);
}

.guest-form__input--textarea {
  min-height: 108px;
  resize: vertical;
}

.guest-form__input.is-invalid {
  border-color: var(--copper-deep);
}

.guest-form__input.is-invalid:focus {
  box-shadow: 0 0 0 3px var(--copper-soft);
}

.guest-form__error {
  color: var(--copper-deep);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.04em;
  line-height: 1.5;
}

.guest-form__hint {
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.6;
}

.guest-form__preview {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 22px;
  padding: 13px 15px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--paper-2);
}

.guest-form__preview-avatar {
  position: relative;
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--paper-2);
}

.guest-form__preview-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--paper-2);
  object-fit: cover;
}

.guest-form__preview-letter {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--pine);
  color: var(--copper-bright);
  font-family: var(--font-display);
  font-size: 18px;
  font-style: italic;
  line-height: 1;
}

.guest-form__preview-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.guest-form__preview-label {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  line-height: 1;
  text-transform: uppercase;
}

.guest-form__preview-text {
  color: var(--ink-soft);
  font-size: 12.5px;
  line-height: 1.6;
}

.guest-form__foot {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 26px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.guest-form__submit {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 12px 22px;
  border: 1px solid var(--copper);
  border-radius: var(--radius);
  background: var(--copper);
  color: var(--on-dark);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  line-height: 1;
  text-transform: uppercase;
  cursor: pointer;
  transition:
    background-color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease),
    opacity var(--dur-fast) var(--ease);
}

.guest-form__submit:hover:not(:disabled) {
  border-color: var(--copper-deep);
  background: var(--copper-deep);
}

.guest-form__submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.guest-form__note {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.guest-form__status {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 18px;
  padding: 12px 15px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 13.5px;
  line-height: 1.7;
}

.guest-form__status--success {
  color: var(--copper-deep);
  border-color: var(--copper);
  background: var(--copper-soft);
}

.guest-form__status--success :deep(.app-icon) {
  margin-top: 3px;
  color: var(--copper);
}

.guest-form__status--error {
  color: var(--copper-deep);
  border-color: var(--copper-deep);
}

.guest-form__status--error :deep(.app-icon) {
  margin-top: 3px;
  color: var(--copper-deep);
}

@media (max-width: 720px) {
  .guest-form__grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .guest-form__foot {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .guest-form__submit {
    width: 100%;
    justify-content: center;
  }
}
</style>

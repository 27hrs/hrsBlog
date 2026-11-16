<script setup>
import { onMounted, reactive, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { ApiError } from '../../../api/client.js'
import { FRIEND_LINK_LIMITS, isHttpUrl, submitFriendLink } from '../../../api/friend-links.js'

const fields = [
  {
    name: 'siteName',
    label: '站点名称',
    required: true,
    type: 'text',
    placeholder: '例如：雾湖手记',
    maxlength: FRIEND_LINK_LIMITS.siteName,
  },
  {
    name: 'siteUrl',
    label: '站点地址',
    required: true,
    type: 'url',
    placeholder: 'https://example.com',
    maxlength: FRIEND_LINK_LIMITS.siteUrl,
  },
  {
    name: 'linkPageUrl',
    label: '友链页地址',
    required: true,
    type: 'url',
    placeholder: 'https://example.com/links',
    maxlength: FRIEND_LINK_LIMITS.linkPageUrl,
  },
  {
    name: 'avatarUrl',
    label: '头像 / 站点图标 URL',
    required: false,
    type: 'url',
    placeholder: 'https://example.com/avatar.png',
    maxlength: FRIEND_LINK_LIMITS.avatarUrl,
    hint: '留空则使用站点名称首字母生成的字母头像。',
  },
  {
    name: 'description',
    label: '站点简介',
    required: false,
    type: 'textarea',
    placeholder: '用一句话介绍你的站点',
    maxlength: FRIEND_LINK_LIMITS.description,
    wide: true,
  },
  {
    name: 'contact',
    label: '联系方式（仅站长可见）',
    required: false,
    type: 'text',
    placeholder: '邮箱或社交账号',
    maxlength: FRIEND_LINK_LIMITS.contact,
    hint: '仅站长可见，不会展示在页面上。',
    wide: true,
  },
]

const form = reactive({
  siteName: '',
  siteUrl: '',
  linkPageUrl: '',
  avatarUrl: '',
  description: '',
  contact: '',
})

const errors = reactive({})
const submitting = ref(false)
const ready = ref(false)
const status = ref('')
const message = ref('')

function fieldError(field) {
  const value = String(form[field.name] || '').trim()
  const limit = FRIEND_LINK_LIMITS[field.name]

  if (!value) return field.required ? '这一项需要填写' : ''
  if (value.length > limit) return `不能超过 ${limit} 个字符`
  if (field.type === 'url' && !isHttpUrl(value)) {
    return '需要以 http:// 或 https:// 开头的完整网址'
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
  for (const field of fields) form[field.name] = ''
  clearErrors()
}

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
  document.getElementById(`friend-apply-${name}`)?.focus()
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
    await submitFriendLink({ ...form }, { baseUrl: '/api', timeout: 10000 })
    status.value = 'success'
    message.value = '已提交，等待站长审核'
    resetForm()
  } catch (error) {
    status.value = 'error'
    message.value =
      error?.code === 'INVALID_PAYLOAD'
        ? '申请信息填写不完整，请检查后重试。'
        : error?.message || '提交失败，请稍后再试'
    applyServerErrors(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  ready.value = true
})
</script>

<template>
  <form class="apply" novalidate @submit.prevent="onSubmit">
    <div class="apply__grid">
      <div
        v-for="field in fields"
        :key="field.name"
        class="apply__field"
        :class="{ 'apply__field--wide': field.wide }"
      >
        <label class="apply__label" :for="`friend-apply-${field.name}`">
          <span>{{ field.label }}</span>
          <span v-if="field.required" class="apply__required" aria-hidden="true">*</span>
          <span v-else class="apply__optional">选填</span>
        </label>

        <textarea
          v-if="field.type === 'textarea'"
          :id="`friend-apply-${field.name}`"
          v-model="form[field.name]"
          class="apply__input apply__input--textarea"
          :class="{ 'is-invalid': errors[field.name] }"
          :rows="3"
          :maxlength="field.maxlength"
          :placeholder="field.placeholder"
          :aria-invalid="errors[field.name] ? 'true' : 'false'"
          :aria-describedby="errors[field.name] ? `friend-apply-${field.name}-error` : undefined"
          @input="clearError(field.name)"
        />
        <input
          v-else
          :id="`friend-apply-${field.name}`"
          v-model="form[field.name]"
          class="apply__input"
          :class="{ 'is-invalid': errors[field.name] }"
          :type="field.type"
          :maxlength="field.maxlength"
          :placeholder="field.placeholder"
          :aria-invalid="errors[field.name] ? 'true' : 'false'"
          :aria-describedby="errors[field.name] ? `friend-apply-${field.name}-error` : undefined"
          @input="clearError(field.name)"
        />

        <p v-if="errors[field.name]" :id="`friend-apply-${field.name}-error`" class="apply__error">
          {{ errors[field.name] }}
        </p>
        <p v-else-if="field.hint" class="apply__hint">{{ field.hint }}</p>
      </div>
    </div>

    <div class="apply__foot">
      <button class="apply__submit" type="submit" :disabled="submitting || !ready">
        <span>{{ submitting ? '提交中…' : '提交申请' }}</span>
        <AppIcon v-if="!submitting" name="arrow-right" :size="14" />
      </button>

      <p class="apply__note">提交后需要站长人工审核，通过后才会出现在上面的列表里。</p>
    </div>

    <p
      v-if="message"
      class="apply__status"
      :class="status === 'success' ? 'apply__status--success' : 'apply__status--error'"
      role="status"
    >
      <AppIcon v-if="status === 'success'" name="check" :size="15" />
      <AppIcon v-else name="close" :size="15" />
      <span>{{ message }}</span>
    </p>

    <noscript>
      <p class="apply__note">申请表单需要 JavaScript 才能提交，你也可以直接写信给我。</p>
    </noscript>
  </form>
</template>

<style scoped>
.apply {
  display: flex;
  flex-direction: column;
}

.apply__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 24px;
}

.apply__field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.apply__field--wide {
  grid-column: 1 / -1;
}

.apply__label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--ink);
  font-size: 13px;
  line-height: 1.4;
}

.apply__required {
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1;
}

.apply__optional {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  line-height: 1;
  text-transform: uppercase;
}

.apply__input {
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

.apply__input::placeholder {
  color: var(--muted);
  opacity: 0.7;
}

.apply__input:focus {
  border-color: var(--copper);
  box-shadow: 0 0 0 3px var(--copper-soft);
}

.apply__input--textarea {
  min-height: 88px;
  resize: vertical;
}

.apply__input.is-invalid {
  border-color: var(--copper-deep);
}

.apply__input.is-invalid:focus {
  box-shadow: 0 0 0 3px var(--copper-soft);
}

.apply__error {
  color: var(--copper-deep);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.04em;
  line-height: 1.5;
}

.apply__hint {
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.6;
}

.apply__foot {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 26px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.apply__submit {
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

.apply__submit:hover:not(:disabled) {
  background: var(--copper-deep);
  border-color: var(--copper-deep);
}

.apply__submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.apply__note {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.apply__status {
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

.apply__status--success {
  color: var(--copper-deep);
  border-color: var(--copper);
  background: var(--copper-soft);
}

.apply__status--success :deep(.app-icon) {
  margin-top: 3px;
  color: var(--copper);
}

.apply__status--error {
  color: var(--copper-deep);
  border-color: var(--copper-deep);
}

.apply__status--error :deep(.app-icon) {
  margin-top: 3px;
  color: var(--copper-deep);
}

@media (max-width: 720px) {
  .apply__grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .apply__foot {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .apply__submit {
    width: 100%;
    justify-content: center;
  }
}
</style>

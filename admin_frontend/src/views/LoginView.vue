<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/common/BrandMark.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import ThemeToggle from '@/components/layout/ThemeToggle.vue'
import { useAuthStore } from '@/stores/auth.js'
import { useToastStore } from '@/stores/toast.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const toast = useToastStore()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const formError = ref('')
const fieldErrors = ref({})
const showDevHint = import.meta.env.DEV

function validate() {
  const errors = {}
  if (!username.value.trim()) errors.username = '请输入用户名'
  else if (username.value.trim().length < 3) errors.username = '用户名至少 3 个字符'
  else if (username.value.trim().length > 64) errors.username = '用户名不能超过 64 个字符'

  if (!password.value) errors.password = '请输入密码'
  else if (password.value.length < 8) errors.password = '密码至少 8 个字符'
  else if (password.value.length > 64) errors.password = '密码不能超过 64 个字符'

  fieldErrors.value = errors
  return !Object.keys(errors).length
}

function fillDemoAccount() {
  username.value = 'user'
  password.value = 'admin123'
  formError.value = ''
  fieldErrors.value = {}
}

async function submit() {
  formError.value = ''
  if (submitting.value) return
  if (!validate()) return

  submitting.value = true
  try {
    await auth.login({
      username: username.value.trim(),
      password: password.value,
    })
    toast.success(`欢迎回来，${auth.displayName}`)
    const target =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/') &&
      !route.query.redirect.startsWith('//')
        ? route.query.redirect
        : '/dashboard'
    await router.replace(target)
  } catch (error) {
    formError.value = error.message || '登录失败，请稍后重试'
    if (error.fieldErrors) {
      fieldErrors.value = { ...fieldErrors.value, ...error.fieldErrors }
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <div class="login-page__texture" aria-hidden="true" />
    <div class="login-page__glow" aria-hidden="true" />

    <header class="login-page__topbar">
      <div class="login-page__brand">
        <BrandMark :size="28" />
        <span>HrsBlog</span>
        <span class="login-page__brand-tag">Admin</span>
      </div>
      <ThemeToggle />
    </header>

    <section class="login-card" aria-labelledby="login-title">
      <div class="login-card__rule" aria-hidden="true" />

      <div class="login-card__intro">
        <p class="login-card__eyebrow">
          <span class="login-card__eyebrow-line" aria-hidden="true" />
          HRSBLOG / ADMIN
        </p>
        <h1 id="login-title" class="login-card__title">继续写下去。</h1>
        <p class="login-card__description">
          登录管理后台，发布新的文字、整理旧的想法，并照看你的数字花园。
        </p>
      </div>

      <form class="login-form" novalidate @submit.prevent="submit">
        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="login-username">用户名</label>
            <span class="form-label__optional">Username</span>
          </div>
          <div class="input-group">
            <span class="input-group__icon">
              <AppIcon name="user" :size="16" />
            </span>
            <input
              id="login-username"
              v-model="username"
              class="input"
              :class="{ 'input--invalid': fieldErrors.username }"
              type="text"
              name="username"
              autocomplete="username"
              placeholder="your-name"
              spellcheck="false"
              autofocus
              :disabled="submitting"
              @input="fieldErrors.username = ''; formError = ''"
            />
          </div>
          <p v-if="fieldErrors.username" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ fieldErrors.username }}
          </p>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="login-password">密码</label>
            <span class="form-label__optional">Password</span>
          </div>
          <div class="input-group login-password">
            <span class="input-group__icon">
              <AppIcon name="lock" :size="16" />
            </span>
            <input
              id="login-password"
              v-model="password"
              class="input"
              :class="{ 'input--invalid': fieldErrors.password }"
              :type="showPassword ? 'text' : 'password'"
              name="password"
              autocomplete="current-password"
              placeholder="至少 8 位"
              :disabled="submitting"
              @input="fieldErrors.password = ''; formError = ''"
            />
            <button
              class="login-password__toggle"
              type="button"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              tabindex="0"
              @click="showPassword = !showPassword"
            >
              <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="16" />
            </button>
          </div>
          <p v-if="fieldErrors.password" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ fieldErrors.password }}
          </p>
        </div>

        <div v-if="formError" class="login-error" role="alert">
          <span class="login-error__icon">
            <AppIcon name="alert-circle" :size="16" />
          </span>
          <span>{{ formError }}</span>
        </div>

        <button class="btn btn--primary btn--lg btn--block login-submit" type="submit" :disabled="submitting">
          <span v-if="submitting" class="btn__spinner" />
          <span class="btn__label">{{ submitting ? '正在验证…' : '进入管理后台' }}</span>
          <AppIcon v-if="!submitting" name="arrow-right" :size="16" />
        </button>

        <div v-if="showDevHint" class="login-dev">
          <div class="login-dev__copy">
            <span class="login-dev__label">LOCAL PREVIEW</span>
            <span class="mono">user / admin123</span>
          </div>
          <button class="login-dev__button" type="button" @click="fillDemoAccount">
            填入
            <AppIcon name="arrow-up-right" :size="12" />
          </button>
        </div>
      </form>

      <div class="login-card__footer">
        <span class="login-card__secure">
          <AppIcon name="lock" :size="12" />
          仅限站点管理员访问
        </span>
        <span class="login-card__edition">Edition 01 · 2026</span>
      </div>
    </section>

    <footer class="login-page__footer">
      <span>HrsBlog · Content workspace</span>
      <span class="login-page__footer-dot" aria-hidden="true" />
      <span>Vue 3 / VitePress</span>
    </footer>
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  padding: 96px 24px 72px;
  overflow: hidden;
  background: var(--bg-app);
  isolation: isolate;
}

.login-page::before {
  position: absolute;
  z-index: -1;
  top: 0;
  left: 50%;
  width: min(1200px, 120vw);
  height: 520px;
  content: '';
  background:
    radial-gradient(circle at 50% 0%, var(--brand-soft) 0%, transparent 62%),
    radial-gradient(circle at 80% 25%, color-mix(in srgb, var(--info) 6%, transparent), transparent 40%);
  transform: translateX(-50%);
}

.login-page__texture {
  position: absolute;
  z-index: -1;
  inset: 0;
  opacity: 0.55;
  background-image:
    linear-gradient(var(--border-subtle) 1px, transparent 1px),
    linear-gradient(90deg, var(--border-subtle) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 70% 65% at 50% 36%, #000 5%, transparent 72%);
}

.login-page__glow {
  position: absolute;
  z-index: -1;
  bottom: -280px;
  left: 50%;
  width: 880px;
  height: 440px;
  background: radial-gradient(ellipse, var(--brand-soft) 0%, transparent 66%);
  filter: blur(28px);
  transform: translateX(-50%);
}

.login-page__topbar {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 64px;
  padding: 0 clamp(20px, 4vw, 42px);
}

.login-page__brand {
  display: flex;
  gap: 9px;
  align-items: center;
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.login-page__brand-tag {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  margin-left: 1px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
}

.login-card {
  position: relative;
  width: min(420px, 100%);
  padding: 32px;
  overflow: hidden;
  background: color-mix(in srgb, var(--bg-elevated) 92%, transparent);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow:
    var(--shadow-lg),
    0 0 0 1px rgb(255 255 255 / 25%) inset;
  backdrop-filter: blur(20px);
  animation: login-arrive 500ms var(--ease-out) both;
}

[data-theme='dark'] .login-card {
  background: color-mix(in srgb, var(--bg-elevated) 94%, transparent);
  box-shadow:
    0 30px 90px rgb(0 0 0 / 34%),
    0 0 0 1px rgb(255 255 255 / 3%) inset;
}

.login-card__rule {
  position: absolute;
  top: 0;
  left: 32px;
  width: 42px;
  height: 2px;
  background: var(--brand);
  border-radius: 0 0 2px 2px;
}

.login-card__intro {
  margin-bottom: 26px;
}

.login-card__eyebrow {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 15px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}

.login-card__eyebrow-line {
  width: 16px;
  height: 1px;
  background: var(--brand);
}

.login-card__title {
  font-size: clamp(26px, 5vw, 32px);
  font-weight: var(--weight-semibold);
  line-height: 1.15;
  color: var(--text-primary);
  letter-spacing: -0.05em;
}

.login-card__description {
  max-width: 330px;
  margin-top: 11px;
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--text-secondary);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 17px;
}

.login-password .input {
  padding-right: 42px;
}

.login-password__toggle {
  position: absolute;
  right: 6px;
  display: grid;
  width: 30px;
  height: 30px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-xs);
  place-items: center;
}

.login-password__toggle:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.login-password__toggle:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.login-error {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 9px 10px;
  font-size: var(--text-xs);
  line-height: 1.5;
  color: var(--danger-text);
  background: var(--danger-soft);
  border: 1px solid color-mix(in srgb, var(--danger) 16%, transparent);
  border-radius: var(--radius-sm);
  animation: shake-in 260ms var(--ease-out);
}

.login-error__icon {
  flex: none;
  margin-top: 1px;
}

.login-submit {
  margin-top: 2px;
}

.login-submit .btn__label {
  flex: 1;
}

.login-dev {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 9px 8px 11px;
  background: var(--bg-inset);
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
}

.login-dev__copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.login-dev__label {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}

.login-dev__copy .mono {
  overflow: hidden;
  font-size: var(--text-2xs);
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-dev__button {
  display: inline-flex;
  flex: none;
  gap: 3px;
  align-items: center;
  height: 24px;
  padding: 0 7px;
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--brand-text);
  cursor: pointer;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
}

.login-dev__button:hover {
  border-color: var(--brand);
}

.login-dev__button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.login-card__footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding-top: 18px;
  margin-top: 24px;
  font-size: 10px;
  color: var(--text-tertiary);
  border-top: 1px solid var(--border-subtle);
}

.login-card__secure {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}

.login-card__edition {
  font-family: var(--font-mono);
  letter-spacing: 0.04em;
}

.login-page__footer {
  position: absolute;
  bottom: 22px;
  display: flex;
  gap: 8px;
  align-items: center;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.03em;
}

.login-page__footer-dot {
  width: 3px;
  height: 3px;
  background: var(--text-disabled);
  border-radius: 50%;
}

@keyframes login-arrive {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes shake-in {
  0% {
    opacity: 0;
    transform: translateX(-3px);
  }

  60% {
    transform: translateX(2px);
  }

  100% {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (max-width: 520px) {
  .login-page {
    padding: 88px 16px 60px;
  }

  .login-card {
    padding: 26px 22px;
  }

  .login-card__rule {
    left: 22px;
  }

  .login-card__footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }

  .login-page__footer {
    display: none;
  }
}
</style>

<script setup>
import { ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { changePassword, updateUsername } from '@/api/auth.js'
import { useAuthStore } from '@/stores/auth.js'
import { useToastStore } from '@/stores/toast.js'

const auth = useAuthStore()
const toast = useToastStore()

// 账号安全：用户名与密码各自独立提交，互不影响
const USERNAME_PATTERN = /^[a-zA-Z0-9_-]{3,32}$/

const usernameForm = ref({ newUsername: '' })
const usernameFormErrors = ref({})
const usernameFormError = ref('')
const saving = ref(false)

const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordFormErrors = ref({})
const passwordFormError = ref('')
const savingPassword = ref(false)

/** 用户名校验：3-32 位，字母/数字/下划线/中划线，且不能与当前用户名相同。 */
function validateUsername() {
  const errors = {}
  const value = usernameForm.value.newUsername.trim()

  if (!value) {
    errors.newUsername = '请输入新用户名'
  } else if (!USERNAME_PATTERN.test(value)) {
    errors.newUsername = '用户名只能包含字母、数字、下划线与中划线，长度 3-32'
  } else if (value === auth.username) {
    errors.newUsername = '新用户名不能与当前用户名相同'
  }

  usernameFormErrors.value = errors
  return !Object.keys(errors).length
}

/** 密码校验：原密码必填，新密码 8-64 位且同时包含字母和数字，两次输入需一致。 */
function validatePassword() {
  const errors = {}
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value

  if (!oldPassword) {
    errors.oldPassword = '请输入原密码'
  }

  if (!newPassword) {
    errors.newPassword = '请输入新密码'
  } else if (newPassword.length < 8) {
    errors.newPassword = '新密码至少 8 位'
  } else if (newPassword.length > 64) {
    errors.newPassword = '新密码不能超过 64 位'
  } else if (!/[a-zA-Z]/.test(newPassword) || !/\d/.test(newPassword)) {
    errors.newPassword = '新密码必须同时包含字母和数字'
  }

  if (!confirmPassword) {
    errors.confirmPassword = '请再次输入新密码'
  } else if (confirmPassword !== newPassword) {
    errors.confirmPassword = '两次输入的密码不一致'
  }

  passwordFormErrors.value = errors
  return !Object.keys(errors).length
}

async function submitUsername() {
  if (saving.value) return
  usernameFormError.value = ''
  if (!validateUsername()) return

  const newUsername = usernameForm.value.newUsername.trim()
  saving.value = true
  try {
    const response = await updateUsername({ newUsername })
    // 新的访问令牌与用户信息同步到本地会话
    auth.applySession(response)
    usernameForm.value = { newUsername }
    usernameFormErrors.value = {}
    toast.success('用户名已更新，请使用新用户名登录')
  } catch (error) {
    const fieldErrors = error?.fieldErrors || {}
    if (fieldErrors.newUsername) {
      // 字段级错误已经在输入框下方展示，不再重复弹提示
      usernameFormErrors.value = { ...usernameFormErrors.value, ...fieldErrors }
    } else {
      usernameFormError.value = error?.message || '用户名更新失败，请稍后重试'
      toast.error(usernameFormError.value)
    }
  } finally {
    saving.value = false
  }
}

async function submitPassword() {
  if (savingPassword.value) return
  passwordFormError.value = ''
  if (!validatePassword()) return

  const { oldPassword, newPassword } = passwordForm.value
  savingPassword.value = true
  try {
    const response = await changePassword({ oldPassword, newPassword })
    // 密码变更同样会签发新令牌，用户信息保持不变
    auth.applySession(response)
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    passwordFormErrors.value = {}
    toast.success('密码已修改，请牢记新密码')
  } catch (error) {
    const fieldErrors = error?.fieldErrors || {}
    if (fieldErrors.newPassword) {
      // 字段级错误已经在输入框下方展示，不再重复弹提示
      passwordFormErrors.value = { ...passwordFormErrors.value, ...fieldErrors }
    } else {
      passwordFormError.value = error?.message || '密码修改失败，请稍后重试'
      toast.error(passwordFormError.value)
    }
    // 失败时保留原密码，方便用户直接重试
  } finally {
    savingPassword.value = false
  }
}
</script>

<template>
  <div class="account-security">
    <div class="callout callout--info account-security__intro">
      <AppIcon class="callout__icon" name="info" :size="15" />
      <span>
        修改用户名或密码后，服务端会签发新的访问令牌，当前会话会自动更新，无需重新登录。
      </span>
    </div>

    <div class="account-security__grid">
      <form class="account-form" novalidate @submit.prevent="submitUsername">
        <div class="account-form__head">
          <h3 class="account-form__title">修改用户名</h3>
          <span class="account-form__subtitle">Username</span>
        </div>

        <p class="account-form__current">
          <AppIcon name="user" :size="13" />
          <span>当前账号</span>
          <span class="account-form__current-name mono">{{ auth.username || '—' }}</span>
        </p>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="account-username">新用户名</label>
            <span class="form-label__optional">Username</span>
          </div>
          <div class="input-group">
            <span class="input-group__icon">
              <AppIcon name="user" :size="16" />
            </span>
            <input
              id="account-username"
              v-model="usernameForm.newUsername"
              class="input"
              :class="{ 'input--invalid': usernameFormErrors.newUsername }"
              type="text"
              name="username"
              autocomplete="username"
              spellcheck="false"
              placeholder="your-name"
              :disabled="saving"
              @input="usernameFormErrors.newUsername = ''; usernameFormError = ''"
            />
          </div>
          <p class="form-help">
            3-32 位，仅允许字母、数字、下划线与中划线；用户名同时是登录账号。
          </p>
          <p v-if="usernameFormErrors.newUsername" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ usernameFormErrors.newUsername }}
          </p>
        </div>

        <div v-if="usernameFormError" class="callout callout--danger account-form__error">
          <AppIcon class="callout__icon" name="alert-circle" :size="15" />
          <span>{{ usernameFormError }}</span>
        </div>

        <button class="btn btn--primary" type="submit" :disabled="saving">
          <span v-if="saving" class="btn__spinner" />
          {{ saving ? '正在提交…' : '更新用户名' }}
        </button>
      </form>

      <form class="account-form" novalidate @submit.prevent="submitPassword">
        <div class="account-form__head">
          <h3 class="account-form__title">修改密码</h3>
          <span class="account-form__subtitle">Password</span>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="account-old-password">原密码</label>
            <span class="form-label__optional">Current password</span>
          </div>
          <div class="input-group">
            <span class="input-group__icon">
              <AppIcon name="lock" :size="16" />
            </span>
            <input
              id="account-old-password"
              v-model="passwordForm.oldPassword"
              class="input"
              :class="{ 'input--invalid': passwordFormErrors.oldPassword }"
              type="password"
              name="current-password"
              autocomplete="current-password"
              placeholder="当前使用的密码"
              :disabled="savingPassword"
              @input="passwordFormErrors.oldPassword = ''; passwordFormError = ''"
            />
          </div>
          <p v-if="passwordFormErrors.oldPassword" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ passwordFormErrors.oldPassword }}
          </p>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="account-new-password">新密码</label>
            <span class="form-label__optional">New password</span>
          </div>
          <div class="input-group">
            <span class="input-group__icon">
              <AppIcon name="lock" :size="16" />
            </span>
            <input
              id="account-new-password"
              v-model="passwordForm.newPassword"
              class="input"
              :class="{ 'input--invalid': passwordFormErrors.newPassword }"
              type="password"
              name="new-password"
              autocomplete="new-password"
              placeholder="至少 8 位，含字母与数字"
              :disabled="savingPassword"
              @input="passwordFormErrors.newPassword = ''; passwordFormError = ''"
            />
          </div>
          <p v-if="passwordFormErrors.newPassword" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ passwordFormErrors.newPassword }}
          </p>
        </div>

        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="account-confirm-password">确认新密码</label>
            <span class="form-label__optional">Confirm password</span>
          </div>
          <div class="input-group">
            <span class="input-group__icon">
              <AppIcon name="lock" :size="16" />
            </span>
            <input
              id="account-confirm-password"
              v-model="passwordForm.confirmPassword"
              class="input"
              :class="{ 'input--invalid': passwordFormErrors.confirmPassword }"
              type="password"
              name="confirm-password"
              autocomplete="new-password"
              placeholder="再次输入新密码"
              :disabled="savingPassword"
              @input="passwordFormErrors.confirmPassword = ''; passwordFormError = ''"
            />
          </div>
          <p v-if="passwordFormErrors.confirmPassword" class="form-error">
            <AppIcon name="alert-circle" :size="13" />
            {{ passwordFormErrors.confirmPassword }}
          </p>
        </div>

        <div v-if="passwordFormError" class="callout callout--danger account-form__error">
          <AppIcon class="callout__icon" name="alert-circle" :size="15" />
          <span>{{ passwordFormError }}</span>
        </div>

        <button class="btn btn--primary" type="submit" :disabled="savingPassword">
          <span v-if="savingPassword" class="btn__spinner" />
          {{ savingPassword ? '正在提交…' : '更新密码' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.account-security {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.account-security__intro {
  align-items: flex-start;
  font-size: var(--text-xs);
}

.account-security__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.account-form {
  display: flex;
  flex-direction: column;
  gap: 13px;
  min-width: 0;
  padding: 16px;
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.account-form__head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
}

.account-form__title {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.account-form__subtitle {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.account-form__current {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 7px 10px;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.account-form__current-name {
  font-size: var(--text-xs);
  color: var(--text-primary);
}

.account-form__error {
  align-items: flex-start;
  font-size: var(--text-xs);
}

.account-form .btn--primary {
  align-self: flex-start;
}

@media (max-width: 960px) {
  .account-security__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 600px) {
  .account-form {
    padding: 13px;
  }

  .account-form .btn--primary {
    align-self: stretch;
  }
}
</style>

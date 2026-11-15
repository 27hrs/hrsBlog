<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuPortal,
  DropdownMenuRoot,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from 'reka-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import ThemeToggle from './ThemeToggle.vue'
import { useAuthStore } from '@/stores/auth.js'
import { useToastStore } from '@/stores/toast.js'

defineProps({
  collapsed: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle-sidebar'])
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const section = computed(() => route.meta.section || '工作台')
const pageTitle = computed(() => route.meta.title || '管理后台')
const isEditor = computed(() => route.name === 'article-create')
const blogUrl = computed(() => {
  const configured = import.meta.env.VITE_BLOG_URL
  if (configured) return configured
  return `${window.location.protocol}//${window.location.hostname}:27080`
})

function openBlog() {
  window.open(blogUrl.value, '_blank', 'noopener,noreferrer')
}

function goCreate() {
  if (!isEditor.value) router.push({ name: 'article-create' })
}

function logout() {
  auth.clearSession()
  toast.success('已安全退出')
  router.replace({ name: 'login' })
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__left">
      <button
        class="header-toggle"
        type="button"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="emit('toggle-sidebar')"
      >
        <AppIcon
          class="header-toggle__desktop"
          :name="collapsed ? 'chevrons-right' : 'chevrons-left'"
          :size="18"
        />
        <AppIcon class="header-toggle__mobile" name="menu" :size="19" />
      </button>

      <div class="app-header__crumbs" aria-label="面包屑">
        <span class="app-header__crumb-root">HrsBlog</span>
        <AppIcon name="chevron-right" :size="12" />
        <span class="app-header__crumb-section">{{ section }}</span>
        <AppIcon name="chevron-right" :size="12" />
        <span class="app-header__crumb-current">{{ pageTitle }}</span>
      </div>
    </div>

    <div class="app-header__right">
      <button
        v-if="!isEditor"
        class="btn btn--primary btn--sm header-create"
        type="button"
        @click="goCreate"
      >
        <AppIcon name="plus" :size="14" />
        <span>写文章</span>
      </button>

      <ThemeToggle />

      <span class="app-header__divider" aria-hidden="true" />

      <DropdownMenuRoot>
        <DropdownMenuTrigger as-child>
          <button class="user-trigger" type="button" aria-label="打开账户菜单">
            <span class="avatar avatar--md user-trigger__avatar">{{ auth.initials }}</span>
            <span class="user-trigger__copy">
              <span class="user-trigger__name">{{ auth.displayName }}</span>
              <span class="user-trigger__role">管理员</span>
            </span>
            <AppIcon class="user-trigger__chevron" name="chevron-down" :size="13" />
          </button>
        </DropdownMenuTrigger>

        <DropdownMenuPortal>
          <DropdownMenuContent class="dropdown-menu account-menu" :side-offset="7" align="end">
            <div class="account-menu__identity">
              <span class="avatar avatar--lg">{{ auth.initials }}</span>
              <span class="account-menu__identity-copy">
                <strong>{{ auth.displayName }}</strong>
                <small>@{{ auth.username || 'admin' }}</small>
              </span>
            </div>
            <DropdownMenuSeparator class="dropdown-separator" />
            <DropdownMenuLabel class="dropdown-label">快捷操作</DropdownMenuLabel>
            <DropdownMenuItem class="dropdown-item" @select="openBlog">
              <AppIcon name="globe" :size="16" />
              访问博客前台
              <AppIcon class="account-menu__right" name="arrow-up-right" :size="13" />
            </DropdownMenuItem>
            <DropdownMenuItem
              class="dropdown-item"
              @select="router.push({ name: 'article-create' })"
            >
              <AppIcon name="pen-line" :size="16" />
              新建文章
            </DropdownMenuItem>
            <DropdownMenuSeparator class="dropdown-separator" />
            <DropdownMenuItem class="dropdown-item dropdown-item--danger" @select="logout">
              <AppIcon name="logout" :size="16" />
              退出登录
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenuPortal>
      </DropdownMenuRoot>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  display: flex;
  flex: none;
  gap: var(--space-4);
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 var(--page-pad-x);
  background: var(--header-bg);
  border-bottom: 1px solid var(--border-subtle);
  backdrop-filter: blur(14px);
}

.app-header__left,
.app-header__right {
  display: flex;
  gap: 9px;
  align-items: center;
  min-width: 0;
}

.app-header__left {
  gap: 10px;
}

.app-header__right {
  flex: none;
}

.header-toggle {
  display: grid;
  flex: none;
  width: 32px;
  height: 32px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  place-items: center;
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.header-toggle:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.header-toggle:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.header-toggle__mobile {
  display: none;
}

.app-header__crumbs {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  font-size: var(--text-xs);
  line-height: 1;
  color: var(--text-tertiary);
}

.app-header__crumb-root {
  color: var(--text-secondary);
  font-weight: var(--weight-semibold);
  letter-spacing: -0.01em;
}

.app-header__crumb-section {
  white-space: nowrap;
}

.app-header__crumb-current {
  overflow: hidden;
  color: var(--text-primary);
  font-weight: var(--weight-medium);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-create {
  height: 32px;
  font-size: var(--text-xs);
}

.app-header__divider {
  width: 1px;
  height: 19px;
  margin: 0 4px;
  background: var(--border);
}

.user-trigger {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 38px;
  padding: 3px 5px 3px 4px;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.user-trigger:hover {
  background: var(--bg-hover);
  border-color: var(--border-subtle);
}

.user-trigger:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.user-trigger__avatar {
  background: linear-gradient(145deg, var(--brand-soft), var(--brand-soft-strong));
  border: 1px solid var(--brand-soft-strong);
}

.user-trigger__copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  line-height: 1;
}

.user-trigger__name {
  max-width: 100px;
  overflow: hidden;
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-trigger__role {
  font-size: 10px;
  color: var(--text-tertiary);
}

.user-trigger__chevron {
  color: var(--text-tertiary);
}

.account-menu {
  min-width: 238px;
}

.account-menu__identity {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 10px 8px;
}

.account-menu__identity-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.account-menu__identity-copy strong {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.account-menu__identity-copy small {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.account-menu__right {
  margin-left: auto;
  color: var(--text-tertiary);
}

@media (max-width: 1024px) {
  .header-toggle__desktop {
    display: none;
  }

  .header-toggle__mobile {
    display: block;
  }
}

@media (max-width: 760px) {
  .app-header {
    padding: 0 16px;
  }

  .app-header__crumb-root,
  .app-header__crumb-section,
  .app-header__crumbs > .app-icon {
    display: none;
  }

  .app-header__sm-hide {
    display: none;
  }

  .header-create span {
    display: none;
  }

  .header-create {
    width: 32px;
    padding: 0;
  }

  .app-header__divider,
  .user-trigger__copy,
  .user-trigger__chevron {
    display: none;
  }

  .user-trigger {
    height: 34px;
    padding: 2px;
    border-color: transparent;
  }
}
</style>

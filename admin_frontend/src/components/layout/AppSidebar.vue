<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import BrandMark from '@/components/common/BrandMark.vue'
import AppIcon from '@/components/common/AppIcon.vue'

defineProps({
  collapsed: { type: Boolean, default: false },
  mobile: { type: Boolean, default: false },
})

const emit = defineEmits(['navigate'])
const route = useRoute()

const groups = [
  {
    label: '工作台',
    items: [{ name: 'dashboard', label: '管理主页', icon: 'dashboard' }],
  },
  {
    label: '内容',
    items: [
      { name: 'article-list', label: '文章管理', icon: 'article' },
      { name: 'article-create', label: '文章投放', icon: 'pen-line' },
      { name: 'friend-links', label: '友链管理', icon: 'link' },
    ],
  },
  {
    label: '站点',
    items: [{ name: 'settings', label: '站点设置', icon: 'settings' }],
  },
  {
    label: '系统',
    items: [{ name: 'logs', label: '系统日志', icon: 'file-edit' }],
  },
]

const blogUrl = computed(() => {
  const configured = import.meta.env.VITE_BLOG_URL
  if (configured) return configured
  return `${window.location.protocol}//${window.location.hostname}:27080`
})

function isActive(item) {
  if (item.name === 'dashboard') return route.name === 'dashboard'
  if (item.name === 'article-list') {
    return route.name === 'article-list' || route.name === 'article-edit'
  }
  return route.name === item.name
}
</script>

<template>
  <aside class="sidebar" :class="{ 'is-collapsed': collapsed && !mobile, 'is-mobile': mobile }">
    <RouterLink class="sidebar__brand" :to="{ name: 'dashboard' }" @click="emit('navigate')">
      <BrandMark :size="30" />
      <span v-if="!collapsed || mobile" class="sidebar__brand-copy">
        <span class="sidebar__brand-name">HrsBlog</span>
        <span class="sidebar__brand-caption">内容工作台</span>
      </span>
    </RouterLink>

    <nav class="sidebar__nav" aria-label="主导航">
      <div v-for="group in groups" :key="group.label" class="sidebar__group">
        <p v-if="!collapsed || mobile" class="sidebar__group-label">{{ group.label }}</p>
        <div class="sidebar__items">
          <RouterLink
            v-for="item in group.items"
            :key="item.name"
            class="sidebar__link"
            :class="{ 'is-active': isActive(item) }"
            :to="{ name: item.name }"
            :title="collapsed && !mobile ? item.label : undefined"
            @click="emit('navigate')"
          >
            <span class="sidebar__link-icon">
              <AppIcon :name="item.icon" :size="18" />
            </span>
            <span v-if="!collapsed || mobile" class="sidebar__link-label">{{ item.label }}</span>
          </RouterLink>
        </div>
      </div>
    </nav>

    <div class="sidebar__bottom">
      <a
        class="sidebar__sub-link"
        :href="blogUrl"
        target="_blank"
        rel="noreferrer"
        :title="collapsed && !mobile ? '访问博客' : undefined"
      >
        <AppIcon name="globe" :size="17" />
        <span v-if="!collapsed || mobile">访问博客</span>
        <AppIcon
          v-if="!collapsed || mobile"
          class="sidebar__sub-link-arrow"
          name="arrow-up-right"
          :size="13"
        />
      </a>
      <div v-if="!collapsed || mobile" class="sidebar__version">
        <span class="pulse-dot" />
        <span>HrsBlog v1.0</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  height: 100%;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--border-subtle);
  transition:
    width var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out);
}

.sidebar.is-collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar.is-mobile {
  width: 272px;
  border-right: 0;
}

.sidebar__brand {
  display: flex;
  flex: none;
  gap: 11px;
  align-items: center;
  height: var(--header-height);
  padding: 0 20px;
  color: var(--text-primary);
  text-decoration: none;
  border-bottom: 1px solid var(--border-subtle);
}

.sidebar__brand:hover {
  text-decoration: none;
}

.sidebar__brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1;
}

.sidebar__brand-name {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.sidebar__brand-caption {
  margin-top: 5px;
  font-size: 10px;
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.sidebar__nav {
  flex: 1;
  padding: 22px 12px;
  overflow-y: auto;
}

.sidebar__group + .sidebar__group {
  margin-top: 24px;
}

.sidebar__group-label {
  margin-bottom: 7px;
  padding: 0 9px;
  font-size: 10px;
  font-weight: var(--weight-semibold);
  color: var(--text-tertiary);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.sidebar__items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar__link {
  position: relative;
  display: flex;
  gap: 11px;
  align-items: center;
  height: 38px;
  padding: 0 9px;
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
  text-decoration: none;
  border-radius: var(--radius-sm);
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.sidebar.is-collapsed .sidebar__link {
  justify-content: center;
  padding: 0;
}

.sidebar__link:hover {
  color: var(--text-primary);
  text-decoration: none;
  background: var(--bg-hover);
}

.sidebar__link.is-active {
  color: var(--brand-text);
  background: var(--brand-soft);
}

.sidebar__link.is-active::before {
  position: absolute;
  left: -12px;
  width: 3px;
  height: 18px;
  content: '';
  background: var(--brand);
  border-radius: 0 3px 3px 0;
}

.sidebar__link:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.sidebar__link-icon {
  display: grid;
  flex: none;
  width: 20px;
  height: 20px;
  place-items: center;
}

.sidebar__link.is-active .sidebar__link-icon {
  color: var(--brand-text);
}

.sidebar__link-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar__bottom {
  flex: none;
  padding: 12px;
  border-top: 1px solid var(--border-subtle);
}

.sidebar__sub-link {
  display: flex;
  gap: 10px;
  align-items: center;
  height: 36px;
  padding: 0 9px;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  text-decoration: none;
  border-radius: var(--radius-sm);
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.sidebar.is-collapsed .sidebar__sub-link {
  justify-content: center;
  padding: 0;
}

.sidebar__sub-link:hover {
  color: var(--text-primary);
  text-decoration: none;
  background: var(--bg-hover);
}

.sidebar__sub-link-arrow {
  margin-left: auto;
}

.sidebar__version {
  display: flex;
  gap: 7px;
  align-items: center;
  margin-top: 7px;
  padding: 7px 9px 2px;
  font-size: 10px;
  color: var(--text-tertiary);
  letter-spacing: 0.03em;
}

@media (max-width: 1024px) {
  .sidebar {
    width: 272px;
    border-right: 0;
  }
}
</style>

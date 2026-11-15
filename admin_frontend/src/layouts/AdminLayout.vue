<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import {
  DialogClose,
  DialogContent,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
  VisuallyHidden,
} from 'reka-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const route = useRoute()
const collapsed = ref(localStorage.getItem('hrsblog-admin-sidebar-collapsed') === '1')
const isMobile = ref(false)
const mobileOpen = ref(false)
const mediaQuery = window.matchMedia('(max-width: 1024px)')

function syncViewport(event) {
  isMobile.value = event.matches
  if (!event.matches) mobileOpen.value = false
}

function toggleSidebar() {
  if (isMobile.value) {
    mobileOpen.value = true
    return
  }
  collapsed.value = !collapsed.value
  localStorage.setItem('hrsblog-admin-sidebar-collapsed', collapsed.value ? '1' : '0')
}

watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
  },
)

onMounted(() => {
  isMobile.value = mediaQuery.matches
  mediaQuery.addEventListener('change', syncViewport)
})

onBeforeUnmount(() => {
  mediaQuery.removeEventListener('change', syncViewport)
})
</script>

<template>
  <div class="admin-layout" :class="{ 'is-collapsed': collapsed }">
    <div class="admin-layout__sidebar">
      <AppSidebar :collapsed="collapsed" />
    </div>

    <div class="admin-layout__workspace">
      <AppHeader :collapsed="collapsed" @toggle-sidebar="toggleSidebar" />

      <main class="admin-main">
        <div class="admin-main__inner">
          <RouterView v-slot="{ Component }">
            <Transition name="view" mode="out-in">
              <component :is="Component" :key="route.path" />
            </Transition>
          </RouterView>
        </div>
      </main>

      <footer class="admin-footer">
        <div class="admin-footer__group">
          <span>HrsBlog Admin</span>
          <span class="admin-footer__dot" aria-hidden="true" />
          <span>专注内容，保持简单</span>
        </div>
        <div class="admin-footer__group admin-footer__group--right">
          <span class="mono">v1.0.0</span>
          <span class="admin-footer__separator" aria-hidden="true" />
          <span>{{ new Date().getFullYear() }} · Vue 3 + VitePress</span>
        </div>
      </footer>
    </div>
  </div>

  <DialogRoot :open="mobileOpen" @update:open="mobileOpen = $event">
    <DialogPortal>
      <DialogOverlay class="drawer-overlay" />
      <DialogContent class="drawer-content">
        <VisuallyHidden>
          <DialogTitle>导航菜单</DialogTitle>
        </VisuallyHidden>
        <DialogClose class="drawer-close" type="button" aria-label="关闭导航菜单">
          <AppIcon name="close" :size="17" />
        </DialogClose>
        <AppSidebar mobile @navigate="mobileOpen = false" />
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg-app);
}

.admin-layout__sidebar {
  position: sticky;
  top: 0;
  z-index: var(--z-sidebar);
  flex: none;
  width: var(--sidebar-width);
  height: 100vh;
  transition: width var(--duration-base) var(--ease-out);
}

.admin-layout.is-collapsed .admin-layout__sidebar {
  width: var(--sidebar-collapsed-width);
}

.admin-layout__workspace {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 100vh;
}

.admin-main {
  flex: 1;
  padding: var(--page-pad-y) var(--page-pad-x) var(--space-8);
}

.admin-main__inner {
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
}

.admin-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: var(--footer-height);
  padding: 0 var(--page-pad-x);
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
  border-top: 1px solid var(--border-subtle);
  background: color-mix(in srgb, var(--bg-app) 85%, transparent);
}

.admin-footer__group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.admin-footer__group--right {
  gap: 7px;
}

.admin-footer__dot {
  width: 3px;
  height: 3px;
  background: var(--text-disabled);
  border-radius: 50%;
}

.admin-footer__separator {
  width: 1px;
  height: 9px;
  background: var(--border);
}

.view-enter-active,
.view-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.view-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.view-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}

.drawer-overlay {
  position: fixed;
  z-index: calc(var(--z-overlay) + 2);
  inset: 0;
  background: var(--bg-backdrop);
  backdrop-filter: blur(2px);
  animation: drawer-fade-in var(--duration-base) var(--ease-out);
}

.drawer-content {
  position: fixed;
  z-index: calc(var(--z-overlay) + 3);
  top: 0;
  left: 0;
  width: 272px;
  max-width: calc(100vw - 48px);
  height: 100dvh;
  overflow: hidden;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border);
  box-shadow: var(--shadow-lg);
  animation: drawer-slide-in var(--duration-base) var(--ease-out);
}

.drawer-close {
  position: absolute;
  z-index: 2;
  top: 14px;
  right: 13px;
  display: grid;
  width: 30px;
  height: 30px;
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  place-items: center;
}

.drawer-close:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.drawer-close:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--focus-ring);
}

@keyframes drawer-fade-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@keyframes drawer-slide-in {
  from {
    transform: translateX(-12px);
  }

  to {
    transform: translateX(0);
  }
}

@media (max-width: 1024px) {
  .admin-layout__sidebar {
    display: none;
  }

  .admin-main {
    padding: 24px 20px 32px;
  }

  .admin-footer {
    padding: 0 20px;
  }
}

@media (max-width: 640px) {
  .admin-main {
    padding: 18px 16px 28px;
  }

  .admin-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    padding: 12px 16px;
  }

  .admin-footer__group--right {
    display: none;
  }
}
</style>

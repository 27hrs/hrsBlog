<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useData, useRoute, withBase } from 'vitepress'
import AppIcon from './AppIcon.vue'
import BrandMark from './BrandMark.vue'
import SocialLinks from './SocialLinks.vue'
import ThemeToggle from './ThemeToggle.vue'

const { theme } = useData()
const route = useRoute()

const site = computed(() => theme.value.site || { nav: [], social: [] })
const nav = computed(() => site.value.nav || [])
const scrolled = ref(false)
const menuOpen = ref(false)
const menuButton = ref(null)
const closeButton = ref(null)
let lastFocused = null

function isActive(link) {
  if (link === '/') return route.path === '/' || route.path === '/index.html'
  const clean = link.endsWith('/') ? link.slice(0, -1) : link
  return route.path === clean || route.path === link || route.path.startsWith(`${clean}/`)
}

function updateScroll() {
  scrolled.value = window.scrollY > 18
}

async function openMenu() {
  lastFocused = document.activeElement
  menuOpen.value = true
  document.body.style.overflow = 'hidden'
  await nextTick()
  closeButton.value?.focus()
}

function closeMenu() {
  if (!menuOpen.value) return
  menuOpen.value = false
  document.body.style.overflow = ''
  if (lastFocused instanceof HTMLElement) lastFocused.focus()
}

function onKeydown(event) {
  if (event.key === 'Escape' && menuOpen.value) {
    closeMenu()
    return
  }

  if (event.key !== 'Tab' || !menuOpen.value) return

  const panel = document.querySelector('.drawer__panel')
  const focusables = panel
    ? [...panel.querySelectorAll('a[href], button:not([disabled])')].filter(
        (element) => element.offsetParent !== null,
      )
    : []

  if (!focusables.length) return

  const first = focusables[0]
  const last = focusables[focusables.length - 1]

  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => {
  updateScroll()
  window.addEventListener('scroll', updateScroll, { passive: true })
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateScroll)
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <header class="site-header" :class="{ 'is-scrolled': scrolled }">
    <div class="site-header__inner wrap">
      <a class="brand" href="/" aria-label="HrsBlog 首页">
        <BrandMark :size="30" />
        <span class="brand__word">Hrs<span>Blog</span></span>
      </a>

      <nav class="nav" aria-label="主导航">
        <a
          v-for="item in nav"
          :key="item.link"
          class="nav__link"
          :class="{ 'is-active': isActive(item.link) }"
          :href="withBase(item.link)"
        >
          {{ item.label }}
        </a>
      </nav>

      <div class="actions">
        <a
          class="icon-button icon-button--dark header-rss"
          href="/feed.xml"
          aria-label="RSS 订阅"
          title="RSS 订阅"
        >
          <AppIcon name="rss" :size="16" />
        </a>
        <ThemeToggle />
        <button
          ref="menuButton"
          class="icon-button icon-button--dark menu-toggle"
          type="button"
          aria-label="打开导航菜单"
          :aria-expanded="menuOpen"
          @click="openMenu"
        >
          <AppIcon name="menu" :size="18" />
        </button>
      </div>
    </div>
  </header>

  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="menuOpen" class="drawer" @click.self="closeMenu">
        <aside class="drawer__panel" role="dialog" aria-modal="true" aria-label="移动端导航">
          <div class="drawer__head">
            <span class="drawer__brand">
              <BrandMark :size="28" />
              <span>HrsBlog</span>
            </span>
            <button
              ref="closeButton"
              class="icon-button icon-button--dark"
              type="button"
              aria-label="关闭导航菜单"
              @click="closeMenu"
            >
              <AppIcon name="close" :size="18" />
            </button>
          </div>

          <nav class="drawer__nav" aria-label="移动端主导航">
            <a
              v-for="(item, index) in nav"
              :key="item.link"
              class="drawer__link"
              :class="{ 'is-active': isActive(item.link) }"
              :href="withBase(item.link)"
              @click="closeMenu"
            >
              <span class="drawer__index">0{{ index + 1 }}</span>
              <span>{{ item.label }}</span>
              <AppIcon name="arrow-up-right" :size="16" />
            </a>
          </nav>

          <div class="drawer__foot">
            <p class="drawer__note">{{ site.author?.tagline }}</p>
            <SocialLinks :links="site.social || []" variant="footer" />
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 60;
  height: var(--header-h);
  color: var(--on-dark);
  border-bottom: 1px solid rgb(244 240 230 / 10%);
  background: linear-gradient(180deg, rgb(11 21 16 / 62%) 0%, rgb(11 21 16 / 34%) 100%);
  backdrop-filter: blur(14px) saturate(1.08);
  -webkit-backdrop-filter: blur(14px) saturate(1.08);
  transition:
    background-color var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}

.site-header.is-scrolled {
  border-bottom-color: rgb(244 240 230 / 17%);
  background: rgb(10 19 14 / 88%);
  box-shadow: 0 12px 34px rgb(5 10 7 / 20%);
}

.site-header__inner {
  position: relative;
  display: flex;
  align-items: center;
  height: 100%;
  gap: 26px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  color: var(--on-dark);
}

.brand:hover {
  color: var(--on-dark);
}

.brand__word {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.01em;
  line-height: 1;
  white-space: nowrap;
}

.brand__word span {
  color: var(--on-dark-soft);
  font-weight: 400;
}

.nav {
  position: absolute;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 30px;
  transform: translateX(-50%);
}

.nav__link {
  position: relative;
  padding: 11px 1px;
  color: var(--on-dark-soft);
  font-size: 13px;
  letter-spacing: 0.08em;
  line-height: 1;
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease);
}

.nav__link::after {
  position: absolute;
  right: 0;
  bottom: 4px;
  left: 0;
  height: 1px;
  background: var(--copper-bright);
  content: '';
  transform: scaleX(0);
  transform-origin: left;
  transition: transform var(--dur) var(--ease);
}

.nav__link:hover,
.nav__link.is-active {
  color: var(--on-dark);
}

.nav__link.is-active::after {
  transform: scaleX(1);
}

.actions {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-left: auto;
}

.menu-toggle {
  display: none;
}

/* Drawer ------------------------------------------------------------------ */

.drawer {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  justify-content: flex-end;
  background: rgb(5 11 8 / 58%);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
}

.drawer__panel {
  display: flex;
  flex-direction: column;
  width: min(370px, 88vw);
  height: 100%;
  padding: 22px 26px 30px;
  color: var(--on-dark);
  background:
    radial-gradient(120% 70% at 100% 0%, rgb(217 154 108 / 14%) 0%, transparent 52%),
    linear-gradient(180deg, #14261d 0%, #0c1711 100%);
  border-left: 1px solid rgb(244 240 230 / 12%);
  box-shadow: -24px 0 70px rgb(0 0 0 / 36%);
}

.drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 26px;
  border-bottom: 1px solid rgb(244 240 230 / 12%);
}

.drawer__brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-display);
  font-size: 20px;
}

.drawer__nav {
  display: flex;
  flex-direction: column;
  padding: 26px 0 12px;
}

.drawer__link {
  display: grid;
  grid-template-columns: 34px 1fr 18px;
  align-items: center;
  gap: 12px;
  padding: 18px 0;
  color: var(--on-dark-soft);
  border-bottom: 1px solid rgb(244 240 230 / 9%);
  font-family: var(--font-display);
  font-size: 29px;
  font-weight: 400;
  line-height: 1.2;
  transition: color var(--dur-fast) var(--ease);
}

.drawer__link:hover,
.drawer__link.is-active {
  color: var(--on-dark);
}

.drawer__link.is-active .drawer__index,
.drawer__link:hover .drawer__index {
  color: var(--copper-bright);
}

.drawer__index {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  color: var(--on-dark-faint);
}

.drawer__foot {
  margin-top: auto;
  padding-top: 30px;
  border-top: 1px solid rgb(244 240 230 / 12%);
}

.drawer__note {
  max-width: 24em;
  margin-bottom: 20px;
  color: var(--on-dark-soft);
  font-size: 13px;
  line-height: 1.8;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity var(--dur) var(--ease);
}

.drawer-enter-active .drawer__panel,
.drawer-leave-active .drawer__panel {
  transition: transform 360ms var(--ease);
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .drawer__panel,
.drawer-leave-to .drawer__panel {
  transform: translateX(100%);
}

@media (max-width: 960px) {
  .nav {
    display: none;
  }

  .menu-toggle {
    display: inline-grid;
  }
}

@media (max-width: 720px) {
  .site-header__inner {
    gap: 16px;
  }

  .brand__word {
    font-size: 18px;
  }

  .header-rss {
    display: none;
  }

  .actions {
    gap: 7px;
  }
}
</style>

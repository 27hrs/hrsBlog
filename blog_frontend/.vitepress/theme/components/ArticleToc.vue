<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  headers: { type: Array, default: () => [] },
})

const active = ref('')
let ticking = false
let lockUntil = 0

function updateActive() {
  ticking = false

  if (typeof window === 'undefined' || Date.now() < lockUntil) return

  const offset = 120
  let current = props.headers[0]?.slug || ''

  for (const header of props.headers) {
    const element = document.getElementById(header.slug)
    if (!element) continue
    if (element.getBoundingClientRect().top <= offset) current = header.slug
  }

  active.value = current
}

function onScroll() {
  if (ticking) return
  ticking = true
  window.requestAnimationFrame(updateActive)
}

function onClick(header, event) {
  active.value = header.slug
  lockUntil = Date.now() + 900
  const details = event.currentTarget.closest('details')
  if (details) details.open = false
}

onMounted(async () => {
  await nextTick()
  updateActive()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
})

watch(
  () => props.headers,
  async () => {
    await nextTick()
    updateActive()
  },
  { deep: true },
)
</script>

<template>
  <nav class="toc" aria-label="本文目录">
    <ol class="toc__list">
      <li
        v-for="header in headers"
        :key="header.slug"
        class="toc__item"
        :class="[`toc__item--h${header.level}`, { 'is-active': active === header.slug }]"
      >
        <a
          class="toc__link"
          :href="`#${header.slug}`"
          :aria-current="active === header.slug ? 'location' : undefined"
          @click="onClick(header, $event)"
        >
          {{ header.title }}
        </a>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.toc__list {
  list-style: none;
}

.toc__item {
  position: relative;
  border-left: 1px solid var(--line);
}

.toc__link {
  display: block;
  padding: 7px 0 7px 14px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.45;
  transition: color var(--dur-fast) var(--ease);
}

.toc__item--h3 .toc__link {
  padding-left: 26px;
  font-size: 12px;
}

.toc__item.is-active {
  border-left-color: var(--copper);
}

.toc__item.is-active .toc__link {
  color: var(--copper);
}

.toc__link:hover {
  color: var(--ink);
}
</style>

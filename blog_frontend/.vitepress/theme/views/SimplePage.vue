<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'
import FramePanel from '../components/FramePanel.vue'
import AppIcon from '../components/AppIcon.vue'

const { frontmatter } = useData()
const title = computed(() => frontmatter.value.title || '页面')
const description = computed(() => frontmatter.value.description || '')
</script>

<template>
  <div class="simple">
    <div class="wrap">
      <FramePanel as="section" tone="paper" label="PAGE / IN PROGRESS" class="simple__panel">
        <p class="simple__eyebrow">HRSBLOG · FIELD NOTES</p>
        <h1 class="simple__title">{{ title }}</h1>
        <p v-if="description" class="simple__lead">{{ description }}</p>

        <div class="simple__content">
          <slot />
        </div>

        <a class="simple__back" href="/">
          <AppIcon name="arrow-right" :size="14" />
          <span>回到首页</span>
        </a>
      </FramePanel>
    </div>
  </div>
</template>

<style scoped>
.simple {
  padding: clamp(64px, 10vh, 120px) 0 clamp(80px, 12vh, 140px);
}

.simple__panel {
  width: min(820px, 100%);
  margin-inline: auto;
  padding: clamp(34px, 5vw, 56px);
}

.simple__eyebrow {
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.2em;
  line-height: 1;
}

.simple__title {
  margin-top: 18px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.simple__lead {
  max-width: 40em;
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.9;
}

.simple__content {
  margin-top: 30px;
  padding-top: 26px;
  border-top: 1px solid var(--line);
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.95;
}

.simple__content :deep(h2) {
  margin-top: 28px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 500;
  letter-spacing: -0.01em;
}

.simple__content :deep(p) {
  margin-top: 14px;
}

.simple__content :deep(ul),
.simple__content :deep(ol) {
  margin-top: 14px;
  padding-left: 1.35em;
}

.simple__content :deep(ul) {
  list-style: disc;
}

.simple__content :deep(ol) {
  list-style: decimal;
}

.simple__content :deep(li) {
  margin-top: 6px;
  padding-left: 0.15em;
}

.simple__content :deep(strong) {
  color: var(--ink);
  font-weight: 600;
}

.simple__content :deep(blockquote) {
  margin-top: 20px;
  padding: 14px 18px;
  border-left: 2px solid var(--copper);
  background: var(--copper-soft);
  color: var(--ink-soft);
}

.simple__content :deep(a) {
  color: var(--copper);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.simple__back {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-top: 34px;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  line-height: 1;
  text-transform: uppercase;
}

.simple__back:hover {
  color: var(--copper);
}
</style>

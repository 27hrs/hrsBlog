<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import FramePanel from '../components/FramePanel.vue'
import SocialLinks from '../components/SocialLinks.vue'
import AppIcon from '../components/AppIcon.vue'

const { theme } = useData()
const site = computed(() => theme.value.site || {})
const author = computed(() => site.value.author || {})
const stack = computed(() => site.value.stack || [])
const social = computed(() => site.value.social || [])

function isExternal(href) {
  return /^https?:\/\//i.test(href || '')
}
</script>

<template>
  <div class="about">
    <div class="wrap about__wrap">
      <FramePanel as="section" tone="glass" label="ABOUT / 01" class="about__profile">
        <p class="eyebrow about__eyebrow">
          <span>HRSBLOG</span>
          <span class="eyebrow__rule" aria-hidden="true" />
          <span>{{ author.location }}</span>
        </p>

        <div class="about__avatar">
          <img
            v-if="author.avatar"
            :src="author.avatar"
            :alt="`${author.name} 的头像`"
            width="112"
            height="112"
          />
          <span class="about__avatar-dot" aria-hidden="true" />
        </div>

        <h1 class="about__name">{{ author.name }}</h1>
        <p class="about__role">{{ author.role }}</p>
        <p class="about__intro">{{ author.bio || author.intro }}</p>

        <SocialLinks :links="social" variant="hero" />
      </FramePanel>

      <FramePanel as="section" tone="paper" label="02 / ABOUT ME" class="about__body">
        <div class="article-prose about-prose">
          <slot />
        </div>

        <section v-if="stack.length" class="about__section">
          <p class="eyebrow about__section-eyebrow">
            <span>技术栈 / 兴趣</span>
            <span class="eyebrow__rule" aria-hidden="true" />
          </p>
          <ul class="about__stack">
            <li v-for="item in stack" :key="item">{{ item }}</li>
          </ul>
        </section>

        <section class="about__section about__contact">
          <p class="eyebrow about__section-eyebrow">
            <span>联系我</span>
            <span class="eyebrow__rule" aria-hidden="true" />
          </p>
          <div class="about__contact-list">
            <a
              v-for="link in social"
              :key="link.href"
              class="about__contact-link"
              :href="link.href"
              :target="isExternal(link.href) ? '_blank' : undefined"
              :rel="isExternal(link.href) ? 'noopener noreferrer' : undefined"
            >
              <AppIcon :name="link.icon" :size="16" />
              <span>{{ link.label }}</span>
              <span class="about__contact-value">{{
                link.href.replace(/^mailto:/, '').replace(/^https?:\/\//, '')
              }}</span>
              <AppIcon name="arrow-up-right" :size="12" />
            </a>
          </div>
        </section>

        <a class="about__back" :href="withBase('/articles/')">
          <AppIcon name="arrow-right" :size="14" />
          <span>去看文章</span>
        </a>
      </FramePanel>
    </div>
  </div>
</template>

<style scoped>
.about {
  padding: clamp(36px, 6vh, 68px) 0 clamp(70px, 9vw, 110px);
}

.about__wrap {
  display: flex;
  max-width: 880px;
  flex-direction: column;
  gap: 24px;
}

.about__profile {
  position: relative;
  padding: clamp(34px, 5vw, 52px);
  text-align: center;
}

.about__eyebrow {
  justify-content: center;
  color: var(--copper-deep);
}

.about__avatar {
  position: relative;
  width: 112px;
  height: 112px;
  margin: 28px auto 0;
  padding: 4px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--paper-2);
}

.about__avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.about__avatar-dot {
  position: absolute;
  top: 7px;
  right: 1px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--copper);
  box-shadow: 0 0 0 4px var(--surface-solid);
}

.about__name {
  margin-top: 20px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.6rem, 5vw, 3.8rem);
  font-weight: 500;
  letter-spacing: -0.03em;
  line-height: 1;
}

.about__role {
  margin-top: 10px;
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
  line-height: 1.4;
  text-transform: uppercase;
}

.about__intro {
  max-width: 34em;
  margin: 22px auto 0;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.95;
  text-wrap: pretty;
}

.about__profile :deep(.socials) {
  justify-content: center;
  margin-top: 24px;
}

.about__body {
  padding: clamp(32px, 4.5vw, 54px);
}

.about-prose > :first-child {
  margin-top: 0;
}

.about-prose h2 {
  margin-top: 2.2em;
  padding-top: 0.6em;
  border-top: 1px solid var(--line);
}

.about-prose h2:first-of-type {
  margin-top: 1.8em;
}

.about-prose h3 {
  margin-top: 1.6em;
}

.about__section {
  margin-top: 40px;
  padding-top: 26px;
  border-top: 1px solid var(--line);
}

.about__section-eyebrow {
  color: var(--copper-deep);
}

.about__stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  list-style: none;
}

.about__stack li {
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
  line-height: 1;
}

.about__contact-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.about__contact-link {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 13px;
  border: 1px solid var(--line);
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.4;
  transition:
    color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease);
}

.about__contact-link:hover {
  color: var(--copper);
  border-color: var(--copper);
}

.about__contact-value {
  margin-left: auto;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.02em;
}

.about__contact-link :deep(.app-icon:last-child) {
  opacity: 0.4;
}

.about__back {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-top: 34px;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  line-height: 1;
  text-transform: uppercase;
}

.about__back:hover {
  color: var(--copper);
}

@media (max-width: 620px) {
  .about {
    padding-top: 22px;
  }

  .about__profile {
    padding: 30px 22px;
  }

  .about__avatar {
    width: 92px;
    height: 92px;
  }

  .about__intro {
    font-size: 14px;
  }

  .about__body {
    padding: 28px 20px;
  }

  .about__contact-list {
    grid-template-columns: 1fr;
  }

  .about__contact-value {
    display: none;
  }
}
</style>

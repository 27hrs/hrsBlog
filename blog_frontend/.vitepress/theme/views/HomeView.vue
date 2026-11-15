<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import ArticleCard from '../components/ArticleCard.vue'
import FramePanel from '../components/FramePanel.vue'
import SocialLinks from '../components/SocialLinks.vue'
import AppIcon from '../components/AppIcon.vue'
import { useArticles } from '../composables/useArticles.js'

const { theme } = useData()
const site = computed(() => theme.value.site || {})
const { latest } = useArticles()
const articles = latest
const author = computed(() => site.value.author || {})
</script>

<template>
  <div class="home">
    <section class="hero" aria-labelledby="hero-title">
      <div class="wrap hero__wrap">
        <div class="hero__stage">
          <FramePanel as="section" tone="glass" label="01 / FIELD NOTES" class="hero__panel">
            <p class="eyebrow hero__eyebrow">
              <span>{{ author.location }}</span>
              <span class="eyebrow__rule" aria-hidden="true" />
              <span>A PERSONAL JOURNAL · 2026</span>
            </p>

            <div class="hero__identity">
              <div class="hero__avatar">
                <img
                  :src="author.avatar"
                  :alt="`${author.name} 的头像`"
                  width="84"
                  height="84"
                  decoding="async"
                />
                <span class="hero__avatar-dot" aria-hidden="true" />
              </div>

              <div class="hero__copy">
                <h1 id="hero-title" class="hero__title">
                  你好，我是 <em>{{ author.name }}</em><span class="hero__period">。</span>
                </h1>
                <p class="hero__tagline">{{ author.tagline }}</p>
              </div>
            </div>

            <p class="hero__intro">{{ author.intro }}</p>

            <div class="hero__footer">
              <SocialLinks :links="site.social || []" variant="hero" />
              <span class="hero__role">{{ author.role }}</span>
            </div>
          </FramePanel>

          <div class="hero__aside" aria-hidden="true">
            <span class="hero__aside-line" />
            <span>LAGO DI BRAIES</span>
            <span>46°41′N · 12°05′E</span>
            <span>FIELD NOTES / 2026</span>
          </div>
        </div>

        <a class="hero__scroll" href="#latest">
          <span>向下阅读</span>
          <span class="hero__scroll-line" aria-hidden="true" />
          <AppIcon name="arrow-down" :size="13" />
        </a>
      </div>
    </section>

    <section id="latest" class="latest" aria-labelledby="latest-title">
      <div class="wrap">
        <FramePanel as="section" tone="paper" label="02 / LATEST NOTES" class="latest__panel">
          <header class="latest__head">
            <div>
              <p class="eyebrow latest__eyebrow">
                <span>RECENT WRITING</span>
                <span class="eyebrow__rule" aria-hidden="true" />
              </p>
              <h2 id="latest-title" class="latest__title">最新文章</h2>
            </div>
            <p class="latest__count">
              <span>{{ String(articles.length).padStart(2, '0') }}</span>
              <span>篇 / 按时间排序</span>
            </p>
          </header>

          <div v-if="articles.length" class="latest__list">
            <ArticleCard
              v-for="(article, index) in articles"
              :key="article.slug"
              :article="article"
              :index="index + 1"
            />
          </div>

          <div v-else class="latest__empty">
            <p>还没有发布文章。去湖边走走，回来再写。</p>
            <a :href="withBase('/about')">先认识一下这里 →</a>
          </div>

          <a class="latest__all" :href="withBase('/articles/')">
            <span>读全部文章</span>
            <span class="latest__all-line" aria-hidden="true" />
            <AppIcon name="arrow-right" :size="16" />
          </a>
        </FramePanel>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  padding-bottom: clamp(40px, 5vw, 72px);
}

/* Hero -------------------------------------------------------------------- */

.hero {
  display: flex;
  align-items: center;
  min-height: min(78svh, 820px);
  padding: clamp(40px, 7vh, 76px) 0 116px;
}

.hero__wrap {
  position: relative;
}

.hero__stage {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 42px;
}

.hero__panel {
  width: min(720px, 100%);
  min-width: 0;
  padding: 42px 44px 38px;
  animation: panel-rise 900ms var(--ease) both;
}

.hero__eyebrow {
  color: var(--copper-deep);
}

.hero__identity {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 32px;
}

.hero__avatar {
  position: relative;
  flex: none;
  width: 84px;
  height: 84px;
  padding: 3px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--paper-2);
}

.hero__avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.hero__avatar-dot {
  position: absolute;
  top: 5px;
  right: 1px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--copper);
  box-shadow: 0 0 0 4px var(--surface-solid);
}

.hero__copy {
  min-width: 0;
}

.hero__title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.5rem, 4.2vw, 3.9rem);
  font-weight: 500;
  letter-spacing: -0.025em;
  line-height: 1.08;
  overflow-wrap: break-word;
  text-wrap: pretty;
}

.hero__title em {
  color: var(--ink);
  font-style: italic;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.hero__period {
  color: var(--copper);
}

.hero__tagline {
  margin-top: 13px;
  color: var(--ink-soft);
  font-size: 15px;
  letter-spacing: 0.02em;
  line-height: 1.7;
}

.hero__intro {
  max-width: 36em;
  margin-top: 28px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.95;
  text-wrap: pretty;
}

.hero__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 27px;
  padding-top: 21px;
  border-top: 1px solid var(--line);
}

.hero__role {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.11em;
  line-height: 1.4;
  text-transform: uppercase;
}

.hero__aside {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-top: 22px;
  color: rgb(244 240 230 / 60%);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.2em;
  line-height: 1;
  text-shadow: 0 1px 12px rgb(5 12 8 / 45%);
  writing-mode: vertical-rl;
}

.hero__aside-line {
  width: 1px;
  height: 46px;
  background: rgb(244 240 230 / 38%);
}

.hero__scroll {
  position: absolute;
  bottom: -74px;
  left: 0;
  display: inline-flex;
  align-items: center;
  gap: 11px;
  padding: 8px 11px;
  color: var(--ink-soft);
  border: 1px solid rgb(255 255 255 / 30%);
  background: rgb(246 242 232 / 66%);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.16em;
  line-height: 1;
  text-transform: uppercase;
  transition:
    color var(--dur-fast) var(--ease),
    background-color var(--dur-fast) var(--ease);
}

.hero__scroll:hover {
  color: var(--copper-deep);
  background: rgb(246 242 232 / 88%);
}

html[data-theme='dark'] .hero__scroll {
  background: rgb(20 27 22 / 72%);
}

html[data-theme='dark'] .hero__scroll:hover {
  background: rgb(20 27 22 / 92%);
}

.hero__scroll-line {
  width: 32px;
  height: 1px;
  background: currentColor;
  opacity: 0.55;
  transform-origin: left;
  transition: transform var(--dur) var(--ease);
}

.hero__scroll:hover .hero__scroll-line {
  transform: scaleX(1.25);
}

/* Latest ------------------------------------------------------------------ */

.latest {
  position: relative;
  margin-top: -74px;
  scroll-margin-top: calc(var(--header-h) + 24px);
}

.latest__panel {
  min-width: 0;
  padding: 44px 46px 0;
  background-image: radial-gradient(115% 90% at 12% 0%, rgb(255 255 255 / 30%) 0%, transparent 58%);
  animation: panel-rise 1000ms 120ms var(--ease) both;
}

.latest__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 26px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--line);
}

.latest__eyebrow {
  color: var(--copper-deep);
}

.latest__title {
  margin-top: 13px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2rem, 3vw, 2.75rem);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.latest__count {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-bottom: 5px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.09em;
  line-height: 1.4;
  white-space: nowrap;
}

.latest__count span:first-child {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 22px;
  font-style: italic;
  letter-spacing: -0.02em;
}

.latest__list {
  display: flex;
  flex-direction: column;
}

.latest__empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  padding: 44px 0;
  border-top: 1px solid var(--line);
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.9;
}

.latest__empty a {
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.latest__all {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 25px 0 27px;
  border-top: 1px solid var(--line);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  line-height: 1;
  text-transform: uppercase;
  transition: color var(--dur-fast) var(--ease);
}

.latest__all-line {
  flex: 1;
  height: 1px;
  background: var(--line);
  transition: background-color var(--dur) var(--ease);
}

.latest__all:hover {
  color: var(--copper);
}

.latest__all:hover .latest__all-line {
  background: var(--copper);
}

@keyframes panel-rise {
  from {
    transform: translateY(18px);
  }

  to {
    transform: translateY(0);
  }
}

@media (max-width: 1180px) {
  .hero__aside {
    display: none;
  }

  .hero__panel {
    width: min(760px, 100%);
  }
}

@media (max-width: 860px) {
  .hero {
    min-height: 0;
    padding: 30px 0 104px;
  }

  .hero__panel {
    padding: 34px 30px 30px;
  }

  .hero__identity {
    gap: 18px;
    margin-top: 26px;
  }

  .hero__avatar {
    width: 72px;
    height: 72px;
  }

  .hero__title {
    font-size: clamp(2.2rem, 6.7vw, 3.2rem);
  }

  .hero__intro {
    margin-top: 22px;
    font-size: 14.5px;
  }

  .hero__footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .hero__scroll {
    display: none;
  }

  .latest {
    margin-top: -54px;
  }

  .latest__panel {
    padding: 38px 30px 0;
  }

  .latest__head {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .latest__count {
    padding-bottom: 0;
  }
}

@media (max-width: 620px) {
  .hero {
    padding-top: 20px;
    padding-bottom: 84px;
  }

  .hero__panel {
    padding: 30px 22px 26px;
  }

  .hero__eyebrow {
    gap: 8px;
    font-size: 8px;
    letter-spacing: 0.13em;
  }

  .hero__eyebrow .eyebrow__rule {
    width: 20px;
  }

  .hero__identity {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }

  .hero__copy {
    width: 100%;
  }

  .hero__avatar {
    width: 56px;
    height: 56px;
  }

  .hero__avatar-dot {
    width: 7px;
    height: 7px;
    box-shadow: 0 0 0 3px var(--surface-solid);
  }

  .hero__title {
    font-size: clamp(2rem, 8.4vw, 2.7rem);
    line-height: 1.12;
  }

  .hero__tagline {
    margin-top: 9px;
    font-size: 13px;
    line-height: 1.65;
  }

  .hero__intro {
    font-size: 13.5px;
    line-height: 1.9;
  }

  .hero__scroll {
    display: none;
  }

  .latest {
    margin-top: -40px;
  }

  .latest__panel {
    padding: 32px 20px 0;
  }

  .latest__title {
    font-size: 2rem;
  }

  .latest__all {
    padding: 22px 0 24px;
  }
}

@media (max-width: 360px) {
  .hero__panel {
    padding-inline: 18px;
  }

  .hero__title {
    font-size: clamp(1.9rem, 8.2vw, 2.4rem);
  }

  .hero__intro {
    font-size: 13px;
  }
}
</style>

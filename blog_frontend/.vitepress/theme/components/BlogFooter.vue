<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import AppIcon from './AppIcon.vue'
import BrandMark from './BrandMark.vue'
import SocialLinks from './SocialLinks.vue'
import { useArticles } from '../composables/useArticles.js'

const { theme } = useData()
const { generatedAt } = useArticles()
const site = computed(() => theme.value.site || {})
const year = new Date().getFullYear()
const startYear = computed(() => site.value.footer?.startYear || year)
const copyright = computed(() =>
  startYear.value === year ? `© ${year}` : `© ${startYear.value}–${year}`,
)
const syncedAt = computed(() => {
  if (!generatedAt) return site.value.footer?.syncedAt || ''
  return String(generatedAt).slice(0, 10).replaceAll('-', '.')
})
</script>

<template>
  <footer class="site-footer">
    <div class="site-footer__inner wrap">
      <div class="footer-grid">
        <div class="footer-brand">
          <a class="footer-brand__mark" href="/" aria-label="HrsBlog 首页">
            <BrandMark :size="38" />
            <span class="footer-brand__name">Hrs<span>Blog</span></span>
          </a>
          <p class="footer-brand__tagline">{{ site.tagline }}</p>
          <p class="footer-brand__intro">
            {{ site.description }}
          </p>
        </div>

        <div class="footer-col">
          <p class="footer-col__title">SITEMAP</p>
          <nav class="footer-nav" aria-label="页脚导航">
            <a
              v-for="item in site.nav || []"
              :key="item.link"
              :href="withBase(item.link)"
              class="footer-nav__link"
            >
              {{ item.label }}
              <AppIcon name="arrow-up-right" :size="11" />
            </a>
          </nav>
        </div>

        <div class="footer-col">
          <p class="footer-col__title">ELSEWHERE</p>
          <SocialLinks :links="site.social || []" variant="footer" />
        </div>
      </div>

      <div class="footer-bottom">
        <p class="footer-bottom__left">
          <span>{{ copyright }} Hrs</span>
          <span class="footer-bottom__dot" aria-hidden="true" />
          <span>{{ site.footer?.note }}</span>
        </p>
        <p class="footer-bottom__right">
          <span class="footer-bottom__sync">最近同步 {{ syncedAt }}</span>
          <a
            v-if="site.footer?.icp"
            class="footer-bottom__icp"
            :href="site.footer.icpUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ site.footer.icp }}
          </a>
        </p>
      </div>
    </div>

    <span class="site-footer__watermark" aria-hidden="true">HrsBlog</span>
  </footer>
</template>

<style scoped>
.site-footer {
  position: relative;
  z-index: 2;
  overflow: hidden;
  margin-top: clamp(72px, 9vw, 112px);
  color: var(--on-dark-soft);
  border-top: 1px solid rgb(217 154 108 / 30%);
  background:
    radial-gradient(90% 130% at 100% 0%, rgb(217 154 108 / 9%) 0%, transparent 48%),
    linear-gradient(180deg, rgb(13 25 18 / 92%) 0%, rgb(7 15 10 / 98%) 100%);
}

.site-footer__inner {
  position: relative;
  z-index: 1;
  padding-top: clamp(44px, 6vw, 66px);
  padding-bottom: 26px;
}

.footer-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(140px, 0.65fr) minmax(180px, 0.8fr);
  gap: clamp(32px, 5vw, 80px);
}

.footer-brand__mark {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--on-dark);
}

.footer-brand__mark:hover {
  color: var(--on-dark);
}

.footer-brand__name {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.footer-brand__name span {
  color: var(--on-dark-soft);
  font-weight: 400;
}

.footer-brand__tagline {
  margin-top: 18px;
  color: var(--on-dark);
  font-family: var(--font-display);
  font-size: 17px;
  letter-spacing: 0.02em;
}

.footer-brand__intro {
  max-width: 34em;
  margin-top: 10px;
  color: var(--on-dark-soft);
  font-size: 13px;
  line-height: 1.9;
}

.footer-col__title {
  margin-bottom: 15px;
  color: var(--copper-bright);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.2em;
  line-height: 1;
}

.footer-nav {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.footer-nav__link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 0;
  color: var(--on-dark-soft);
  font-size: 13px;
  line-height: 1.5;
  transition: color var(--dur-fast) var(--ease);
}

.footer-nav__link :deep(.app-icon) {
  opacity: 0;
  transform: translate(-3px, 3px);
  transition:
    opacity var(--dur-fast) var(--ease),
    transform var(--dur-fast) var(--ease);
}

.footer-nav__link:hover {
  color: var(--on-dark);
}

.footer-nav__link:hover :deep(.app-icon) {
  opacity: 0.7;
  transform: translate(0, 0);
}

.footer-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: clamp(36px, 5vw, 58px);
  padding-top: 18px;
  border-top: 1px solid rgb(244 240 230 / 11%);
  color: rgb(244 240 230 / 52%);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.07em;
  line-height: 1.9;
}

.footer-bottom__left,
.footer-bottom__right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.footer-bottom__dot {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
}

.footer-bottom__icp {
  color: rgb(244 240 230 / 52%);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.footer-bottom__icp:hover {
  color: var(--on-dark);
}

.site-footer__watermark {
  position: absolute;
  right: -0.05em;
  bottom: -0.28em;
  z-index: 0;
  color: rgb(244 240 230 / 3.4%);
  font-family: var(--font-display);
  font-size: clamp(110px, 17vw, 230px);
  font-weight: 500;
  letter-spacing: -0.04em;
  line-height: 1;
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .footer-grid {
    grid-template-columns: 1fr 1fr;
  }

  .footer-brand {
    grid-column: 1 / -1;
  }
}

@media (max-width: 620px) {
  .footer-grid {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .footer-bottom {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .footer-bottom__right {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>

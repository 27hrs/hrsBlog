<script setup>
import { computed } from 'vue'
import FramePanel from '../components/FramePanel.vue'
import FriendLinkCard from '../components/FriendLinkCard.vue'
import FriendLinkApplyForm from '../components/FriendLinkApplyForm.vue'
import AppIcon from '../components/AppIcon.vue'
import { useFriendLinks } from '../composables/useFriendLinks.js'

const { items, count, generatedAt } = useFriendLinks()

const syncLabel = computed(() => {
  if (!generatedAt) return ''
  return String(generatedAt).slice(0, 10).replaceAll('-', '.')
})
</script>

<template>
  <div class="links">
    <div class="wrap">
      <FramePanel as="section" tone="glass" label="03 / FRIENDS" class="links__head">
        <div class="links__head-row">
          <div class="links__head-copy">
            <p class="eyebrow links__eyebrow">
              <span>LINKS &amp; NEIGHBOURS</span>
              <span class="eyebrow__rule" aria-hidden="true" />
              <span>互相串门</span>
            </p>
            <h1 class="links__title">友情链接</h1>
            <p class="links__lead">
              路过的地方总要留一盏灯。这里放着我常读的博客与站点，也欢迎你来敲门。
            </p>
          </div>

          <div class="links__head-meta">
            <p class="links__count">
              <span>{{ String(count).padStart(2, '0') }}</span>
              <span>个站点</span>
            </p>
            <p v-if="syncLabel" class="links__sync">最近同步 {{ syncLabel }}</p>
          </div>
        </div>
      </FramePanel>

      <FramePanel
        v-if="items.length"
        as="section"
        tone="paper"
        label="04 / APPROVED"
        class="links__panel"
      >
        <div class="links__grid">
          <FriendLinkCard
            v-for="(item, index) in items"
            :key="item.siteUrl"
            :item="item"
            :index="index"
          />
        </div>
      </FramePanel>

      <FramePanel
        v-else
        as="section"
        tone="paper"
        label="04 / APPROVED"
        class="links__panel links__panel--empty"
      >
        <div class="links__empty">
          <p class="links__empty-title">还没有人留下门牌。</p>
          <p class="links__empty-text">第一个友链，也许就是你的站点。</p>
          <a class="links__empty-cta" href="#friend-links-apply">
            去申请第一条友链
            <AppIcon name="arrow-down" :size="13" />
          </a>
        </div>
      </FramePanel>

      <FramePanel
        as="section"
        tone="panel"
        label="05 / APPLY"
        id="friend-links-apply"
        class="links__panel links__apply"
      >
        <div class="links__apply-head">
          <h2 class="links__apply-title">申请友链</h2>
          <p class="links__apply-lead">
            填好下面的表单，提交后我会人工审核。联系方式只有我自己看得到，不会出现在页面上。
          </p>
        </div>

        <FriendLinkApplyForm />
      </FramePanel>
    </div>
  </div>
</template>

<style scoped>
.links {
  padding: clamp(30px, 5vh, 64px) 0 clamp(70px, 9vw, 110px);
}

.links__head {
  padding: clamp(30px, 4vw, 46px);
  margin-bottom: 24px;
}

.links__head-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 36px;
}

.links__head-copy {
  min-width: 0;
}

.links__eyebrow {
  color: var(--copper-deep);
}

.links__title {
  margin-top: 18px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 5vw, 4.2rem);
  font-weight: 500;
  letter-spacing: -0.035em;
  line-height: 1;
}

.links__lead {
  max-width: 38em;
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.9;
  text-wrap: pretty;
}

.links__head-meta {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  padding-bottom: 4px;
}

.links__count {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  line-height: 1.4;
  text-transform: uppercase;
}

.links__count span:first-child {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 26px;
  font-style: italic;
  letter-spacing: -0.02em;
}

.links__sync {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.links__panel {
  padding: clamp(24px, 3vw, 34px);
  margin-bottom: 24px;
}

.links__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.links__empty {
  padding: 40px 0 26px;
  color: var(--ink-soft);
  font-size: 14.5px;
  line-height: 1.9;
  text-wrap: pretty;
}

.links__empty-title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 500;
  letter-spacing: -0.01em;
}

.links__empty-text {
  margin-top: 8px;
}

.links__empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-top: 20px;
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  line-height: 1;
  text-transform: uppercase;
  transition: color var(--dur-fast) var(--ease);
}

.links__empty-cta:hover {
  color: var(--copper-deep);
}

.links__apply {
  scroll-margin-top: calc(var(--header-h) + 24px);
}

.links__apply-head {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line);
}

.links__apply-title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 2.4vw, 2.1rem);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.links__apply-lead {
  max-width: 46em;
  margin-top: 10px;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.85;
}

@media (max-width: 1024px) {
  .links__head-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 20px;
  }

  .links__head-meta {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    padding-bottom: 0;
  }
}

@media (max-width: 720px) {
  .links {
    padding-top: 20px;
  }

  .links__grid {
    grid-template-columns: 1fr;
  }

  .links__head {
    padding: 28px 22px;
    margin-bottom: 18px;
  }

  .links__title {
    margin-top: 14px;
    font-size: clamp(2.6rem, 13vw, 3.4rem);
  }

  .links__lead {
    margin-top: 12px;
    font-size: 14px;
  }

  .links__head-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .links__panel {
    padding: 22px 18px;
    margin-bottom: 18px;
  }
}
</style>

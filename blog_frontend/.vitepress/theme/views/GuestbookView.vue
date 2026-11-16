<script setup>
import { computed, onMounted } from 'vue'
import FramePanel from '../components/FramePanel.vue'
import GuestMessageCard from '../components/GuestMessageCard.vue'
import GuestMessageForm from '../components/GuestMessageForm.vue'
import AppIcon from '../components/AppIcon.vue'
import { useGuestbook } from '../composables/useGuestbook.js'

const { enabled, pageSize, items, page, totalElements, totalPages, loading, error, load } =
  useGuestbook()

const countLabel = computed(() => String(totalElements.value).padStart(2, '0'))

const hasMessages = computed(() => items.value.length > 0)

/** 分页按钮：最多显示 5 个页码（含首尾）。 */
const pageButtons = computed(() => {
  const total = totalPages.value
  if (total <= 1) return []

  const current = page.value
  const start = Math.max(0, Math.min(current - 2, total - 5))
  const end = Math.min(total, start + 5)

  const buttons = []
  for (let index = start; index < end; index += 1) {
    buttons.push(index)
  }
  return buttons
})

const canPrev = computed(() => page.value > 0)
const canNext = computed(() => page.value + 1 < totalPages.value)

function goTo(target) {
  if (target < 0 || target >= totalPages.value) return
  load(target)
}

onMounted(() => {
  if (enabled.value) load(0)
})
</script>

<template>
  <div class="guestbook">
    <div class="wrap">
      <FramePanel as="section" tone="glass" label="03 / GUESTBOOK" class="guestbook__head">
        <div class="guestbook__head-row">
          <div class="guestbook__head-copy">
            <p class="eyebrow guestbook__eyebrow">
              <span>GUESTBOOK &amp; NOTES</span>
              <span class="eyebrow__rule" aria-hidden="true" />
              <span>路过留声</span>
            </p>
            <h1 class="guestbook__title">留言</h1>
            <p class="guestbook__lead">路过就留一句话。</p>
          </div>

          <div class="guestbook__head-meta">
            <p class="guestbook__count">
              <span>{{ countLabel }}</span>
              <span>条留言</span>
            </p>
            <p class="guestbook__page-size">每页 {{ pageSize }} 条</p>
          </div>
        </div>
      </FramePanel>

      <FramePanel
        v-if="!enabled"
        as="section"
        tone="paper"
        label="04 / APPROVED"
        class="guestbook__panel guestbook__panel--empty"
      >
        <div class="guestbook__empty">
          <p class="guestbook__empty-title">留言板暂时没有开放。</p>
          <p class="guestbook__empty-text">等它重新亮起灯，你再来说点什么。</p>
        </div>
      </FramePanel>

      <FramePanel
        v-else-if="error"
        as="section"
        tone="paper"
        label="04 / APPROVED"
        class="guestbook__panel guestbook__panel--empty"
      >
        <div class="guestbook__empty">
          <p class="guestbook__empty-title">留言暂时加载不出来。</p>
          <p class="guestbook__empty-text">{{ error }}</p>
          <button class="guestbook__empty-cta" type="button" @click="load(page)">
            重新加载
            <AppIcon name="refresh" :size="13" />
          </button>
        </div>
      </FramePanel>

      <FramePanel
        v-else-if="loading && !hasMessages"
        as="section"
        tone="paper"
        label="04 / APPROVED"
        class="guestbook__panel guestbook__panel--empty"
      >
        <div class="guestbook__empty">
          <p class="guestbook__empty-title">正在读取留言…</p>
          <p class="guestbook__empty-text">稍等片刻。</p>
        </div>
      </FramePanel>

      <FramePanel
        v-else-if="hasMessages"
        as="section"
        tone="paper"
        label="04 / APPROVED"
        class="guestbook__panel"
      >
        <div class="guestbook__list">
          <GuestMessageCard
            v-for="(message, index) in items"
            :key="message.id ?? index"
            :message="message"
            :index="index"
          />
        </div>

        <nav v-if="totalPages > 1" class="guestbook__pagination" aria-label="留言分页">
          <button
            class="guestbook__page-button"
            type="button"
            :disabled="!canPrev"
            aria-label="上一页"
            @click="goTo(page - 1)"
          >
            <AppIcon name="arrow-right" :size="14" class="guestbook__page-prev" />
          </button>

          <button
            v-for="button in pageButtons"
            :key="button"
            class="guestbook__page-button guestbook__page-button--number"
            :class="{ 'is-active': button === page }"
            type="button"
            :aria-current="button === page ? 'page' : undefined"
            @click="goTo(button)"
          >
            {{ button + 1 }}
          </button>

          <button
            class="guestbook__page-button"
            type="button"
            :disabled="!canNext"
            aria-label="下一页"
            @click="goTo(page + 1)"
          >
            <AppIcon name="arrow-right" :size="14" />
          </button>
        </nav>
      </FramePanel>

      <FramePanel
        v-else
        as="section"
        tone="paper"
        label="04 / APPROVED"
        class="guestbook__panel guestbook__panel--empty"
      >
        <div class="guestbook__empty">
          <p class="guestbook__empty-title">还没有留言，去敲第一扇门。</p>
          <p class="guestbook__empty-text">第一条留言也许就是你的。</p>
          <a class="guestbook__empty-cta" href="#guestbook-write">
            去写第一条
            <AppIcon name="arrow-down" :size="13" />
          </a>
        </div>
      </FramePanel>

      <FramePanel
        v-if="enabled"
        as="section"
        tone="panel"
        label="05 / WRITE"
        id="guestbook-write"
        class="guestbook__panel guestbook__write"
      >
        <div class="guestbook__write-head">
          <h2 class="guestbook__write-title">留下留言</h2>
          <p class="guestbook__write-lead">
            填写你的留言。
          </p>
        </div>

        <GuestMessageForm />
      </FramePanel>
    </div>
  </div>
</template>

<style scoped>
.guestbook {
  padding: clamp(30px, 5vh, 64px) 0 clamp(70px, 9vw, 110px);
}

.guestbook__head {
  padding: clamp(30px, 4vw, 46px);
  margin-bottom: 24px;
}

.guestbook__head-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 36px;
}

.guestbook__eyebrow {
  color: var(--copper-deep);
}

.guestbook__title {
  margin-top: 18px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 5vw, 4.2rem);
  font-weight: 500;
  letter-spacing: -0.035em;
  line-height: 1;
}

.guestbook__lead {
  max-width: 38em;
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.9;
  text-wrap: pretty;
}

.guestbook__head-meta {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  padding-bottom: 4px;
}

.guestbook__count {
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

.guestbook__count span:first-child {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 26px;
  font-style: italic;
  letter-spacing: -0.02em;
}

.guestbook__page-size {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.guestbook__panel {
  padding: clamp(24px, 3vw, 34px);
  margin-bottom: 24px;
}

.guestbook__list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.guestbook__pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  justify-content: center;
  margin-top: 26px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.guestbook__page-button {
  display: grid;
  place-items: center;
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease),
    background-color var(--dur-fast) var(--ease);
}

.guestbook__page-button:hover:not(:disabled) {
  border-color: var(--copper);
  color: var(--copper-deep);
}

.guestbook__page-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.guestbook__page-button--number.is-active {
  border-color: var(--copper);
  background: var(--copper);
  color: var(--on-dark);
}

.guestbook__page-prev {
  transform: rotate(180deg);
}

.guestbook__write {
  scroll-margin-top: calc(var(--header-h) + 24px);
}

.guestbook__write-head {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line);
}

.guestbook__write-title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 2.4vw, 2.1rem);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.guestbook__write-lead {
  max-width: 46em;
  margin-top: 10px;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.85;
}

.guestbook__panel--empty {
  padding: clamp(34px, 5vw, 58px);
}

.guestbook__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.guestbook__empty-title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(1.2rem, 2vw, 1.5rem);
  letter-spacing: -0.02em;
}

.guestbook__empty-text {
  max-width: 34em;
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.8;
}

.guestbook__empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  padding: 9px 16px;
  border: 1px solid var(--copper);
  border-radius: var(--radius);
  background: transparent;
  color: var(--copper-deep);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  line-height: 1;
  text-transform: uppercase;
  cursor: pointer;
  transition:
    background-color var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.guestbook__empty-cta:hover {
  background: var(--copper);
  color: var(--on-dark);
}

@media (max-width: 1024px) {
  .guestbook__head-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }

  .guestbook__head-meta {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    padding-bottom: 0;
  }
}

@media (max-width: 720px) {
  .guestbook {
    padding-top: 20px;
  }

  .guestbook__head {
    padding: 28px 22px;
    margin-bottom: 18px;
  }

  .guestbook__title {
    margin-top: 14px;
    font-size: clamp(2.6rem, 13vw, 3.4rem);
  }

  .guestbook__lead {
    margin-top: 12px;
    font-size: 14px;
  }

  .guestbook__head-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .guestbook__panel {
    padding: 22px 18px;
    margin-bottom: 18px;
  }
}
</style>

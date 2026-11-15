<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useData, withBase } from 'vitepress'
import { useArticles } from '../composables/useArticles.js'
import FramePanel from '../components/FramePanel.vue'
import ArticleToc from '../components/ArticleToc.vue'
import ReadingProgress from '../components/ReadingProgress.vue'
import AppIcon from '../components/AppIcon.vue'

const { frontmatter, page, theme } = useData()
const { items, getArticle, getAdjacent } = useArticles()

const site = computed(() => theme.value.site || {})
const author = computed(() => site.value.author || {})
const article = computed(() => getArticle(frontmatter.value.slug))
const adjacent = computed(() => getAdjacent(frontmatter.value.slug))

function flattenHeaders(headers) {
  return headers.flatMap((header) => [
    header,
    ...flattenHeaders(header.children || []),
  ])
}

const headers = computed(() =>
  flattenHeaders(page.value.headers || []).filter(
    (header) => header.level === 2 || header.level === 3,
  ),
)

const articleNumber = computed(() => {
  const index = items.findIndex((item) => item.slug === frontmatter.value.slug)
  return index >= 0 ? String(index + 1).padStart(2, '0') : '00'
})

const dateLabel = computed(() => {
  if (article.value?.dateLabel) return article.value.dateLabel
  return String(frontmatter.value.date || '').slice(0, 10).replaceAll('-', '.')
})

const readingTime = computed(() => frontmatter.value.readingTime || article.value?.readingTime || 1)
const tags = computed(() => article.value?.tags || frontmatter.value.tags || [])
const cover = computed(() => frontmatter.value.cover || article.value?.cover || '')
const coverAlt = computed(
  () => frontmatter.value.coverAlt || article.value?.coverAlt || `${frontmatter.value.title} 的封面`,
)
const summary = computed(() => frontmatter.value.summary || '')

const copied = ref(false)
const proseRef = ref(null)
let copyTimer = null

async function writeClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
  }
}

async function copyLink() {
  if (typeof window === 'undefined') return

  await writeClipboard(window.location.href)
  copied.value = true
  clearTimeout(copyTimer)
  copyTimer = setTimeout(() => {
    copied.value = false
  }, 2200)
}

async function onProseClick(event) {
  const button = event.target.closest('button.copy')
  if (!button) return

  const wrapper = button.closest('div[class*="language-"]')
  const pre = wrapper?.querySelector('pre')
  if (!pre) return

  await writeClipboard(pre.innerText)
  button.dataset.copied = 'true'
  setTimeout(() => {
    delete button.dataset.copied
  }, 1600)
}

onMounted(async () => {
  await nextTick()
  if (!proseRef.value) return

  proseRef.value.addEventListener('click', onProseClick)
  proseRef.value.querySelectorAll('button.copy').forEach((button) => {
    button.setAttribute('aria-label', '复制代码')
  })
})

onBeforeUnmount(() => {
  clearTimeout(copyTimer)
  proseRef.value?.removeEventListener('click', onProseClick)
})
</script>

<template>
  <div class="article-page">
    <ReadingProgress />

    <div class="wrap article-page__grid">
      <div class="article-main">
        <FramePanel
          as="article"
          tone="paper"
          :label="`ARTICLE / ${articleNumber}`"
          class="article-sheet"
        >
          <header class="article-head">
            <p class="eyebrow article__eyebrow">
              <span>{{ dateLabel }}</span>
              <span class="eyebrow__rule" aria-hidden="true" />
              <span>约 {{ readingTime }} 分钟</span>
            </p>

            <h1 class="article__title">{{ frontmatter.title }}</h1>
            <p v-if="summary" class="article__lead">{{ summary }}</p>

            <div class="article__byline">
              <img
                v-if="author.avatar"
                class="article__avatar"
                :src="author.avatar"
                :alt="`${author.name} 的头像`"
                width="28"
                height="28"
              />
              <span class="article__author">{{ author.name }}</span>
              <span class="article__byline-dot" aria-hidden="true" />
              <time :datetime="frontmatter.publishedAt">{{ dateLabel }}</time>
              <span class="article__byline-dot" aria-hidden="true" />
              <span>{{ article?.categoryLabel || '文章' }}</span>
            </div>
          </header>

          <figure v-if="cover" class="article__cover">
            <img
              :src="cover"
              :alt="coverAlt"
              width="1200"
              height="675"
              loading="eager"
              decoding="async"
            />
          </figure>

          <details v-if="headers.length" class="article__toc-mobile">
            <summary>
              本文目录
              <span>{{ String(headers.length).padStart(2, '0') }} 节</span>
            </summary>
            <ArticleToc :headers="headers" />
          </details>

          <div ref="proseRef" class="article-prose">
            <slot />
          </div>

          <footer class="article-foot">
            <div v-if="tags.length" class="article__tags" aria-label="文章标签">
              <a
                v-for="tag in tags"
                :key="tag.slug"
                class="article__tag"
                :href="withBase(`/articles/?tag=${tag.slug}`)"
              >
                <span aria-hidden="true">[</span>
                {{ tag.name }}
                <span aria-hidden="true">]</span>
              </a>
            </div>

            <div class="article__actions">
              <button class="article__action" type="button" @click="copyLink">
                <AppIcon name="arrow-up-right" :size="12" />
                <span aria-live="polite">{{ copied ? '已复制链接' : '复制链接' }}</span>
              </button>
              <a class="article__action" :href="withBase('/articles/')">
                <AppIcon name="arrow-right" :size="12" />
                <span>全部文章</span>
              </a>
            </div>
          </footer>
        </FramePanel>

        <nav class="article-pager" aria-label="上一篇与下一篇">
          <a
            v-if="adjacent.previous"
            class="pager-link"
            :href="withBase(adjacent.previous.url)"
          >
            <span class="pager-link__label">
              <AppIcon name="arrow-right" :size="12" class="pager-link__icon pager-link__icon--prev" />
              上一篇
            </span>
            <span class="pager-link__title">{{ adjacent.previous.title }}</span>
            <span class="pager-link__meta">{{ adjacent.previous.dateLabel }}</span>
          </a>
          <span v-else class="pager-link pager-link--empty">
            <span class="pager-link__label">已经是第一篇</span>
            <span class="pager-link__title">从这里往回，是更早的文字。</span>
          </span>

          <a v-if="adjacent.next" class="pager-link pager-link--next" :href="withBase(adjacent.next.url)">
            <span class="pager-link__label">
              下一篇
              <AppIcon name="arrow-right" :size="12" class="pager-link__icon" />
            </span>
            <span class="pager-link__title">{{ adjacent.next.title }}</span>
            <span class="pager-link__meta">{{ adjacent.next.dateLabel }}</span>
          </a>
        </nav>
      </div>

      <aside v-if="headers.length" class="article-aside">
        <FramePanel as="section" tone="panel" label="目录 / CONTENTS" class="article-toc-panel">
          <ArticleToc :headers="headers" />
        </FramePanel>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.article-page {
  padding: clamp(34px, 5vh, 58px) 0 clamp(64px, 8vw, 100px);
}

.article-page__grid {
  display: grid;
  grid-template-columns: minmax(0, 780px) 240px;
  justify-content: center;
  gap: 42px;
  align-items: start;
}

.article-main {
  min-width: 0;
}

.article-sheet {
  padding: clamp(32px, 4.4vw, 54px);
}

.article__eyebrow {
  color: var(--copper-deep);
}

.article__title {
  margin-top: 20px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.3rem, 4.2vw, 3.5rem);
  font-weight: 500;
  letter-spacing: -0.03em;
  line-height: 1.12;
  text-wrap: pretty;
}

.article__lead {
  max-width: 40em;
  margin-top: 20px;
  color: var(--ink-soft);
  font-size: 17px;
  line-height: 1.9;
  text-wrap: pretty;
}

.article__byline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
  margin-top: 24px;
  padding-top: 17px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.06em;
  line-height: 1.5;
}

.article__avatar {
  width: 28px;
  height: 28px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
}

.article__author {
  color: var(--ink-soft);
  font-family: var(--font-body);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.article__byline-dot {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.7;
}

.article__cover {
  margin: 30px 0 0;
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--paper-2);
}

.article__cover img {
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  filter: saturate(0.94) contrast(1.015);
}

.article-prose {
  margin-top: 30px;
  padding-top: 28px;
  border-top: 1px solid var(--line);
}

.article__toc-mobile {
  display: none;
  margin-top: 22px;
  border: 1px solid var(--line);
  background: var(--surface-panel);
}

.article__toc-mobile summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  line-height: 1.4;
  cursor: pointer;
  list-style: none;
}

.article__toc-mobile summary::-webkit-details-marker {
  display: none;
}

.article__toc-mobile summary span {
  color: var(--muted);
  font-size: 9px;
}

.article__toc-mobile summary::after {
  content: '+';
  margin-left: 8px;
  color: var(--copper);
  font-size: 13px;
  line-height: 1;
}

.article__toc-mobile[open] summary::after {
  content: '−';
}

.article__toc-mobile :deep(.toc) {
  padding: 0 14px 12px;
}

.article-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-top: 40px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.article__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.article__tag {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  line-height: 1.4;
}

.article__tag:hover {
  color: var(--copper);
}

.article__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 18px;
}

.article__action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.09em;
  line-height: 1.4;
  cursor: pointer;
}

.article__action:hover {
  color: var(--copper);
}

.article-aside {
  position: sticky;
  top: calc(var(--header-h) + 30px);
}

.article-toc-panel {
  padding: 24px 22px 20px;
}

.article-pager {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;
}

.pager-link {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 118px;
  padding: 22px;
  border: 1px solid var(--line);
  background: var(--surface-panel);
  transition:
    border-color var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

a.pager-link:hover {
  border-color: var(--copper);
  transform: translateY(-2px);
}

.pager-link--next {
  align-items: flex-end;
  text-align: right;
}

.pager-link__label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  line-height: 1;
  text-transform: uppercase;
}

.pager-link__icon--prev {
  transform: rotate(180deg);
}

.pager-link__title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  line-height: 1.45;
}

.pager-link__meta {
  margin-top: auto;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.06em;
}

.pager-link--empty {
  justify-content: center;
  background: transparent;
  border-style: dashed;
}

.pager-link--empty .pager-link__title {
  color: var(--muted);
  font-size: 14px;
}

@media (max-width: 1100px) {
  .article-page__grid {
    grid-template-columns: minmax(0, 820px);
  }

  .article-aside {
    display: none;
  }

  .article__toc-mobile {
    display: block;
  }
}

@media (max-width: 720px) {
  .article-page {
    padding-top: 18px;
  }

  .article-sheet {
    padding: 28px 20px;
  }

  .article__title {
    margin-top: 15px;
    font-size: clamp(2rem, 10vw, 2.7rem);
  }

  .article__lead {
    margin-top: 14px;
    font-size: 15px;
  }

  .article__byline {
    margin-top: 19px;
    padding-top: 14px;
    font-size: 8.5px;
  }

  .article__cover {
    margin-top: 22px;
  }

  .article__cover img {
    aspect-ratio: 2.2 / 1;
  }

  .article-prose {
    margin-top: 24px;
    padding-top: 22px;
  }

  .article-foot {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
    margin-top: 30px;
  }

  .article__actions {
    justify-content: flex-start;
  }

  .article-pager {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .pager-link {
    min-height: 98px;
    padding: 18px;
  }
}
</style>

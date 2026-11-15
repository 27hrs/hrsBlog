<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useData, withBase } from 'vitepress'
import ArticleCard from '../components/ArticleCard.vue'
import ArchiveFilters from '../components/ArchiveFilters.vue'
import ArchiveSidebar from '../components/ArchiveSidebar.vue'
import FramePanel from '../components/FramePanel.vue'
import AppIcon from '../components/AppIcon.vue'
import { useArticles } from '../composables/useArticles.js'

const { theme } = useData()
const site = computed(() => theme.value.site || {})
const { items, tags, categories, featured, generatedAt } = useArticles()

const activeCategory = ref('')
const activeTag = ref('')
const ready = ref(false)

const filtered = computed(() => {
  return items.filter((article) => {
    if (activeCategory.value && article.category !== activeCategory.value) return false
    if (activeTag.value && !article.tags.some((tag) => tag.slug === activeTag.value)) return false
    return true
  })
})

const activeCategoryLabel = computed(
  () => categories.find((category) => category.id === activeCategory.value)?.label || '',
)

const activeTagLabel = computed(
  () => tags.find((tag) => tag.slug === activeTag.value)?.name || '',
)

const syncLabel = computed(() => {
  if (!generatedAt) return ''
  return String(generatedAt).slice(0, 10).replaceAll('-', '.')
})

function readQuery() {
  if (typeof window === 'undefined') return
  const params = new URLSearchParams(window.location.search)
  const category = params.get('category') || ''
  const tag = params.get('tag') || ''

  activeCategory.value = categories.some((item) => item.id === category) ? category : ''
  activeTag.value = tags.some((item) => item.slug === tag) ? tag : ''
}

function syncQuery() {
  if (typeof window === 'undefined' || !ready.value) return
  const url = new URL(window.location.href)

  if (activeCategory.value) url.searchParams.set('category', activeCategory.value)
  else url.searchParams.delete('category')

  if (activeTag.value) url.searchParams.set('tag', activeTag.value)
  else url.searchParams.delete('tag')

  window.history.replaceState(null, '', url)
}

function selectCategory(id) {
  activeCategory.value = id
}

function selectTag(slug) {
  activeTag.value = activeTag.value === slug ? '' : slug
}

function clearFilters() {
  activeCategory.value = ''
  activeTag.value = ''
}

function onPopState() {
  readQuery()
}

watch([activeCategory, activeTag], syncQuery)

onMounted(() => {
  readQuery()
  ready.value = true
  window.addEventListener('popstate', onPopState)
})

onBeforeUnmount(() => {
  window.removeEventListener('popstate', onPopState)
})
</script>

<template>
  <div class="archive">
    <div class="wrap">
      <FramePanel as="section" tone="glass" label="01 / ARCHIVE" class="archive__head">
        <div class="archive__head-row">
          <div class="archive__head-copy">
            <p class="eyebrow archive__eyebrow">
              <span>ALL NOTES</span>
              <span class="eyebrow__rule" aria-hidden="true" />
              <span>按时间排序</span>
            </p>
            <h1 class="archive__title">文章</h1>
            <p class="archive__lead">所有写过的字，按时间排好。可以按分类或标签慢慢翻。</p>
          </div>

          <div class="archive__head-meta">
            <p class="archive__count">
              <span>{{ String(items.length).padStart(2, '0') }}</span>
              <span>篇已发布</span>
            </p>
            <p v-if="syncLabel" class="archive__sync">最近同步 {{ syncLabel }}</p>
            <a class="archive__rss" href="/feed.xml">
              <AppIcon name="rss" :size="13" />
              RSS
            </a>
          </div>
        </div>
      </FramePanel>

      <div class="archive__layout">
        <FramePanel as="section" tone="paper" label="02 / ALL NOTES" class="archive__panel">
          <ArchiveFilters
            :categories="categories"
            :tags="tags"
            :active-category="activeCategory"
            :active-tag="activeTag"
            :filtered-count="filtered.length"
            :total-count="items.length"
            @select-category="selectCategory"
            @select-tag="selectTag"
            @clear="clearFilters"
          />

          <div v-if="filtered.length" class="archive__list">
            <ArticleCard
              v-for="(article, index) in filtered"
              :key="article.slug"
              :article="article"
              :index="index + 1"
              compact
            />
          </div>

          <div v-else class="archive__empty">
            <p class="archive__empty-title">没有匹配的文章。</p>
            <p>
              <template v-if="activeTagLabel">#{{ activeTagLabel }}</template>
              <template v-if="activeCategoryLabel"> · {{ activeCategoryLabel }}</template>
              这个组合下暂时是空的，换一个筛选试试。
            </p>
            <button class="archive__empty-clear" type="button" @click="clearFilters">
              清除筛选
              <AppIcon name="arrow-right" :size="13" />
            </button>
          </div>
        </FramePanel>

        <aside class="archive__aside" aria-label="文章筛选与推荐">
          <ArchiveSidebar
            :categories="categories"
            :tags="tags"
            :popular="featured"
            :friend-links="site.friendLinks || []"
            :active-category="activeCategory"
            :active-tag="activeTag"
            @select-category="selectCategory"
            @select-tag="selectTag"
          />
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.archive {
  padding: clamp(30px, 5vh, 64px) 0 clamp(70px, 9vw, 110px);
}

.archive__head {
  padding: clamp(30px, 4vw, 46px);
  margin-bottom: 24px;
}

.archive__head-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 36px;
}

.archive__eyebrow {
  color: var(--copper-deep);
}

.archive__title {
  margin-top: 18px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 5vw, 4.2rem);
  font-weight: 500;
  letter-spacing: -0.035em;
  line-height: 1;
}

.archive__lead {
  max-width: 38em;
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.9;
  text-wrap: pretty;
}

.archive__head-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex: none;
  padding-bottom: 4px;
}

.archive__count {
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

.archive__count span:first-child {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 26px;
  font-style: italic;
  letter-spacing: -0.02em;
}

.archive__sync {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.archive__rss {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.archive__rss:hover {
  color: var(--copper);
}

.archive__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 32px;
  align-items: start;
}

.archive__panel {
  min-width: 0;
  padding: 34px 46px 0;
}

.archive__list {
  display: flex;
  flex-direction: column;
}

.archive__aside {
  position: sticky;
  top: calc(var(--header-h) + 24px);
}

.archive__empty {
  padding: 48px 0 56px;
  border-top: 1px solid var(--line);
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.9;
}

.archive__empty-title {
  margin-bottom: 8px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 500;
}

.archive__empty-clear {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-top: 18px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  cursor: pointer;
}

@media (max-width: 1180px) {
  .archive__layout {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .archive__aside {
    position: static;
  }

  .archive__aside :deep(.archive-sidebar) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px;
  }
}

@media (max-width: 1024px) {
  .archive__head-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 20px;
  }

  .archive__head-meta {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    padding-bottom: 0;
  }
}

@media (max-width: 860px) {
  .archive__panel {
    padding: 28px 30px 0;
  }
}

@media (max-width: 720px) {
  .archive {
    padding-top: 20px;
  }

  .archive__head {
    padding: 28px 22px;
    margin-bottom: 18px;
  }

  .archive__head-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .archive__title {
    margin-top: 14px;
    font-size: clamp(2.6rem, 13vw, 3.4rem);
  }

  .archive__lead {
    margin-top: 12px;
    font-size: 14px;
  }

  .archive__panel {
    padding: 26px 20px 0;
  }

  .archive__aside :deep(.archive-sidebar) {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StatCard from '@/components/dashboard/StatCard.vue'
import TrendChart from '@/components/dashboard/TrendChart.vue'
import RecentList from '@/components/dashboard/RecentList.vue'
import TodoList from '@/components/dashboard/TodoList.vue'
import { loadDashboardData } from '@/api/stats.js'
import { resolveAssetUrl } from '@/api/client.js'
import { useAuthStore } from '@/stores/auth.js'
import {
  formatDate,
  formatRelative,
  formatWeekday,
  greetingFor,
} from '@/utils/format.js'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const error = ref('')
const data = ref(null)
const refreshing = ref(false)

const blogBaseUrl = computed(() => {
  const configured = import.meta.env.VITE_BLOG_URL
  if (configured) return configured.replace(/\/$/, '')
  return `${window.location.protocol}//${window.location.hostname}:27080`
})

const today = new Date()
const dateLabel = `${today.getFullYear()} 年 ${today.getMonth() + 1} 月 ${today.getDate()} 日`
const weekday = formatWeekday(today)
const greeting = greetingFor(today)

const stats = computed(() => data.value?.stats || {})
const todoItems = computed(() => data.value?.todos || [])
const recentArticles = computed(() =>
  (data.value?.recentArticles || []).map((article) => ({
    ...article,
    coverUrl: resolveAssetUrl(article.coverUrl),
    url: `${blogBaseUrl.value}/articles/${article.slug}`,
  })),
)

async function load({ silent = false } = {}) {
  if (silent) refreshing.value = true
  else loading.value = true
  error.value = ''
  try {
    data.value = await loadDashboardData()
  } catch (loadError) {
    error.value = loadError.message || '数据加载失败'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => load())

function goCreate() {
  router.push({ name: 'article-create' })
}
</script>

<template>
  <div class="dashboard page-reveal">
    <header class="dashboard-head">
      <div class="dashboard-head__copy">
        <p class="dashboard-head__date">
          <span>{{ dateLabel }}</span>
          <span class="dashboard-head__date-divider" aria-hidden="true" />
          <span>{{ weekday }}</span>
          <span class="dashboard-head__edition">HRS / DAILY</span>
        </p>
        <h1 class="dashboard-head__title">
          {{ greeting }}，{{ auth.displayName }}<span class="dashboard-head__period">.</span>
        </h1>
        <p class="dashboard-head__lead">今天也留下一点什么。把想法放进文章里，让它慢慢生长。</p>
      </div>

      <div class="dashboard-head__actions">
        <a class="btn btn--ghost dashboard-head__blog" :href="blogBaseUrl" target="_blank" rel="noreferrer">
          <AppIcon name="globe" :size="15" />
          <span>查看博客</span>
          <AppIcon name="arrow-up-right" :size="13" />
        </a>
        <button class="btn btn--primary" type="button" @click="goCreate">
          <AppIcon name="plus" :size="15" />
          写文章
        </button>
      </div>
    </header>

    <div v-if="error" class="callout callout--danger dashboard-error" role="alert">
      <AppIcon class="callout__icon" name="alert-circle" :size="16" />
      <span>{{ error }}</span>
      <button class="dashboard-error__retry" type="button" @click="load()">重新加载</button>
    </div>

    <section class="stats-grid" aria-label="内容统计">
      <StatCard
        label="文章"
        :value="stats.articles ?? 0"
        icon="article"
        :hint="stats.publishedThisMonth ? `本月新增 ${stats.publishedThisMonth} 篇` : '持续记录你的想法'"
        :delta="stats.publishedThisMonth ? `+${stats.publishedThisMonth}` : ''"
        :loading="loading"
        featured
        index="01"
      />
      <StatCard
        label="评论"
        :value="stats.comments"
        icon="message-square"
        hint="评论模块接口待接入"
        :loading="loading"
        index="02"
      />
      <StatCard
        label="访客"
        :value="stats.visitors"
        icon="users"
        hint="访问统计接口待接入"
        :loading="loading"
        index="03"
      />
      <StatCard
        label="草稿"
        :value="stats.drafts ?? 0"
        icon="file-edit"
        :hint="stats.drafts ? '有想法正在等待发布' : '没有积压的草稿'"
        :loading="loading"
        index="04"
      />
    </section>

    <div class="dashboard-grid">
      <TrendChart
        class="dashboard-grid__trend"
        :labels="data?.trend?.labels || []"
        :series="data?.trend?.series || []"
        :preview="data?.preview || false"
        :loading="loading"
      />

      <RecentList
        class="dashboard-grid__comments"
        title="最近评论"
        subtitle="来自读者的回声"
        icon="message-square"
        :items="data?.recentComments || []"
        :loading="loading"
        unavailable
        empty-title="评论模块还在路上"
        empty-description="接入评论接口后，这里会出现最新的读者留言与待审核内容。"
        empty-icon="message-square"
      >
        <template #item="{ item }">
          <span class="comment-avatar">{{ item.author?.slice(0, 1) || '读' }}</span>
          <div class="comment-copy">
            <p class="comment-copy__head">
              <strong>{{ item.author }}</strong>
              <span>{{ formatRelative(item.createdAt) }}</span>
            </p>
            <p class="comment-copy__body">{{ item.content }}</p>
          </div>
        </template>
      </RecentList>

      <RecentList
        class="dashboard-grid__recent"
        title="最近文章"
        subtitle="刚刚写下或更新过的内容"
        icon="article"
        :items="recentArticles"
        :loading="loading"
        :link-to="{ name: 'article-list' }"
        link-label="全部文章"
        empty-title="还没有已发布文章"
        empty-description="写下第一篇，让你的数字花园有一棵新的植物。"
        empty-icon="feather"
      >
        <template #item="{ item }">
          <a class="recent-article" :href="item.url" target="_blank" rel="noreferrer">
            <span class="recent-article__cover">
              <img v-if="item.coverUrl" :src="item.coverUrl" alt="" />
              <span v-else class="recent-article__cover-fallback">
                <AppIcon name="article" :size="15" />
              </span>
            </span>
            <span class="recent-article__copy">
              <span class="recent-article__title">{{ item.title }}</span>
              <span class="recent-article__meta">
                {{ formatDate(item.publishedAt) }}
                <span aria-hidden="true">·</span>
                {{ (item.tags || []).slice(0, 2).map((tag) => tag.name).join(' / ') || '未分类' }}
              </span>
            </span>
            <span class="status status--published recent-article__status">已发布</span>
            <AppIcon class="recent-article__external" name="arrow-up-right" :size="14" />
          </a>
        </template>
        <template #empty-actions>
          <button class="btn btn--soft btn--sm" type="button" @click="goCreate">
            <AppIcon name="plus" :size="13" />
            写第一篇
          </button>
        </template>
      </RecentList>

      <TodoList class="dashboard-grid__todo" :items="todoItems" :loading="loading" />
    </div>

    <p class="dashboard-footnote">
      <span class="pulse-dot" />
      <span>{{ refreshing ? '正在刷新数据…' : '数据来自 HrsBlog 访客接口与本机草稿' }}</span>
      <button class="dashboard-footnote__refresh" type="button" :disabled="loading || refreshing" @click="load({ silent: true })">
        <AppIcon name="refresh" :size="12" />
        刷新
      </button>
    </p>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.dashboard-head {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 2px 0 2px;
}

.dashboard-head__copy {
  min-width: 0;
}

.dashboard-head__date {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 14px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.dashboard-head__date-divider {
  width: 3px;
  height: 3px;
  background: var(--text-disabled);
  border-radius: 50%;
}

.dashboard-head__edition {
  padding-left: 3px;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-disabled);
  letter-spacing: 0.05em;
}

.dashboard-head__title {
  font-size: clamp(24px, 2.5vw, 30px);
  font-weight: var(--weight-semibold);
  line-height: 1.2;
  color: var(--text-primary);
  letter-spacing: -0.04em;
}

.dashboard-head__period {
  color: var(--brand);
}

.dashboard-head__lead {
  margin-top: 9px;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.dashboard-head__actions {
  display: flex;
  flex: none;
  gap: 7px;
  align-items: center;
  padding-bottom: 2px;
}

.dashboard-head__blog {
  color: var(--text-secondary);
}

.dashboard-head__blog:hover {
  color: var(--text-primary);
}

.dashboard-error {
  align-items: center;
}

.dashboard-error__retry {
  margin-left: auto;
  padding: 0;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: currentcolor;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-grid__trend {
  grid-column: span 8;
}

.dashboard-grid__comments {
  grid-column: span 4;
}

.dashboard-grid__recent {
  grid-column: span 7;
}

.dashboard-grid__todo {
  grid-column: span 5;
}

.dashboard-grid__recent :deep(.recent-list__item) {
  border-top: 0;
}

.dashboard-grid__recent :deep(.recent-list) {
  margin: 0 9px;
}

.dashboard-footnote {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 0 2px;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.dashboard-footnote .pulse-dot {
  width: 5px;
  height: 5px;
}

.dashboard-footnote__refresh {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 0;
  margin-left: 2px;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.dashboard-footnote__refresh:hover:not(:disabled) {
  color: var(--brand-text);
}

.dashboard-footnote__refresh:disabled {
  cursor: progress;
  opacity: 0.55;
}

.comment-avatar {
  display: grid;
  flex: none;
  width: 28px;
  height: 28px;
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
  color: var(--brand-text);
  background: var(--brand-soft);
  border-radius: 50%;
  place-items: center;
}

.comment-copy {
  flex: 1;
  min-width: 0;
}

.comment-copy__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.comment-copy__head strong {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.comment-copy__head span {
  font-size: 10px;
  color: var(--text-tertiary);
}

.comment-copy__body {
  margin-top: 3px;
  overflow: hidden;
  font-size: var(--text-xs);
  line-height: 1.55;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-article {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 9px 6px;
  margin: 0 -6px;
  color: inherit;
  text-decoration: none;
  border-radius: var(--radius-xs);
  transition: background-color var(--duration-fast) var(--ease-out);
}

.recent-article:hover {
  background: var(--bg-hover);
  text-decoration: none;
}

.recent-article__cover {
  display: grid;
  flex: none;
  width: 36px;
  height: 36px;
  overflow: hidden;
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xs);
  place-items: center;
}

.recent-article__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recent-article__cover-fallback {
  display: grid;
  color: var(--text-tertiary);
  place-items: center;
}

.recent-article__copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.recent-article__title {
  overflow: hidden;
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-article__meta {
  display: flex;
  gap: 4px;
  align-items: center;
  overflow: hidden;
  font-size: 10px;
  color: var(--text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-article__status {
  flex: none;
  height: 20px;
  font-size: 9px;
}

.recent-article__external {
  flex: none;
  color: var(--text-tertiary);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.recent-article:hover .recent-article__external {
  opacity: 1;
}

@media (max-width: 1240px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid__trend {
    grid-column: span 7;
  }

  .dashboard-grid__comments {
    grid-column: span 5;
  }

  .dashboard-grid__recent {
    grid-column: span 7;
  }

  .dashboard-grid__todo {
    grid-column: span 5;
  }
}

@media (max-width: 960px) {
  .dashboard-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }

  .dashboard-head__actions {
    padding-bottom: 0;
  }

  .dashboard-grid__trend,
  .dashboard-grid__comments,
  .dashboard-grid__recent,
  .dashboard-grid__todo {
    grid-column: span 12;
  }
}

@media (max-width: 600px) {
  .dashboard {
    gap: 16px;
  }

  .stats-grid {
    gap: 8px;
  }

  .dashboard-head__title {
    font-size: 23px;
  }

  .dashboard-head__lead {
    max-width: 310px;
    line-height: 1.65;
  }

  .dashboard-head__edition {
    display: none;
  }

  .dashboard-head__blog {
    display: none;
  }

  .recent-article__status {
    display: none;
  }

  .dashboard-footnote {
    flex-wrap: wrap;
  }
}
</style>

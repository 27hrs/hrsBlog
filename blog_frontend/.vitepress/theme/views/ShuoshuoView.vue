<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import FramePanel from '../components/FramePanel.vue'
import AppIcon from '../components/AppIcon.vue'
import { useShuoshuo } from '../composables/useShuoshuo.js'

const { theme } = useData()
const { items, count, generatedAt } = useShuoshuo()

/** 说说页文案：后台站点设置可覆盖，读不到时回退本地默认值。 */
const config = computed(() => theme.value.site?.shuoshuo || {})
const title = computed(() => config.value.title || '说说')
const description = computed(
  () => config.value.description || '一些零碎的近况与随手记，不成篇，只记录。',
)
const emptyText = computed(() => config.value.emptyText || '还没有留下说说，等第一条出现。')

const syncLabel = computed(() => {
  if (!generatedAt) return ''
  return String(generatedAt).slice(0, 10).replaceAll('-', '.')
})

const MONTHS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二']

/**
 * publishedAt 是构建期归一化后的 +08:00 ISO 串，
 * 但仍按 Asia/Shanghai 拆解，避免运行环境的时区影响分组结果。
 */
const formatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function shanghaiParts(value) {
  if (!value) return null

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null

  const parts = Object.fromEntries(
    formatter
      .formatToParts(date)
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, part.value]),
  )

  return {
    year: parts.year,
    month: parts.month,
    day: parts.day,
    hour: parts.hour === '24' ? '00' : parts.hour,
    minute: parts.minute,
  }
}

/** 排序：时间新的在前；没有时间的排在最后。 */
const entries = computed(() =>
  items
    .map((item) => ({ item, parts: shanghaiParts(item.publishedAt) }))
    .sort((a, b) => {
      if (!a.parts && !b.parts) return 0
      if (!a.parts) return 1
      if (!b.parts) return -1
      const byTime = String(b.item.publishedAt || '').localeCompare(String(a.item.publishedAt || ''))
      return byTime || String(b.item.id ?? '').localeCompare(String(a.item.id ?? ''))
    }),
)

/**
 * 按「年 → 月」分组：分组依据是 publishedAt 在 Asia/Shanghai 时区的本地年月，
 * 最新的年份、月份排在前面；缺时间的条目归入「未标注」分组放在最后。
 */
const groups = computed(() => {
  const years = new Map()

  for (const entry of entries.value) {
    const yearKey = entry.parts ? entry.parts.year : ''
    if (!years.has(yearKey)) years.set(yearKey, { year: yearKey, months: new Map() })

    const yearGroup = years.get(yearKey)
    const monthKey = entry.parts ? entry.parts.month : ''
    if (!yearGroup.months.has(monthKey)) {
      yearGroup.months.set(monthKey, {
        month: monthKey,
        label: entry.parts ? `${MONTHS[Number(monthKey) - 1]}月` : '未标注',
        items: [],
      })
    }

    yearGroup.months.get(monthKey).items.push(entry)
  }

  return [...years.values()].map((year) => ({
    year: year.year,
    label: year.year ? `${year.year} 年` : '未标注时间',
    months: [...year.months.values()],
  }))
})

/** 卡片脚注的日期标签：2026.11.18 22:50 */
function dateLabel(entry) {
  if (!entry.parts) return ''
  const { year, month, day, hour, minute } = entry.parts
  return `${year}.${month}.${day} ${hour}:${minute}`
}
</script>

<template>
  <div class="shuoshuo">
    <div class="wrap">
      <FramePanel as="section" tone="glass" label="03 / SHUOSHUO" class="shuoshuo__head">
        <div class="shuoshuo__head-row">
          <div class="shuoshuo__head-copy">
            <p class="eyebrow shuoshuo__eyebrow">
              <span>SHUOSHUO &amp; NOTES</span>
              <span class="eyebrow__rule" aria-hidden="true" />
              <span>碎念记录</span>
            </p>
            <h1 class="shuoshuo__title">{{ title }}</h1>
            <p class="shuoshuo__lead">{{ description }}</p>
          </div>

          <div class="shuoshuo__head-meta">
            <p class="shuoshuo__count">
              <span>{{ String(count).padStart(2, '0') }}</span>
              <span>条说说</span>
            </p>
            <p v-if="syncLabel" class="shuoshuo__sync">最近同步 {{ syncLabel }}</p>
          </div>
        </div>
      </FramePanel>

      <div v-if="groups.length" class="shuoshuo__timeline">
        <section v-for="group in groups" :key="group.year || 'unknown'" class="shuoshuo__year">
          <p class="shuoshuo__year-label">
            <span>{{ group.label }}</span>
            <span class="shuoshuo__year-rule" aria-hidden="true" />
          </p>

          <div v-for="month in group.months" :key="month.month || 'unknown'" class="shuoshuo__month">
            <p class="shuoshuo__month-label">{{ month.label }}</p>

            <ol class="shuoshuo__items">
              <li
                v-for="(entry, index) in month.items"
                :key="entry.item.id ?? `${entry.item.publishedAt}-${index}`"
                class="shuoshuo__item"
              >
                <span class="shuoshuo__node" aria-hidden="true" />

                <FramePanel as="article" tone="paper" interactive class="shuoshuo__card">
                  <div
                    v-if="entry.item.html"
                    class="shuoshuo__body article-prose"
                    v-html="entry.item.html"
                  />
                  <p v-else class="shuoshuo__body shuoshuo__body--plain">{{ entry.item.content }}</p>

                  <p v-if="dateLabel(entry)" class="shuoshuo__date">
                    <time :datetime="entry.item.publishedAt">{{ dateLabel(entry) }}</time>
                    <span class="shuoshuo__date-rule" aria-hidden="true" />
                  </p>
                </FramePanel>
              </li>
            </ol>
          </div>
        </section>
      </div>

      <FramePanel
        v-else
        as="section"
        tone="paper"
        label="04 / TIMELINE"
        class="shuoshuo__panel shuoshuo__panel--empty"
      >
        <div class="shuoshuo__empty">
          <p class="shuoshuo__empty-title">这里还很安静。</p>
          <p class="shuoshuo__empty-text">{{ emptyText }}</p>
          <a class="shuoshuo__empty-cta" :href="withBase('/about')">
            去关于页看看
            <AppIcon name="arrow-right" :size="13" />
          </a>
        </div>
      </FramePanel>
    </div>
  </div>
</template>

<style scoped>
/* 说说页沿用雾湖手记的纸面底色与遮挡强度。 */
:global(.site--shuoshuo) {
  --backdrop-veil: 0.18;
}

.shuoshuo {
  padding: clamp(30px, 5vh, 64px) 0 clamp(70px, 9vw, 110px);
}

.shuoshuo__head {
  padding: clamp(30px, 4vw, 46px);
  margin-bottom: 34px;
}

.shuoshuo__head-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 36px;
}

.shuoshuo__head-copy {
  min-width: 0;
}

.shuoshuo__eyebrow {
  color: var(--copper-deep);
}

.shuoshuo__title {
  margin-top: 18px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 5vw, 4.2rem);
  font-weight: 500;
  letter-spacing: -0.035em;
  line-height: 1;
}

.shuoshuo__lead {
  max-width: 38em;
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.9;
  text-wrap: pretty;
}

.shuoshuo__head-meta {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  padding-bottom: 4px;
}

.shuoshuo__count {
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

.shuoshuo__count span:first-child {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 26px;
  font-style: italic;
  letter-spacing: -0.02em;
}

.shuoshuo__sync {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

/* 时间线 ------------------------------------------------------------------ */

.shuoshuo__timeline {
  display: flex;
  flex-direction: column;
  gap: 46px;
}

/* 眉签：主题里没有独立的 SectionLabel 组件，这里用年份 + 细线复刻画框的标签语言。 */
.shuoshuo__year-label {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--copper-deep);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.2em;
  line-height: 1;
  text-transform: uppercase;
}

.shuoshuo__year-rule {
  flex: 1;
  height: 1px;
  background: var(--line);
}

.shuoshuo__month {
  margin-top: 26px;
}

.shuoshuo__month-label {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.16em;
  line-height: 1;
}

/* 左侧细线时间轴：条目挂在竖线上，圆点标记发布时刻。 */
.shuoshuo__items {
  margin-top: 18px;
  padding-left: 26px;
  border-left: 1px solid var(--line);
  list-style: none;
}

.shuoshuo__item {
  position: relative;
  padding-bottom: 18px;
}

.shuoshuo__item:last-child {
  padding-bottom: 0;
}

.shuoshuo__node {
  position: absolute;
  top: 24px;
  left: -30px;
  width: 9px;
  height: 9px;
  border: 1px solid var(--copper);
  border-radius: 50%;
  background: var(--paper);
  transition:
    background-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}

.shuoshuo__item:hover .shuoshuo__node {
  background: var(--copper);
  box-shadow: 0 0 0 4px var(--copper-soft);
}

.shuoshuo__card {
  padding: 20px 24px 16px;
}

.shuoshuo__body {
  font-size: 15px;
  line-height: 1.9;
}

.shuoshuo__body--plain {
  color: var(--ink-soft);
  overflow-wrap: anywhere;
  text-wrap: pretty;
}

/* 脚注：日期标签 + 一小段随 hover 延长的细线。 */
.shuoshuo__date {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
  line-height: 1.4;
  transition: color var(--dur-fast) var(--ease);
}

.shuoshuo__card:hover .shuoshuo__date {
  color: var(--copper);
}

.shuoshuo__date-rule {
  width: 22px;
  height: 1px;
  background: currentColor;
  opacity: 0.6;
  transform: scaleX(0.35);
  transform-origin: left;
  transition: transform var(--dur) var(--ease);
}

.shuoshuo__card:hover .shuoshuo__date-rule {
  transform: scaleX(1);
}

/* 空态 -------------------------------------------------------------------- */

.shuoshuo__panel--empty {
  padding: clamp(34px, 5vw, 58px);
}

.shuoshuo__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.shuoshuo__empty-title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(1.2rem, 2vw, 1.5rem);
  letter-spacing: -0.02em;
}

.shuoshuo__empty-text {
  max-width: 34em;
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.8;
}

.shuoshuo__empty-cta {
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
  transition:
    background-color var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.shuoshuo__empty-cta:hover {
  background: var(--copper);
  color: var(--on-dark);
}

@media (max-width: 1024px) {
  .shuoshuo__head-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 20px;
  }

  .shuoshuo__head-meta {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    padding-bottom: 0;
  }
}

@media (max-width: 720px) {
  .shuoshuo {
    padding-top: 20px;
  }

  .shuoshuo__head {
    padding: 28px 22px;
    margin-bottom: 24px;
  }

  .shuoshuo__title {
    margin-top: 14px;
    font-size: clamp(2.6rem, 13vw, 3.4rem);
  }

  .shuoshuo__lead {
    margin-top: 12px;
    font-size: 14px;
  }

  .shuoshuo__head-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .shuoshuo__timeline {
    gap: 34px;
  }

  .shuoshuo__items {
    padding-left: 18px;
  }

  .shuoshuo__node {
    left: -22px;
  }

  .shuoshuo__card {
    padding: 16px 17px 14px;
  }

  .shuoshuo__body {
    font-size: 14.5px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .shuoshuo__card:hover .shuoshuo__date-rule {
    transform: scaleX(0.35);
  }
}
</style>

<script setup>
import { computed, ref, watch } from 'vue'
import VisXYContainer from '@unovis/vue/containers/xy-container'
import VisArea from '@unovis/vue/components/area'
import VisLine from '@unovis/vue/components/line'
import VisAxis from '@unovis/vue/components/axis'
import AppIcon from '@/components/common/AppIcon.vue'
import { useThemeStore } from '@/stores/theme.js'
import { formatNumber } from '@/utils/format.js'

const props = defineProps({
  labels: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  preview: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})

const theme = useThemeStore()
const range = ref(14)

const palettes = computed(() =>
  theme.isDark
    ? {
        brand: '#60a5fa',
        secondary: '#667384',
        grid: '#2b3038',
        axis: '#77818f',
        tooltipBorder: '#39404b',
        tooltipBg: '#24282f',
      }
    : {
        brand: '#3b82f6',
        secondary: '#94a3b8',
        grid: '#edf0f4',
        axis: '#8d96a4',
        tooltipBorder: '#e3e7ed',
        tooltipBg: '#ffffff',
      },
)

const visibleLabels = computed(() => {
  const labels = Array.isArray(props.labels) ? props.labels : []
  return labels.slice(Math.max(0, labels.length - range.value))
})

const visibleSeries = computed(() =>
  (props.series || []).map((item) => ({
    ...item,
    data: (item.data || []).slice(Math.max(0, item.data.length - range.value)),
  })),
)

const points = computed(() =>
  visibleLabels.value.map((label, index) => {
    const point = { index, label }
    visibleSeries.value.forEach((item) => {
      point[item.key] = Number(item.data?.[index] ?? 0)
    })
    return point
  }),
)

const viewsSeries = computed(() => visibleSeries.value.find((item) => item.key === 'views'))
const visitorsSeries = computed(() => visibleSeries.value.find((item) => item.key === 'visitors'))
const publishedSeries = computed(() => visibleSeries.value.find((item) => item.key === 'published'))
const hasViews = computed(() => Boolean(viewsSeries.value))
const hasVisitors = computed(() => Boolean(visitorsSeries.value))
const hasPublished = computed(() => Boolean(publishedSeries.value))
const hasSignal = computed(() => points.value.some((point) => Object.keys(point).some((key) => typeof point[key] === 'number' && point[key] > 0)))

const seriesTotal = computed(() => {
  const totals = {}
  visibleSeries.value.forEach((item) => {
    totals[item.key] = item.data.reduce((sum, value) => sum + (Number(value) || 0), 0)
  })
  return totals
})

const x = (point) => point.index
const yViews = (point) => point.views
const yVisitors = (point) => point.visitors
const yPublished = (point) => point.published

const xTicks = computed(() => {
  const count = visibleLabels.value.length
  if (count <= 1) return [0]
  if (count <= 7) return visibleLabels.value.map((_, index) => index)
  const targetTicks = 5
  const step = (count - 1) / (targetTicks - 1)
  return Array.from({ length: targetTicks }, (_, index) => Math.round(index * step))
})

const chartVars = computed(() => ({
  '--vis-axis-grid-color': palettes.value.grid,
  '--vis-axis-domain-color': 'transparent',
  '--vis-axis-tick-color': 'transparent',
  '--vis-axis-tick-label-color': palettes.value.axis,
  '--vis-axis-label-color': palettes.value.axis,
  '--vis-axis-tick-label-font-size': '11px',
  '--vis-axis-tick-label-weight': '400',
  '--vis-axis-grid-line-dasharray': '3 5',
  '--vis-axis-grid-opacity': '1',
  '--vis-font-family': 'var(--font-sans)',
}))

function formatXTick(value) {
  return visibleLabels.value[value] || ''
}

function formatYTick(value) {
  if (Number(value) >= 1000) return `${(Number(value) / 1000).toFixed(1)}k`
  return String(value)
}

const hover = ref(null)
const hoveredPoint = computed(() =>
  hover.value ? points.value[hover.value.index] || null : null,
)

function seriesColor(key) {
  return key === 'views' || key === 'published'
    ? palettes.value.brand
    : palettes.value.secondary
}

function onChartHover(event) {
  if (!points.value.length) return
  const rect = event.currentTarget.getBoundingClientRect()
  if (!rect.width) return

  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  const index = Math.round(ratio * (points.value.length - 1))
  const pointerX = ratio * rect.width
  const cardHalfWidth = 86
  const cardX = Math.min(
    Math.max(pointerX, cardHalfWidth + 4),
    Math.max(rect.width - cardHalfWidth - 4, cardHalfWidth + 4),
  )
  hover.value = { index, pointerX, cardX }
}

function clearHover() {
  hover.value = null
}

watch(range, clearHover)
watch(
  () => props.labels,
  () => {
    clearHover()
  },
)

const summaryText = computed(() => {
  if (hasViews.value) {
    return `${range.value} 天累计访问 ${formatNumber(seriesTotal.value.views || 0)} · 访客 ${formatNumber(seriesTotal.value.visitors || 0)}`
  }
  if (hasPublished.value) {
    return `${range.value} 天共发布 ${formatNumber(seriesTotal.value.published || 0)} 篇文章`
  }
  return '暂无可用数据'
})
</script>

<template>
  <section class="panel trend-panel" aria-labelledby="trend-title">
    <header class="panel__header trend-panel__header">
      <div class="trend-panel__heading">
        <div class="trend-panel__title-row">
          <h2 id="trend-title" class="panel__title">访问趋势</h2>
          <span v-if="preview" class="trend-preview" title="后端统计接口尚未接入，图表使用开发预览数据">
            <AppIcon name="sparkles" :size="11" />
            预览数据
          </span>
        </div>
        <p class="panel__subtitle">观察内容与读者之间，正在发生的变化。</p>
      </div>

      <div class="segmented" role="group" aria-label="趋势时间范围">
        <button
          v-for="option in [7, 14, 30]"
          :key="option"
          class="segmented__item"
          type="button"
          :aria-pressed="range === option"
          @click="range = option"
        >
          {{ option }} 天
        </button>
      </div>
    </header>

    <div class="trend-panel__body">
      <div v-if="loading" class="trend-skeleton">
        <span class="skeleton" style="height: 14px; width: 28%" />
        <span class="skeleton trend-skeleton__chart" />
      </div>

      <div v-else-if="!hasSignal" class="trend-empty">
        <span class="trend-empty__icon">
          <AppIcon name="trending-up" :size="22" />
        </span>
        <div>
          <p class="trend-empty__title">还没有可绘制的访问数据</p>
          <p class="trend-empty__description">
            统计接口接入后，这里会自动呈现文章阅读量与访客趋势。
          </p>
        </div>
      </div>

      <template v-else>
        <div class="chart-stage" :style="chartVars" role="img" :aria-label="`访问趋势图，${summaryText}`">
          <VisXYContainer
            :data="points"
            :height="244"
            :margin="{ top: 12, right: 14, bottom: 28, left: 38 }"
          >
            <VisArea
              v-if="hasViews || hasPublished"
              :data="points"
              :x="x"
              :y="hasViews ? yViews : yPublished"
              :color="palettes.brand"
              :opacity="0.14"
              :line="true"
              :line-width="2.1"
              curve-type="monotoneX"
            />
            <VisLine
              v-if="hasVisitors"
              :data="points"
              :x="x"
              :y="yVisitors"
              :color="palettes.secondary"
              :line-width="1.4"
              :line-dash-array="[4, 4]"
            />
            <VisAxis
              type="x"
              :tick-values="xTicks"
              :tick-format="formatXTick"
              :grid-line="false"
              :domain-line="false"
              :tick-line="false"
            />
            <VisAxis
              type="y"
              :num-ticks="4"
              :tick-format="formatYTick"
              :grid-line="true"
              :domain-line="false"
              :tick-line="false"
            />
          </VisXYContainer>

          <div
            class="chart-hover-zone"
            aria-hidden="true"
            @mousemove="onChartHover"
            @mouseleave="clearHover"
          >
            <span
              v-if="hover"
              class="chart-crosshair"
              :style="{ left: `${hover.pointerX}px` }"
            />
            <div
              v-if="hover && hoveredPoint"
              class="chart-hover-card"
              :style="{ left: `${hover.cardX}px` }"
            >
              <p class="chart-hover-card__date">{{ hoveredPoint.label }}</p>
              <div
                v-for="item in visibleSeries"
                :key="item.key"
                class="chart-hover-card__row"
              >
                <span class="chart-hover-card__name">
                  <i :style="{ background: seriesColor(item.key) }" />
                  {{ item.label }}
                </span>
                <strong>{{ formatNumber(hoveredPoint[item.key]) }}</strong>
              </div>
            </div>
          </div>
        </div>

        <footer class="trend-panel__footer">
          <div class="trend-legend">
            <span v-for="item in visibleSeries" :key="item.key" class="trend-legend__item">
              <span
                class="trend-legend__mark"
                :class="{ 'trend-legend__mark--dashed': item.key === 'visitors' }"
              />
              {{ item.label }}
            </span>
          </div>
          <span class="trend-summary">{{ summaryText }}</span>
        </footer>
      </template>
    </div>
  </section>
</template>

<style scoped>
.trend-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.trend-panel__header {
  padding-bottom: 17px;
  align-items: center;
}

.trend-panel__heading {
  min-width: 0;
}

.trend-panel__title-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.trend-preview {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 19px;
  padding: 0 6px;
  font-size: 10px;
  font-weight: var(--weight-medium);
  color: var(--warning-text);
  background: var(--warning-soft);
  border-radius: var(--radius-full);
}

.trend-panel__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 0 14px 0 10px;
}

.chart-stage {
  position: relative;
  width: 100%;
  min-height: 244px;
  font-family: var(--font-sans);
  font-variant-numeric: tabular-nums;
}

.chart-hover-zone {
  position: absolute;
  z-index: 2;
  inset: 12px 14px 28px 38px;
  cursor: crosshair;
}

.chart-crosshair {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  pointer-events: none;
  background: color-mix(in srgb, var(--text-primary) 20%, transparent);
  border-radius: 1px;
}

.chart-crosshair::before {
  position: absolute;
  top: 0;
  left: 50%;
  width: 5px;
  height: 5px;
  content: '';
  background: var(--brand);
  border: 1.5px solid var(--bg-surface);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.chart-hover-card {
  position: absolute;
  top: 7px;
  width: 172px;
  padding: 9px 10px;
  pointer-events: none;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-lg);
  transform: translateX(-50%);
}

.chart-hover-card__date {
  padding-bottom: 7px;
  margin-bottom: 6px;
  font-size: 10px;
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  letter-spacing: 0.03em;
  border-bottom: 1px solid var(--border-subtle);
}

.chart-hover-card__row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.chart-hover-card__name {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--text-secondary);
}

.chart-hover-card__name i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.chart-hover-card__row strong {
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.trend-skeleton {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 16px 10px 20px;
}

.trend-skeleton__chart {
  height: 220px;
  border-radius: var(--radius-sm);
}

.trend-empty {
  display: flex;
  gap: 14px;
  align-items: center;
  min-height: 246px;
  margin: 4px 12px 12px;
  padding: 26px;
  background:
    linear-gradient(135deg, var(--bg-inset), transparent 56%),
    var(--bg-surface);
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
}

.trend-empty__icon {
  display: grid;
  flex: none;
  width: 44px;
  height: 44px;
  color: var(--text-tertiary);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 50%;
  place-items: center;
}

.trend-empty__title {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.trend-empty__description {
  max-width: 330px;
  margin-top: 5px;
  font-size: var(--text-xs);
  line-height: 1.65;
  color: var(--text-tertiary);
}

.trend-panel__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 15px;
  margin-top: auto;
  border-top: 1px solid var(--border-subtle);
}

.trend-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  align-items: center;
}

.trend-legend__item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: var(--text-2xs);
  color: var(--text-secondary);
}

.trend-legend__mark {
  width: 14px;
  height: 2px;
  background: var(--brand);
  border-radius: 2px;
}

.trend-legend__mark--dashed {
  height: 0;
  background: transparent;
  border-top: 1.5px dashed var(--chart-secondary);
  border-radius: 0;
}

.trend-summary {
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 560px) {
  .trend-panel__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .trend-panel__body {
    padding-left: 2px;
  }

  .trend-panel__footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
  }

  .trend-empty {
    min-height: 200px;
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

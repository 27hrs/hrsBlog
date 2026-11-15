<script setup>
import AppIcon from '@/components/common/AppIcon.vue'

defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: null },
  icon: { type: String, default: 'article' },
  hint: { type: String, default: '' },
  delta: { type: String, default: '' },
  deltaTone: { type: String, default: 'positive' },
  loading: { type: Boolean, default: false },
  featured: { type: Boolean, default: false },
  index: { type: String, default: '' },
})
</script>

<template>
  <article class="stat-card" :class="{ 'stat-card--featured': featured, 'is-loading': loading }">
    <div class="stat-card__top">
      <span class="stat-card__label">{{ label }}</span>
      <span class="stat-card__icon">
        <AppIcon :name="icon" :size="16" />
      </span>
    </div>

    <div class="stat-card__value-row">
      <span v-if="loading" class="skeleton stat-card__value-skeleton" />
      <span v-else class="stat-card__value">
        <template v-if="value === null || value === undefined">—</template>
        <template v-else>{{ value }}</template>
      </span>
      <span v-if="delta && !loading" class="stat-card__delta" :class="`stat-card__delta--${deltaTone}`">
        <AppIcon v-if="deltaTone === 'positive'" name="arrow-up-right" :size="11" />
        {{ delta }}
      </span>
    </div>

    <div class="stat-card__footer">
      <span v-if="loading" class="skeleton skeleton--text" style="width: 52%" />
      <span v-else class="stat-card__hint">{{ hint }}</span>
    </div>

    <span v-if="index" class="stat-card__index" aria-hidden="true">{{ index }}</span>
    <span class="stat-card__texture" aria-hidden="true" />
  </article>
</template>

<style scoped>
.stat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 146px;
  padding: 17px 18px 15px;
  overflow: hidden;
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  border-radius: var(--card-radius);
  box-shadow: var(--shadow-card);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.stat-card:hover {
  border-color: var(--border-strong);
  transform: translateY(-1px);
}

.stat-card--featured {
  border-color: color-mix(in srgb, var(--brand) 24%, var(--border));
}

.stat-card--featured::before {
  position: absolute;
  top: 0;
  left: 18px;
  width: 26px;
  height: 2px;
  content: '';
  background: var(--brand);
  border-radius: 0 0 2px 2px;
}

.stat-card__top {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.stat-card__label {
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
}

.stat-card__icon {
  display: grid;
  width: 29px;
  height: 29px;
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  place-items: center;
}

.stat-card--featured .stat-card__icon {
  color: var(--brand-text);
  background: var(--brand-soft);
  border-color: transparent;
}

.stat-card__value-row {
  display: flex;
  gap: 9px;
  align-items: baseline;
  margin-top: 15px;
}

.stat-card__value {
  font-size: 29px;
  font-weight: var(--weight-semibold);
  line-height: 1;
  color: var(--text-primary);
  letter-spacing: -0.045em;
  font-variant-numeric: tabular-nums;
}

.stat-card__value-skeleton {
  width: 56px;
  height: 27px;
  margin-top: 2px;
  border-radius: var(--radius-xs);
}

.stat-card__delta {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  font-size: 10px;
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
}

.stat-card__delta--positive {
  color: var(--success-text);
}

.stat-card__delta--neutral {
  color: var(--text-tertiary);
}

.stat-card__footer {
  display: flex;
  align-items: center;
  min-height: 18px;
  margin-top: auto;
  padding-top: 13px;
}

.stat-card__hint {
  overflow: hidden;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-card__index {
  position: absolute;
  right: 17px;
  bottom: 13px;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-disabled);
  letter-spacing: 0.03em;
}

.stat-card__texture {
  position: absolute;
  right: -28px;
  bottom: -38px;
  width: 110px;
  height: 110px;
  pointer-events: none;
  background: radial-gradient(circle, var(--bg-subtle) 0%, transparent 68%);
  opacity: 0.75;
}

.stat-card--featured .stat-card__texture {
  background: radial-gradient(circle, var(--brand-soft) 0%, transparent 68%);
}
</style>

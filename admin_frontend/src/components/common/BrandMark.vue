<script setup>
defineProps({
  size: { type: [Number, String], default: 32 },
  tone: { type: String, default: 'brand' },
})
</script>

<template>
  <svg
    class="brand-mark"
    :class="`brand-mark--${tone}`"
    :width="typeof size === 'number' ? `${size}px` : size"
    :height="typeof size === 'number' ? `${size}px` : size"
    viewBox="0 0 32 32"
    fill="none"
    role="img"
    aria-label="HrsBlog"
  >
    <rect width="32" height="32" rx="8" fill="currentColor" />
    <path
      d="M10 9.25v13.5M22 9.25v13.5M10 16h12"
      stroke="var(--brand-mark-ink, #ffffff)"
      stroke-width="2.1"
      stroke-linecap="round"
    />
    <circle cx="23.5" cy="23.5" r="2" fill="var(--brand-mark-dot, #bfdbfe)" />
  </svg>
</template>

<style scoped>
.brand-mark {
  flex: none;
  color: var(--brand);
}

.brand-mark--inverse {
  color: rgba(255, 255, 255, 0.92);
  --brand-mark-ink: #172554;
  --brand-mark-dot: #3b82f6;
}
</style>

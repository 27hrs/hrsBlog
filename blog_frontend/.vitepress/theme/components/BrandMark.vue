<script setup>
defineProps({
  size: { type: [Number, String], default: 32 },
})
</script>

<template>
  <svg
    class="brand-mark"
    :width="typeof size === 'number' ? `${size}px` : size"
    :height="typeof size === 'number' ? `${size}px` : size"
    viewBox="0 0 32 32"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <rect width="32" height="32" rx="8" fill="currentColor" />
    <path
      d="M10 9.25v13.5M22 9.25v13.5M10 16h12"
      stroke="var(--brand-mark-ink, #1d3a30)"
      stroke-width="2"
      stroke-linecap="round"
    />
    <circle cx="24" cy="24" r="2" fill="var(--copper)" />
  </svg>
</template>

<style scoped>
.brand-mark {
  flex: none;
  color: var(--on-dark);
}
</style>

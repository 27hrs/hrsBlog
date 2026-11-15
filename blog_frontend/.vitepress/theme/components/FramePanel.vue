<script setup>
defineProps({
  as: { type: String, default: 'div' },
  tone: { type: String, default: 'paper' },
  label: { type: String, default: '' },
  interactive: { type: Boolean, default: false },
})
</script>

<template>
  <component
    :is="as"
    class="frame"
    :class="[`frame--${tone}`, { 'frame--interactive': interactive }]"
  >
    <span class="frame__tick frame__tick--tl" aria-hidden="true" />
    <span class="frame__tick frame__tick--tr" aria-hidden="true" />
    <span class="frame__tick frame__tick--bl" aria-hidden="true" />
    <span class="frame__tick frame__tick--br" aria-hidden="true" />
    <span v-if="label" class="frame__label">{{ label }}</span>
    <slot />
  </component>
</template>

<style scoped>
.frame {
  --frame-bg: var(--surface-paper);
  --frame-border: var(--frame-line);

  position: relative;
  border: 1px solid var(--frame-border);
  border-radius: var(--radius);
  background: var(--frame-bg);
  box-shadow: var(--frame-shadow-soft);
  transition:
    transform var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}

.frame--glass {
  --frame-bg: var(--surface-glass);
  --frame-border: var(--glass-line);

  backdrop-filter: blur(18px) saturate(1.06);
  -webkit-backdrop-filter: blur(18px) saturate(1.06);
  box-shadow: var(--frame-shadow);
}

.frame--paper {
  --frame-bg: var(--surface-paper);
}

.frame--panel {
  --frame-bg: var(--surface-panel);
}

.frame--bare {
  --frame-bg: transparent;

  box-shadow: none;
}

.frame__label {
  position: absolute;
  top: -9px;
  left: 26px;
  z-index: 2;
  padding: 4px 9px 3px;
  border: 1px solid var(--frame-border);
  background: var(--surface-solid);
  color: var(--copper);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  line-height: 1;
  text-transform: uppercase;
  white-space: nowrap;
}

.frame__tick {
  position: absolute;
  z-index: 2;
  width: 10px;
  height: 10px;
  color: var(--copper);
  opacity: 0.6;
  pointer-events: none;
  transition:
    width var(--dur) var(--ease),
    height var(--dur) var(--ease),
    opacity var(--dur) var(--ease);
}

.frame__tick--tl {
  top: 7px;
  left: 7px;
  border-top: 1px solid currentColor;
  border-left: 1px solid currentColor;
}

.frame__tick--tr {
  top: 7px;
  right: 7px;
  border-top: 1px solid currentColor;
  border-right: 1px solid currentColor;
}

.frame__tick--bl {
  bottom: 7px;
  left: 7px;
  border-bottom: 1px solid currentColor;
  border-left: 1px solid currentColor;
}

.frame__tick--br {
  right: 7px;
  bottom: 7px;
  border-right: 1px solid currentColor;
  border-bottom: 1px solid currentColor;
}

.frame--interactive:hover {
  transform: translateY(-2px);
  border-color: var(--copper);
  box-shadow: var(--frame-shadow);
}

.frame--interactive:hover .frame__tick {
  width: 14px;
  height: 14px;
  opacity: 0.9;
}

@media (max-width: 720px) {
  .frame__label {
    left: 18px;
    font-size: 8px;
  }
}
</style>

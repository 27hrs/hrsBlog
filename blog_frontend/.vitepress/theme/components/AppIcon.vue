<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 16 },
})

const icons = {
  'arrow-right': {
    paths: ['M4 12h15', 'm13 6 6 6-6 6'],
  },
  'arrow-up-right': {
    paths: ['M7 17 17 7', 'M8 7h9v9'],
  },
  'arrow-down': {
    paths: ['M12 4v15', 'm6 13 6 6 6-6'],
  },
  refresh: {
    paths: ['M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8', 'M21 3v5h-5'],
  },
  menu: {
    paths: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  },
  close: {
    paths: ['M6 6l12 12', 'M18 6 6 18'],
  },
  check: {
    paths: ['M4.5 12.5 9.5 17.5 19.5 6.5'],
  },
  sun: {
    paths: [
      'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z',
      'M12 2v2',
      'M12 20v2',
      'M4.93 4.93l1.42 1.42',
      'M17.65 17.65l1.42 1.42',
      'M2 12h2',
      'M20 12h2',
      'M4.93 19.07l1.42-1.42',
      'M17.65 6.35l1.42-1.42',
    ],
  },
  moon: {
    paths: ['M20.5 14.2A8.6 8.6 0 0 1 9.8 3.5 8.7 8.7 0 1 0 20.5 14.2Z'],
  },
  mail: {
    paths: ['M3 6.5h18v11H3z', 'm3.5 7 8.5 6 8.5-6'],
  },
  rss: {
    paths: ['M5 19a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z', 'M5 12a7 7 0 0 1 7 7', 'M5 5a14 14 0 0 1 14 14'],
  },
  github: {
    fill: true,
    paths: [
      'M12 .5C5.73.5.5 5.73.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56v-2.17c-3.2.7-3.88-1.54-3.88-1.54-.52-1.33-1.28-1.69-1.28-1.69-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.23-1.28-5.23-5.68 0-1.25.45-2.28 1.19-3.08-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.8 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.8 1.19 1.83 1.19 3.08 0 4.41-2.69 5.38-5.25 5.67.41.35.78 1.05.78 2.12v3.14c0 .31.21.67.8.56A10.52 10.52 0 0 0 23.5 12C23.5 5.73 18.27.5 12 .5Z',
    ],
  },
}

const icon = computed(() => icons[props.name])
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    :fill="icon?.fill ? 'currentColor' : 'none'"
    :stroke="icon?.fill ? 'none' : 'currentColor'"
    stroke-width="1.55"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="path in icon?.paths || []" :key="path" :d="path" />
  </svg>
</template>

<style scoped>
.app-icon {
  flex: none;
  overflow: visible;
}
</style>

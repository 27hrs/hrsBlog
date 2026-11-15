<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'

/**
 * 背景图来源：
 * - 后台「站点设置 → 外观」配置了 `backgroundImage`（http(s) 或站内路径）时使用它；
 * - 未配置时回退到内置湖景响应式版本（AVIF/WebP/JPG 三档）。
 * `backgroundOverlay`（0-100）映射为 `--backdrop-veil`，控制纸面遮罩强度。
 */
const { theme } = useData()
const site = computed(() => theme.value.site || {})

const customImage = computed(() => {
  const url = String(site.value.backgroundImage ?? '').trim()
  if (!url) return ''
  return /^https?:\/\//i.test(url) || url.startsWith('/') ? url : ''
})

const veil = computed(() => {
  const value = Number(site.value.backgroundOverlay)
  if (!Number.isFinite(value)) return null
  return Math.min(100, Math.max(0, value)) / 100
})
</script>

<template>
  <div class="backdrop" aria-hidden="true" :style="veil !== null ? { '--backdrop-veil': veil } : undefined">
    <picture v-if="customImage" class="backdrop__picture">
      <img
        class="backdrop__image"
        :src="customImage"
        alt=""
        fetchpriority="high"
        decoding="async"
        loading="eager"
      />
    </picture>
    <picture v-else class="backdrop__picture">
      <source
        type="image/avif"
        srcset="/images/hero-1600.avif 1600w, /images/hero-2400.avif 2400w"
        sizes="100vw"
      />
      <source
        type="image/webp"
        srcset="/images/hero-1600.webp 1600w, /images/hero-2400.webp 2400w"
        sizes="100vw"
      />
      <img
        class="backdrop__image"
        src="/images/hero-1600.jpg"
        srcset="/images/hero-1600.jpg 1600w, /images/hero-2400.jpg 2400w"
        sizes="100vw"
        width="2400"
        height="1350"
        alt=""
        fetchpriority="high"
        decoding="async"
      />
    </picture>

    <div class="backdrop__shade" />
    <div class="backdrop__grain" />
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background: #101a14;
  pointer-events: none;
}

.backdrop__picture,
.backdrop__image,
.backdrop__shade,
.backdrop__grain {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.backdrop__image {
  object-fit: cover;
  object-position: 50% 56%;
  filter: saturate(0.88) contrast(1.02) brightness(0.95);
  transform: scale(1.015);
  animation: backdrop-settle 1600ms var(--ease) both;
}

.backdrop__shade {
  background:
    linear-gradient(180deg, rgb(9 18 13 / 58%) 0%, rgb(9 18 13 / 18%) 19%, transparent 38%),
    linear-gradient(90deg, rgb(9 18 13 / 20%) 0%, transparent 46%),
    linear-gradient(0deg, rgb(9 18 13 / 24%) 0%, transparent 28%),
    rgb(9 18 13 / var(--backdrop-veil, 0.08));
}

.backdrop__grain {
  inset: -40%;
  width: 180%;
  height: 180%;
  opacity: 0.05;
  mix-blend-mode: soft-light;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='180' height='180'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.55'/%3E%3C/svg%3E");
}

html[data-theme='dark'] .backdrop__image {
  filter: saturate(0.68) contrast(1.02) brightness(0.52);
}

html[data-theme='dark'] .backdrop__shade {
  background:
    linear-gradient(180deg, rgb(5 10 7 / 70%) 0%, rgb(5 10 7 / 24%) 19%, transparent 38%),
    linear-gradient(90deg, rgb(5 10 7 / 24%) 0%, transparent 46%),
    linear-gradient(0deg, rgb(5 10 7 / 40%) 0%, transparent 30%),
    rgb(5 10 7 / var(--backdrop-veil, 0.32));
}

@keyframes backdrop-settle {
  from {
    transform: scale(1.06);
  }

  to {
    transform: scale(1.015);
  }
}

@media (max-width: 720px) {
  .backdrop__image {
    object-position: 58% 58%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .backdrop__image {
    animation: none;
    transform: scale(1.015);
  }
}
</style>

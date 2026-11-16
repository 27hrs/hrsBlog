<script setup>
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  item: { type: Object, required: true },
  index: { type: Number, default: 0 },
})

const imageFailed = ref(false)

const showImage = computed(() => Boolean(props.item.avatarUrl) && !imageFailed.value)

const letter = computed(() => {
  const name = String(props.item.siteName || '').trim()
  return name ? name.slice(0, 1) : '·'
})

const domain = computed(() => {
  const value = String(props.item.siteUrl || '').trim()
  if (!value) return ''

  try {
    return new URL(value).host.replace(/^www\./i, '')
  } catch {
    return value.replace(/^https?:\/\//i, '').replace(/^www\./i, '').split('/')[0]
  }
})

const delay = computed(() => `${Math.min(props.index, 8) * 70}ms`)
</script>

<template>
  <article class="friend-card" :style="{ '--friend-delay': delay }">
    <a
      class="friend-card__link"
      :href="item.siteUrl"
      target="_blank"
      rel="noopener noreferrer"
    >
      <span class="friend-card__avatar">
        <span class="friend-card__letter" aria-hidden="true">{{ letter }}</span>
        <img
          v-if="showImage"
          class="friend-card__img"
          :src="item.avatarUrl"
          alt=""
          width="56"
          height="56"
          loading="lazy"
          decoding="async"
          @error="imageFailed = true"
        />
      </span>

      <span class="friend-card__body">
        <span class="friend-card__name">{{ item.siteName }}</span>
        <span v-if="item.description" class="friend-card__desc">{{ item.description }}</span>
        <span class="friend-card__meta">
          <span class="friend-card__domain">{{ domain }}</span>
          <span class="friend-card__arrow" aria-hidden="true">
            <AppIcon name="arrow-up-right" :size="16" />
          </span>
        </span>
      </span>
    </a>

    <a
      v-if="item.linkPageUrl"
      class="friend-card__page"
      :href="item.linkPageUrl"
      target="_blank"
      rel="noopener noreferrer"
    >
      友链页
    </a>
  </article>
</template>

<style scoped>
.friend-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 20px 22px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-paper);
  box-shadow: var(--frame-shadow-soft);
  transition:
    transform var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
  animation: friend-rise 640ms var(--ease) both;
  animation-delay: var(--friend-delay, 0ms);
}

.friend-card:hover {
  border-color: var(--copper);
  box-shadow: var(--frame-shadow);
  transform: translateY(-2px);
}

.friend-card:focus-within {
  border-color: var(--copper);
}

.friend-card__link {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  color: inherit;
  text-decoration: none;
}

/* 整张卡片都是主链接的点击区域（“拉长链接”）。 */
.friend-card__link::after {
  position: absolute;
  inset: 0;
  content: '';
}

.friend-card__link:focus-visible {
  outline: none;
}

/* 焦点环画在覆盖整张卡片的伪元素上，而不是链接内容上。 */
.friend-card__link:focus-visible::after {
  outline: 2px solid var(--copper);
  outline-offset: -2px;
}

.friend-card__avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--paper-2);
  aspect-ratio: 1 / 1;
}

/* 头像盖在字母头像上：加载失败时 @error 会移除它，不会露出破图或撑破圆形。 */
.friend-card__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--paper-2);
  object-fit: cover;
}

.friend-card__letter {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--pine);
  color: var(--copper-bright);
  font-family: var(--font-display);
  font-size: 26px;
  font-style: italic;
  line-height: 1;
}

.friend-card__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.friend-card__name {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 1.32rem;
  font-weight: 500;
  letter-spacing: -0.01em;
  line-height: 1.3;
  overflow-wrap: break-word;
  transition: color var(--dur-fast) var(--ease);
}

.friend-card:hover .friend-card__name {
  color: var(--copper-deep);
}

.friend-card__desc {
  display: -webkit-box;
  margin-top: 8px;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.friend-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}

.friend-card__domain {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.05em;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.friend-card__arrow {
  flex: none;
  color: var(--muted);
  opacity: 0.55;
  transition:
    color var(--dur-fast) var(--ease),
    opacity var(--dur-fast) var(--ease),
    transform var(--dur) var(--ease);
}

.friend-card:hover .friend-card__arrow {
  color: var(--copper);
  opacity: 1;
  transform: translate(3px, -3px);
}

.friend-card__page {
  position: relative;
  z-index: 2;
  align-self: flex-start;
  margin-top: 14px;
  padding: 4px 9px 3px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  line-height: 1;
  transition:
    color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease);
}

.friend-card__page:hover {
  color: var(--copper);
  border-color: var(--copper);
}

@keyframes friend-rise {
  from {
    opacity: 0;
    translate: 0 16px;
  }

  to {
    opacity: 1;
    translate: 0 0;
  }
}

@media (max-width: 720px) {
  .friend-card {
    padding: 18px 18px 16px;
  }

  .friend-card__name {
    font-size: 1.2rem;
  }

  .friend-card__desc {
    font-size: 13px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .friend-card {
    animation: none;
  }

  .friend-card:hover {
    transform: none;
  }

  .friend-card:hover .friend-card__arrow {
    transform: none;
  }
}
</style>

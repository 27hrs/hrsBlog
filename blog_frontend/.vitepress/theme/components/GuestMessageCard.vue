<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  message: { type: Object, required: true },
  index: { type: Number, default: 0 },
})

const imageFailed = ref(false)

/** 头像：优先用后端派生的 QQ 头像，加载失败时回退到昵称首字。 */
const showImage = computed(() => Boolean(props.message.avatarUrl) && !imageFailed.value)

const letter = computed(() => {
  const nickname = String(props.message.nickname || '').trim()
  return nickname ? nickname.slice(0, 1) : '·'
})

const replies = computed(() => (Array.isArray(props.message.replies) ? props.message.replies : []))

const delay = computed(() => `${Math.min(props.index, 8) * 60}ms`)

const MONTHS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二']

/** 留言时间：2026.11.16 22:08 */
function formatTime(value) {
  if (!value) return ''

  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 相对时间：刚刚 / 3 分钟前 / 2 天前 / 2026.11.16 */
function formatRelative(value) {
  if (!value) return ''

  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const diff = Date.now() - date.getTime()
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff >= 0 && diff < minute) return '刚刚'
  if (diff >= 0 && diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff >= 0 && diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff >= 0 && diff < day * 30) return `${Math.floor(diff / day)} 天前`
  return `${date.getFullYear()}.${MONTHS[date.getMonth()]}月`
}
</script>

<template>
  <article class="guest-message" :style="{ '--guest-delay': delay }">
    <div class="guest-message__head">
      <span class="guest-message__avatar">
        <span class="guest-message__letter" aria-hidden="true">{{ letter }}</span>
        <img
          v-if="showImage"
          class="guest-message__img"
          :src="message.avatarUrl"
          alt=""
          width="48"
          height="48"
          loading="lazy"
          decoding="async"
          referrerpolicy="no-referrer"
          @error="imageFailed = true"
        />
      </span>

      <span class="guest-message__identity">
        <span class="guest-message__nickname">{{ message.nickname }}</span>
        <span v-if="message.createdAt" class="guest-message__time">
          <time :datetime="message.createdAt">{{ formatTime(message.createdAt) }}</time>
          <span aria-hidden="true">·</span>
          <span>{{ formatRelative(message.createdAt) }}</span>
        </span>
      </span>
    </div>

    <p class="guest-message__content">{{ message.content }}</p>

    <div v-if="replies.length" class="guest-message__replies">
      <div v-for="reply in replies" :key="reply.id ?? reply.content" class="guest-reply">
        <p class="guest-reply__label">站长回复</p>
        <p class="guest-reply__content">{{ reply.content }}</p>
      </div>
    </div>
  </article>
</template>

<style scoped>
.guest-message {
  position: relative;
  padding: 20px 22px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-paper);
  box-shadow: var(--frame-shadow-soft);
  transition:
    transform var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
  animation: guest-rise 620ms var(--ease) both;
  animation-delay: var(--guest-delay, 0ms);
}

.guest-message:hover {
  border-color: var(--copper);
  box-shadow: var(--frame-shadow);
  transform: translateY(-2px);
}

.guest-message__head {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.guest-message__avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--paper-2);
  aspect-ratio: 1 / 1;
}

/* 头像盖在字母头像上：加载失败时 @error 会移除它，不会露出破图。 */
.guest-message__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--paper-2);
  object-fit: cover;
}

.guest-message__letter {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--pine);
  color: var(--copper-bright);
  font-family: var(--font-display);
  font-size: 22px;
  font-style: italic;
  line-height: 1;
}

.guest-message__identity {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.guest-message__nickname {
  overflow: hidden;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 1.22rem;
  font-weight: 500;
  letter-spacing: -0.01em;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guest-message__time {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.05em;
}

.guest-message__content {
  margin-top: 14px;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.9;
  overflow-wrap: anywhere;
  text-wrap: pretty;
}

.guest-message__replies {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
}

.guest-reply {
  padding-left: 14px;
  border-left: 2px solid var(--copper);
}

.guest-reply__label {
  color: var(--copper-deep);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  line-height: 1;
  text-transform: uppercase;
}

.guest-reply__content {
  margin-top: 8px;
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.85;
  overflow-wrap: anywhere;
}

@keyframes guest-rise {
  from {
    opacity: 0;
    translate: 0 14px;
  }

  to {
    opacity: 1;
    translate: 0 0;
  }
}

@media (max-width: 720px) {
  .guest-message {
    padding: 17px 16px 15px;
  }

  .guest-message__nickname {
    font-size: 1.1rem;
  }

  .guest-message__content {
    margin-top: 12px;
    font-size: 13.5px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .guest-message {
    animation: none;
  }

  .guest-message:hover {
    transform: none;
  }
}
</style>

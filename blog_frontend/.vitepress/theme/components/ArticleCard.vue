<script setup>
import { withBase } from 'vitepress'
import AppIcon from './AppIcon.vue'

defineProps({
  article: { type: Object, required: true },
  index: { type: Number, required: true },
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <a
    class="post"
    :class="{ 'post--compact': compact }"
    :href="withBase(article.url)"
    :aria-labelledby="`post-title-${article.slug}`"
  >
    <span class="post__index" aria-hidden="true">{{ String(index).padStart(2, '0') }}</span>

    <div class="post__body">
      <div class="post__meta">
        <time :datetime="article.publishedAt">{{ article.dateLabel }}</time>
        <span class="post__dot" aria-hidden="true" />
        <span>约 {{ article.readingTime }} 分钟</span>
      </div>

      <h3 :id="`post-title-${article.slug}`" class="post__title">
        {{ article.title }}
      </h3>

      <p class="post__summary">{{ article.summary }}</p>

      <div v-if="article.tags?.length" class="post__tags" aria-label="文章标签">
        <span v-for="tag in article.tags" :key="tag.slug" class="post__tag">
          <span aria-hidden="true">[</span>
          {{ tag.name }}
          <span aria-hidden="true">]</span>
        </span>
      </div>
    </div>

    <figure class="post__cover" :class="{ 'post__cover--empty': !article.cover }">
      <img
        v-if="article.cover"
        :src="article.cover"
        :alt="article.coverAlt || `${article.title} 的封面`"
        width="720"
        height="480"
        loading="lazy"
        decoding="async"
      />
      <div v-else class="post__cover-fallback" aria-hidden="true">
        <span class="post__cover-num">{{ String(index).padStart(2, '0') }}</span>
        <span class="post__cover-tag">{{ article.tags?.[0]?.name || 'FIELD NOTE' }}</span>
      </div>
    </figure>

    <span class="post__arrow" aria-hidden="true">
      <AppIcon name="arrow-up-right" :size="18" />
    </span>
  </a>
</template>

<style scoped>
.post {
  position: relative;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) 236px 26px;
  grid-template-areas: 'index body cover arrow';
  gap: 24px;
  align-items: center;
  margin-inline: -46px;
  padding: 30px 46px 32px;
  border-top: 1px solid var(--line-soft);
  color: inherit;
  text-decoration: none;
  transition: background-color var(--dur) var(--ease);
}

.post:focus-visible {
  outline: 2px solid var(--copper);
  outline-offset: -2px;
}

.post--compact {
  grid-template-columns: 56px minmax(0, 1fr) 190px;
  grid-template-areas: 'index body cover';
}

.post--compact .post__arrow {
  display: none;
}

.post::before {
  position: absolute;
  top: 30px;
  bottom: 32px;
  left: 0;
  width: 2px;
  background: var(--copper);
  content: '';
  opacity: 0;
  transform: scaleY(0.4);
  transform-origin: top;
  transition:
    opacity var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.post:hover {
  background: rgb(255 255 255 / 30%);
}

html[data-theme='dark'] .post:hover {
  background: rgb(255 255 255 / 3%);
}

.post:hover::before {
  opacity: 0.85;
  transform: scaleY(1);
}

.post__index {
  grid-area: index;
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 29px;
  font-style: italic;
  font-weight: 400;
  letter-spacing: -0.03em;
  line-height: 1;
}

.post__body {
  grid-area: body;
  min-width: 0;
}

.post__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.08em;
  line-height: 1.4;
  text-transform: uppercase;
}

.post__dot {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.7;
}

.post__title {
  max-width: 24em;
  margin-top: 10px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(1.45rem, 2.1vw, 1.9rem);
  font-weight: 500;
  letter-spacing: -0.01em;
  line-height: 1.38;
  overflow-wrap: break-word;
  transition: color var(--dur-fast) var(--ease);
}

.post:hover .post__title {
  color: var(--copper-deep);
}

.post__summary {
  display: -webkit-box;
  max-width: 68ch;
  margin-top: 10px;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 14.5px;
  line-height: 1.84;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.post__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 14px;
}

.post__tag {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.06em;
  line-height: 1.3;
  transition: color var(--dur-fast) var(--ease);
}

.post:hover .post__tag {
  color: var(--copper);
}

/* Cover ------------------------------------------------------------------ */

.post__cover {
  grid-area: cover;
  align-self: center;
  width: 100%;
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--paper-2);
  transition:
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}

.post__cover img {
  width: 100%;
  height: auto;
  aspect-ratio: 3 / 2;
  object-fit: cover;
  filter: saturate(0.94) contrast(1.015);
  transition:
    transform 700ms var(--ease),
    filter var(--dur) var(--ease);
}

.post:hover .post__cover {
  border-color: var(--copper);
  box-shadow: 0 10px 26px rgb(15 26 20 / 12%);
}

.post:hover .post__cover img {
  filter: saturate(1) contrast(1.02);
  transform: scale(1.035);
}

.post__cover--empty {
  border-color: transparent;
  background: var(--pine);
}

.post__cover-fallback {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  aspect-ratio: 3 / 2;
  padding: 18px;
  color: var(--on-dark);
  background:
    radial-gradient(80% 100% at 100% 0%, rgb(196 124 76 / 26%) 0%, transparent 55%),
    var(--pine);
}

.post__cover-num {
  color: var(--copper-bright);
  font-family: var(--font-display);
  font-size: 58px;
  font-style: italic;
  letter-spacing: -0.04em;
  line-height: 0.9;
}

.post__cover-tag {
  color: var(--on-dark-soft);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.15em;
  line-height: 1;
  text-transform: uppercase;
}

.post__arrow {
  grid-area: arrow;
  align-self: center;
  color: var(--muted);
  opacity: 0.55;
  transition:
    color var(--dur-fast) var(--ease),
    opacity var(--dur-fast) var(--ease),
    transform var(--dur) var(--ease);
}

.post:hover .post__arrow {
  color: var(--copper);
  opacity: 1;
  transform: translate(3px, -3px);
}

@media (min-width: 1000px) and (max-width: 1199px) {
  .post {
    grid-template-columns: 56px minmax(0, 1fr) 200px;
    grid-template-areas: 'index body cover';
  }

  .post__arrow {
    display: none;
  }
}

@media (max-width: 999px) {
  .post {
    grid-template-columns: 30px minmax(0, 1fr) 160px;
    grid-template-areas: 'index body cover';
    gap: 18px;
  }

  .post__index {
    font-size: 26px;
  }

  .post__arrow {
    display: none;
  }
}

@media (min-width: 721px) and (max-width: 860px) {
  .post {
    align-items: start;
    margin-inline: -30px;
    padding-inline: 30px;
  }
}

@media (max-width: 720px) {
  .post {
    grid-template-columns: 30px minmax(0, 1fr);
    grid-template-areas:
      'cover cover'
      'index body';
    gap: 16px;
    align-items: start;
    margin-inline: -20px;
    padding: 24px 20px 26px;
  }

  .post::before {
    top: 24px;
    bottom: 26px;
  }

  .post__cover {
    align-self: start;
  }

  .post__cover img,
  .post__cover-fallback {
    aspect-ratio: 2.4 / 1;
  }

  .post__cover-fallback {
    padding: 14px 16px;
  }

  .post__cover-num {
    font-size: 44px;
  }

  .post__index {
    padding-top: 1px;
    font-size: 22px;
  }

  .post__title {
    margin-top: 8px;
    font-size: 1.32rem;
  }

  .post__summary {
    font-size: 13.5px;
    -webkit-line-clamp: 3;
  }

  .post__arrow {
    display: none;
  }
}
</style>

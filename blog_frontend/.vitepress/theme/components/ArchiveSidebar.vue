<script setup>
import { computed } from 'vue'
import { withBase } from 'vitepress'
import FramePanel from './FramePanel.vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  popular: { type: Array, default: () => [] },
  friendLinks: { type: Array, default: () => [] },
  activeCategory: { type: String, default: '' },
  activeTag: { type: String, default: '' },
})

const emit = defineEmits(['select-category', 'select-tag'])

const maxTagCount = computed(() => Math.max(1, ...props.tags.map((tag) => tag.count)))

function tagFontSize(count) {
  const ratio = count / maxTagCount.value
  return `${11.5 + ratio * 3.4}px`
}
</script>

<template>
  <div class="archive-sidebar">
    <FramePanel as="section" tone="paper" label="分类 / CATEGORIES" class="side-panel">
      <ul class="category-list">
        <li>
          <button
            class="category-item"
            :class="{ 'is-active': !activeCategory }"
            type="button"
            @click="emit('select-category', '')"
          >
            <span>全部文章</span>
            <span class="category-item__count">{{ categories.reduce((sum, item) => sum + item.count, 0) }}</span>
          </button>
        </li>
        <li v-for="category in categories" :key="category.id">
          <button
            class="category-item"
            :class="{ 'is-active': activeCategory === category.id }"
            type="button"
            @click="emit('select-category', activeCategory === category.id ? '' : category.id)"
          >
            <span>{{ category.label }}</span>
            <span class="category-item__count">{{ category.count }}</span>
          </button>
        </li>
      </ul>
    </FramePanel>

    <FramePanel v-if="tags.length" as="section" tone="paper" label="标签 / TAGS" class="side-panel">
      <div class="tag-cloud">
        <button
          v-for="tag in tags"
          :key="tag.slug"
          class="tag-cloud__item"
          :class="{ 'is-active': activeTag === tag.slug }"
          type="button"
          :style="{ fontSize: tagFontSize(tag.count) }"
          @click="emit('select-tag', activeTag === tag.slug ? '' : tag.slug)"
        >
          {{ tag.name }}
          <span class="tag-cloud__count">{{ tag.count }}</span>
        </button>
      </div>
    </FramePanel>

    <FramePanel v-if="popular.length" as="section" tone="paper" label="热门 / POPULAR" class="side-panel">
      <ol class="popular-list">
        <li v-for="(article, index) in popular" :key="article.slug">
          <a class="popular-item" :href="withBase(article.url)">
            <span class="popular-item__index">0{{ index + 1 }}</span>
            <span class="popular-item__body">
              <span class="popular-item__title">{{ article.title }}</span>
              <span class="popular-item__meta">{{ article.dateLabel }} · 约 {{ article.readingTime }} 分钟</span>
            </span>
          </a>
        </li>
      </ol>
    </FramePanel>

    <FramePanel
      v-if="friendLinks.length"
      as="section"
      tone="paper"
      label="友链 / FRIENDS"
      class="side-panel"
    >
      <ul class="friend-list">
        <li v-for="link in friendLinks" :key="link.url">
          <a class="friend-item" :href="link.url" target="_blank" rel="noopener noreferrer">
            <span class="friend-item__body">
              <span class="friend-item__name">{{ link.name }}</span>
              <span v-if="link.description" class="friend-item__description">{{ link.description }}</span>
            </span>
            <AppIcon name="arrow-up-right" :size="11" />
          </a>
        </li>
      </ul>
    </FramePanel>
  </div>
</template>

<style scoped>
.archive-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.side-panel {
  padding: 24px 22px 20px;
}

.category-list,
.friend-list {
  list-style: none;
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
  background: transparent;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease);
}

.category-list li:last-child .category-item {
  border-bottom: 0;
}

.category-item:hover,
.category-item.is-active {
  color: var(--copper);
}

.category-item.is-active span:first-child {
  font-weight: 600;
}

.category-item__count {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 9px 13px;
}

.tag-cloud__item {
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--font-display);
  font-style: italic;
  font-weight: 500;
  line-height: 1.3;
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease);
}

.tag-cloud__item:hover,
.tag-cloud__item.is-active {
  color: var(--copper);
}

.tag-cloud__item.is-active {
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.tag-cloud__count {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
  font-style: normal;
}

.popular-list {
  list-style: none;
}

.popular-item {
  display: flex;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid var(--line-soft);
}

.popular-list li:last-child .popular-item {
  border-bottom: 0;
}

.popular-item:hover .popular-item__title {
  color: var(--copper);
}

.popular-item__index {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: 13px;
  font-style: italic;
  line-height: 1.5;
}

.popular-item__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.popular-item__title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.45;
  transition: color var(--dur-fast) var(--ease);
}

.popular-item__meta {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.04em;
}

.friend-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line-soft);
  color: var(--ink-soft);
}

.friend-list li:last-child .friend-item {
  border-bottom: 0;
}

.friend-item:hover {
  color: var(--copper);
}

.friend-item__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.friend-item__name {
  font-size: 13px;
  line-height: 1.4;
}

.friend-item__description {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.4;
}
</style>

<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import AppSwitch from '@/components/common/AppSwitch.vue'
import ImageUploader from '@/components/common/ImageUploader.vue'
import TagInput from './TagInput.vue'
import { ARTICLE_CATEGORIES } from '@/data/categories.js'
import { useArticleDraftStore } from '@/stores/articleDraft.js'

defineProps({
  errors: { type: Object, default: () => ({}) },
})

const draft = useArticleDraftStore()

const categoryOptions = ARTICLE_CATEGORIES.map((category) => ({
  value: category.value,
  label: category.label,
}))
const visibilityOptions = [
  { value: 'public', label: '公开' },
  { value: 'unlisted', label: '不列入列表' },
  { value: 'private', label: '仅自己可见' },
]

const status = computed(() => {
  if (draft.form.status === 'PUBLISHED') {
    return { label: '准备发布', className: 'status--published' }
  }
  if (draft.form.scheduledAt) {
    return { label: '计划草稿', className: 'status--scheduled' }
  }
  return { label: '草稿', className: 'status--draft' }
})

const scheduleLabel = computed(() => {
  if (!draft.form.scheduledAt) return ''
  const date = new Date(draft.form.scheduledAt)
  if (Number.isNaN(date.getTime())) return draft.form.scheduledAt
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
})

const minDateTime = computed(() => {
  const date = new Date(Date.now() + 5 * 60 * 1000)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
})

const summaryCount = computed(() => draft.form.summary.length)

function setTags(tags) {
  draft.form.tags = tags
}

function togglePinned(value) {
  draft.form.pinned = value
}

function toggleFeatured(value) {
  draft.form.featured = value
}
</script>

<template>
  <div class="settings-stack">
    <section class="panel settings-panel" aria-labelledby="settings-publish">
      <div class="settings-panel__head">
        <div>
          <h2 id="settings-publish" class="settings-panel__title">发布设置</h2>
          <p class="settings-panel__description">决定这篇文章何时、以何种方式出现。</p>
        </div>
        <AppIcon class="settings-panel__head-icon" name="send" :size="16" />
      </div>

      <div class="settings-section">
        <div class="setting-line">
          <span class="setting-line__label">当前状态</span>
          <span class="status" :class="status.className">{{ status.label }}</span>
        </div>
        <p class="setting-tip">通过页面右上角的「保存草稿」或「发布文章」执行最终操作。</p>
      </div>

      <div class="settings-section">
        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="article-visibility">
              可见性
              <span class="local-badge">本地</span>
            </label>
          </div>
          <AppSelect
            id="article-visibility"
            v-model="draft.form.visibility"
            :options="visibilityOptions"
            aria-label="文章可见性"
          />
          <p v-if="draft.form.visibility !== 'public'" class="form-help">
            当前后端仅支持公开文章；非公开设置不会同步，发布操作会被阻止。
          </p>
          <p v-else class="form-help">发布后所有访客均可阅读。</p>
        </div>
      </div>

      <div class="settings-section">
        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="article-schedule">
              定时发布
              <span class="local-badge">本地</span>
            </label>
            <button
              v-if="draft.form.scheduledAt"
              class="text-button"
              type="button"
              @click="draft.form.scheduledAt = ''"
            >
              清除
            </button>
          </div>
          <input
            id="article-schedule"
            v-model="draft.form.scheduledAt"
            class="input"
            :class="{ 'input--invalid': errors.scheduledAt }"
            type="datetime-local"
            :min="minDateTime"
          />
          <p v-if="errors.scheduledAt" class="form-error">{{ errors.scheduledAt }}</p>
          <p v-else-if="scheduleLabel" class="form-help">
            计划于 {{ scheduleLabel }}。后端暂未提供定时任务，当前仅在草稿中记录计划时间。
          </p>
          <p v-else class="form-help">留空表示不定时；计划时间不会自动触发发布。</p>
        </div>
      </div>
    </section>

    <section class="panel settings-panel" aria-labelledby="settings-organize">
      <div class="settings-panel__head">
        <div>
          <h2 id="settings-organize" class="settings-panel__title">内容组织</h2>
          <p class="settings-panel__description">让读者更容易找到这篇文章。</p>
        </div>
        <AppIcon class="settings-panel__head-icon" name="folder" :size="16" />
      </div>

      <div class="settings-section">
        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="article-category">
              分类
              <span class="local-badge">本地</span>
            </label>
          </div>
          <AppSelect
            id="article-category"
            v-model="draft.form.category"
            :options="categoryOptions"
            aria-label="文章分类"
          />
          <p class="form-help">分类用于后台视图与本机组织，不会写入公开接口。</p>
        </div>
      </div>

      <div class="settings-section">
        <div class="form-field">
          <span class="form-label">标签</span>
          <TagInput
            :model-value="draft.form.tags"
            :error="errors.tags"
            @update:model-value="setTags"
          />
        </div>
      </div>
    </section>

    <section class="panel settings-panel" aria-labelledby="settings-cover">
      <div class="settings-panel__head">
        <div>
          <h2 id="settings-cover" class="settings-panel__title">封面与摘要</h2>
          <p class="settings-panel__description">列表页和分享卡片的第一印象。</p>
        </div>
        <AppIcon class="settings-panel__head-icon" name="image" :size="16" />
      </div>

      <div class="settings-section">
        <div class="form-field">
          <span class="form-label">封面图片</span>
          <ImageUploader v-model="draft.form.coverUrl" compact label="上传封面" />
          <p v-if="errors.coverUrl" class="form-error">{{ errors.coverUrl }}</p>
        </div>
      </div>

      <div class="settings-section">
        <div class="form-field">
          <div class="form-label-row">
            <label class="form-label" for="article-summary">摘要</label>
            <span class="form-counter">{{ summaryCount }} / 500</span>
          </div>
          <textarea
            id="article-summary"
            v-model="draft.form.summary"
            class="textarea"
            :class="{ 'textarea--invalid': errors.summary }"
            rows="3"
            maxlength="500"
            placeholder="用一两句话说明这篇文章…"
          />
          <p v-if="errors.summary" class="form-error">{{ errors.summary }}</p>
          <p v-else class="form-help">留空时列表页会显示默认占位文案。</p>
        </div>
      </div>
    </section>

    <section class="panel settings-panel" aria-labelledby="settings-promote">
      <div class="settings-panel__head">
        <div>
          <h2 id="settings-promote" class="settings-panel__title">推荐</h2>
          <p class="settings-panel__description">把重要的文章放在更显眼的位置。</p>
        </div>
        <AppIcon class="settings-panel__head-icon" name="pin" :size="16" />
      </div>

      <div class="settings-section settings-section--switches">
        <div class="switch-row">
          <div class="switch-copy">
            <p class="switch-copy__title">
              置顶文章
              <span class="local-badge">本地</span>
            </p>
            <p class="switch-copy__desc">在本地内容视图中优先展示。</p>
          </div>
          <AppSwitch
            :model-value="draft.form.pinned"
            aria-label="是否置顶文章"
            @update:model-value="togglePinned"
          />
        </div>
        <div class="divider" />
        <div class="switch-row">
          <div class="switch-copy">
            <p class="switch-copy__title">
              推荐内容
              <span class="local-badge">本地</span>
            </p>
            <p class="switch-copy__desc">标记为值得优先阅读的文章。</p>
          </div>
          <AppSwitch
            :model-value="draft.form.featured"
            aria-label="是否推荐文章"
            @update:model-value="toggleFeatured"
          />
        </div>
      </div>
    </section>

    <p class="settings-note">
      <AppIcon name="info" :size="13" />
      带「本地」标记的字段暂存于当前浏览器。后端补齐对应字段后可无缝切换为远端持久化。
    </p>
  </div>
</template>

<style scoped>
.settings-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings-panel {
  overflow: hidden;
}

.settings-panel__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 15px 17px 0;
}

.settings-panel__title {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  line-height: 1.4;
  color: var(--text-primary);
}

.settings-panel__description {
  margin-top: 3px;
  font-size: var(--text-2xs);
  line-height: 1.5;
  color: var(--text-tertiary);
}

.settings-panel__head-icon {
  margin-top: 1px;
  color: var(--text-tertiary);
}

.settings-section {
  padding: 14px 17px;
}

.settings-section + .settings-section {
  border-top: 1px solid var(--border-subtle);
}

.settings-section--switches {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 15px;
  padding-bottom: 16px;
}

.setting-line {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.setting-line__label {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
}

.setting-tip {
  margin-top: 6px;
  font-size: var(--text-2xs);
  line-height: 1.55;
  color: var(--text-tertiary);
}

.local-badge {
  display: inline-flex;
  align-items: center;
  height: 16px;
  padding: 0 4px;
  margin-left: 3px;
  font-size: 9px;
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
  vertical-align: 1px;
  background: var(--bg-inset);
  border: 1px solid var(--border-subtle);
  border-radius: 3px;
}

.text-button {
  padding: 0;
  font-size: var(--text-2xs);
  font-weight: var(--weight-medium);
  color: var(--brand-text);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.text-button:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.settings-note {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding: 0 3px 4px;
  font-size: 10px;
  line-height: 1.55;
  color: var(--text-tertiary);
}

.settings-note .app-icon {
  margin-top: 1px;
}

.settings-section :deep(.switch-row) {
  align-items: flex-start;
}

.settings-section :deep(.image-uploader__preview) {
  aspect-ratio: 16 / 8.6;
}
</style>

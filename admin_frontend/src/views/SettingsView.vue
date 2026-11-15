<script setup>
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AppModal from '@/components/common/AppModal.vue'
import SchemaForm from '@/components/settings/SchemaForm.vue'
import {
  firstSectionWithErrors,
  settingSections,
} from '@/config/settingsRegistry.js'
import { useSettingsStore } from '@/stores/settings.js'
import { useToastStore } from '@/stores/toast.js'
import { formatDateTime } from '@/utils/format.js'

const settings = useSettingsStore()
const toast = useToastStore()

const activeKey = ref(settingSections[0].key)
const resetOpen = ref(false)

const activeSection = computed(
  () => settingSections.find((section) => section.key === activeKey.value) || settingSections[0],
)
const activeIndex = computed(
  () => settingSections.findIndex((section) => section.key === activeSection.value.key) + 1,
)
const totalFields = computed(() =>
  settingSections.reduce((count, section) => count + section.fields.length, 0),
)

const sourceDescription = computed(() => {
  if (settings.loading) return '正在读取设置…'
  if (settings.source === 'remote') return '设置已连接服务端'
  return '当前保存在本机浏览器'
})

onMounted(() => {
  settings.load()
})

function selectSection(key) {
  activeKey.value = key
}

function onFieldUpdate({ key, value }) {
  settings.updateField(key, value)
}

async function saveAllSettings() {
  const result = await settings.saveAll()
  if (!result.ok) {
    if (result.unauthorized) return
    toast.warning(result.message || '请检查表单内容')
    const firstErrorSection = firstSectionWithErrors(settings.errors)
    if (firstErrorSection) activeKey.value = firstErrorSection
    return
  }

  if (result.localOnly) {
    toast.warning('设置已保存到本机。当前后端尚未提供站点设置接口。')
  } else {
    toast.success('所有设置已保存')
  }
}

function confirmReset() {
  settings.resetToDefaults()
  resetOpen.value = false
  toast.info('已恢复默认值，点击「保存全部」后生效')
}

function formatSaveTime(value) {
  return value ? formatDateTime(value) : '尚未保存'
}
</script>

<template>
  <div class="settings-page page-reveal">
    <header class="settings-head">
      <div class="settings-head__copy">
        <p class="settings-head__eyebrow">
          SYSTEM / SITE SETTINGS
          <span class="settings-head__eyebrow-rule" aria-hidden="true" />
          {{ settingSections.length }} 个分类 · {{ totalFields }} 项配置
        </p>
        <h1 class="settings-head__title">站点设置</h1>
        <p class="settings-head__lead">把这里调成你希望读者记住的样子。所有更改统一保存。</p>
      </div>

      <div class="settings-head__status">
        <span
          class="sync-pill"
          :class="settings.source === 'remote' ? 'sync-pill--remote' : 'sync-pill--local'"
          :title="sourceDescription"
        >
          <span class="sync-pill__dot" />
          {{ settings.source === 'remote' ? '服务端同步' : '本机保存' }}
        </span>
        <span v-if="settings.lastSavedAt" class="settings-head__saved-at">
          {{ formatSaveTime(settings.lastSavedAt) }}
        </span>
      </div>
    </header>

    <div
      v-if="!settings.loading && settings.source === 'local'"
      class="callout callout--info settings-source-note"
    >
      <AppIcon class="callout__icon" name="info" :size="15" />
      <span>
        后端尚未提供站点设置读写接口，配置会安全地保存在当前浏览器；它不会自动同步到 VitePress 构建产物。
      </span>
    </div>
    <div v-else-if="settings.loadError" class="callout callout--warning settings-source-note">
      <AppIcon class="callout__icon" name="alert-triangle" :size="15" />
      <span>{{ settings.loadError }}，当前使用本机缓存。</span>
    </div>

    <div class="settings-layout">
      <aside class="settings-nav" aria-label="设置分类">
        <div class="settings-nav__head">
          <span>设置分类</span>
          <span class="settings-nav__count">{{ settingSections.length }}</span>
        </div>
        <nav class="settings-nav__list">
          <button
            v-for="(section, index) in settingSections"
            :key="section.key"
            class="settings-nav__item"
            :class="{ 'is-active': section.key === activeKey }"
            type="button"
            :aria-current="section.key === activeKey ? 'page' : undefined"
            @click="selectSection(section.key)"
          >
            <span class="settings-nav__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="settings-nav__icon">
              <AppIcon :name="section.icon" :size="15" />
            </span>
            <span class="settings-nav__label">{{ section.label }}</span>
            <span
              v-if="settings.sectionDirty(section.key)"
              class="settings-nav__dirty"
              title="有未保存的更改"
            />
          </button>
        </nav>
        <div class="settings-nav__foot">
          <span class="pulse-dot" />
          <span>{{ sourceDescription }}</span>
        </div>
      </aside>

      <section class="settings-main" :aria-labelledby="`settings-section-${activeSection.key}`">
        <div class="settings-main__head">
          <div>
            <p class="settings-main__eyebrow">
              <span class="mono">SET / {{ String(activeIndex).padStart(2, '0') }}</span>
              <span v-if="settings.sectionDirty(activeSection.key)" class="settings-main__unsaved">
                未保存
              </span>
            </p>
            <h2 :id="`settings-section-${activeSection.key}`" class="settings-main__title">
              {{ activeSection.label }}
            </h2>
            <p class="settings-main__description">{{ activeSection.description }}</p>
          </div>
          <AppIcon class="settings-main__watermark" :name="activeSection.icon" :size="22" />
        </div>

        <div v-if="settings.loading" class="panel settings-form-panel settings-form-skeleton">
          <div v-for="index in 6" :key="index" class="settings-skeleton-row">
            <span class="skeleton skeleton--text" style="width: 24%" />
            <span class="skeleton settings-skeleton-row__input" />
          </div>
        </div>

        <Transition v-else name="section-swap" mode="out-in">
          <div :key="activeSection.key" class="panel settings-form-panel">
            <SchemaForm
              :section="activeSection"
              :model-value="settings.values"
              :errors="settings.errors"
              @update-field="onFieldUpdate"
            />
          </div>
        </Transition>

        <div class="settings-savebar">
          <div class="settings-savebar__status">
            <span v-if="settings.loading" class="settings-savebar__muted">正在读取配置…</span>
            <span v-else-if="settings.isDirty" class="settings-savebar__dirty">
              <span class="settings-savebar__dot" />
              {{ settings.dirtyFieldCount }} 项更改尚未保存
            </span>
            <span v-else class="settings-savebar__saved">
              <AppIcon name="check-circle" :size="13" />
              所有更改已保存
            </span>
            <span class="settings-savebar__time">
              {{ formatSaveTime(settings.lastSavedAt) }}
            </span>
          </div>

          <div class="settings-savebar__actions">
            <button
              class="btn btn--ghost btn--sm"
              type="button"
              :disabled="settings.loading || settings.saving"
              @click="resetOpen = true"
            >
              <AppIcon name="rotate-ccw" :size="13" />
              恢复默认
            </button>
            <button
              class="btn btn--primary"
              type="button"
              :disabled="settings.loading || settings.saving || !settings.isDirty"
              @click="saveAllSettings"
            >
              <span v-if="settings.saving" class="btn__spinner" />
              <AppIcon v-else name="save" :size="14" />
              {{ settings.saving ? '保存中…' : '保存全部设置' }}
            </button>
          </div>
        </div>
      </section>
    </div>

    <AppModal
      v-model:open="resetOpen"
      title="恢复默认设置？"
      description="当前表单中的所有修改会被默认值覆盖。恢复后仍需点击「保存全部设置」才会写入存储。"
      size="sm"
    >
      <div class="reset-modal__note">
        <AppIcon name="alert-triangle" :size="17" />
        <span>这一操作不会立即覆盖已保存的配置，你仍可以在保存前关闭页面放弃更改。</span>
      </div>
      <template #footer>
        <button class="btn" type="button" @click="resetOpen = false">取消</button>
        <button class="btn btn--danger" type="button" @click="confirmReset">恢复默认值</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-head {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
}

.settings-head__copy {
  min-width: 0;
}

.settings-head__eyebrow {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 11px;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

.settings-head__eyebrow-rule {
  width: 18px;
  height: 1px;
  background: var(--border-strong);
}

.settings-head__title {
  font-size: clamp(23px, 2.4vw, 28px);
  font-weight: var(--weight-semibold);
  line-height: 1.2;
  color: var(--text-primary);
  letter-spacing: -0.04em;
}

.settings-head__lead {
  margin-top: 7px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.settings-head__status {
  display: flex;
  flex-direction: column;
  gap: 5px;
  align-items: flex-end;
  flex: none;
}

.sync-pill {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 25px;
  padding: 0 9px;
  font-size: 10px;
  font-weight: var(--weight-medium);
  border-radius: var(--radius-full);
}

.sync-pill--remote {
  color: var(--success-text);
  background: var(--success-soft);
}

.sync-pill--local {
  color: var(--warning-text);
  background: var(--warning-soft);
}

.sync-pill__dot {
  width: 4px;
  height: 4px;
  background: currentcolor;
  border-radius: 50%;
}

.settings-head__saved-at {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
}

.settings-source-note {
  align-items: flex-start;
}

.settings-layout {
  display: grid;
  grid-template-columns: 214px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.settings-nav {
  position: sticky;
  top: calc(var(--header-height) + 18px);
  overflow: hidden;
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  border-radius: var(--card-radius);
  box-shadow: var(--shadow-card);
}

.settings-nav__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 9px;
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.settings-nav__count {
  display: inline-grid;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  font-family: var(--font-sans);
  font-size: 9px;
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border-radius: 3px;
  place-items: center;
}

.settings-nav__list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 0 7px 8px;
}

.settings-nav__item {
  position: relative;
  display: flex;
  gap: 7px;
  align-items: center;
  width: 100%;
  height: 38px;
  padding: 0 8px;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.settings-nav__item:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.settings-nav__item.is-active {
  color: var(--brand-text);
  background: var(--brand-soft);
}

.settings-nav__index {
  width: 16px;
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--text-tertiary);
}

.settings-nav__icon {
  display: grid;
  flex: none;
  width: 18px;
  height: 18px;
  place-items: center;
}

.settings-nav__item.is-active .settings-nav__icon {
  color: var(--brand-text);
}

.settings-nav__label {
  flex: 1;
  font-weight: var(--weight-medium);
}

.settings-nav__dirty {
  width: 5px;
  height: 5px;
  background: var(--warning);
  border-radius: 50%;
  box-shadow: 0 0 0 2px var(--warning-soft);
}

.settings-nav__foot {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 10px 14px;
  font-size: 9px;
  color: var(--text-tertiary);
  background: var(--bg-inset);
  border-top: 1px solid var(--border-subtle);
}

.settings-nav__foot .pulse-dot {
  width: 5px;
  height: 5px;
}

.settings-main {
  display: flex;
  flex-direction: column;
  gap: 13px;
  min-width: 0;
}

.settings-main__head {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 2px 3px 4px;
}

.settings-main__eyebrow {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 7px;
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.03em;
}

.settings-main__unsaved {
  display: inline-flex;
  align-items: center;
  height: 17px;
  padding: 0 5px;
  font-family: var(--font-sans);
  font-size: 9px;
  color: var(--warning-text);
  background: var(--warning-soft);
  border-radius: 3px;
}

.settings-main__title {
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  line-height: 1.3;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.settings-main__description {
  max-width: 620px;
  margin-top: 5px;
  font-size: var(--text-xs);
  line-height: 1.65;
  color: var(--text-tertiary);
}

.settings-main__watermark {
  flex: none;
  margin-top: 7px;
  color: var(--text-disabled);
}

.settings-form-panel {
  padding: 24px 24px 26px;
}

.settings-form-skeleton {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.settings-skeleton-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-skeleton-row__input {
  height: 36px;
  border-radius: var(--radius-sm);
}

.settings-savebar {
  position: sticky;
  bottom: 12px;
  z-index: 10;
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 9px 11px 9px 16px;
  background: color-mix(in srgb, var(--bg-elevated) 92%, transparent);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(14px);
}

.settings-savebar__status {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 10px;
  align-items: center;
  font-size: var(--text-2xs);
  color: var(--text-tertiary);
}

.settings-savebar__dirty {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-weight: var(--weight-medium);
  color: var(--warning-text);
}

.settings-savebar__dot {
  width: 5px;
  height: 5px;
  background: var(--warning);
  border-radius: 50%;
}

.settings-savebar__saved {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  color: var(--success-text);
}

.settings-savebar__time {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
}

.settings-savebar__muted {
  color: var(--text-tertiary);
}

.settings-savebar__actions {
  display: flex;
  flex: none;
  gap: 6px;
  align-items: center;
}

.settings-savebar__actions .btn--primary {
  min-width: 130px;
}

.section-swap-enter-active,
.section-swap-leave-active {
  transition:
    opacity var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.section-swap-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.section-swap-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}

.reset-modal__note {
  display: flex;
  gap: 9px;
  padding: 11px 12px;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  background: var(--bg-inset);
  border-radius: var(--radius-sm);
}

.reset-modal__note .app-icon {
  flex: none;
  margin-top: 1px;
  color: var(--warning);
}

@media (max-width: 1024px) {
  .settings-layout {
    grid-template-columns: 190px minmax(0, 1fr);
  }
}

@media (max-width: 860px) {
  .settings-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .settings-head__status {
    align-items: flex-start;
  }

  .settings-layout {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .settings-nav {
    position: static;
    width: 100%;
  }

  .settings-nav__head {
    display: none;
  }

  .settings-nav__list {
    flex-direction: row;
    gap: 4px;
    padding: 7px;
    overflow-x: auto;
  }

  .settings-nav__item {
    width: auto;
    flex: none;
    height: 32px;
    padding: 0 9px;
    white-space: nowrap;
  }

  .settings-nav__index {
    display: none;
  }

  .settings-nav__foot {
    display: none;
  }

  .settings-main {
    width: 100%;
  }

  .settings-form-panel {
    padding: 19px 17px 21px;
  }

  .settings-savebar {
    bottom: 8px;
  }
}

@media (max-width: 600px) {
  .settings-main__head {
    padding: 2px 2px;
  }

  .settings-savebar {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
    padding: 10px;
  }

  .settings-savebar__actions {
    width: 100%;
    justify-content: space-between;
  }

  .settings-savebar__actions .btn--primary {
    flex: 1;
  }

  .settings-head__saved-at {
    display: none;
  }

  .settings-source-note {
    font-size: 10px;
  }
}
</style>

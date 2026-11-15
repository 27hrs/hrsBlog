<script setup>
import FramePanel from '../components/FramePanel.vue'
import AppIcon from '../components/AppIcon.vue'
</script>

<template>
  <div class="notfound">
    <div class="wrap">
      <FramePanel as="section" tone="panel" label="404 / LOST" class="notfound__panel">
        <p class="notfound__code">404</p>
        <h1 class="notfound__title">这一页漂走了。</h1>
        <p class="notfound__text">
          它可能被移动、删除，或者从未存在。你可以从这里回到有光的地方。
        </p>
        <div class="notfound__actions">
          <a class="notfound__link" href="/">
            <AppIcon name="arrow-right" :size="14" />
            回到首页
          </a>
          <a class="notfound__link notfound__link--muted" href="/articles/">
            浏览文章
          </a>
        </div>
      </FramePanel>
    </div>
  </div>
</template>

<style scoped>
.notfound {
  display: flex;
  align-items: center;
  min-height: calc(100svh - var(--header-h) - 220px);
  padding: 64px 0 100px;
}

.notfound__panel {
  width: min(720px, 100%);
  margin-inline: auto;
  padding: clamp(38px, 6vw, 64px);
}

.notfound__code {
  color: var(--copper);
  font-family: var(--font-display);
  font-size: clamp(5rem, 13vw, 9rem);
  font-style: italic;
  letter-spacing: -0.06em;
  line-height: 0.9;
}

.notfound__title {
  margin-top: 12px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.notfound__text {
  max-width: 34em;
  margin-top: 16px;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.9;
}

.notfound__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 30px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.notfound__link {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  line-height: 1;
  text-transform: uppercase;
}

.notfound__link:hover {
  color: var(--copper);
}

.notfound__link--muted {
  color: var(--muted);
}
</style>

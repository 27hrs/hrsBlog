<script setup>
import { computed } from 'vue'
import { Content, useData } from 'vitepress'
import PageBackdrop from './components/PageBackdrop.vue'
import BlogHeader from './components/BlogHeader.vue'
import BlogFooter from './components/BlogFooter.vue'
import HomeView from './views/HomeView.vue'
import ArticleListView from './views/ArticleListView.vue'
import ArticleView from './views/ArticleView.vue'
import AboutView from './views/AboutView.vue'
import LinksView from './views/LinksView.vue'
import SimplePage from './views/SimplePage.vue'
import NotFoundView from './views/NotFoundView.vue'

const { frontmatter, page } = useData()

const layout = computed(() => {
  if (page.value.isNotFound) return 'notfound'
  return frontmatter.value.layout || 'page'
})
</script>

<template>
  <div class="site" :class="`site--${layout}`">
    <a class="skip-link" href="#main">跳到主要内容</a>
    <PageBackdrop />
    <BlogHeader />

    <main id="main" class="site__main" tabindex="-1">
      <HomeView v-if="layout === 'home'" />
      <ArticleListView v-else-if="layout === 'articles'" />
      <ArticleView v-else-if="layout === 'article'">
        <Content />
      </ArticleView>
      <AboutView v-else-if="layout === 'about'">
        <Content />
      </AboutView>
      <LinksView v-else-if="layout === 'links'" />
      <NotFoundView v-else-if="layout === 'notfound'" />
      <SimplePage v-else>
        <Content />
      </SimplePage>
    </main>

    <BlogFooter />
  </div>
</template>

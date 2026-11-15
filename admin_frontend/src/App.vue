<script setup>
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import AppToast from '@/components/common/AppToast.vue'
import { useThemeStore } from '@/stores/theme.js'

const theme = useThemeStore()

onMounted(() => {
  theme.init()
})
</script>

<template>
  <RouterView />
  <AppToast />
</template>

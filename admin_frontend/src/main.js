import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router/index.js'
import { setUnauthorizedHandler } from './api/client.js'
import { setNotifier } from './api/notify.js'
import { useAuthStore } from './stores/auth.js'
import { useToastStore } from './stores/toast.js'
import './styles/theme.css'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const auth = useAuthStore(pinia)
const toast = useToastStore(pinia)

setNotifier((message, type = 'info') => {
  toast.push(message, { type })
})

setUnauthorizedHandler(() => {
  const hadSession = Boolean(auth.token)
  auth.clearSession()
  if (router.currentRoute.value.name !== 'login') {
    const redirect = router.currentRoute.value.fullPath
    router.replace({ name: 'login', query: redirect ? { redirect } : {} })
  }
  if (hadSession) {
    toast.push('登录状态已失效，请重新登录', { type: 'warning' })
  }
})

app.use(router)
app.mount('#app')

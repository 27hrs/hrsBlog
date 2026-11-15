import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
function createBackendProxy(target) {
  return {
    target,
    changeOrigin: true,
    configure(proxy) {
      // The browser talks to Vite same-origin; the backend's strict CORS
      // allow-list must not see the dev server origin on the proxied request.
      proxy.on('proxyReq', (proxyRequest) => {
        proxyRequest.removeHeader('origin')
      })
    },
  }
}

export default defineConfig(({ mode }) => {

  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_DEV_BACKEND_TARGET || 'http://localhost:27000'

  return {
    base: '/admin/',
    plugins: [vue()],
    server: {
      port: 27081,
      strictPort: true,
      proxy: {
        '/api': createBackendProxy(backendTarget),
        '/assets/image': createBackendProxy(backendTarget),
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      target: 'es2022',
      cssCodeSplit: true,
      chunkSizeWarningLimit: 900,
    },
  }
})

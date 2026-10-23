import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 後端 CORS 預設允許 http://localhost:5173，改 port 時後端的 CORS_ALLOWED_ORIGINS 也要一起改
    port: 5173,
    strictPort: true,
  },
})

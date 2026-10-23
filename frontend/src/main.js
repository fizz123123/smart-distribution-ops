import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import 'element-plus/dist/index.css'
// 設計 token 與全站樣式；要在 Element Plus 的樣式之後載入，主題變數才會覆蓋預設值
import '@/styles/base.css'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

async function bootstrap() {
  const app = createApp(App)

  app.use(createPinia())
  // Element Plus 使用繁體中文語系（日期選擇器、分頁等內建文字）
  app.use(ElementPlus, { locale: zhTw })

  // 重新整理頁面時，先用保存的 token 取回目前使用者，再交給路由判斷權限
  await useAuthStore().restore()

  app.use(router)
  app.mount('#app')
}

bootstrap()

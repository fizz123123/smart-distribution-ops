import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import 'element-plus/dist/index.css'

import router from './router'
import { useAuthStore } from './stores/auth'

async function bootstrap() {
  // 根元件目前只負責顯示路由畫面；之後建立 App.vue 時改成 createApp(App)
  const app = createApp({ render: () => h(RouterView) })

  app.use(createPinia())
  // Element Plus 使用繁體中文語系（日期選擇器、分頁等內建文字）
  app.use(ElementPlus, { locale: zhTw })

  // 重新整理頁面時，先用保存的 token 取回目前使用者，再交給路由判斷權限
  await useAuthStore().restore()

  app.use(router)
  app.mount('#app')
}

bootstrap()

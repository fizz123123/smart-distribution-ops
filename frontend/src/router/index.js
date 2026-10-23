import { createRouter, createWebHistory } from 'vue-router'

import { canAccess } from '@/config/navigation'
import { useAuthStore } from '@/stores/auth'

export const LOGIN_PATH = '/login'
export const FORBIDDEN_PATH = '/forbidden'

const BRAND = 'THERMAL ROLL'

/**
 * 路由約定：
 * - 不需登入、也不套用版面的畫面（例如登入頁）設定 meta: { public: true }
 * - 需要權限的畫面設定 meta: { navigation: findNavigationItem('畫面編號') }，
 *   路由守衛會依 config/navigation.js 的權限擋下沒有權限的人
 * - meta.title 是瀏覽器分頁標題
 *
 * 範例：
 * {
 *   path: '/login',
 *   component: () => import('@/views/LoginView.vue'),
 *   meta: { title: '登入', public: true },
 * },
 * {
 *   path: 'customers',
 *   component: () => import('@/views/customers/CustomerListView.vue'),
 *   meta: { title: '客戶', navigation: findNavigationItem('MDM-01') },
 * },
 */
const routes = []

const router = createRouter({
  // History Mode：部署時 CloudFront 需對前端路徑回傳 index.html（13 號）
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// 前端只擋「看不到的頁面」，API 仍會由後端重新檢查權限
router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.path === LOGIN_PATH) {
    return authStore.isLoggedIn ? '/' : true
  }

  if (to.meta.public) {
    return true
  }

  if (!authStore.isLoggedIn) {
    return { path: LOGIN_PATH, query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }

  const item = to.meta.navigation
  if (item && !canAccess(item, authStore)) {
    return FORBIDDEN_PATH
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title}｜${BRAND}` : BRAND
})

export default router

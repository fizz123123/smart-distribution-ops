import { createRouter, createWebHistory } from 'vue-router'

import { canAccess, findNavigationItem } from '@/config/navigation'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

export const LOGIN_PATH = '/login'
export const FORBIDDEN_PATH = '/forbidden'

const BRAND = 'THERMAL ROLL'

/**
 * 路由約定：
 * - 不需登入、也不套用 AppLayout 的畫面（例如登入頁）設定 meta: { public: true }
 * - 需要權限的畫面設定 meta: { navigation: findNavigationItem('畫面編號') }，
 *   路由守衛會依 config/navigation.js 的權限擋下沒有權限的人
 * - meta.title 是瀏覽器分頁標題
 */
const routes = [
  {
    path: LOGIN_PATH,
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登入', public: true },
  },
  {
    path: '/',
    component: AppLayout,
    children: [
      // 登入後的第一頁：我的工作台（HOME-01），所有人都一樣
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'admin/users',
        name: 'users',
        component: () => import('@/views/users/UserListView.vue'),
        meta: { title: '使用者與權限', navigation: findNavigationItem('ADM-01') },
      },
      {
        path: 'admin/users/:id(\\d+)',
        name: 'userDetail',
        component: () => import('@/views/users/UserDetailView.vue'),
        meta: { title: '使用者詳情', navigation: findNavigationItem('ADM-01') },
      },
      {
        path: FORBIDDEN_PATH.slice(1),
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: '沒有權限' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: '找不到頁面' },
      },
    ],
  },
]

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

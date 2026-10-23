import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import * as authApi from '@/api/auth'
import { clearToken, getToken, onUnauthorized, setToken } from '@/api/http'
import { ROLE_LABELS } from '@/config/identity'

/**
 * 目前使用者與權限。前端只用權限決定顯示什麼，最後是否允許仍由後端判斷（13 號）。
 * user 的形狀同後端 CurrentUserResponse：{ id, email, name, roles[], permissions[] }
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)

  const isLoggedIn = computed(() => user.value !== null)
  const permissions = computed(() => new Set(user.value?.permissions ?? []))

  // 帳號選單顯示用，例如「業務・業務主管」
  const roleLabel = computed(() =>
    (user.value?.roles ?? []).map((role) => ROLE_LABELS[role] ?? role).join('・'),
  )

  function hasPermission(permissionCode) {
    return permissions.value.has(permissionCode)
  }

  // 待核准清單：有任何一種核准權限就顯示
  const canApprove = computed(() =>
    [...permissions.value].some((permission) => permission.endsWith('_APPROVE')),
  )

  async function login(email, password) {
    const result = await authApi.login(email, password)
    setToken(result.accessToken)
    user.value = result.user
  }

  function logout() {
    clearToken()
    user.value = null
  }

  // 有保存的 token 時重新取回使用者；token 失效會由 401 處理清除
  async function restore() {
    if (!getToken()) {
      return
    }

    try {
      user.value = await authApi.fetchCurrentUser()
    } catch {
      logout()
    }
  }

  onUnauthorized(() => {
    user.value = null
    // 和 router 的 LOGIN_PATH 相同；這裡不 import router，避免 store 與 router 互相依賴
    if (window.location.pathname === '/login') {
      return
    }
    // 用整頁導向，確保所有畫面狀態都被清除
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.assign(`/login?redirect=${redirect}`)
  })

  return { user, isLoggedIn, roleLabel, hasPermission, canApprove, login, logout, restore }
})

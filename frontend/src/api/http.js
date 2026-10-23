import axios from 'axios'

import { ApiError } from './errors'

const TOKEN_KEY = 'accessToken'

/**
 * 全專案共用的 axios 實例：
 * - 每個請求自動帶上 JWT（Bearer）
 * - 401 時清除登入狀態並通知外部導回登入頁
 * - 失敗時一律丟出 ApiError，畫面直接用 error.message、error.fieldErrors
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// token 放在 sessionStorage：關閉分頁就登出，不會留在共用電腦上
export function getToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    sessionStorage.setItem(TOKEN_KEY, token)
  } catch {
    // 私密瀏覽等情況下 sessionStorage 可能不可用；這時重新整理頁面就需要重新登入
  }
}

export function clearToken() {
  try {
    sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // 同上
  }
}

// 由 auth store 註冊，避免 http 直接依賴 router 與 store
let unauthorizedHandler = () => {}

export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!error.response) {
      return Promise.reject(ApiError.network())
    }

    // 登入 API 本身的 401 是帳密錯誤，要留給登入頁顯示
    const isLoginRequest = error.config?.url?.endsWith('/auth/login')
    if (error.response.status === 401 && !isLoginRequest) {
      clearToken()
      unauthorizedHandler()
    }

    return Promise.reject(ApiError.fromResponse(error.response.status, error.response.data))
  },
)

export function getErrorMessage(error) {
  return error?.message || '系統發生錯誤，請稍後再試。'
}

export default http

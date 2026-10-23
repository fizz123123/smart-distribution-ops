import { computed, reactive, ref, shallowRef } from 'vue'

import { useAuthStore } from '@/stores/auth'

// 登入表單的狀態與送出邏輯（AUTH-01）。畫面只負責顯示，這裡負責驗證與呼叫 API。
export function useLogin({ onSuccess } = {}) {
  const authStore = useAuthStore()
  const credentials = reactive({ email: '', password: '' })
  const isSubmitting = ref(false)
  const loginError = shallowRef(null)

  const canSubmit = computed(
    () => credentials.email.trim() !== '' && credentials.password !== '' && !isSubmitting.value,
  )

  // Element Plus 表單驗證規則：失焦時檢查，錯誤訊息寫清楚要怎麼修正
  const rules = {
    email: [
      { required: true, message: '請輸入 Email', trigger: 'blur' },
      { type: 'email', message: 'Email 格式不正確，例如 name@company.com', trigger: 'blur' },
    ],
    password: [{ required: true, message: '請輸入密碼', trigger: 'blur' }],
  }

  async function submitLogin(formRef) {
    loginError.value = null
    const isValid = await formRef?.validate().catch(() => false)
    if (isValid === false || !canSubmit.value) return

    isSubmitting.value = true
    try {
      await authStore.login(credentials.email.trim(), credentials.password)
      onSuccess?.()
    } catch (error) {
      loginError.value = error
      // 登入失敗時清空密碼，帳號保留，方便重新輸入
      credentials.password = ''
    } finally {
      isSubmitting.value = false
    }
  }

  return { credentials, rules, isSubmitting, loginError, canSubmit, submitLogin }
}

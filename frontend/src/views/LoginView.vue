<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLogin } from '@/composables/useLogin'
import AuthLayout from '@/layouts/AuthLayout.vue'

// 登入頁（AUTH-01）。不套用 AppLayout，外框用 AuthLayout（與設定密碼頁共用）。
const route = useRoute()
const router = useRouter()
const formRef = ref(null)

const { credentials, rules, isSubmitting, loginError, canSubmit, submitLogin } = useLogin({
  // 登入成功後回到原本要去的頁面；沒有的話回工作台。只接受站內路徑，避免被導到外部網址
  onSuccess: () => {
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
  },
})
</script>

<template>
  <AuthLayout>
    <section class="card login-card" aria-labelledby="login-title">
      <h1 id="login-title">登入</h1>
      <p class="sub">請輸入你的 Email 和密碼</p>

      <div v-if="loginError" class="error" role="alert">
        <b>{{ loginError.message }}</b>
        <span v-if="loginError.traceId" class="mono">追蹤碼：{{ loginError.traceId }}</span>
      </div>

      <el-form
        ref="formRef"
        :model="credentials"
        :rules="rules"
        label-position="top"
        :disabled="isSubmitting"
        @submit.prevent="submitLogin(formRef)"
      >
        <el-form-item label="帳號" prop="email" for="login-email">
          <el-input
            id="login-email"
            v-model="credentials.email"
            name="email"
            type="email"
            autocomplete="username"
            placeholder="name@company.com"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密碼" prop="password" for="login-password">
          <el-input
            id="login-password"
            v-model="credentials.password"
            name="password"
            type="password"
            autocomplete="current-password"
            show-password
            placeholder="請輸入密碼"
            size="large"
          />
        </el-form-item>

        <el-button
          type="primary"
          native-type="submit"
          size="large"
          class="submit"
          :loading="isSubmitting"
          :disabled="!canSubmit"
        >
          {{ isSubmitting ? '登入中…' : '登入' }}
        </el-button>
      </el-form>

      <p class="help">忘記密碼或帳號被停用，請聯絡系統管理員。</p>
    </section>
  </AuthLayout>
</template>

<style scoped>
/* 右側登入卡 */
.login-card {
  padding: 36px 36px 28px;
}

.login-card h1 {
  font-size: 26px;
  font-weight: 800;
  color: var(--accent-strong);
}

.login-card > .sub {
  margin: 4px 0 24px;
  font-size: 14px;
}

.error {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 18px;
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
}

.submit {
  width: 100%;
  margin-top: 8px;
}

.help {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
  color: var(--ink-3);
  text-align: center;
}
</style>

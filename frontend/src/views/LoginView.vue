<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLogin } from '@/composables/useLogin'

// 登入頁（AUTH-01）。不套用 AppLayout，獨立全版面。
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
  <div class="blobs" aria-hidden="true"><i class="b1"></i><i class="b2"></i><i class="b3"></i></div>

  <main class="login-page">
    <section class="brand">
      <div class="logo" aria-hidden="true">
        <span class="logo-mark mono">TR</span>
        <span>THERMAL<em>ROLL</em></span>
      </div>
      <p class="headline">保溫與防蝕材料<br />經銷營運系統</p>
      <p class="lead">客戶、訂單、採購、庫存、出貨與應收，<br />在同一套系統裡追蹤到底。</p>
      <ul class="points">
        <li><span class="dot" aria-hidden="true"></span>主管核准後才預留庫存</li>
        <li><span class="dot" aria-hidden="true"></span>缺料自動標示，一鍵轉採購</li>
        <li><span class="dot" aria-hidden="true"></span>出貨過帳即建立應收</li>
      </ul>
    </section>

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
  </main>

  <footer class="foot mono">THERMAL ROLL</footer>
</template>

<style scoped>
.blobs i {
  position: fixed;
  border-radius: 50%;
  filter: blur(90px);
  z-index: 0;
  pointer-events: none;
}

.b1 {
  width: 760px;
  height: 760px;
  left: -220px;
  top: -240px;
  background: var(--brand-teal);
  opacity: 0.42;
}

.b2 {
  width: 720px;
  height: 720px;
  right: -200px;
  bottom: -240px;
  background: var(--brand-ocean);
  opacity: 0.3;
}

.b3 {
  width: 420px;
  height: 420px;
  left: 40%;
  top: 30%;
  background: var(--brand-sage);
  opacity: 0.3;
}

.login-page {
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 40px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  align-items: center;
  gap: 64px;
  max-width: 1080px;
  margin: 0 auto;
  padding: 48px 32px 24px;
}

/* 左側品牌區 */
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.4px;
  color: var(--ink);
}

.logo em {
  font-style: normal;
  color: var(--accent);
  margin-left: 6px;
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--r-sm);
  background: var(--accent-strong);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.headline {
  margin-top: 40px;
  font-size: 40px;
  line-height: 1.25;
  font-weight: 800;
  color: var(--accent-strong);
}

.lead {
  margin-top: 16px;
  font-size: 16px;
  line-height: 1.7;
  color: var(--ink-2);
}

.points {
  list-style: none;
  margin: 32px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 14px;
  color: var(--ink);
}

.points li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dot {
  width: 8px;
  height: 8px;
  flex: none;
  background: var(--accent);
}

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

.foot {
  position: relative;
  z-index: 1;
  text-align: center;
  font-size: 11px;
  color: var(--ink-3);
  padding-bottom: 16px;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr);
    gap: 32px;
    padding: 32px 16px;
  }

  .headline {
    margin-top: 24px;
    font-size: 30px;
  }

  .points {
    display: none;
  }
}
</style>

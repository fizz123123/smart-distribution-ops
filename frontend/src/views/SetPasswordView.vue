<script setup>
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { setPassword } from '@/api/auth'
import AppIcon from '@/components/AppIcon.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'

// 設定密碼頁（AUTH-02）：從信中連結進來，不需登入；首次設定（啟用）與重設密碼共用。
// 網址 /set-password?token=…；新密碼 8～20 字，兩次輸入是否一致由前端檢查，後端只收 newPassword。
const route = useRoute()
const token = typeof route.query.token === 'string' ? route.query.token : ''

const form = reactive({ newPassword: '', confirmPassword: '' })
const formRef = ref(null)
const isSubmitting = ref(false)
const error = ref(null)
// done：設定完成；invalid：連結無效（沒有 token，或後端回 PASSWORD_TOKEN_INVALID）
const result = ref(token ? '' : 'invalid')

const rules = {
  newPassword: [
    { required: true, message: '請輸入新密碼', trigger: 'blur' },
    { min: 8, max: 20, message: '密碼長度要 8～20 個字元', trigger: ['blur', 'change'] },
  ],
  confirmPassword: [
    { required: true, message: '請再輸入一次新密碼', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value && value !== form.newPassword
          ? callback(new Error('兩次輸入的密碼不一致'))
          : callback(),
      trigger: ['blur', 'change'],
    },
  ],
}

const canSubmit = computed(
  () => form.newPassword !== '' && form.confirmPassword !== '' && !isSubmitting.value,
)

async function submit() {
  error.value = null
  const isValid = await formRef.value?.validate().catch(() => false)
  if (isValid === false) return
  isSubmitting.value = true
  try {
    await setPassword(token, form.newPassword)
    result.value = 'done'
  } catch (e) {
    if (e.code === 'PASSWORD_TOKEN_INVALID') result.value = 'invalid'
    else error.value = e
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <section class="card auth-card" aria-labelledby="set-password-title">
      <template v-if="result === 'done'">
        <div class="result ok">
          <AppIcon name="check" :size="28" />
          <h1 id="set-password-title">密碼已設定</h1>
          <p class="sub">請使用新密碼登入。</p>
        </div>
        <RouterLink :to="{ name: 'login' }" class="btn btn-primary wide">前往登入</RouterLink>
      </template>

      <template v-else-if="result === 'invalid'">
        <div class="result bad">
          <AppIcon name="alert" :size="28" />
          <h1 id="set-password-title">連結已失效</h1>
          <p class="sub">連結已失效，請聯絡系統管理員重新寄送。</p>
        </div>
        <RouterLink :to="{ name: 'login' }" class="btn wide">返回登入</RouterLink>
      </template>

      <template v-else>
        <h1 id="set-password-title">設定密碼</h1>
        <p class="sub">請設定你的登入密碼，完成後即可登入。</p>

        <div v-if="error" class="error" role="alert">
          <b>{{ error.message }}</b>
          <span v-if="error.traceId" class="mono">追蹤碼：{{ error.traceId }}</span>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          :disabled="isSubmitting"
          @submit.prevent="submit"
        >
          <el-form-item label="新密碼" prop="newPassword" for="new-password">
            <el-input
              id="new-password"
              v-model="form.newPassword"
              type="password"
              autocomplete="new-password"
              show-password
              maxlength="20"
              placeholder="8～20 個字元"
              size="large"
            />
          </el-form-item>
          <el-form-item label="確認新密碼" prop="confirmPassword" for="confirm-password">
            <el-input
              id="confirm-password"
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              show-password
              maxlength="20"
              placeholder="再輸入一次新密碼"
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
            {{ isSubmitting ? '設定中…' : '設定密碼' }}
          </el-button>
        </el-form>

        <p class="help">
          連結有問題？請聯絡系統管理員。
          <RouterLink :to="{ name: 'login' }">返回登入</RouterLink>
        </p>
      </template>
    </section>
  </AuthLayout>
</template>

<style scoped>
.auth-card {
  padding: 36px 36px 28px;
}

.auth-card h1 {
  font-size: 26px;
  font-weight: 800;
  color: var(--accent-strong);
}

.sub {
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

.help a {
  margin-left: 6px;
  font-weight: 600;
  color: var(--accent);
}

.result {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.result.ok .icon {
  color: var(--success);
}

.result.bad .icon {
  color: var(--danger);
}

.wide {
  display: flex;
  justify-content: center;
  width: 100%;
  text-decoration: none;
}
</style>

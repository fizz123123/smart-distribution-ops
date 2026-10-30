<script setup>
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { changeOwnPassword } from '@/api/users'
import { notify } from '@/utils/notify'

// 修改自己的密碼（頭像選單）：須輸入目前密碼；新密碼 8～20 字，兩次輸入是否一致由前端檢查。
// 成功後不用重新登入，下次登入使用新密碼。
const open = defineModel('open', { type: Boolean, required: true })

const formRef = ref(null)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const isSubmitting = ref(false)
const error = shallowRef(null)

watch(open, (isOpen) => {
  if (!isOpen) return
  Object.assign(form, { currentPassword: '', newPassword: '', confirmPassword: '' })
  error.value = null
})

// 目前密碼錯誤（CURRENT_PASSWORD_INCORRECT）顯示在目前密碼欄位下方
const currentPasswordError = computed(() =>
  error.value?.code === 'CURRENT_PASSWORD_INCORRECT' ? error.value.message : '',
)
const otherError = computed(() =>
  error.value && error.value.code !== 'CURRENT_PASSWORD_INCORRECT' ? error.value : null,
)

const rules = {
  currentPassword: [{ required: true, message: '請輸入目前密碼', trigger: 'blur' }],
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

async function save() {
  const isValid = await formRef.value?.validate().catch(() => false)
  if (isValid === false) return
  isSubmitting.value = true
  error.value = null
  try {
    await changeOwnPassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword,
    })
    open.value = false
    notify.success('密碼已更新')
  } catch (e) {
    error.value = e
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="open"
    title="修改密碼"
    width="440px"
    :close-on-click-modal="!isSubmitting"
    append-to-body
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      :disabled="isSubmitting"
      @submit.prevent="save"
    >
      <el-form-item
        label="目前密碼"
        prop="currentPassword"
        for="current-password"
        :error="currentPasswordError"
      >
        <el-input
          id="current-password"
          v-model="form.currentPassword"
          type="password"
          autocomplete="current-password"
          show-password
          maxlength="20"
        />
      </el-form-item>
      <el-form-item label="新密碼" prop="newPassword" for="change-new-password">
        <el-input
          id="change-new-password"
          v-model="form.newPassword"
          type="password"
          autocomplete="new-password"
          show-password
          maxlength="20"
          placeholder="8～20 個字元"
        />
      </el-form-item>
      <el-form-item label="確認新密碼" prop="confirmPassword" for="change-confirm-password">
        <el-input
          id="change-confirm-password"
          v-model="form.confirmPassword"
          type="password"
          autocomplete="new-password"
          show-password
          maxlength="20"
          placeholder="再輸入一次新密碼"
        />
      </el-form-item>
    </el-form>
    <p v-if="otherError" class="error" role="alert">{{ otherError.message }}</p>
    <template #footer>
      <el-button text :disabled="isSubmitting" @click="open = false">取消</el-button>
      <el-button type="primary" :loading="isSubmitting" @click="save">更新密碼</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.error {
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
  font-weight: 600;
}
</style>

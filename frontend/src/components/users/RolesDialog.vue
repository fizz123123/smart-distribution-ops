<script setup>
import { computed, ref, shallowRef, watch } from 'vue'
import RoleCheckboxes from '@/components/users/RoleCheckboxes.vue'
import { rolesToSubmit } from '@/config/identity'

// 編輯角色對話框：勾選要給的角色（整組取代），必填原因。
// 主管包含職員角色：勾主管時職員角色鎖住，只送主管給後端。
// APP_ADMIN 是固定的系統管理角色，不能透過畫面指派（後端 APP_ADMIN_AUTHORIZATION_IMMUTABLE）。
const open = defineModel('open', { type: Boolean, required: true })

const props = defineProps({
  roles: { type: Array, required: true }, // 可選的角色 [{ roleCode, name, includedRoleCodes }]
  current: { type: Array, required: true }, // 目前的角色代碼
  isSubmitting: { type: Boolean, default: false },
  error: { type: Object, default: null },
})

const emit = defineEmits(['confirm'])

const selected = ref([])
const reason = ref('')
const touched = shallowRef(false)

watch(open, (isOpen) => {
  if (!isOpen) return
  selected.value = [...props.current]
  reason.value = ''
  touched.value = false
})

const canConfirm = computed(
  () => selected.value.length > 0 && reason.value.trim() !== '' && !props.isSubmitting,
)

function confirm() {
  touched.value = true
  if (canConfirm.value) {
    emit('confirm', {
      roleCodes: rolesToSubmit(props.roles, selected.value),
      reason: reason.value.trim(),
    })
  }
}
</script>

<template>
  <el-dialog
    v-model="open"
    title="編輯角色"
    width="560px"
    :close-on-click-modal="!isSubmitting"
    append-to-body
  >
    <p class="desc">角色決定使用者能用哪些功能。勾選的結果會<b>整組取代</b>目前的角色。</p>
    <RoleCheckboxes v-model="selected" :roles="roles" />
    <p v-if="touched && !selected.length" class="field-error">至少要選一個角色</p>
    <el-form label-position="top" @submit.prevent="confirm">
      <el-form-item label="原因" required for="roles-reason">
        <el-input
          id="roles-reason"
          v-model="reason"
          type="textarea"
          :rows="2"
          maxlength="500"
          placeholder="例如 新進採購人員"
        />
      </el-form-item>
    </el-form>
    <p v-if="error" class="error" role="alert">{{ error.message }}</p>
    <template #footer>
      <el-button text :disabled="isSubmitting" @click="open = false">取消</el-button>
      <el-button type="primary" :loading="isSubmitting" :disabled="!canConfirm" @click="confirm"
        >儲存角色</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped>
.desc {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--ink-2);
}

.field-error {
  margin: 6px 0 10px;
  font-size: 12px;
  color: var(--danger);
}

.error {
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
  font-weight: 600;
}
</style>

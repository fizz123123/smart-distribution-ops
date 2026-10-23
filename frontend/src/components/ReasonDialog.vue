<script setup>
import { computed, ref, watch } from 'vue'

// 需要填「原因」的操作共用的確認對話框（例如停用、重新啟用、移除授權）。
// 只負責畫面；送出後由父元件呼叫 API，成功再關閉。
const open = defineModel('open', { type: Boolean, required: true })

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  confirmLabel: { type: String, default: '確認' },
  placeholder: { type: String, default: '請說明原因' },
  danger: { type: Boolean, default: false },
  isSubmitting: { type: Boolean, default: false },
  error: { type: Object, default: null },
})

const emit = defineEmits(['confirm'])

const reason = ref('')
const inputId = `reason-${Math.random().toString(36).slice(2, 8)}`

// 每次打開都清空，避免帶到上一次的原因
watch(open, (isOpen) => {
  if (isOpen) reason.value = ''
})

const canConfirm = computed(() => reason.value.trim() !== '' && !props.isSubmitting)

function confirm() {
  if (canConfirm.value) emit('confirm', reason.value.trim())
}
</script>

<template>
  <el-dialog
    v-model="open"
    :title="title"
    width="480px"
    :close-on-click-modal="!isSubmitting"
    :close-on-press-escape="!isSubmitting"
    :show-close="!isSubmitting"
    append-to-body
  >
    <p v-if="description" class="desc">{{ description }}</p>
    <slot />
    <el-form label-position="top" @submit.prevent="confirm">
      <el-form-item label="原因" required :for="inputId">
        <el-input
          :id="inputId"
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          :placeholder="placeholder"
        />
      </el-form-item>
    </el-form>
    <p v-if="error" class="error" role="alert">{{ error.message }}</p>

    <template #footer>
      <el-button :disabled="isSubmitting" @click="open = false">取消</el-button>
      <el-button
        :type="danger ? 'danger' : 'primary'"
        :loading="isSubmitting"
        :disabled="!canConfirm"
        @click="confirm"
      >
        {{ confirmLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.desc {
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink-2);
}

.error {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
  font-weight: 600;
}

:deep(.el-form-item) {
  margin-bottom: 0;
}
</style>

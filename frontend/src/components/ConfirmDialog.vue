<script setup>
// 不用填原因的確認對話框（例如寄送啟用連結、密碼重設連結）。
// 只負責畫面；確認後由父元件呼叫 API，成功再關閉。
const open = defineModel('open', { type: Boolean, required: true })

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  confirmLabel: { type: String, default: '確認' },
  isSubmitting: { type: Boolean, default: false },
  error: { type: Object, default: null },
})

const emit = defineEmits(['confirm'])
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
    <p class="desc">{{ description }}</p>
    <p v-if="error" class="error" role="alert">{{ error.message }}</p>
    <template #footer>
      <el-button :disabled="isSubmitting" @click="open = false">取消</el-button>
      <el-button type="primary" :loading="isSubmitting" @click="emit('confirm')">
        {{ confirmLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink-2);
  overflow-wrap: anywhere;
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
</style>

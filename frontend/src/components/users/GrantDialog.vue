<script setup>
import { computed, reactive, ref, watch } from 'vue'

// 個別授權的送出視窗，兩種用法：
//   勾選送出：列出這次新增、移除的權限；有新增時要填期間。原因同時作為授權原因與異動原因。
//   編輯一筆：權限固定，只改原因、開始時間、結束時間（結束時間空白＝不限期）。
// 只負責畫面；確認後由父元件整組送給後端，成功再關閉。
const open = defineModel('open', { type: Boolean, required: true })

const props = defineProps({
  title: { type: String, required: true },
  added: { type: Array, default: () => [] }, // 這次新增的權限名稱
  removed: { type: Array, default: () => [] }, // 這次移除的權限名稱
  grant: { type: Object, default: null }, // 編輯的那一筆（有值＝編輯一筆）
  grantLabel: { type: String, default: '' }, // 編輯那一筆的權限名稱
  isSubmitting: { type: Boolean, default: false },
  error: { type: Object, default: null },
})

const emit = defineEmits(['confirm'])

const form = reactive({ reason: '', validFrom: null, validTo: null })
const triedSubmit = ref(false)

const isEdit = computed(() => props.grant !== null)
// 只有移除時不需要期間
const needsPeriod = computed(() => isEdit.value || props.added.length > 0)

watch(open, (isOpen) => {
  if (!isOpen) return
  Object.assign(form, {
    reason: props.grant?.reason ?? '',
    validFrom: props.grant ? new Date(props.grant.validFrom) : new Date(),
    validTo: props.grant?.validTo ? new Date(props.grant.validTo) : null,
  })
  triedSubmit.value = false
})

const problems = computed(() => ({
  reason: form.reason.trim() ? '' : '請說明原因',
  validFrom: !needsPeriod.value || form.validFrom ? '' : '請選開始時間',
  validTo:
    needsPeriod.value && form.validTo && form.validFrom && form.validTo <= form.validFrom
      ? '結束時間要晚於開始時間'
      : '',
}))

const showError = (field) => (triedSubmit.value ? problems.value[field] : '')

function confirm() {
  triedSubmit.value = true
  if (Object.values(problems.value).some(Boolean) || props.isSubmitting) return
  emit('confirm', {
    reason: form.reason.trim(),
    validFrom: needsPeriod.value ? form.validFrom.toISOString() : null,
    validTo: needsPeriod.value && form.validTo ? form.validTo.toISOString() : null,
  })
}
</script>

<template>
  <el-dialog
    v-model="open"
    :title="title"
    width="520px"
    :close-on-click-modal="!isSubmitting"
    append-to-body
  >
    <div v-if="isEdit" class="summary">
      <span class="label">權限</span><b>{{ grantLabel }}</b>
    </div>
    <div v-else class="summary">
      <p v-if="added.length"><span class="label add">新增</span>{{ added.join('、') }}</p>
      <p v-if="removed.length"><span class="label remove">移除</span>{{ removed.join('、') }}</p>
    </div>

    <el-form label-position="top" @submit.prevent="confirm">
      <el-form-item label="原因" required :error="showError('reason')" for="grant-reason">
        <el-input
          id="grant-reason"
          v-model="form.reason"
          maxlength="500"
          placeholder="例如 主管休假代理"
        />
      </el-form-item>
      <div v-if="needsPeriod" class="pair">
        <el-form-item label="開始" required :error="showError('validFrom')">
          <el-date-picker
            v-model="form.validFrom"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="開始時間"
            aria-label="開始時間"
          />
        </el-form-item>
        <el-form-item label="結束" :error="showError('validTo')">
          <el-date-picker
            v-model="form.validTo"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="不限期"
            aria-label="結束時間"
          />
        </el-form-item>
      </div>
    </el-form>
    <p v-if="error" class="error" role="alert">{{ error.message }}</p>
    <template #footer>
      <el-button text :disabled="isSubmitting" @click="open = false">取消</el-button>
      <el-button type="primary" :loading="isSubmitting" @click="confirm">送出</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.summary {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--subtle);
  font-size: 14px;
  line-height: 1.8;
}

.summary p {
  margin: 0;
}

.label {
  display: inline-block;
  margin-right: 10px;
  padding: 0 8px;
  border-radius: var(--r-sm);
  background: var(--info-soft);
  font-size: 12px;
  font-weight: 600;
  color: var(--accent-strong);
}

.label.remove {
  background: var(--danger-soft);
  color: var(--danger);
}

.pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.pair :deep(.el-date-editor) {
  width: 100%;
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

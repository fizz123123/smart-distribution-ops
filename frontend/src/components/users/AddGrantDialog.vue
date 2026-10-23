<script setup>
import { computed, reactive, watch } from 'vue'
import { ACTION_LABELS, NOT_GRANTABLE, RESOURCE_LABELS } from '@/config/identity'

// 新增一筆個別額外授權：先選資料，再選動作，填原因與期間（結束時間空白＝不限期）。
// 角色已有、或已經授權過的動作會變灰不能選。送出由父元件處理（後端是整組取代，父元件會帶上原本的授權）。
const open = defineModel('open', { type: Boolean, required: true })

const props = defineProps({
  permissions: { type: Array, required: true }, // 可授予的權限 [{ permissionCode, resource, action }]
  roleOwned: { type: Array, default: () => [] }, // 角色本來就有的權限代碼
  granted: { type: Array, default: () => [] }, // 已經有個別授權的權限代碼
  isSubmitting: { type: Boolean, default: false },
  error: { type: Object, default: null },
})

const emit = defineEmits(['confirm'])

const form = reactive({ resource: '', action: '', reason: '', validFrom: null, validTo: null })
const touched = reactive({ submit: false })

watch(open, (isOpen) => {
  if (!isOpen) return
  Object.assign(form, {
    resource: '',
    action: '',
    reason: '',
    validFrom: new Date(),
    validTo: null,
  })
  touched.submit = false
})

// 換資料時清掉動作
watch(
  () => form.resource,
  () => (form.action = ''),
)

// 帳號管理權限不能當作個別授權
const grantable = computed(() =>
  props.permissions.filter((p) => !NOT_GRANTABLE.includes(p.permissionCode)),
)

const unavailable = (code) => {
  if (props.roleOwned.includes(code)) return '角色已有'
  if (props.granted.includes(code)) return '已授權'
  return ''
}

const resources = computed(() =>
  [...new Set(grantable.value.map((p) => p.resource))].map((resource) => ({
    resource,
    label: RESOURCE_LABELS[resource] ?? resource,
    // 這個資料的動作都不能選時，資料本身也變灰
    disabled: grantable.value
      .filter((p) => p.resource === resource)
      .every((p) => unavailable(p.permissionCode)),
  })),
)

const actions = computed(() =>
  grantable.value
    .filter((p) => p.resource === form.resource)
    .map((p) => ({
      code: p.permissionCode,
      label: ACTION_LABELS[p.action] ?? p.action,
      note: unavailable(p.permissionCode),
    })),
)

const problems = computed(() => ({
  resource: form.resource ? '' : '請選擇資料',
  action: form.action ? '' : '請選擇動作',
  reason: form.reason.trim() ? '' : '請說明原因',
  validFrom: form.validFrom ? '' : '請選開始時間',
  validTo:
    form.validTo && form.validFrom && form.validTo <= form.validFrom
      ? '結束時間要晚於開始時間'
      : '',
}))

const showError = (field) => (touched.submit ? problems.value[field] : '')

function confirm() {
  touched.submit = true
  if (Object.values(problems.value).some(Boolean) || props.isSubmitting) return
  emit('confirm', {
    permissionCode: form.action,
    reason: form.reason.trim(),
    validFrom: form.validFrom.toISOString(),
    validTo: form.validTo ? form.validTo.toISOString() : null,
  })
}
</script>

<template>
  <el-dialog
    v-model="open"
    title="新增授權"
    width="520px"
    :close-on-click-modal="!isSubmitting"
    append-to-body
  >
    <p class="desc">在角色之外，另外給這位使用者一項權限。角色已有的權限不能再選。</p>
    <el-form label-position="top" @submit.prevent="confirm">
      <div class="pair">
        <el-form-item label="資料" required :error="showError('resource')">
          <el-select
            v-model="form.resource"
            placeholder="選擇資料"
            aria-label="資料"
            popper-class="grant-options"
          >
            <el-option
              v-for="r in resources"
              :key="r.resource"
              :label="r.label"
              :value="r.resource"
              :disabled="r.disabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="動作" required :error="showError('action')">
          <el-select
            v-model="form.action"
            :placeholder="form.resource ? '選擇動作' : '先選資料'"
            :disabled="!form.resource"
            aria-label="動作"
            popper-class="grant-options"
          >
            <el-option
              v-for="a in actions"
              :key="a.code"
              :label="a.label"
              :value="a.code"
              :disabled="!!a.note"
            >
              <span>{{ a.label }}</span>
              <small class="note">{{ a.note }}</small>
            </el-option>
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="原因" required :error="showError('reason')" for="grant-reason">
        <el-input
          id="grant-reason"
          v-model="form.reason"
          maxlength="500"
          placeholder="請說明原因"
        />
      </el-form-item>
      <div class="pair">
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
      <el-button type="primary" :loading="isSubmitting" @click="confirm">新增授權</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.desc {
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--ink-2);
}

.pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.pair :deep(.el-select),
.pair :deep(.el-date-editor) {
  width: 100%;
}

.note {
  float: right;
  margin-left: 12px;
  font-size: 11px;
  color: var(--ink-3);
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

<style>
/* 選單是掛在 body 底下，所以這段不能用 scoped：不能選的動作變灰 */
.grant-options .el-select-dropdown__item.is-disabled {
  color: var(--ink-3);
  opacity: 0.6;
}
</style>

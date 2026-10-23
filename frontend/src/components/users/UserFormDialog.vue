<script setup>
import { computed, reactive, ref, shallowRef } from 'vue'
import { createUser, replaceRoles, updateUser } from '@/api/users'
import { CREATE_ROLE_REASON } from '@/config/identity'

// 新增／編輯使用者對話框。新增要填 Email、姓名、初始密碼和角色（至少一個）；編輯只能改 Email 和姓名。
// 新增時先建立帳號，再指派角色（兩支 API）。父元件用 ref 呼叫 openCreate()／openEdit(user)，儲存成功後收到 saved 事件。
const props = defineProps({
  roles: { type: Array, default: () => [] }, // 可選的角色 [{ roleCode, name }]
})

const emit = defineEmits(['saved'])

const open = ref(false)
const editing = shallowRef(null)
const formRef = ref(null)
const form = reactive({ email: '', name: '', password: '', roleCodes: [] })
const isSubmitting = ref(false)
const error = shallowRef(null)

const isEdit = computed(() => editing.value !== null)
const fieldError = (field) =>
  error.value?.fieldErrors?.find((e) => e.field === field)?.message ?? ''

const rules = computed(() => ({
  email: [
    { required: true, message: '請輸入 Email', trigger: 'blur' },
    { type: 'email', message: 'Email 格式不正確', trigger: 'blur' },
  ],
  name: [{ required: true, message: '請輸入姓名', trigger: 'blur' }],
  password: isEdit.value
    ? []
    : [
        { required: true, message: '請輸入初始密碼', trigger: 'blur' },
        { min: 8, max: 20, message: '密碼長度要 8～20 個字', trigger: 'blur' },
      ],
  roleCodes: isEdit.value
    ? []
    : [{ type: 'array', required: true, message: '至少要選一個角色', trigger: 'change' }],
}))

const assignable = computed(() => props.roles.filter((r) => r.roleCode !== 'APP_ADMIN'))

function openCreate() {
  editing.value = null
  Object.assign(form, { email: '', name: '', password: '', roleCodes: [] })
  error.value = null
  open.value = true
}

function openEdit(user) {
  editing.value = user
  Object.assign(form, { email: user.email, name: user.name, password: '', roleCodes: [] })
  error.value = null
  open.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  isSubmitting.value = true
  error.value = null
  try {
    if (isEdit.value) {
      const saved = await updateUser(editing.value.id, {
        email: form.email.trim(),
        name: form.name.trim(),
        version: editing.value.version,
      })
      open.value = false
      emit('saved', saved, { created: false })
      return
    }
    const created = await createUser({
      email: form.email.trim(),
      name: form.name.trim(),
      password: form.password,
    })
    open.value = false
    try {
      const saved = await replaceRoles(created.id, {
        roleCodes: form.roleCodes,
        reason: CREATE_ROLE_REASON,
        version: created.version,
      })
      emit('saved', saved, { created: true })
    } catch (roleError) {
      // 帳號已建立但角色沒存成功：一樣帶去詳情頁，讓管理員在那裡補指派
      emit('saved', created, { created: true, roleError })
    }
  } catch (e) {
    error.value = e
  } finally {
    isSubmitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog
    v-model="open"
    :title="isEdit ? '編輯使用者' : '新增使用者'"
    width="520px"
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
      <el-form-item label="Email" prop="email" for="user-email" :error="fieldError('email')">
        <el-input
          id="user-email"
          v-model="form.email"
          type="email"
          autocomplete="off"
          placeholder="name@company.com"
        />
      </el-form-item>
      <el-form-item label="姓名" prop="name" for="user-name" :error="fieldError('name')">
        <el-input id="user-name" v-model="form.name" maxlength="100" placeholder="例如 王小明" />
      </el-form-item>
      <el-form-item
        v-if="!isEdit"
        label="初始密碼"
        prop="password"
        for="user-password"
        :error="fieldError('password')"
      >
        <el-input
          id="user-password"
          v-model="form.password"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="8～20 個字"
        />
        <div class="help">請把初始密碼交給使用者本人</div>
      </el-form-item>
      <el-form-item v-if="!isEdit" label="角色" prop="roleCodes">
        <fieldset class="roles">
          <legend class="sr-only">角色</legend>
          <label v-for="r in assignable" :key="r.roleCode" class="role">
            <input v-model="form.roleCodes" type="checkbox" :value="r.roleCode" />
            <span>{{ r.name }}</span>
          </label>
        </fieldset>
      </el-form-item>
    </el-form>
    <p v-if="!isEdit" class="note">個別額外授權要等建立完成後，到使用者詳情頁設定。</p>
    <p v-if="error && !error.fieldErrors?.length" class="error" role="alert">{{ error.message }}</p>
    <template #footer>
      <el-button text :disabled="isSubmitting" @click="open = false">取消</el-button>
      <el-button type="primary" :loading="isSubmitting" @click="save">{{
        isEdit ? '儲存變更' : '建立使用者'
      }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.roles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  margin: 0;
  padding: 0;
  border: 0;
}

.role {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  line-height: 1.4;
  cursor: pointer;
}

.role:has(input:checked) {
  border-color: var(--accent);
  background: var(--info-soft);
}

.role input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent-strong);
}

.role span {
  font-weight: 600;
}

.help {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

.note {
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: rgba(105, 176, 172, 0.14);
  font-size: 12.5px;
  color: var(--ink-2);
}

.error {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
  font-weight: 600;
}
</style>

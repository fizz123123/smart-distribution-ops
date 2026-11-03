<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAuthorizationOptions, fetchUsers } from '@/api/users'
import ListPage from '@/components/ListPage.vue'
import UserFormDialog from '@/components/users/UserFormDialog.vue'
import { useAsync } from '@/composables/useAsync'
import { toUserRow, usersList as config } from '@/config/identity'
import { notify } from '@/utils/notify'

// 使用者與權限（ADM-01）清單：GET /api/v1/users。
const router = useRouter()
const formDialog = ref(null)
const { data: options } = useAsync(fetchAuthorizationOptions)

async function loadUsers() {
  const page = await fetchUsers({ page: 0, size: 100 })
  return { ...page, content: page.content.map(toUserRow) }
}

// 新增成功後直接進詳情頁
function handleSaved(saved, { roleError } = {}) {
  if (roleError) {
    notify.error(`${saved.name}的帳號已建立，但角色沒有存成功：${roleError.message}`)
  } else {
    notify.success(`${saved.name}的帳號已建立，啟用連結已寄出。`)
  }
  router.push({ name: 'userDetail', params: { id: saved.id } })
}
</script>

<template>
  <ListPage :config="config" :load="loadUsers" @primary="formDialog.openCreate()" />
  <UserFormDialog ref="formDialog" :roles="options?.roles ?? []" @saved="handleSaved" />
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const open = ref(false)
const root = ref(null)
const trigger = ref(null)
const firstItem = ref(null)
const isChangingPassword = ref(false)

const initial = () => authStore.user?.name.slice(0, 1) ?? ''

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await nextTick()
    firstItem.value?.focus()
  }
}

function close(returnFocus = false) {
  open.value = false
  if (returnFocus) trigger.value?.focus()
}

function onDocClick(e) {
  if (open.value && root.value && !root.value.contains(e.target)) close()
}

function onKey(e) {
  if (e.key === 'Escape' && open.value) close(true)
}

function openChangePassword() {
  close()
  isChangingPassword.value = true
}

function handleLogout() {
  close()
  // 之後若有未儲存的表單，要先跳確認視窗再登出。
  authStore.logout()
  router.replace({ name: 'login' })
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div ref="root" class="account">
    <button
      ref="trigger"
      class="avatar-btn"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open"
      :aria-label="`帳號選單：${authStore.user?.name ?? ''}`"
      @click="toggle"
    >
      <span class="avatar">{{ initial() }}</span>
    </button>

    <div
      v-if="open && authStore.user"
      class="menu"
      role="menu"
      :aria-label="`${authStore.user?.name} 的帳號`"
    >
      <div class="head">
        <span class="avatar lg">{{ initial() }}</span>
        <div>
          <b>{{ authStore.user.name }}</b>
          <span class="mono">{{ authStore.user.email }}</span>
          <em>{{ authStore.roleLabel }}</em>
        </div>
      </div>
      <div class="sep" role="separator"></div>
      <button
        ref="firstItem"
        class="item"
        type="button"
        role="menuitem"
        @click="openChangePassword"
      >
        <AppIcon name="edit" :size="20" />修改密碼
      </button>
      <button class="item" type="button" role="menuitem" @click="handleLogout">
        <AppIcon name="logout" :size="20" />登出
      </button>
    </div>
    <ChangePasswordDialog v-model:open="isChangingPassword" />
  </div>
</template>

<style scoped>
.account {
  position: relative;
}

.avatar-btn {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: var(--r-lg);
  background: rgba(230, 241, 243, 0.9);
}

.avatar-btn[aria-expanded='true'] {
  box-shadow: inset 0 0 0 2px rgba(0, 98, 132, 0.28);
}

.avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--accent-strong);
  color: #fff;
  font-weight: 700;
}

.avatar.lg {
  flex: none;
  width: 48px;
  height: 48px;
  font-size: 18px;
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  width: 280px;
  padding: 8px 0;
  background: #fff;
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-pop);
  z-index: 50;
}

.head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 18px 14px;
}

.head > div {
  min-width: 0;
}

.head b {
  display: block;
  font-size: 15px;
}

/* 只套用在 Email 那行，不要影響左邊的大頭像（大頭像也是 span） */
.head .mono {
  display: block;
  overflow-wrap: anywhere;
  font-size: 12px;
  color: var(--ink-3);
}

.head em {
  display: inline-block;
  margin-top: 4px;
  padding: 1px 8px;
  font-style: normal;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--r-xs);
}

.sep {
  height: 1px;
  margin: 4px 0;
  background: var(--line);
}

.item {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 11px 18px;
  border: 0;
  background: transparent;
  font-size: 14px;
  text-align: left;
}

.item:hover,
.item:focus-visible {
  background: #f1f6f7;
  outline: none;
}
</style>

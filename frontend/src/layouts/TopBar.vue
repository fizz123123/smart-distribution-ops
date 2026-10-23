<script setup>
import AppIcon from '@/components/AppIcon.vue'
import AccountMenu from './AccountMenu.vue'
import GlobalSearch from './GlobalSearch.vue'
import { useAppStore } from '@/stores/app'
import { formatTime } from '@/utils/format'

const appStore = useAppStore()
</script>

<template>
  <header class="bar">
    <div class="tools">
      <GlobalSearch />
      <!-- 更新時間放在提示文字裡；工作台另外顯示在問候語後面 -->
      <button
        class="icon-btn"
        type="button"
        :aria-label="`重新整理（資料更新於 ${formatTime(appStore.lastSyncedAt)}）`"
        :title="`重新整理・資料更新於 ${formatTime(appStore.lastSyncedAt)}`"
        @click="appStore.requestRefresh"
      >
        <AppIcon name="refresh" />
      </button>
      <!-- 通知：圖示先保留，未讀數量等後端有 API 再接 -->
      <button class="icon-btn bell" type="button" aria-label="通知">
        <AppIcon name="bell" />
      </button>
    </div>

    <div class="account-cell">
      <AccountMenu />
    </div>
  </header>
</template>

<style scoped>
.bar {
  position: relative;
  z-index: 30;
  display: flex;
  align-items: center;
  height: var(--bar-h);
}

.tools {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 12px;
}

.bell {
  position: relative;
}

.bell em {
  position: absolute;
  top: -2px;
  right: -3px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  border: 2px solid #fff;
  background: var(--danger);
  color: #fff;
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
}

/* 頭像前一條細分隔線，跟工具圖示分開 */
.account-cell {
  display: flex;
  align-items: center;
  height: 32px;
  padding-left: 14px;
  border-left: 1px solid var(--line);
}
</style>

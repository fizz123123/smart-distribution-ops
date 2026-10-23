import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 全站畫面狀態：頂部列的重新整理按鈕與資料更新時間。
 */
export const useAppStore = defineStore('app', () => {
  const lastSyncedAt = ref(new Date())
  // AppLayout 用它當 RouterView 的 key，改變時重新掛載頁面，頁面會重新呼叫自己的 API
  const refreshKey = ref(0)

  function requestRefresh() {
    refreshKey.value += 1
  }

  function markSynced() {
    lastSyncedAt.value = new Date()
  }

  return { lastSyncedAt, refreshKey, requestRefresh, markSynced }
})

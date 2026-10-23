import { ref, shallowRef } from 'vue'

// 載入資料的共用寫法：回傳 data、isLoading、error 與 reload。
// 畫面只呼叫模組的 api 函式，不直接碰 http。
export function useAsync(loader, { immediate = true } = {}) {
  const data = shallowRef(null)
  const isLoading = ref(false)
  const error = shallowRef(null)

  async function reload() {
    isLoading.value = true
    error.value = null
    try {
      data.value = await loader()
    } catch (e) {
      error.value = e
    } finally {
      isLoading.value = false
    }
  }

  if (immediate) reload()
  return { data, isLoading, error, reload }
}

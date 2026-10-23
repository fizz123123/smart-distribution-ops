import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

// 側邊欄展開／收合狀態（app 層的畫面偏好）。記在瀏覽器裡，下次打開維持上次的樣子。
// 收合只在桌面寬度生效；窄螢幕時版面改成上下排列，一律顯示完整選單。
const STORAGE_KEY = 'tr.sidebarCollapsed'
const NARROW_QUERY = '(max-width: 900px)'

function readStored() {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

// 全站共用同一份狀態
const prefersCollapsed = ref(readStored())
const isNarrowScreen = ref(typeof window !== 'undefined' && window.matchMedia(NARROW_QUERY).matches)

watch(prefersCollapsed, (value) => {
  try {
    localStorage.setItem(STORAGE_KEY, value ? '1' : '0')
  } catch {
    // 私密瀏覽等情況下無法儲存，只影響「記住選擇」，不影響功能
  }
})

export function useSidebar() {
  const isCollapsed = computed(() => prefersCollapsed.value && !isNarrowScreen.value)

  let mediaQuery
  const onChange = (event) => {
    isNarrowScreen.value = event.matches
  }
  onMounted(() => {
    mediaQuery = window.matchMedia(NARROW_QUERY)
    isNarrowScreen.value = mediaQuery.matches
    mediaQuery.addEventListener('change', onChange)
  })
  onBeforeUnmount(() => mediaQuery?.removeEventListener('change', onChange))

  function toggleCollapsed() {
    prefersCollapsed.value = !prefersCollapsed.value
  }

  return { isCollapsed, isNarrowScreen, toggleCollapsed }
}

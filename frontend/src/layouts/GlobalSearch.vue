<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { notify } from '@/utils/notify'

// 全站搜尋（app 層）。平常只顯示放大鏡圖示，點擊或按 ⌘K／Ctrl+K 才展開輸入框。
// 清單頁有自己的清單內搜尋；這裡是「知道單號、想直接跳過去」用的。
// 無障礙：按鈕有 aria-expanded；展開後游標直接進輸入框；Esc 收起並把焦點還給圖示。
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
const shortcutLabel = isMac ? '⌘K' : 'Ctrl+K'

const isOpen = ref(false)
const keyword = ref('')
const inputRef = ref(null)
const toggleRef = ref(null)

async function open() {
  isOpen.value = true
  await nextTick()
  inputRef.value?.focus()
}

function close({ returnFocus = true } = {}) {
  isOpen.value = false
  if (returnFocus) nextTick(() => toggleRef.value?.focus())
}

// 焦點離開搜尋區時：沒打字就自動收起；有打字就保留，避免打到一半消失
function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget) && keyword.value.trim() === '') {
    close({ returnFocus: false })
  }
}

function onSearch() {
  // 全站搜尋結果頁完成後改成導向結果頁
  if (keyword.value.trim()) notify.error('全站搜尋尚未開放，請先使用清單內的搜尋')
}

function onGlobalKeydown(event) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    open()
  }
}

onMounted(() => document.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onGlobalKeydown))
</script>

<template>
  <div class="global-search" :class="{ open: isOpen }" @focusout="onFocusOut">
    <form
      v-show="isOpen"
      id="global-search-form"
      class="field"
      role="search"
      @submit.prevent="onSearch"
      @keydown.esc.prevent="close()"
    >
      <AppIcon name="search" :size="16" />
      <label class="sr-only" for="global-search">搜尋全站</label>
      <input
        id="global-search"
        ref="inputRef"
        v-model="keyword"
        type="search"
        placeholder="搜尋單號、客戶、商品"
        autocomplete="off"
      />
      <kbd class="mono">Esc</kbd>
    </form>
    <button
      v-show="!isOpen"
      ref="toggleRef"
      class="icon-btn"
      type="button"
      :aria-label="`搜尋（${shortcutLabel}）`"
      :title="`搜尋（${shortcutLabel}）`"
      aria-controls="global-search-form"
      :aria-expanded="isOpen"
      @click="open"
    >
      <AppIcon name="search" />
    </button>
  </div>
</template>

<style scoped>
.global-search {
  display: flex;
  align-items: center;
}

.field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 300px;
  height: 36px;
  padding: 0 8px 0 12px;
  border: 1.5px solid var(--accent);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 0 0 4px rgba(0, 98, 132, 0.12);
  color: var(--ink-3);
  animation: grow 0.16s ease-out;
}

.field input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  font-size: 13.5px;
  color: var(--ink);
}

.field input:focus {
  outline: none;
}

.field input::placeholder {
  color: var(--ink-3);
}

kbd {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--subtle);
  font-size: 11px;
  color: var(--ink-3);
}

@keyframes grow {
  from {
    width: 36px;
    opacity: 0.4;
  }
}

@media (prefers-reduced-motion: reduce) {
  .field {
    animation: none;
  }
}

@media (max-width: 640px) {
  .field {
    width: min(300px, 60vw);
  }
}
</style>

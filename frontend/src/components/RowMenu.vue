<script setup>
import AppIcon from './AppIcon.vue'

// 清單每一列最右邊的「⋯」選單。選單內容依狀態與權限由父元件決定；
// 沒有任何項目時不顯示按鈕，但欄位寬度保留，版面不會因人而跑版。
// 鍵盤：Enter／空白鍵打開，方向鍵移動，Esc 關閉（Element Plus 下拉選單內建）。
defineProps({
  // [{ key, label, icon?, danger? }]
  items: { type: Array, required: true },
  // 螢幕閱讀器念的名稱，例如「C-0001 的更多操作」
  label: { type: String, required: true },
})

const emit = defineEmits(['select'])
</script>

<template>
  <el-dropdown
    v-if="items.length"
    trigger="click"
    placement="bottom-end"
    @command="emit('select', $event)"
  >
    <button type="button" class="icon-btn more" :aria-label="label" :title="label">
      <AppIcon name="more" :size="20" />
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="item in items"
          :key="item.key"
          :command="item.key"
          :class="{ danger: item.danger }"
        >
          <span class="item">
            <AppIcon v-if="item.icon" :name="item.icon" :size="15" />{{ item.label }}
          </span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.more :deep(svg) {
  stroke-width: 3;
}

.item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>

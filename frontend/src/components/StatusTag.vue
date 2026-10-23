<script setup>
import { computed } from 'vue'
import { statusOf } from '@/config/status'

// 狀態一律「小方塊＋文字」，不只靠顏色（WCAG 1.4.1）。
// 可以給 map＋code 查對照表，也可以直接給 label＋tone。
const props = defineProps({
  map: { type: String, default: '' },
  code: { type: String, default: '' },
  label: { type: String, default: '' },
  tone: { type: String, default: '' },
})

const info = computed(() => {
  if (props.label) return { label: props.label, tone: props.tone || 'neutral' }
  return statusOf(props.map, props.code)
})
</script>

<template>
  <span class="status" :class="`tone-${info.tone}`">
    <span class="mark" aria-hidden="true"></span>{{ info.label }}
  </span>
</template>

<style scoped>
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.mark {
  width: 8px;
  height: 8px;
  flex: none;
  background: currentColor;
}

.tone-danger {
  color: var(--danger);
}

.tone-warning {
  color: var(--warning);
}

.tone-info {
  color: var(--accent);
}

.tone-success {
  color: var(--success);
}

.tone-strong {
  color: var(--accent-strong);
}

.tone-neutral {
  color: var(--ink-3);
}
</style>

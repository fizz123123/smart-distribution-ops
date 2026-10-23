<script setup>
import { computed } from 'vue'

// 分段進度條：10 格，數字文字放在上方，進度條本身只是輔助。
const props = defineProps({
  value: { type: Number, required: true },
  max: { type: Number, required: true },
  text: { type: String, required: true },
  segments: { type: Number, default: 10 },
})

const filled = computed(() => {
  if (!props.max) return 0
  return Math.round(Math.min(props.value / props.max, 1) * props.segments)
})
</script>

<template>
  <div class="prog">
    <div class="text">{{ text }}</div>
    <div
      class="bar"
      role="progressbar"
      :aria-valuenow="value"
      :aria-valuemin="0"
      :aria-valuemax="max"
      :aria-label="text"
    >
      <i v-for="n in segments" :key="n" :class="{ on: n <= filled }"></i>
    </div>
  </div>
</template>

<style scoped>
.text {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--ink-2);
  white-space: nowrap;
}

.bar {
  display: flex;
  gap: 2px;
  margin-top: 5px;
}

.bar i {
  width: 9px;
  height: 6px;
  border-radius: 2px;
  background: #dce8eb;
}

.bar i.on {
  background: var(--accent);
}
</style>

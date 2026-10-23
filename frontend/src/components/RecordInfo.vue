<script setup>
import { formatDate } from '@/utils/format'

// 建立、核准、更新紀錄（12 號 §2：可修改資源共用欄位）。小字、一行、靠右，不搶基本資料的視線。
// 必須放在 <dl> 裡（根元素是 dt＋dd 的群組）；詳情頁放在基本資料右下角。
defineProps({
  createdAt: { type: String, default: '' },
  createdBy: { type: String, default: '' },
  approvedAt: { type: String, default: '' },
  approvedBy: { type: String, default: '' },
  updatedAt: { type: String, default: '' },
})
</script>

<template>
  <div class="record">
    <dt>紀錄</dt>
    <dd>
      <span v-if="createdAt"
        >建立 {{ formatDate(createdAt)
        }}<template v-if="createdBy">・{{ createdBy }}</template></span
      >
      <span v-if="approvedAt"
        >核准 {{ formatDate(approvedAt)
        }}<template v-if="approvedBy">・{{ approvedBy }}</template></span
      >
      <span v-if="updatedAt">更新 {{ formatDate(updatedAt) }}</span>
    </dd>
  </div>
</template>

<style scoped>
.record {
  text-align: right;
}

dt {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

dd {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 2px 16px;
  margin: 0;
  font-size: 12px;
  color: var(--ink-3);
}
</style>

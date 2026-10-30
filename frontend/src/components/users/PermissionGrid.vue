<script setup>
import { computed } from 'vue'
import { gridColumnWidths } from '@/config/identity'

// 有效權限表（資料 × 動作）。檢視時顯示勾勾；編輯時可以勾選或取消個別額外授權。
// 角色本來就有的權限：深藍勾，不能改。個別額外授權：綠色圓點。
// v-model 是編輯中「要有個別授權」的權限代碼；只有 editing 為 true 時才會改動。
const checked = defineModel({ type: Array, default: () => [] })

const props = defineProps({
  grid: { type: Object, required: true }, // permissionGrid() 的結果
  editing: { type: Boolean, default: false },
})

const widths = computed(() => gridColumnWidths(props.grid.actions))
// 每格的標記放在和標題文字同寬的位置裡置中，勾勾才會對準標題正中間（標題每字約 11.5px）
const slotStyle = (i) => ({ width: `${props.grid.actions[i].label.length * 11.5}px` })

function toggle(code, isChecked) {
  checked.value = isChecked ? [...checked.value, code] : checked.value.filter((c) => c !== code)
}
</script>

<template>
  <div class="table-wrap">
    <table class="table perm-table">
      <caption class="sr-only">
        有效權限
      </caption>
      <colgroup>
        <col v-for="(w, i) in widths" :key="i" :style="{ width: `${w}px` }" />
      </colgroup>
      <thead>
        <tr>
          <th scope="col">資料</th>
          <th v-for="a in grid.actions" :key="a.key" scope="col" class="cell" :title="a.fullLabel">
            {{ a.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in grid.rows" :key="r.resource">
          <th scope="row">{{ r.label }}</th>
          <td v-for="(c, i) in r.cells" :key="i" class="cell">
            <span v-if="c" class="slot" :style="slotStyle(i)">
              <!-- 角色本來就有：深藍勾，不能改 -->
              <span v-if="c.kind === 'role'" class="mark role" title="角色本來就有">
                <input v-if="editing" type="checkbox" checked disabled aria-label="角色本來就有" />
                <template v-else>✓<span class="sr-only">角色本來就有</span></template>
              </span>
              <!-- 可以個別授權 -->
              <template v-else-if="c.kind === 'grant' || c.kind === 'open'">
                <input
                  v-if="editing"
                  type="checkbox"
                  class="grant-box"
                  :checked="checked.includes(c.code)"
                  :aria-label="`${r.label}・${grid.actions[i].fullLabel}`"
                  @change="toggle(c.code, $event.target.checked)"
                />
                <span v-else-if="c.kind === 'grant'" class="mark grant" title="個別額外授權">
                  <span class="sr-only">個別額外授權</span>
                </span>
                <span v-else class="mark missing">—<span class="sr-only">沒有</span></span>
              </template>
              <span v-else class="mark missing">—<span class="sr-only">沒有</span></span>
            </span>
          </td>
        </tr>
        <tr v-if="!grid.rows.length">
          <td :colspan="grid.actions.length + 1" class="state">沒有任何權限</td>
        </tr>
      </tbody>
    </table>
  </div>
  <p class="legend">
    <span><i class="dot role"></i>角色權限</span>
    <span><i class="dot grant"></i>個別額外授權</span>
  </p>
</template>

<style scoped>
.perm-table {
  table-layout: fixed;
}

/* 表格下面還有圖例，最後一列也保留底線（共用樣式預設會拿掉） */
.perm-table tbody tr:last-child td {
  border-bottom: 1px solid var(--line-faint);
}

.perm-table th[scope='row'] {
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  text-align: left;
  white-space: nowrap;
}

/* 靠左：和下方個別授權清單的欄位起點對齊 */
.perm-table .cell {
  text-align: left;
  white-space: nowrap;
}

.slot {
  display: inline-flex;
  justify-content: center;
  min-width: 16px;
}

.mark {
  font-weight: 700;
}

.mark.role {
  color: var(--accent-strong);
}

.mark.grant {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--success);
  vertical-align: middle;
}

.mark.missing {
  font-weight: 400;
  color: var(--ink-muted);
}

input[type='checkbox'] {
  width: 16px;
  height: 16px;
  vertical-align: middle;
  accent-color: var(--success);
}

input[type='checkbox']:disabled {
  accent-color: var(--ink-muted);
  cursor: not-allowed;
}

.legend {
  display: flex;
  gap: 18px;
  padding: 10px 20px 14px;
  font-size: 12px;
  color: var(--ink-3);
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 6px;
  border-radius: 3px;
  vertical-align: -1px;
}

.dot.role {
  background: var(--accent-strong);
}

.dot.grant {
  border-radius: 50%;
  background: var(--success);
}

.state {
  padding: 32px 14px;
  text-align: center;
  color: var(--ink-3);
}
</style>

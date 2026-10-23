<script setup>
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from './AppIcon.vue'
import StatusTag from './StatusTag.vue'
import RowMenu from './RowMenu.vue'
import SegProgress from './SegProgress.vue'
import { useAsync } from '@/composables/useAsync'
import { useAppStore } from '@/stores/app'
import { formatMoney, pad2 } from '@/utils/format'

// 共用清單頁（shared 層）。各模組傳入自己的 config 與 load 函式，這個元件不知道任何模組。
// config：title、description、searchPlaceholder、primaryAction、statusMap、tabs、columns、rowAction
// 新增按鈕放在卡片右上，和清單內搜尋同一排
// 進入詳情：點第一個 doc 欄（編號）。有 rowLink(row) 就是真的連結；還沒有詳情頁的先發出 open-row。
// 操作欄只放真正的動作（例如出貨過帳）；沒有 rowAction 就不顯示操作欄。rowAction 回傳 null 代表這列沒有動作。
// rowMenu(row) 回傳「⋯」選單項目 [{ key, label, icon?, danger? }]，選了會發出 menu-select(key, row)。
// rowAction 可以是字串（每列一樣），或函式 row => ({ label, primary })（依資料列狀態不同）
// 欄位型別：doc、text、mono、date、money、num、status、flags、progress、tag、warning
// 欄位可設 width（例如 "38%"）；沒設的欄位平分剩下的寬度。sub 是第二行小字，太長會顯示「…」，滑過看全文。
const props = defineProps({
  config: { type: Object, required: true },
  load: { type: Function, required: true },
})

const emit = defineEmits(['primary', 'row-action', 'open-row', 'menu-select'])
const appStore = useAppStore()

const { data, isLoading, error, reload } = useAsync(async () => {
  const page = await props.load()
  appStore.markSynced()
  return page
})
const allRows = computed(() => data.value?.content ?? [])

const activeTabIndex = ref(0)
const searchKeyword = ref('')

function tabRows(index) {
  const t = props.config.tabs[index]
  return t?.filter ? allRows.value.filter(t.filter) : allRows.value
}

const rows = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  const base = tabRows(activeTabIndex.value)
  if (!kw) return base
  return base.filter((r) =>
    Object.values(r).some((v) => typeof v === 'string' && v.toLowerCase().includes(kw)),
  )
})

const isNumeric = (c) => ['money', 'num'].includes(c.type) || c.align === 'right'
const isOverdue = (row) => Array.isArray(row.flags) && row.flags.includes('OVERDUE')

// 欄寬：有設 width 的照設定，其餘平均分配（table-layout: fixed），操作欄固定寬度。
const linkColumnKey = computed(() => props.config.columns.find((c) => c.type === 'doc')?.key)
const hasActions = computed(() => Boolean(props.config.rowAction || props.config.rowMenu))
const hasMenuOnly = computed(() => Boolean(props.config.rowMenu && !props.config.rowAction))
const columnCount = computed(() => props.config.columns.length + (hasActions.value ? 1 : 0))

function rowActionOf(row) {
  const action = props.config.rowAction
  if (!action) return null
  if (typeof action === 'function') return action(row)
  return { label: action, primary: props.config.rowActionPrimary }
}

// 讓父元件在送出命令後可以重新載入清單
defineExpose({ reload })
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ config.title }}</h1>
        <p class="desc">{{ config.description }}</p>
      </div>
    </header>

    <section class="card">
      <div class="card-head">
        <div class="tabs" role="group" aria-label="篩選">
          <button
            v-for="(t, i) in config.tabs"
            :key="t.label"
            type="button"
            class="chip"
            :aria-pressed="activeTabIndex === i"
            @click="activeTabIndex = i"
          >
            {{ t.label }} <span>{{ pad2(tabRows(i).length) }}</span>
          </button>
        </div>
        <div class="head-tools">
          <label class="filter">
            <AppIcon name="search" :size="15" />
            <span class="sr-only">在{{ config.title }}中搜尋</span>
            <input
              v-model="searchKeyword"
              type="search"
              :placeholder="config.searchPlaceholder || `搜尋${config.title}`"
            />
          </label>
          <button
            v-if="config.primaryAction"
            type="button"
            class="btn btn-primary add"
            @click="emit('primary')"
          >
            <span class="lead"><AppIcon name="plus" :size="16" />{{ config.primaryAction }}</span>
          </button>
        </div>
      </div>

      <div v-if="error" class="state error" role="alert">
        <AppIcon name="alert" :size="22" />
        <div>
          <b>{{ error.message }}</b>
          <div v-if="error.traceId" class="sub mono">追蹤碼：{{ error.traceId }}</div>
        </div>
        <button type="button" class="btn" @click="reload">
          重新載入<span class="arrow" aria-hidden="true">↻</span>
        </button>
      </div>

      <div v-else class="table-wrap" :aria-busy="isLoading">
        <table class="table">
          <caption class="sr-only">
            {{
              config.title
            }}清單
          </caption>
          <thead>
            <tr>
              <th
                v-for="c in config.columns"
                :key="c.key"
                scope="col"
                :class="{ 'text-right': isNumeric(c) }"
                :style="c.width ? { width: c.width } : null"
              >
                {{ c.label }}
              </th>
              <th
                v-if="hasActions"
                scope="col"
                class="actions-col text-right"
                :class="{ 'menu-only': hasMenuOnly }"
              >
                <span :class="{ 'sr-only': hasMenuOnly }">操作</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="isLoading && !allRows.length">
              <td :colspan="columnCount" class="state">載入中…</td>
            </tr>
            <tr v-for="row in rows" :key="row.id" :class="{ 'is-overdue': isOverdue(row) }">
              <td
                v-for="c in config.columns"
                :key="c.key"
                :class="{
                  num: ['money', 'num'].includes(c.type),
                  date: c.type === 'date',
                  'text-right': c.align === 'right',
                  mono: c.type === 'mono' || c.mono,
                }"
              >
                <template v-if="c.type === 'doc'">
                  <template v-if="c.key === linkColumnKey">
                    <RouterLink
                      v-if="config.rowLink"
                      :to="config.rowLink(row)"
                      class="doc-id doc-link"
                    >
                      {{ row[c.key] }}
                    </RouterLink>
                    <button
                      v-else
                      type="button"
                      class="doc-id doc-link"
                      @click="emit('open-row', row)"
                    >
                      {{ row[c.key] }}
                    </button>
                  </template>
                  <div v-else class="doc-id">{{ row[c.key] }}</div>
                  <div v-if="c.sub" class="sub ellipsis" :title="row[c.sub]">{{ row[c.sub] }}</div>
                </template>
                <StatusTag
                  v-else-if="c.type === 'status'"
                  :map="c.map || config.statusMap"
                  :code="row[c.key]"
                />
                <div v-else-if="c.type === 'flags'" class="flags">
                  <StatusTag v-for="f in row[c.key]" :key="f" map="flag" :code="f" />
                  <span v-if="!row[c.key].length" class="sub">—</span>
                </div>
                <template v-else-if="c.type === 'progress'">
                  <SegProgress v-if="row[c.key]" v-bind="row[c.key]" />
                  <span v-else class="sub">—</span>
                </template>
                <span v-else-if="c.type === 'tag'" class="type-tag">{{ row[c.key] }}</span>
                <template v-else-if="c.type === 'warning'">
                  <StatusTag v-if="row[c.key]" :label="row[c.key]" tone="danger" />
                  <span v-else class="sub">—</span>
                </template>
                <template v-else-if="c.type === 'money'">{{ formatMoney(row[c.key]) }}</template>
                <template v-else>
                  <div>{{ row[c.key] }}</div>
                  <div v-if="c.sub" class="sub">{{ row[c.sub] }}</div>
                </template>
              </td>
              <td v-if="hasActions" class="text-right">
                <button
                  v-if="rowActionOf(row)"
                  type="button"
                  class="btn"
                  :class="{ 'btn-primary': rowActionOf(row).primary }"
                  @click="emit('row-action', row)"
                >
                  {{ rowActionOf(row).label }}<span class="arrow" aria-hidden="true">↗</span>
                </button>
                <RowMenu
                  v-if="config.rowMenu"
                  :items="config.rowMenu(row)"
                  :label="`${row[linkColumnKey] ?? ''} 的更多操作`"
                  @select="emit('menu-select', $event, row)"
                />
              </td>
            </tr>
            <tr v-if="!isLoading && !rows.length">
              <td :colspan="columnCount" class="state">
                <AppIcon name="list" :size="22" />
                <div>沒有符合條件的資料</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-top: 2px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  min-height: var(--bar-h);
  padding: 4px var(--tools-w) 0 4px;
}

.page-head h1 {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: var(--accent-strong);
}

.desc {
  margin-top: 4px;
  font-size: 14px;
  color: var(--ink-2);
}

.btn .lead {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn.add {
  min-width: 0;
  justify-content: center;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 220px;
  padding: 6px 12px;
  border: 1px solid rgba(43, 95, 117, 0.18);
  border-radius: var(--r-md);
  background: var(--subtle);
  color: var(--ink-3);
}

.filter:focus-within {
  border-color: var(--accent);
}

.filter input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  font-size: 13px;
}

.filter input:focus {
  outline: none;
}

.table-wrap {
  overflow-x: auto;
}

/* 平均分配欄寬；內容太長時換行，不撐開欄位 */
.table {
  table-layout: fixed;
}

.table td {
  overflow-wrap: anywhere;
}

.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.table .actions-col {
  width: 150px;
}

.table .actions-col.menu-only {
  width: 64px;
}

.table th:first-child,
.table td:first-child {
  padding-left: 20px;
}

.flags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.state {
  padding: 40px 14px !important;
  text-align: center;
  color: var(--ink-3);
}

.state .icon {
  margin: 0 auto 6px;
}

.state.error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--danger);
  text-align: left;
}

.state.error .icon {
  margin: 0;
}
</style>

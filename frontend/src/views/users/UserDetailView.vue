<script setup>
import { computed, ref, shallowRef } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ReasonDialog from '@/components/ReasonDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import GrantDialog from '@/components/users/GrantDialog.vue'
import PermissionGrid from '@/components/users/PermissionGrid.vue'
import RolesDialog from '@/components/users/RolesDialog.vue'
import UserFormDialog from '@/components/users/UserFormDialog.vue'
import { useAsync } from '@/composables/useAsync'
import {
  buildGrants,
  GRANT_STATUS,
  grantStatus,
  gridColumnWidths,
  permissionGrid,
  permissionLabel,
  roleLabel,
  toGrantRequest,
  visibleGrants,
} from '@/config/identity'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import { notify } from '@/utils/notify'
import {
  activateUser,
  disableUser,
  fetchAuthorizationOptions,
  fetchUser,
  replacePermissionGrants,
  replaceRoles,
  resendInvitation,
  sendPasswordReset,
} from '@/api/users'

// 使用者詳情（ADM-01）：基本資料、角色、有效權限與個別額外授權。
// 規則：不能修改自己；APP_ADMIN 不能透過畫面指派；帳號管理權限不能個別授予。
// 帳號狀態：待啟用（尚未設定密碼）可重新寄送啟用連結；啟用可寄送密碼重設連結（不能對自己）。
const route = useRoute()
const authStore = useAuthStore()
const userId = computed(() => Number(route.params.id))

const { data: user, isLoading, error, reload } = useAsync(() => fetchUser(userId.value))
const { data: options } = useAsync(fetchAuthorizationOptions)

const isSelf = computed(() => user.value?.id === authStore.user?.id)
const isAppAdmin = computed(() => user.value?.roles.some((r) => r.roleCode === 'APP_ADMIN'))
const isPending = computed(() => user.value?.status === 'PENDING')
// 依後端規則：Email、姓名連自己也能改；停用、啟用、寄送重設連結不能對自己；角色與授權不能對自己或系統管理員
const canEdit = computed(() => authStore.hasPermission('USER_MANAGE'))
const canToggleStatus = computed(() => !isSelf.value && authStore.hasPermission('USER_MANAGE'))
const canResendInvitation = computed(() => isPending.value && canEdit.value)
const canSendReset = computed(
  () => user.value?.status === 'ACTIVE' && !isSelf.value && canEdit.value,
)
const canEditRoles = computed(
  () => !isSelf.value && !isAppAdmin.value && authStore.hasPermission('USER_ROLE_MANAGE'),
)
const canEditGrants = computed(
  () => !isSelf.value && !isAppAdmin.value && authStore.hasPermission('USER_PERMISSION_MANAGE'),
)

// 角色顯示名稱
const roleChips = computed(() =>
  (user.value?.roles ?? []).map((r) => ({ ...r, label: roleLabel(r) })),
)

// 有效權限表與個別授權清單
const grid = computed(() =>
  user.value ? permissionGrid(options.value?.permissions ?? [], user.value) : null,
)
const grants = computed(() => (user.value ? visibleGrants(user.value) : []))
const activeGrantCodes = computed(() =>
  (user.value?.permissionGrants ?? [])
    .filter((g) => grantStatus(g) === GRANT_STATUS.ACTIVE)
    .map((g) => g.permission.permissionCode),
)
const labelOf = (code) => {
  const p = options.value?.permissions.find((x) => x.permissionCode === code)
  return p ? permissionLabel(p) : code
}

// 個別授權清單各欄跨幾個權限表欄位：原因＝查看＋建立；期間從「修改」開始跨四欄；狀態一欄；其餘給操作
const SPAN = { reason: 2, period: 4, status: 1 }
const gridWidths = computed(() => (grid.value ? gridColumnWidths(grid.value.actions) : []))
const actionsSpan = computed(() =>
  Math.max(1, gridWidths.value.length - 1 - SPAN.reason - SPAN.period - SPAN.status),
)

// 基本資料三格對齊下方權限表：姓名對齊「建立」欄、建立時間對齊「送審」欄。
// 權限表貼齊卡片內緣，欄寬依比例縮放；基本資料區左右各內縮 24px，而表格文字內縮 14px，
// 所以位置 = 比例 ×（基本資料寬 + 48px）− 10px。
const factsColumns = computed(() => {
  if (!grid.value) return null
  const widths = gridColumnWidths(grid.value.actions)
  const total = widths.reduce((a, b) => a + b, 0)
  const startOf = (key) => {
    const i = grid.value.actions.findIndex((a) => a.key === key) + 1
    return widths.slice(0, i).reduce((a, b) => a + b, 0) / total
  }
  const col = (from, to) => `calc(${(to - from) * 100}% + ${(to - from) * 48 - (from ? 0 : 10)}px)`
  const name = startOf('CREATE')
  const created = startOf('SUBMIT')
  return { gridTemplateColumns: `${col(0, name)} ${col(name, created)} auto`, columnGap: 0 }
})

// 編輯有效權限：勾選後送出
const editingGrid = ref(false)
const checked = ref([])
const added = computed(() => checked.value.filter((c) => !activeGrantCodes.value.includes(c)))
const removed = computed(() => activeGrantCodes.value.filter((c) => !checked.value.includes(c)))
const hasGridChanges = computed(() => added.value.length > 0 || removed.value.length > 0)

function startEditGrid() {
  checked.value = [...activeGrantCodes.value]
  editingGrid.value = true
}

function cancelEditGrid() {
  editingGrid.value = false
}

// 對話框與命令
const formDialog = ref(null)
// disable、activate、roles、invite、reset、gridSave、grantEdit、grantRemove
const openDialog = ref('')
const targetGrant = shallowRef(null) // 編輯或移除的那一筆授權
const isSubmitting = ref(false)
const commandError = shallowRef(null)

function open(name) {
  commandError.value = null
  openDialog.value = name
}

async function run(command, message) {
  isSubmitting.value = true
  commandError.value = null
  try {
    const result = await command()
    user.value = result
    openDialog.value = ''
    notify.success(typeof message === 'function' ? message(result) : message)
    return true
  } catch (e) {
    commandError.value = e
    return false
  } finally {
    isSubmitting.value = false
  }
}

const version = () => user.value.version
const onDisable = (reason) =>
  run(() => disableUser(userId.value, { reason, version: version() }), '帳號已停用')
const onActivate = (reason) =>
  run(
    () => activateUser(userId.value, { reason, version: version() }),
    (saved) =>
      saved.status === 'PENDING' ? '帳號已重新啟用，使用者尚未設定密碼' : '帳號已重新啟用',
  )
const onRoles = ({ roleCodes, reason }) =>
  run(() => replaceRoles(userId.value, { roleCodes, reason, version: version() }), '角色已更新')
const onResendInvitation = () => run(() => resendInvitation(userId.value), '啟用連結已重新寄出')
const onSendReset = () => run(() => sendPasswordReset(userId.value), '密碼重設連結已寄出')

// 個別授權：後端整組取代，同一權限只能一筆
const saveGrants = (grantsToSend, reason, message) =>
  run(
    () =>
      replacePermissionGrants(userId.value, {
        grants: grantsToSend,
        reason,
        version: version(),
      }),
    message,
  )

async function onGridSave({ reason, validFrom, validTo }) {
  const ok = await saveGrants(
    buildGrants(user.value, {
      added: added.value,
      removed: removed.value,
      reason,
      validFrom,
      validTo,
    }),
    reason,
    '個別授權已更新',
  )
  if (ok) editingGrid.value = false
}

function openGrantEdit(grant) {
  targetGrant.value = grant
  open('grantEdit')
}

const onGrantEdit = ({ reason, validFrom, validTo }) =>
  saveGrants(
    user.value.permissionGrants.map((g) =>
      g === targetGrant.value
        ? { permissionCode: g.permission.permissionCode, reason, validFrom, validTo }
        : toGrantRequest(g),
    ),
    reason,
    '授權已更新',
  )

function openGrantRemove(grant) {
  targetGrant.value = grant
  open('grantRemove')
}

const onGrantRemove = (reason) =>
  saveGrants(
    user.value.permissionGrants.filter((g) => g !== targetGrant.value).map(toGrantRequest),
    reason,
    '授權已移除',
  )

function handleSaved(saved) {
  user.value = saved
  notify.success('使用者資料已更新')
}

const dialogProps = (name) => ({
  open: openDialog.value === name,
  'onUpdate:open': (v) => (openDialog.value = v ? name : ''),
})
</script>

<template>
  <div class="page">
    <p v-if="error" class="card state error" role="alert">
      {{ error.message }} <button type="button" class="btn" @click="reload">重新載入</button>
    </p>

    <template v-else-if="user">
      <header class="page-head">
        <nav class="crumb" aria-label="頁面位置">
          <RouterLink :to="{ name: 'users' }">使用者與權限</RouterLink>
          <span aria-hidden="true">›</span>
          <span aria-current="page">{{ user.email }}</span>
        </nav>
        <h1>
          {{ user.name }}
          <StatusTag map="user" :code="user.status" />
        </h1>
      </header>

      <section class="card info" aria-labelledby="basic-title">
        <div class="info-head">
          <h2 id="basic-title" class="section-title">基本資料</h2>
          <div v-if="canEdit" class="actions">
            <template v-if="canToggleStatus">
              <button
                v-if="user.status !== 'DISABLED'"
                type="button"
                class="btn-quiet"
                @click="open('disable')"
              >
                <AppIcon name="pause" :size="15" />停用帳號
              </button>
              <button v-else type="button" class="btn" @click="open('activate')">
                <span class="lead"><AppIcon name="play" :size="15" />重新啟用</span>
              </button>
            </template>
            <button type="button" class="btn" @click="formDialog.openEdit(user)">
              <span class="lead"><AppIcon name="edit" :size="15" />編輯</span>
            </button>
            <button
              v-if="canResendInvitation"
              type="button"
              class="btn btn-primary"
              @click="open('invite')"
            >
              <span class="lead"><AppIcon name="send" :size="15" />重新寄送啟用連結</span>
            </button>
            <button v-if="canSendReset" type="button" class="btn" @click="open('reset')">
              <span class="lead"><AppIcon name="send" :size="15" />寄送密碼重設連結</span>
            </button>
          </div>
        </div>
        <div class="facts">
          <dl class="facts-list" :style="factsColumns">
            <div>
              <dt>Email（登入帳號）</dt>
              <dd class="mono">{{ user.email }}</dd>
            </div>
            <div>
              <dt>姓名</dt>
              <dd>{{ user.name }}</dd>
            </div>
            <div>
              <dt>建立時間</dt>
              <dd class="mono">{{ formatDateTime(user.createdAt) }}</dd>
            </div>
          </dl>
          <!-- 待啟用：放在基本資料最下面一排 -->
          <div v-if="isPending" class="banner" role="status">
            <AppIcon name="info" :size="16" />
            <p>
              使用者尚未設定密碼，目前無法登入。啟用連結已寄至
              <b>{{ user.email }}</b
              >，連結於 24 小時內有效。如使用者未收到信件，或連結已過期，可重新發送。
            </p>
          </div>
        </div>
      </section>

      <section class="card" aria-labelledby="roles-title">
        <div class="card-head">
          <h3 id="roles-title"><AppIcon name="user" />角色</h3>
          <button v-if="canEditRoles" type="button" class="btn" @click="open('roles')">
            <span class="lead"><AppIcon name="edit" :size="15" />編輯角色</span>
          </button>
        </div>
        <div class="body">
          <ul v-if="roleChips.length" class="chips-list">
            <li v-for="r in roleChips" :key="r.roleCode" class="role-chip">
              {{ r.label }} <small class="mono">{{ r.roleCode }}</small>
            </li>
          </ul>
          <p v-else class="empty">還沒有指派角色，這位使用者登入後沒有任何功能可以用。</p>
        </div>
      </section>

      <section class="card" aria-labelledby="effective-title">
        <div class="card-head">
          <div class="head-title">
            <h3 id="effective-title"><AppIcon name="check" />有效權限</h3>
            <!-- 角色或個別授權異動時更新（D-037：供畫面顯示） -->
            <span v-if="user.authorizationUpdatedAt" class="head-note">
              授權最後更新 {{ formatDateTime(user.authorizationUpdatedAt) }}
            </span>
          </div>
          <template v-if="canEditGrants">
            <button v-if="!editingGrid" type="button" class="btn" @click="startEditGrid">
              <span class="lead"><AppIcon name="edit" :size="15" />編輯</span>
            </button>
            <div v-else class="grid-tools">
              <span class="changes">
                <template v-if="hasGridChanges">
                  新增 {{ added.length }} 項、移除 {{ removed.length }} 項
                </template>
                <template v-else>勾選要額外授權的權限</template>
              </span>
              <button type="button" class="btn-quiet" @click="cancelEditGrid">取消</button>
              <button
                type="button"
                class="btn btn-primary"
                :disabled="!hasGridChanges"
                @click="open('gridSave')"
              >
                送出
              </button>
            </div>
          </template>
        </div>
        <PermissionGrid v-if="grid" v-model="checked" :grid="grid" :editing="editingGrid" />

        <!-- 有生效中或尚未開始的個別授權才顯示清單 -->
        <div v-if="grants.length" class="grants">
          <h4>個別額外授權</h4>
          <div class="table-wrap">
            <!-- 和上面的權限表用同一組欄寬：原因對齊「查看」、期間對齊「修改」 -->
            <table class="table grant-table">
              <caption class="sr-only">
                個別額外授權
              </caption>
              <colgroup>
                <col v-for="(w, i) in gridWidths" :key="i" :style="{ width: `${w}px` }" />
              </colgroup>
              <thead>
                <tr>
                  <th scope="col">權限</th>
                  <th scope="col" :colspan="SPAN.reason">原因</th>
                  <th scope="col" :colspan="SPAN.period">期間</th>
                  <th scope="col" :colspan="SPAN.status">狀態</th>
                  <th scope="col" :colspan="actionsSpan" class="actions-col">
                    <span v-if="canEditGrants" class="sr-only">操作</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="g in grants" :key="g.permission.permissionCode">
                  <th scope="row">{{ permissionLabel(g.permission) }}</th>
                  <td :colspan="SPAN.reason">
                    <span class="reason" :title="g.reason">{{ g.reason }}</span>
                  </td>
                  <td :colspan="SPAN.period" class="mono">
                    {{ formatDateTime(g.validFrom) }} –
                    {{ g.validTo ? formatDateTime(g.validTo) : '不限期' }}
                  </td>
                  <td :colspan="SPAN.status">
                    <StatusTag :label="grantStatus(g).label" :tone="grantStatus(g).tone" />
                  </td>
                  <td :colspan="actionsSpan" class="row-actions">
                    <template v-if="canEditGrants">
                      <button
                        type="button"
                        class="icon-btn"
                        title="編輯"
                        :aria-label="`編輯 ${permissionLabel(g.permission)}`"
                        @click="openGrantEdit(g)"
                      >
                        <AppIcon name="edit" :size="16" />
                      </button>
                      <button
                        type="button"
                        class="icon-btn danger"
                        title="移除"
                        :aria-label="`移除 ${permissionLabel(g.permission)}`"
                        @click="openGrantRemove(g)"
                      >
                        <AppIcon name="trash" :size="16" />
                      </button>
                    </template>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </template>

    <p v-else-if="isLoading" class="card state">載入中…</p>
  </div>

  <UserFormDialog ref="formDialog" @saved="handleSaved" />
  <ReasonDialog
    v-bind="dialogProps('disable')"
    title="停用帳號"
    :description="`停用後「${user?.name ?? ''}」就不能登入，資料都會保留，之後可以重新啟用。`"
    confirm-label="確認停用"
    danger
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onDisable"
  />
  <ReasonDialog
    v-bind="dialogProps('activate')"
    title="重新啟用帳號"
    description="啟用後這位使用者就可以再登入；尚未設定過密碼的帳號會回到待啟用。"
    confirm-label="確認啟用"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onActivate"
  />
  <ConfirmDialog
    v-bind="dialogProps('invite')"
    title="重新寄送啟用連結"
    :description="`啟用連結將重新寄送至 ${user?.email ?? ''}，寄出後，先前的連結將失效。`"
    confirm-label="確認寄送"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onResendInvitation"
  />
  <ConfirmDialog
    v-bind="dialogProps('reset')"
    title="寄送密碼重設連結"
    :description="`密碼重設連結將寄送至 ${user?.email ?? ''}，連結於 24 小時內有效。使用者可透過連結設定新密碼；完成設定前，原密碼仍可使用。`"
    confirm-label="確認寄送"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onSendReset"
  />
  <RolesDialog
    v-if="user"
    v-bind="dialogProps('roles')"
    :roles="options?.roles ?? []"
    :current="user.roles.map((r) => r.roleCode)"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onRoles"
  />
  <GrantDialog
    v-bind="dialogProps('gridSave')"
    title="個別額外授權"
    :added="added.map(labelOf)"
    :removed="removed.map(labelOf)"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onGridSave"
  />
  <GrantDialog
    v-bind="dialogProps('grantEdit')"
    title="編輯授權"
    :grant="targetGrant"
    :grant-label="targetGrant ? permissionLabel(targetGrant.permission) : ''"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onGrantEdit"
  />
  <ReasonDialog
    v-bind="dialogProps('grantRemove')"
    title="移除授權"
    :description="
      targetGrant
        ? `移除「${permissionLabel(targetGrant.permission)}」後，這項權限會立即收回。`
        : ''
    "
    confirm-label="確認移除"
    danger
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onGrantRemove"
  />
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-top: 2px;
}

.page-head {
  min-height: var(--bar-h);
  padding: 2px var(--tools-w) 0 4px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink-3);
}

.crumb a {
  font-weight: 600;
  color: var(--accent);
  text-decoration: none;
}

.page-head h1 {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 4px;
  font-size: 24px;
  font-weight: 800;
  color: var(--accent-strong);
}

.info {
  padding: 20px 24px 22px;
}

.info-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  align-items: center;
}

.head-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
}

.head-note {
  font-size: 12px;
  color: var(--ink-3);
}

.actions .btn,
.card-head .btn {
  min-width: 0;
  justify-content: center;
}

.lead {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.facts {
  display: grid;
  gap: 14px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--line-soft);
}

.facts-list {
  display: grid;
  /* 權限表還沒載入前先依內容寬度排；載入後由 factsColumns 對齊權限表欄位 */
  grid-template-columns: repeat(3, max-content);
  gap: 18px 64px;
  align-content: start;
  margin: 0;
}

.facts dt {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

.facts dd {
  margin: 0;
  padding-right: 8px;
  overflow-wrap: anywhere;
  font-size: 15px;
  font-weight: 600;
}

.body {
  padding: 16px 20px;
}

.chips-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.role-chip {
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--info-soft);
  font-weight: 600;
  color: var(--accent-strong);
}

.role-chip small {
  margin-left: 6px;
  font-size: 10.5px;
  font-weight: 400;
  color: var(--ink-3);
}

.empty {
  font-size: 13px;
  color: var(--ink-3);
}

.actions-col {
  width: 130px;
}

.banner {
  display: flex;
  margin-top: 8px;
  gap: 8px;
  align-items: flex-start;
  font-size: 14px;
  line-height: 1.7;
  color: var(--warning);
}

.banner p {
  margin: 0;
}

.banner .icon {
  flex: none;
  /* 行高 14px × 1.7 ≈ 24px，16px 圖示上下各留 4px，和第一行字置中 */
  margin-top: 4px;
  color: var(--warning);
}

.grid-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.changes {
  font-size: 13px;
  color: var(--ink-2);
}

.grants {
  border-top: 1px solid var(--line-soft);
}

.grants h4 {
  padding: 14px 20px 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}

/* 操作欄從「管理」那一欄開始；圖示按鈕本身有內距，往左拉一點讓鉛筆對齊標題文字 */
.row-actions {
  white-space: nowrap;
}

.row-actions .icon-btn:first-child {
  margin-left: -8px;
}

.grant-table {
  table-layout: fixed;
}

/* 權限名稱和上面權限表的資料名稱同樣式 */
.grant-table th[scope='row'] {
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  text-align: left;
}

/* 原因可能很長：最多兩行，超過就省略，滑鼠移上去看完整內容 */
.reason {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.icon-btn.danger {
  color: var(--ink-3);
}

.icon-btn.danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}
</style>

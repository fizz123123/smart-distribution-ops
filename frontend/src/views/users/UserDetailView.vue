<script setup>
import { computed, ref, shallowRef } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import ReasonDialog from '@/components/ReasonDialog.vue'
import RecordInfo from '@/components/RecordInfo.vue'
import StatusTag from '@/components/StatusTag.vue'
import AddGrantDialog from '@/components/users/AddGrantDialog.vue'
import RolesDialog from '@/components/users/RolesDialog.vue'
import UserFormDialog from '@/components/users/UserFormDialog.vue'
import { useAsync } from '@/composables/useAsync'
import { grantStatus, permissionLabel, permissionTable, roleOwnedCodes } from '@/config/identity'
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
} from '@/api/users'

// 使用者詳情（ADM-01）：基本資料、角色、個別額外授權、有效權限。
// 規則：不能修改自己；APP_ADMIN 不能透過畫面指派；帳號管理權限不能個別授予。
const route = useRoute()
const authStore = useAuthStore()
const userId = computed(() => Number(route.params.id))

const { data: user, isLoading, error, reload } = useAsync(() => fetchUser(userId.value))
const { data: options } = useAsync(fetchAuthorizationOptions)

const isSelf = computed(() => user.value?.id === authStore.user?.id)
const isAppAdmin = computed(() => user.value?.roles.some((r) => r.roleCode === 'APP_ADMIN'))
// 依後端規則：Email、姓名連自己也能改；停用、啟用不能對自己；角色與授權不能對自己或系統管理員
const canEdit = computed(() => authStore.hasPermission('USER_MANAGE'))
const canToggleStatus = computed(() => !isSelf.value && authStore.hasPermission('USER_MANAGE'))
const canEditRoles = computed(
  () => !isSelf.value && !isAppAdmin.value && authStore.hasPermission('USER_ROLE_MANAGE'),
)
const canEditGrants = computed(
  () => !isSelf.value && !isAppAdmin.value && authStore.hasPermission('USER_PERMISSION_MANAGE'),
)

// 有效權限表（資料 × 動作）
const table = computed(() =>
  permissionTable(options.value?.permissions ?? [], user.value?.effectivePermissions ?? []),
)
const roleOwned = computed(() => (user.value ? roleOwnedCodes(user.value) : []))

// 對話框與命令
const formDialog = ref(null)
const openDialog = ref('') // disable、activate、roles、addGrant、removeGrant
const removing = shallowRef(null) // 要移除的那筆授權
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
    user.value = await command()
    openDialog.value = ''
    notify.success(message)
  } catch (e) {
    commandError.value = e
  } finally {
    isSubmitting.value = false
  }
}

const version = () => user.value.version
const onDisable = (reason) =>
  run(() => disableUser(userId.value, { reason, version: version() }), '帳號已停用')
const onActivate = (reason) =>
  run(() => activateUser(userId.value, { reason, version: version() }), '帳號已重新啟用')
const onRoles = ({ roleCodes, reason }) =>
  run(() => replaceRoles(userId.value, { roleCodes, reason, version: version() }), '角色已更新')
// 後端是整組取代：新增＝原本的授權＋新的一筆；移除＝原本的授權扣掉那一筆
const toRequest = (g) => ({
  permissionCode: g.permission.permissionCode,
  reason: g.reason,
  validFrom: g.validFrom,
  validTo: g.validTo,
})
const grantedCodes = computed(() =>
  (user.value?.permissionGrants ?? []).map((g) => g.permission.permissionCode),
)

const onAddGrant = (grant) =>
  run(
    () =>
      replacePermissionGrants(userId.value, {
        grants: [...user.value.permissionGrants.map(toRequest), grant],
        reason: grant.reason,
        version: version(),
      }),
    '已新增授權',
  )

function openRemove(grant) {
  removing.value = grant
  open('removeGrant')
}

const onRemoveGrant = (reason) =>
  run(
    () =>
      replacePermissionGrants(userId.value, {
        grants: user.value.permissionGrants.filter((g) => g !== removing.value).map(toRequest),
        reason,
        version: version(),
      }),
    '已移除授權',
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
                v-if="user.status === 'ACTIVE'"
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
          </div>
        </div>
        <dl class="facts">
          <div>
            <dt>Email（登入帳號）</dt>
            <dd class="mono">{{ user.email }}</dd>
          </div>
          <div>
            <dt>姓名</dt>
            <dd>{{ user.name }}</dd>
          </div>
          <div>
            <dt>授權最後更新</dt>
            <dd class="mono">{{ formatDateTime(user.authorizationUpdatedAt) }}</dd>
          </div>
          <RecordInfo
            class="record-cell"
            :created-at="user.createdAt"
            :updated-at="user.updatedAt"
          />
        </dl>
      </section>

      <section class="card" aria-labelledby="roles-title">
        <div class="card-head">
          <h3 id="roles-title"><AppIcon name="user" />角色</h3>
          <button v-if="canEditRoles" type="button" class="btn" @click="open('roles')">
            <span class="lead"><AppIcon name="edit" :size="15" />編輯角色</span>
          </button>
        </div>
        <div class="body">
          <ul v-if="user.roles.length" class="chips-list">
            <li v-for="r in user.roles" :key="r.roleCode" class="role-chip">
              {{ r.name }} <small class="mono">{{ r.roleCode }}</small>
            </li>
          </ul>
          <p v-else class="empty">還沒有指派角色，這位使用者登入後沒有任何功能可以用。</p>
        </div>
      </section>

      <section class="card" aria-labelledby="grants-title">
        <div class="card-head">
          <h3 id="grants-title"><AppIcon name="layers" />個別額外授權</h3>
          <button v-if="canEditGrants" type="button" class="btn" @click="open('addGrant')">
            <span class="lead"><AppIcon name="plus" :size="15" />新增授權</span>
          </button>
        </div>
        <div class="table-wrap">
          <table class="table">
            <caption class="sr-only">
              個別額外授權
            </caption>
            <thead>
              <tr>
                <th scope="col">權限</th>
                <th scope="col">原因</th>
                <th scope="col">開始</th>
                <th scope="col">結束</th>
                <th scope="col">狀態</th>
                <th v-if="canEditGrants" scope="col" class="actions-col">
                  <span class="sr-only">操作</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="g in user.permissionGrants" :key="g.permission.permissionCode">
                <td>
                  {{ permissionLabel(g.permission) }}
                  <div class="sub mono">{{ g.permission.permissionCode }}</div>
                </td>
                <td>{{ g.reason }}</td>
                <td class="mono">{{ formatDateTime(g.validFrom) }}</td>
                <td class="mono">{{ g.validTo ? formatDateTime(g.validTo) : '不限期' }}</td>
                <td>
                  <StatusTag :label="grantStatus(g).label" :tone="grantStatus(g).tone" />
                </td>
                <td v-if="canEditGrants" class="text-right">
                  <button type="button" class="btn-quiet" @click="openRemove(g)">移除</button>
                </td>
              </tr>
              <tr v-if="!user.permissionGrants.length">
                <td :colspan="canEditGrants ? 6 : 5" class="state">—</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="card" aria-labelledby="effective-title">
        <div class="card-head">
          <h3 id="effective-title"><AppIcon name="check" />有效權限</h3>
        </div>
        <div class="table-wrap">
          <table class="table perm-table">
            <caption class="sr-only">
              有效權限
            </caption>
            <thead>
              <tr>
                <th scope="col">資料</th>
                <th v-for="a in table.actions" :key="a.key" scope="col" class="cell">
                  {{ a.label }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in table.rows" :key="r.resource">
                <th scope="row">{{ r.label }}</th>
                <td v-for="(c, i) in r.cells" :key="i" class="cell" :class="c">
                  <template v-if="c === 'owned'"
                    ><span aria-hidden="true">✓</span><span class="sr-only">有</span></template
                  >
                  <template v-else-if="c === 'missing'"
                    ><span aria-hidden="true">—</span><span class="sr-only">沒有</span></template
                  >
                </td>
              </tr>
              <tr v-if="!table.rows.length">
                <td :colspan="table.actions.length + 1" class="state">沒有任何權限</td>
              </tr>
            </tbody>
          </table>
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
    description="啟用後這位使用者就可以再登入。"
    confirm-label="確認啟用"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onActivate"
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
  <AddGrantDialog
    v-if="user"
    v-bind="dialogProps('addGrant')"
    :permissions="options?.permissions ?? []"
    :role-owned="roleOwned"
    :granted="grantedCodes"
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onAddGrant"
  />
  <ReasonDialog
    v-bind="dialogProps('removeGrant')"
    title="移除授權"
    :description="
      removing ? `移除「${permissionLabel(removing.permission)}」後，這項權限會立即收回。` : ''
    "
    confirm-label="確認移除"
    danger
    :is-submitting="isSubmitting"
    :error="commandError"
    @confirm="onRemoveGrant"
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
  gap: 10px;
  align-items: center;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 28px;
  margin: 18px 0 0;
  padding-top: 18px;
  border-top: 1px solid var(--line-soft);
}

.facts dt {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

.facts dd {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.record-cell {
  grid-column: 1 / -1;
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
  width: 90px;
}

.perm-table th[scope='row'] {
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  text-align: left;
  white-space: nowrap;
}

.perm-table .cell {
  text-align: center;
  white-space: nowrap;
}

.perm-table .owned {
  font-weight: 700;
  color: var(--success);
}

.perm-table .missing {
  color: var(--ink-3);
}
</style>

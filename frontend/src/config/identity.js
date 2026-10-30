// 帳號與權限的顯示設定：角色、權限的中文名稱與使用者清單設定。
import { by } from '@/utils/filters'

// 角色代碼 → 中文（後端 IdentityDataInitializer 的角色清單）
export const ROLE_LABELS = {
  SALES: '業務',
  SALES_MANAGER: '業務主管',
  PURCHASING: '採購',
  PURCHASING_MANAGER: '採購主管',
  WAREHOUSE: '倉管',
  WAREHOUSE_MANAGER: '倉管主管',
  FINANCE: '財務',
  FINANCE_MANAGER: '財務主管',
  MANAGER: '營運主管',
  APP_ADMIN: '系統管理員',
}

// 權限的資源與動作 → 中文（後端權限代碼＝資源_動作，例如 CUSTOMER_VIEW）
export const RESOURCE_LABELS = {
  USER: '使用者',
  USER_ROLE: '使用者角色',
  USER_PERMISSION: '使用者個別權限',
  SO: '銷售訂單',
  SO_CANCELLATION: '餘量取消',
  CUSTOMER: '客戶',
  SUPPLIER: '供應商',
  COMPANY_ITEM: '商品（公司料號）',
  INVENTORY_POLICY: '安全庫存',
  CUSTOMER_ITEM_MAPPING: '客戶料號對應',
  CUSTOMER_PRICE: '客戶價格',
  INVENTORY: '庫存',
  INVENTORY_TRANSACTION: '庫存異動',
  INVENTORY_RESERVATION: '庫存預留',
  INVENTORY_ADJUSTMENT: '庫存調整',
}

export const ACTION_LABELS = {
  VIEW: '查看',
  CREATE: '建立',
  UPDATE: '修改',
  SUBMIT: '送審',
  APPROVE: '核准',
  STATUS_MANAGE: '啟用停用',
  PAYMENT_TERM_MANAGE: '修改付款條件',
  MANAGE: '管理',
  REVOKE: '撤銷',
  POST: '過帳',
}

// 有效權限表的欄標題較窄，只在這裡用短名稱；權限名稱（清單、送出視窗）仍用完整名稱
const GRID_ACTION_LABELS = { PAYMENT_TERM_MANAGE: '付款條件' }

export const permissionLabel = (p) =>
  `${RESOURCE_LABELS[p.resource] ?? p.resource}・${ACTION_LABELS[p.action] ?? p.action}`

// 帳號管理權限不能用個別授權給（後端 USER_MANAGEMENT_PERMISSION_NOT_GRANTABLE）
export const NOT_GRANTABLE = ['USER_MANAGE', 'USER_ROLE_MANAGE', 'USER_PERMISSION_MANAGE']

// 角色的顯示名稱
export const roleLabel = (role) => role.name || ROLE_LABELS[role.roleCode] || role.roleCode

// 清單用的顯示列
export const toUserRow = (u) => ({
  ...u,
  roleText: u.roles.map(roleLabel).join('、') || '—',
})

export const usersList = {
  title: '使用者與權限',
  description: '管理帳號、角色與個別額外授權；任何人都不能修改自己的帳號與授權。',
  searchPlaceholder: '姓名、Email 或角色',
  primaryAction: '新增使用者',
  statusMap: 'user',
  tabs: [
    { label: '全部' },
    { label: '啟用', filter: by('status', 'ACTIVE') },
    { label: '待啟用', filter: by('status', 'PENDING') },
    { label: '停用', filter: by('status', 'DISABLED') },
  ],
  columns: [
    { key: 'name', label: '姓名', type: 'doc', sub: 'email', width: '40%' },
    { key: 'roleText', label: '角色', type: 'text' },
    { key: 'status', label: '狀態', type: 'status' },
  ],
  rowLink: (row) => ({ name: 'userDetail', params: { id: row.id } }),
}

// 新增使用者時一起指派角色，角色 API 必填原因，自動帶這句
export const CREATE_ROLE_REASON = '新增使用者時指派'

// 個別授權的狀態：依開始、結束時間判斷（結束時間空白＝不限期）
export const GRANT_STATUS = {
  UPCOMING: { label: '尚未開始', tone: 'info' },
  ACTIVE: { label: '生效中', tone: 'success' },
  EXPIRED: { label: '已過期', tone: 'neutral' },
}

export function grantStatus(grant, now = new Date()) {
  if (now < new Date(grant.validFrom)) return GRANT_STATUS.UPCOMING
  if (grant.validTo && now >= new Date(grant.validTo)) return GRANT_STATUS.EXPIRED
  return GRANT_STATUS.ACTIVE
}

// 角色本來就有的權限＝有效權限扣掉目前生效中的個別授權
export function roleOwnedCodes(user) {
  const granted = new Set(
    user.permissionGrants
      .filter((g) => grantStatus(g) === GRANT_STATUS.ACTIVE)
      .map((g) => g.permission.permissionCode),
  )
  return user.effectivePermissions.map((p) => p.permissionCode).filter((code) => !granted.has(code))
}

// 有效權限表：列＝資料（資源），欄＝動作。每一格是一個權限，種類：
//   role：角色本來就有（深藍勾，不能改）
//   grant：個別額外授權，生效中（綠勾）
//   open：可以個別授權、目前沒有
//   locked：系統有這個權限，但不能個別授權（帳號管理權限），也沒有擁有
//   none：系統沒有這個動作
export function permissionGrid(allPermissions, user) {
  const effective = user.effectivePermissions
  const roleOwned = new Set(roleOwnedCodes(user))
  const activeGrants = new Set(
    user.permissionGrants
      .filter((g) => grantStatus(g) === GRANT_STATUS.ACTIVE)
      .map((g) => g.permission.permissionCode),
  )
  const all = [
    ...allPermissions,
    ...effective.filter((p) => !allPermissions.some((a) => a.permissionCode === p.permissionCode)),
  ]
  const actions = Object.keys(ACTION_LABELS).filter((a) => all.some((p) => p.action === a))
  const resources = [...new Set(all.map((p) => p.resource))]
  const kindOf = (code) => {
    if (roleOwned.has(code)) return 'role'
    if (activeGrants.has(code)) return 'grant'
    if (NOT_GRANTABLE.includes(code)) return 'locked'
    return 'open'
  }
  return {
    actions: actions.map((a) => ({
      key: a,
      label: GRID_ACTION_LABELS[a] ?? ACTION_LABELS[a],
      fullLabel: ACTION_LABELS[a],
    })),
    rows: resources.map((resource) => ({
      resource,
      label: RESOURCE_LABELS[resource] ?? resource,
      cells: actions.map((action) => {
        const p = all.find((x) => x.resource === resource && x.action === action)
        return p ? { code: p.permissionCode, kind: kindOf(p.permissionCode), permission: p } : null
      }),
    })),
  }
}

// 有效權限表與下方個別授權清單共用的欄寬（px）：第一欄是資料名稱，動作欄一律同寬（以最長的標題為準）。
// 兩張表用同一組欄寬（table-layout: fixed），清單的欄位才會和權限表上下對齊。
export function gridColumnWidths(actions) {
  const longest = Math.max(0, ...actions.map((a) => a.label.length))
  const actionWidth = Math.max(64, longest * 12 + 32)
  return [180, ...actions.map(() => actionWidth)]
}

// 下方清單只顯示還沒過期的授權（生效中、尚未開始）
export const visibleGrants = (user) =>
  user.permissionGrants.filter((g) => grantStatus(g) !== GRANT_STATUS.EXPIRED)

// 送給後端的一筆授權
export const toGrantRequest = (g) => ({
  permissionCode: g.permission.permissionCode,
  reason: g.reason,
  validFrom: g.validFrom,
  validTo: g.validTo,
})

/**
 * 依勾選結果組出整組授權（後端整組取代，同一權限只能一筆）。
 * 取消勾選的移除；新勾選的加入，若原本有已過期的同權限紀錄，就以新設定取代那一筆。
 */
export function buildGrants(user, { added, removed, reason, validFrom, validTo }) {
  const kept = user.permissionGrants
    .filter((g) => !removed.includes(g.permission.permissionCode))
    .filter((g) => !added.includes(g.permission.permissionCode))
    .map(toGrantRequest)
  const fresh = added.map((code) => ({ permissionCode: code, reason, validFrom, validTo }))
  return [...kept, ...fresh]
}

/**
 * 主管包含職員角色（後端 includedRoleCodes）：勾選主管時，被包含的職員角色顯示為已勾選並鎖住。
 * 回傳每個角色的勾選狀態，被包含的角色不送給後端。
 * @param {{ roleCode, name, includedRoleCodes? }[]} roles 可指派的角色
 * @param {string[]} selected 使用者勾選的角色代碼
 */
export function roleChoices(roles, selected) {
  const includedBy = {}
  roles
    .filter((r) => selected.includes(r.roleCode))
    .forEach((r) => (r.includedRoleCodes ?? []).forEach((code) => (includedBy[code] ??= r.name)))
  return roles.map((r) => ({
    ...r,
    includedBy: includedBy[r.roleCode] ?? '',
    checked: selected.includes(r.roleCode) || Boolean(includedBy[r.roleCode]),
  }))
}

// 送給後端的角色：去掉被主管包含的職員角色
export function rolesToSubmit(roles, selected) {
  const choices = roleChoices(roles, selected)
  return selected.filter((code) => !choices.find((c) => c.roleCode === code)?.includedBy)
}

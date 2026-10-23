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

export const permissionLabel = (p) =>
  `${RESOURCE_LABELS[p.resource] ?? p.resource}・${ACTION_LABELS[p.action] ?? p.action}`

// 帳號管理權限不能用個別授權給（後端 USER_MANAGEMENT_PERMISSION_NOT_GRANTABLE）
export const NOT_GRANTABLE = ['USER_MANAGE', 'USER_ROLE_MANAGE', 'USER_PERMISSION_MANAGE']

// 清單用的顯示列
export const toUserRow = (u) => ({
  ...u,
  roleText: u.roles.map((r) => r.name || ROLE_LABELS[r.roleCode] || r.roleCode).join('、') || '—',
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

// 有效權限表：列＝資料（資源），欄＝動作。
// 格子三種：owned（有）、missing（這個動作存在但沒有）、none（系統沒有這個動作）。
export function permissionTable(allPermissions, effectivePermissions) {
  const owned = new Set(effectivePermissions.map((p) => p.permissionCode))
  const all = [
    ...allPermissions,
    ...effectivePermissions.filter(
      (p) => !allPermissions.some((a) => a.permissionCode === p.permissionCode),
    ),
  ]
  const actions = Object.keys(ACTION_LABELS).filter((a) => all.some((p) => p.action === a))
  const resources = [...new Set(all.map((p) => p.resource))]
  return {
    actions: actions.map((a) => ({ key: a, label: ACTION_LABELS[a] })),
    rows: resources.map((resource) => ({
      resource,
      label: RESOURCE_LABELS[resource] ?? resource,
      cells: actions.map((action) => {
        const p = all.find((x) => x.resource === resource && x.action === action)
        if (!p) return 'none'
        return owned.has(p.permissionCode) ? 'owned' : 'missing'
      }),
    })),
  }
}

// 狀態代碼 → 中文標籤與色調。代碼依團隊文件 05、10、11 號。
// tone：danger 紅、warning 橘、info 藍、success 綠、neutral 灰、strong 深藍。

const common = {
  DRAFT: { label: '草稿', tone: 'neutral' },
  PENDING_APPROVAL: { label: '待核准', tone: 'warning' },
  RETURNED: { label: '已退回', tone: 'warning' },
}

export const statusMaps = {
  master: {
    ...common,
    ACTIVE: { label: '啟用', tone: 'success' },
    INACTIVE: { label: '停用', tone: 'neutral' },
  },
  price: {
    PENDING_REVIEW: { label: '待覆核', tone: 'warning' },
    ACTIVE: { label: '生效中', tone: 'success' },
    REJECTED: { label: '已拒絕', tone: 'neutral' },
    REPLACED: { label: '已取代', tone: 'neutral' },
    REVOKED: { label: '已撤銷', tone: 'danger' },
    // 以下是清單上「價格審核」欄的衍生狀態（依這筆對應的所有價格版本算出）
    NONE: { label: '缺少生效價格', tone: 'danger' },
    ACTIVE_WITH_PENDING: { label: '新價格待覆核', tone: 'warning' },
    INACTIVE_MAPPING: { label: '—', tone: 'neutral' },
  },
  so: {
    ...common,
    APPROVED: { label: '已核准', tone: 'info' },
    CLOSED: { label: '已結案', tone: 'strong' },
    CANCELLED: { label: '已放棄', tone: 'neutral' },
  },
  po: {
    ...common,
    APPROVED: { label: '已下單', tone: 'info' },
    PARTIALLY_RECEIVED: { label: '部分到貨', tone: 'info' },
    CLOSED: { label: '已結案', tone: 'strong' },
    CANCELLED: { label: '已取消', tone: 'neutral' },
  },
  do: {
    DRAFT: { label: '待揀貨', tone: 'neutral' },
    READY: { label: '待交車', tone: 'info' },
    POSTED: { label: '已出貨', tone: 'strong' },
    VOIDED: { label: '已作廢', tone: 'neutral' },
  },
  receipt: {
    POSTED: { label: '已過帳', tone: 'strong' },
  },
  adjustment: {
    POSTED: { label: '已過帳', tone: 'strong' },
    REVERSED: { label: '已更正', tone: 'neutral' },
  },
  receivable: {
    PENDING_CONFIRMATION: { label: '待確認', tone: 'warning' },
    OPEN: { label: '未收', tone: 'info' },
    PARTIALLY_PAID: { label: '部分收款', tone: 'info' },
    PAID: { label: '已結清', tone: 'success' },
  },
  payment: {
    CONFIRMED: { label: '已確認', tone: 'success' },
    VOIDED: { label: '已作廢', tone: 'neutral' },
  },
  approval: {
    PENDING: { label: '待處理', tone: 'warning' },
  },
  user: {
    ACTIVE: { label: '啟用', tone: 'success' },
    DISABLED: { label: '停用', tone: 'neutral' },
  },
  binding: {
    ACTIVE: { label: '已綁定', tone: 'success' },
    DISABLED: { label: '已停用', tone: 'neutral' },
    UNBOUND: { label: '已解除', tone: 'neutral' },
  },
  ai: {
    COMPLETED: { label: '已完成', tone: 'success' },
    FAILED: { label: '失敗', tone: 'danger' },
  },
  // 動態標記（不是單據狀態），例如逾期、即將到期
  flag: {
    OVERDUE: { label: '逾期', tone: 'danger' },
    DUE_SOON: { label: '即將到期', tone: 'warning' },
    SHORTAGE: { label: '缺料', tone: 'danger' },
    LOW_STOCK: { label: '低於安全庫存', tone: 'warning' },
    MISSING_PRICE: { label: '缺少價格', tone: 'warning' },
  },
}

export function statusOf(map, code) {
  return statusMaps[map]?.[code] ?? { label: code, tone: 'neutral' }
}

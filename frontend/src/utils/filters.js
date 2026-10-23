// 清單篩選小工具：by('status', 'DRAFT', 'RETURNED') → 篩出狀態為其中之一的資料列。
export const by =
  (field, ...codes) =>
  (row) =>
    codes.includes(row[field])

export const hasFlag = (flag) => (row) => Array.isArray(row.flags) && row.flags.includes(flag)

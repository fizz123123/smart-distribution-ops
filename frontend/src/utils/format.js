import dayjs from 'dayjs'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'

dayjs.extend(utc)
dayjs.extend(timezone)

const DISPLAY_TIMEZONE = 'Asia/Taipei'
const EMPTY = '—'

export const pad2 = (n) => String(n).padStart(2, '0')

// 後端時間是 UTC 的 ISO 8601，畫面一律以台北時間顯示（12 號 2.1）→「2026-03-12 11:00」
export function formatDateTime(value) {
  return value ? dayjs.utc(value).tz(DISPLAY_TIMEZONE).format('YYYY-MM-DD HH:mm') : EMPTY
}

// 「2026-03-12」：日期欄位（例如要求交期）本身就是 YYYY-MM-DD，不做時區轉換；時間欄位取台北日期
export function formatDate(value) {
  if (!value) {
    return EMPTY
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value
  }
  return dayjs.utc(value).tz(DISPLAY_TIMEZONE).format('YYYY-MM-DD')
}

// 「14:05」：頂部列的資料更新時間；傳入 Date 或 ISO 字串
export function formatTime(value) {
  return value ? dayjs(value).tz(DISPLAY_TIMEZONE).format('HH:mm') : EMPTY
}

// 數量與金額由後端計算，前端只負責顯示，不做加減乘除
export function formatQuantity(value, decimalPlaces = 3) {
  if (value === null || value === undefined) {
    return EMPTY
  }
  return Number(value).toLocaleString('zh-TW', { maximumFractionDigits: decimalPlaces })
}

export function formatMoney(value, fractionDigits = 2) {
  if (value === null || value === undefined) {
    return EMPTY
  }
  return Number(value).toLocaleString('zh-TW', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

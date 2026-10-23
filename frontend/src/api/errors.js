// 統一錯誤格式，對應 12 號文件 §2.5：{ code, message, fieldErrors, traceId }。
// 畫面不能只顯示「操作失敗」，要優先顯示 message 與欄位錯誤。

const fallbackMessages = {
  400: '資料格式有誤，請檢查標示的欄位。',
  401: '登入已過期，請重新登入。',
  403: '你沒有執行這個操作的權限。',
  404: '找不到資料，或你沒有查看權限。',
  409: '資料已被其他人更新，請重新載入後再試。',
  422: '資料不符合業務規則。',
}

export class ApiError extends Error {
  constructor({ status, code, message, fieldErrors = [], traceId = '' }) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.traceId = traceId
  }

  static fromResponse(status, data) {
    return new ApiError({
      status,
      code: data?.code || `HTTP_${status}`,
      message: data?.message || fallbackMessages[status] || '系統發生錯誤，請稍後再試。',
      fieldErrors: data?.fieldErrors || [],
      traceId: data?.traceId || '',
    })
  }

  static network() {
    return new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      message: '無法連線到伺服器，請確認後端已經啟動。',
    })
  }

  get isVersionConflict() {
    return this.code === 'VERSION_CONFLICT'
  }
}

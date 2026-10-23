// 全站提示訊息（上方小方塊，幾秒後自動消失）。使用 Element Plus 的 ElMessage，畫面統一呼叫這裡。
import { ElMessage } from 'element-plus'

export const notify = {
  success: (message) => ElMessage.success({ message, grouping: true }),
  error: (message) => ElMessage.error({ message, grouping: true }),
}

// 輸入文字的整理：中文輸入法常打出全形英數與符號（例如 ＠、．），複製貼上也常多帶空白。

// 全形英數與符號（！～ U+FF01–U+FF5E）轉半形；全形空白轉一般空白
export const toHalfWidth = (value) =>
  String(value ?? '')
    .replace(/[\uFF01-\uFF5E]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0xfee0))
    .replace(/\u3000/g, ' ')

// Email：轉半形並去掉前後空白，再交給驗證與後端
export const normalizeEmail = (value) => toHalfWidth(value).trim()

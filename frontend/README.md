# 前端（Vue 3）

智慧營運管理系統（THERMAL ROLL）的 Web 前端。使用 Vue 3、Vite、Vue Router、Pinia、Element Plus、axios 與 dayjs，語言為 JavaScript。

## 環境需求

- Node.js 20 以上（建議 22 LTS）
- 後端已在本機啟動（預設 `http://localhost:8080`），啟動方式見 `backend/` 的說明

## 第一次啟動

```bash
cd frontend
npm install
cp .env.example .env.local   # 需要時修改 API 位址
npm run dev
```

開啟 <http://localhost:5173>。本機後端會自動建立管理員帳號 `admin@smartdistribution.local`／`Admin1234`，可以先用它登入，到「使用者與權限」建立其他角色的測試帳號。

> 開發伺服器固定使用 5173 port，因為後端 CORS 預設只允許 `http://localhost:5173`。要改 port 時，後端的 `CORS_ALLOWED_ORIGINS` 也要一起改。

## 指令

| 指令 | 用途 |
| --- | --- |
| `npm run dev` | 啟動開發伺服器 |
| `npm run build` | 建置正式版到 `dist/` |
| `npm run preview` | 在本機預覽建置結果 |
| `npm run lint` | ESLint 檢查並自動修正 |
| `npm run format` | Prettier 排版 `src/` |

## 目錄結構

```text
src/
├─ main.js         建立 Vue app、載入 Pinia、Element Plus 與全站樣式，還原登入狀態後掛上路由
├─ App.vue         根元件，只顯示路由畫面
├─ api/            呼叫後端 API；每個模組一個檔案，例如 users.js、customers.js
│  ├─ http.js      共用 axios 實例：帶 JWT、處理 401、失敗時丟出 ApiError
│  └─ errors.js    ApiError：{ status, code, message, fieldErrors, traceId }
├─ stores/         Pinia store
│  ├─ auth.js      目前使用者、權限、登入與登出
│  └─ app.js       頂部列的重新整理與資料更新時間
├─ router/         路由、登入與權限守衛、分頁標題
├─ config/
│  ├─ navigation.js  側邊選單與畫面權限（畫面編號對應 docs/12 第 3 節）
│  ├─ status.js      狀態代碼 → 中文與色調，StatusTag 使用
│  └─ identity.js    角色、權限的中文名稱與使用者清單設定
├─ layouts/        登入後的版面：側邊選單、右上角工具、帳號選單、全站搜尋；不用登入的頁面（登入、設定密碼）共用 AuthLayout
├─ components/     共用元件：AppIcon、ListPage、StatusTag、ReasonDialog、ConfirmDialog、ChangePasswordDialog…
│  └─ users/       使用者與權限專用：新增／編輯使用者、角色勾選、有效權限表、個別授權視窗
├─ composables/    可重用的邏輯：useAsync（載入資料）、useSidebar、useLogin
├─ views/          畫面；依模組分資料夾，例如 views/users/；SetPasswordView 是信中連結開啟的設定密碼頁
├─ styles/         設計 token（tokens.css）、Element Plus 主題（element-theme.css）、全站樣式（base.css）
└─ utils/          時間與數字格式、清單篩選、提示訊息（notify）
```

### 樣式

- 顏色、圓角、陰影、字型都定義在 `styles/tokens.css`，元件只使用這些變數，不寫死顏色。
- Element Plus 的顏色由 `styles/element-theme.css` 對應到 token；要改主題色請改 `tokens.css`。
- `base.css` 提供共用的 class，例如 `.card`、`.card-head`、`.btn`、`.btn-primary`、`.table`、`.chip`、`.doc-id`、`.sr-only`。
- 清單頁優先使用 `ListPage`：傳入 config（標題、篩選、欄位）與 load 函式即可，寫法參考 `views/users/UserListView.vue` 與 `config/identity.js` 的 `usersList`。

## 開發約定

這些約定來自 `docs/12-api-and-screen-contracts.md` 與 `docs/13-system-architecture.md`：

1. **API 只透過 `src/api/` 呼叫**：畫面不要直接 `import axios`，統一使用 `http.js` 的實例，JWT 和錯誤處理才會一致。
2. **權限只決定「顯示什麼」**：用 `authStore.hasPermission('CUSTOMER_CREATE')` 決定按鈕要不要出現；真正的權限檢查在後端，前端不能當作安全機制。
3. **不自行推測狀態**：操作成功後，用後端回傳的最新資料（含 `status`、`version`）更新畫面，不要在前端自己改狀態。
4. **修改類請求要帶 `version`**：從詳情取得的 `version` 原樣送回；收到 `409 VERSION_CONFLICT` 時請使用者重新載入。
5. **錯誤訊息用後端的 `message`**：API 失敗時丟出 `ApiError`，直接顯示 `error.message`；欄位錯誤在 `error.fieldErrors`。不要只顯示「操作失敗」。
6. **金額與數量不在前端計算**：小計、稅額、換算後數量都由後端計算回傳，前端只用 `utils/format.js` 顯示。
7. **時間以台北時間顯示**：後端回傳 UTC 時間，用 `formatDateTime()`、`formatDate()` 轉換；`YYYY-MM-DD` 的日期欄位不做時區轉換。
8. **提示訊息用 `notify`**：`notify.success('已建立')`、`notify.error(error.message)`，不要直接呼叫 ElMessage。

## 新增一個畫面

1. 在 `src/api/` 新增該模組的 API 檔案。
2. 在 `src/views/<模組>/` 新增畫面；該模組專用的對話框放 `src/components/<模組>/`。
3. 在 `src/config/navigation.js` 加入選單項目（`screen`、`icon`、`permission`）。
4. 在 `src/router/index.js` 的 `AppLayout` 子路由登錄路由：
   - 需要權限的畫面設定 `meta: { navigation: findNavigationItem('MDM-01') }`，路由守衛會依選單項目的權限擋下沒有權限的人
   - 不需登入、也不套用版面的畫面放在最外層，設定 `meta: { public: true }`
   - `meta.title` 是瀏覽器分頁標題

## 環境變數

| 變數 | 說明 | 本機預設 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | 後端 API 位址，含 `/api/v1` | `http://localhost:8080/api/v1` |

`.env.local` 不進 Git。部署時改為 `https://api.<domain>/api/v1`（docs/13）。

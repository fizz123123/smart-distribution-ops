# API 與畫面契約

> 文件版本：v1.0  
> 建立日期：2026-10-05  
> 最後更新：2026-10-28  
> 文件定位：依功能模組、角色權限、領域規則、資料模型及狀態轉換，定義前端畫面與後端 API 的責任邊界、主要操作、輸入輸出及錯誤處理。

---

## 1. 契約範圍

本文件用來回答：

- 哪個角色會使用哪些畫面。
- 畫面需要查詢及顯示哪些資料。
- 使用者操作對應哪一支 API。
- 後端須驗證哪些權限、狀態及業務規則。
- 成功後回傳什麼結果，失敗時如何讓使用者理解原因。

本文件不規定 Vue 元件切分、CSS、Figma 視覺稿、Spring Boot package、Java 類別名稱、SQL 欄位型別或雲端部署方式。

各命令允許的來源狀態、目標狀態與業務效果，以 [11-state-transitions-and-operations.md](11-state-transitions-and-operations.md) 為依據；跨模組計算與資料限制則參照 [05-domain-rules.md](05-domain-rules.md)。

## 2. 共用 API 約定

### 2.1 基本格式

| 項目 | 約定 |
| --- | --- |
| API Base Path | `/api/v1` |
| 識別碼 | 資源 ID 為資料庫產生的數字；業務編號（例如 `SO-000001`）另以欄位提供（D-055） |
| 傳輸格式 | JSON；正式 PDF 下載端點回傳 `302 Redirect` 至短效 S3 Presigned URL |
| 驗證 | Web 使用 JWT Bearer Token；LINE webhook 使用 LINE 簽章驗證 |
| 日期 | `YYYY-MM-DD`，例如 `2026-10-05` |
| 時間 | ISO 8601，後端保存 UTC，前端以 Asia/Taipei 顯示 |
| 金額 | TWD；交易資料保存未稅金額，應收及收款使用含稅總額 |
| 數量 | request 同時傳交易數量與單位；後端依主檔換算並回傳基準數量 |
| 分頁 | `page` 從 0 開始，搭配 `size`、`sort` |
| 版本控制 | 可修改資源回傳 `version`；命令須帶入目前版本以進行樂觀鎖檢查。核准與退回例外，見第 4 節 |
| 重複操作防護 | 一般命令使用狀態轉換、資源版本及唯一限制；LINE webhook 使用 event ID；建立收款另帶唯一 `clientRequestId` |

### 2.2 權限與資料範圍

- 前端依權限隱藏或停用操作，但後端仍須在每次請求重新驗證。
- 取得角色不代表可以查看所有資料；分析資料依 [角色與權限規格](04-roles-and-permissions.md) 的查看範圍限制。SO 清單與詳情屬於作業資料，具 `SO_VIEW` 即可查看全部（D-051）。
- APP_ADMIN 不因管理帳號而取得客戶價格、PO 採購單價、交易核准或營運分析權限。
- 任何核准 API 都要檢查核准者不是目標建立者，也不是該輪送審者。
- LINE AI 使用綁定後的系統帳號執行權限判斷，不以加入官方帳號作為授權依據。

### 2.3 權限代碼命名

權限代碼採 `RESOURCE_ACTION`，例如：

```text
CUSTOMER_CREATE
CUSTOMER_APPROVE
CUSTOMER_STATUS_MANAGE
SO_CREATE
SO_SUBMIT
SO_APPROVE
PO_CREATE
PO_APPROVE
PO_CLOSE
GOODS_RECEIPT_POST
DO_READY
DO_POST
RECEIVABLE_CONFIRM
RECEIVABLE_INVOICE_CORRECT
PAYMENT_CONFIRM
PAYMENT_VOID
DASHBOARD_VIEW_ALL
AI_SUMMARY_GENERATE
LINE_AI_SO_DRAFT_CREATE
```

角色只是預設權限集合；API 以細部權限及資料範圍作為最終判斷。

### 2.4 成功回應

單筆查詢直接回傳資源；命令操作至少回傳識別碼、最新狀態、版本及操作時間：

```json
{
  "id": 128,
  "soNumber": "SO-000001",
  "status": "APPROVED",
  "version": 4,
  "updatedAt": "2026-10-05T09:15:30Z"
}
```

分頁查詢格式：

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### 2.5 錯誤回應

```json
{
  "code": "SO_ACTIVE_PRICE_REQUIRED",
  "message": "第 2 行 PU-001：尚無已覆核的「個」價格，請先完成價格建檔與覆核。",
  "fieldErrors": [],
  "traceId": "a7d1..."
}
```

建議 HTTP 狀態：

| 狀態 | 使用情境 |
| --- | --- |
| `400` | JSON、格式或基本欄位錯誤 |
| `401` | 未登入或 Token 無效 |
| `403` | 權限或資料範圍不足 |
| `404` | 資源不存在或使用者無權得知其存在 |
| `409` | 狀態不允許、版本衝突、重複編號、重複請求、數量併發衝突，以及其他業務規則不允許的操作 |

本專案不使用 `422`（D-055）。`fieldErrors` 只在 `400` 欄位驗證失敗時有值；業務規則錯誤的說明放在 `message`，需要指出明細時以「第 n 行」開頭。

前端不得只顯示「操作失敗」，應優先呈現 `message` 及可處理的欄位錯誤。

## 3. 畫面總覽

| 畫面編號 | 畫面 | 主要角色 | 主要用途 |
| --- | --- | --- | --- |
| AUTH-01 | 登入 | 全體內部使用者 | 取得 JWT 與目前使用者權限；顯示「忘記密碼請聯絡系統管理員」 |
| AUTH-02 | 設定密碼 | 收到連結的使用者 | 以信中連結首次設定或重設密碼，不需登入 |
| HOME-01 | 我的工作台 | 全體內部使用者 | 彙整目前使用者的待核准、待處理及近期操作；儀表板的「我的待辦」入口連至此畫面 |
| ADM-01 | 使用者與角色 | APP_ADMIN | 管理一般使用者、重寄設定密碼連結、寄送重設密碼連結；APP_ADMIN 為初始化建立的固定專用角色，不在畫面提供指派、移除或額外授權操作 |
| ADM-02 | LINE 綁定 | SALES、APP_ADMIN | 本人取得驗證碼；管理員查看、停用或解除綁定 |
| MDM-01 | 客戶 | SALES、SALES_MANAGER、FINANCE_MANAGER | 客戶建檔、核准、啟停與付款條件 |
| MDM-02 | 公司料號 | SALES、SALES_MANAGER、PURCHASING_MANAGER | 業務維護商品規格、基準單位與偏好供應商；採購主管維護安全庫存 |
| MDM-03 | 客戶料號與價格 | SALES、SALES_MANAGER | 客戶料號對應、價格版本與覆核 |
| MDM-04 | 供應商 | PURCHASING、PURCHASING_MANAGER | 供應商建檔、核准與啟停 |
| SAL-01 | SO 清單 | SALES、SALES_MANAGER、MANAGER | 搜尋、篩選與建立 SO |
| SAL-02 | SO 詳情 | SALES、SALES_MANAGER、PURCHASING | 編輯、送審、核准、查看供應狀態、轉 PO、通知出貨 |
| SAL-03 | 餘量取消 | SALES、SALES_MANAGER | 建立、送審及核准未出貨餘量取消 |
| PUR-01 | PO 清單 | PURCHASING、PURCHASING_MANAGER | 查看採購規劃、待核准、在途與結案 |
| PUR-02 | PO 詳情 | PURCHASING、PURCHASING_MANAGER | 編輯、送審、核准、PDF、取消與短收結案 |
| WH-01 | 收貨入庫 | WAREHOUSE、WAREHOUSE_MANAGER | 依 PO 驗收、檢查過帳內容與完成入庫 |
| WH-02 | 庫存查詢與異動 | WAREHOUSE、WAREHOUSE_MANAGER | 查看餘額、預留、異動與過帳調整 |
| WH-03 | DO 清單與詳情 | SALES、WAREHOUSE | 建立 DO、揀貨、產生 PDF、交車過帳及作廢 |
| FIN-01 | 應收 | FINANCE、FINANCE_MANAGER | 確認應收、追蹤到期與更正外部發票資料 |
| FIN-02 | 收款與核銷 | FINANCE、FINANCE_MANAGER | 登錄收款、分配應收及作廢錯誤收款 |
| ANA-01 | 營運儀表板 | MANAGER 及授權角色 | 查看四組核心指標與下鑽明細 |
| AI-01 | AI 營運摘要 | MANAGER | 依結構化指標產生附來源摘要 |
| APR-01 | 待核准清單 | 各主管、MANAGER | 集中處理自己有權核准且符合分離規則的申請 |

Figma 可依此畫面清單設計版面，但不得在視覺稿中新增繞過狀態規則的操作。

HOME-01 是個人作業入口，不另建立「待辦中心」。畫面可組合我的待核准及各模組既有狀態查詢；儀表板的三張待處理清單則呈現跨部門營運風險，兩者不可混為同一資料集。

## 4. 登入、帳號與共用核准

### 4.1 API 對照

| 畫面／操作 | Method | Path | 權限 | 說明 |
| --- | --- | --- | --- | --- |
| 登入 | `POST` | `/auth/login` | 公開 | 驗證登入 Email 與密碼並回傳 JWT；請求即使附帶過期或無效的既有 token，也不影響重新登入；待啟用帳號回 `401 USER_PENDING_ACTIVATION`，停用帳號回 `401 USER_DISABLED` |
| 以連結設定密碼 | `POST` | `/auth/password` | 公開 | 送 `token`、`newPassword`，成功回 `204`；首次設定與重設共用 |
| 目前使用者 | `GET` | `/users/me` | 已登入 | 回傳使用者、角色、細部權限及資料範圍 |
| 修改自己的密碼 | `PUT` | `/users/me/password` | 已登入 | 送 `currentPassword`、`newPassword`，成功回 `204` |
| 使用者清單／詳情 | `GET` | `/users`、`/users/{id}` | `USER_MANAGE` | 查詢其他一般使用者及目前版本 |
| 建立／修改使用者 | `POST/PATCH` | `/users`、`/users/{id}` | `USER_MANAGE` | 建立時只送 Email 與姓名，不送密碼；建立後為 `PENDING` 並寄出設定密碼連結；Email 為登入識別且正規化後須唯一；不得修改自己的授權 |
| 重寄設定密碼連結 | `POST` | `/users/{id}/invitation` | `USER_MANAGE` | 只限 `PENDING` 帳號；舊連結作廢 |
| 寄送重設密碼連結 | `POST` | `/users/{id}/password-reset` | `USER_MANAGE` | 只限 `ACTIVE` 帳號，且不得對自己操作；舊密碼在設定新密碼前仍可使用 |
| 可指派角色與權限 | `GET` | `/users/authorization-options` | `USER_MANAGE` | 回傳角色與個別權限勾選清單；排除 `APP_ADMIN` 及三項帳號管理權限；每個角色附 `includedRoleCodes` |
| 指派角色 | `PUT` | `/users/{id}/roles` | `USER_ROLE_MANAGE` | 完整取代一般使用者角色集合且至少保留一個角色；異動原因必填；不得操作自己，也不得指派或異動 `APP_ADMIN` |
| 個別額外授權 | `PUT` | `/users/{id}/permission-grants` | `USER_PERMISSION_MANAGE` | 完整取代一般使用者個別業務權限；異動原因必填；不提供 `DENY`，不得授予三項帳號管理權限，不得以 `APP_ADMIN` 為目標；同一權限只能出現一次 |
| 我的待核准 | `GET` | `/approval-requests/my-pending` | 依核准權限 | 後端排除建立者及本輪送審者本人 |
| 核准詳情 | `GET` | `/approval-requests/{id}` | 依核准權限 | 回傳目標摘要、建立者、送審者、警示與歷次意見 |
| 核准 | `POST` | `/approval-requests/{id}/approve` | 依目標核准權限 | 執行目標資料核准及後續效果；`comment` 選填 |
| 退回 | `POST` | `/approval-requests/{id}/return` | 依目標核准權限 | `comment` 必填 |

使用者詳情及授權異動回應包含 `authorizationUpdatedAt` 與最新 `version`。角色或個別授權異動會更新前者並自然遞增版本號；前端送出異動時須帶入目前版本，版本落後時回傳 `409 VERSION_CONFLICT`。

核准命令範例：

```json
{
  "comment": "資料確認無誤"
}
```

目標在待核准期間不得修改，核准與退回因此不帶目標版本。申請已被處理時回傳 `409 APPROVAL_ALREADY_PROCESSED`；兩人同時處理同一申請時，後完成者回傳 `409 VERSION_CONFLICT`。

### 4.2 設定密碼與信件

- 信中連結為 `{APP_FRONTEND_BASE_URL}/set-password?token=…`，首次設定與重設共用 AUTH-02。頁面只需要「新密碼」「確認新密碼」兩個欄位；兩次輸入是否相同由前端檢查，後端只收 `newPassword`。
- 連結有效 24 小時、只能使用一次。連結不存在、已使用、已過期或帳號狀態不符，一律回 `400 PASSWORD_TOKEN_INVALID`，畫面提示「連結已失效，請聯絡系統管理員重新寄送」。
- 信件在帳號資料寫入成功後才於背景寄出；寄送失敗不影響 API 結果，APP_ADMIN 可在 ADM-01 重寄。
- 登入頁不提供自助忘記密碼。APP_ADMIN 在 ADM-01 對 `ACTIVE` 帳號按「寄送重設密碼信」；對 `PENDING` 帳號按「重寄設定密碼信」。

### 4.3 角色與個別授權的指派畫面

角色選項範例：

```json
{
  "roleCode": "SALES_MANAGER",
  "name": "業務主管",
  "includedRoleCodes": ["SALES"]
}
```

- 勾選某個角色時，`includedRoleCodes` 列出的角色顯示為已勾選並鎖住，旁邊註明「已包含於{角色名稱}」。被鎖住的角色不送進 `roleCodes`，資料庫只保存主管角色（D-058）。
- 取消勾選主管時，被包含的角色恢復為可勾選、未勾選的狀態；降職為職員時，改勾職員角色。
- 個別授權沒有單獨的新增或刪除 API。建議畫面先在清單上新增、修改或刪除，最後填寫異動原因並按「儲存」，以 `PUT /users/{id}/permission-grants` 整組送出；未修改的授權須原樣帶回原因與期間。
- 新增個別授權的下拉選單應排除清單中已有的權限；同一權限出現兩次時回 `400 PERMISSION_GRANT_DUPLICATED`。要調整期間，修改原本那一筆即可。
- 代理提早結束時，可把該筆的結束時間改為現在，保留紀錄並使其失效；給錯權限時直接刪除。刪除的授權可從稽核紀錄追查（D-060）。

### 4.4 帳號相關錯誤碼

| 錯誤碼 | HTTP | 情境 |
| --- | --- | --- |
| `AUTH_INVALID_CREDENTIALS` | 401 | Email 不存在或密碼錯誤 |
| `USER_PENDING_ACTIVATION` | 401 | 待啟用帳號嘗試登入 |
| `USER_DISABLED` | 401 | 停用帳號登入或使用既有 JWT |
| `PASSWORD_TOKEN_INVALID` | 400 | 設定密碼連結無效 |
| `CURRENT_PASSWORD_INCORRECT` | 400 | 修改自己的密碼時目前密碼錯誤 |
| `PERMISSION_GRANT_DUPLICATED` | 400 | 個別授權清單中同一權限出現兩次 |
| `USER_STATUS_CONFLICT` | 409 | 帳號狀態不允許此操作，例如重寄設定密碼連結給已啟用的帳號 |
| `USER_SELF_MANAGEMENT_FORBIDDEN` | 403 | 對自己的帳號進行停用、授權異動或寄送重設連結 |

## 5. 主檔管理契約

### 5.1 客戶

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 查詢／詳情 | `GET` | `/customers`、`/customers/{id}` | `CUSTOMER_VIEW` |
| 建立 | `POST` | `/customers` | `CUSTOMER_CREATE` |
| 修改 | `PATCH` | `/customers/{id}` | `CUSTOMER_UPDATE` |
| 送審 | `POST` | `/customers/{id}/submit` | `CUSTOMER_SUBMIT` |
| 停用／重新啟用 | `POST` | `/customers/{id}/deactivate`、`/customers/{id}/activate` | `CUSTOMER_STATUS_MANAGE` |
| 修改付款條件 | `PUT` | `/customers/{id}/payment-term` | `CUSTOMER_PAYMENT_TERM_MANAGE` |

付款條件修改：

```json
{
  "paymentTerm": "NET_30",
  "reason": "依核定交易條件調整",
  "version": 5
}
```

停用或重新啟用必須帶入 `reason` 與 `version`。初次核准由共用核准 API 處理，啟停不另建核准申請。

客戶與供應商編號由系統產生，建立 request 不帶編號；公司料號由使用者輸入。已啟用主檔只能修改非識別資料，嘗試修改識別資料時回傳 `409 MASTER_DATA_IDENTITY_LOCKED`。

主檔的 `PATCH` 須送出全部可編輯欄位與 `version`；可為空的欄位以 `null` 表示清空，不採「未傳即不修改」的部分更新。待核准期間的主檔不得修改，回傳 `409 MASTER_DATA_STATUS_CONFLICT`。

### 5.2 公司料號、客戶料號、價格與供應商

| 資源 | 查詢 | 建立／修改 | 送審 | 狀態操作 |
| --- | --- | --- | --- | --- |
| 公司料號 | `/company-items` | `POST /company-items`、`PATCH /company-items/{id}` | `POST /company-items/{id}/submit` | `POST /company-items/{id}/activate`、`POST /company-items/{id}/deactivate` |
| 客戶料號對應 | `/customer-item-mappings` | `POST /customer-item-mappings`、`PATCH /customer-item-mappings/{id}` | `POST /customer-item-mappings/{id}/submit` | `POST /customer-item-mappings/{id}/activate`、`POST /customer-item-mappings/{id}/deactivate` |
| 客戶價格 | `/customer-prices` | `POST /customer-prices` | 建立後送覆核 | `POST /customer-prices/{id}/revoke` |
| 供應商 | `/suppliers` | `POST /suppliers`、`PATCH /suppliers/{id}` | `POST /suppliers/{id}/submit` | `POST /suppliers/{id}/activate`、`POST /suppliers/{id}/deactivate` |
| 單位換算 | 併入公司料號詳情 | `PUT /company-items/{id}/unit-conversions`：整份取代基準單位以外的換算 | 不另送審 | 沿用 `COMPANY_ITEM_UPDATE`；待核准與停用期間不得修改 |
| 單位清單 | `GET /units` | 不提供維護 API | — | 任何已登入使用者可查詢 |
| 安全庫存政策 | `/company-items/{id}/inventory-policy` | `PUT /company-items/{id}/inventory-policy` | 不另送審 | 修改權限為 `INVENTORY_POLICY_MANAGE`，僅授予採購主管或具相應細部權限者 |

價格建立 request：

```json
{
  "customerItemMappingId": 15,
  "pricingUnit": "PCS",
  "unitPriceExcludingTax": 125.00
}
```

`POST /customer-prices` 成功後直接建立 `PENDING_REVIEW` 價格及對應核准申請；價格沒有另外的草稿與送審 API。同一客戶料號與計價單位已有待覆核價格時，回傳 `409 CUSTOMER_PRICE_PENDING_EXISTS`。價格覆核的「退回」即為拒絕，價格轉為 `REJECTED` 後不可重新送審，須另建新版本。計價單位須為該料號的庫存基準單位或已設定換算的單位，否則回傳 `409 CUSTOMER_PRICE_UNIT_INVALID`。撤銷 request 須包含 `reason` 與 `version`。

公司料號詳情需同時呈現基準單位、安全庫存、偏好供應商、允許交易單位及換算倍率。安全庫存以基準單位輸入，更新 request 必須包含 `safetyStockBaseQty`、`reason` 與 `version`，並保存修改前後值、操作者與時間。重新啟用主檔時，後端重新檢查唯一性及必要關聯。

SO 核准時，若引用價格已轉為 `REPLACED`，後端仍依送審快照完成核准；若已轉為 `REVOKED`，回傳 `SO_PRICE_REVOKED`，不得建立預留或自動改變 SO 狀態。主管須另行執行退回操作並填寫意見。

### 5.3 主檔細部權限與預設角色

部門主管包含同部門職員的全部權限（D-058）：下表列為 SALES、PURCHASING 的權限，SALES_MANAGER、PURCHASING_MANAGER 也具有。

| 權限 | 預設角色 |
| --- | --- |
| `CUSTOMER_VIEW` | SALES、SALES_MANAGER、FINANCE_MANAGER、MANAGER |
| `CUSTOMER_CREATE`、`CUSTOMER_UPDATE`、`CUSTOMER_SUBMIT` | SALES |
| `CUSTOMER_APPROVE` | SALES_MANAGER、MANAGER（替代核准） |
| `CUSTOMER_STATUS_MANAGE` | SALES_MANAGER |
| `CUSTOMER_PAYMENT_TERM_MANAGE` | FINANCE_MANAGER |
| `SUPPLIER_VIEW` | PURCHASING、PURCHASING_MANAGER、MANAGER |
| `SUPPLIER_CREATE`、`SUPPLIER_UPDATE`、`SUPPLIER_SUBMIT` | PURCHASING |
| `SUPPLIER_APPROVE` | PURCHASING_MANAGER、MANAGER（替代核准） |
| `SUPPLIER_STATUS_MANAGE` | PURCHASING_MANAGER |
| `COMPANY_ITEM_VIEW` | SALES、SALES_MANAGER、PURCHASING、PURCHASING_MANAGER、MANAGER |
| `COMPANY_ITEM_CREATE`、`COMPANY_ITEM_UPDATE`、`COMPANY_ITEM_SUBMIT` | SALES |
| `COMPANY_ITEM_APPROVE` | SALES_MANAGER、MANAGER（替代核准） |
| `COMPANY_ITEM_STATUS_MANAGE` | SALES_MANAGER |
| `INVENTORY_POLICY_MANAGE` | PURCHASING_MANAGER |
| `CUSTOMER_ITEM_MAPPING_VIEW` | SALES、SALES_MANAGER、MANAGER |
| `CUSTOMER_ITEM_MAPPING_CREATE`、`CUSTOMER_ITEM_MAPPING_UPDATE`、`CUSTOMER_ITEM_MAPPING_SUBMIT` | SALES |
| `CUSTOMER_ITEM_MAPPING_APPROVE` | SALES_MANAGER、MANAGER（替代核准） |
| `CUSTOMER_ITEM_MAPPING_STATUS_MANAGE` | SALES_MANAGER |
| `CUSTOMER_PRICE_VIEW` | SALES、SALES_MANAGER、MANAGER |
| `CUSTOMER_PRICE_CREATE` | SALES |
| `CUSTOMER_PRICE_APPROVE` | SALES_MANAGER、MANAGER（替代核准） |
| `CUSTOMER_PRICE_REVOKE` | SALES_MANAGER |

單位換算的新增與修改沿用 `COMPANY_ITEM_UPDATE`。核准服務依核准申請的目標類型對應上表的 `XXX_APPROVE` 權限；SO 與庫存權限見 6.3、7.3 節，其他模組的權限於實作時補入本文件。安全庫存查詢接受 `COMPANY_ITEM_VIEW` 或 `INVENTORY_VIEW` 任一權限，倉管也能查看。

## 6. SO 與餘量取消契約

### 6.1 畫面資料

SO詳情 至少顯示：

- 客戶、負責業務、客戶訂單編號與建立管道。
- SO 狀態、核准歷程、要求交期及交貨資料。
- 每筆品項的客戶料號、公司料號、輸入數量與單位、基準數量及價格。
- 已預留、已規劃未下單、採購在途、尚待規劃、已出貨、已取消與未出貨數量。
- 關聯 PO、DO、餘量取消及來源紀錄連結。
- 依目前狀態與權限可執行的操作。

### 6.2 API 對照

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 清單 | `GET` | `/sales-orders?keyword=&status=&customerId=` | `SO_VIEW` |
| 詳情 | `GET` | `/sales-orders/{id}` | `SO_VIEW` |
| 建立草稿 | `POST` | `/sales-orders` | `SO_CREATE` |
| 修改草稿 | `PATCH` | `/sales-orders/{id}` | `SO_UPDATE` |
| 送審 | `POST` | `/sales-orders/{id}/submit` | `SO_SUBMIT` |
| 放棄草稿 | `POST` | `/sales-orders/{id}/cancel` | `SO_UPDATE` |
| 核准／退回 | `POST` | `/approval-requests/{id}/approve`、`/approval-requests/{id}/return` | `SO_APPROVE` |
| 取消申請清單 | `GET` | `/sales-orders/{id}/cancellations` | `SO_VIEW` |
| 建立取消申請 | `POST` | `/sales-orders/{id}/cancellations` | `SO_CANCELLATION_CREATE` |
| 取消申請詳情 | `GET` | `/sales-order-cancellations/{id}` | `SO_VIEW` |
| 修改取消申請 | `PATCH` | `/sales-order-cancellations/{id}` | `SO_CANCELLATION_CREATE` |
| 取消申請送審 | `POST` | `/sales-order-cancellations/{id}/submit` | `SO_CANCELLATION_SUBMIT` |
| 撤回取消申請 | `POST` | `/sales-order-cancellations/{id}/withdraw` | `SO_CANCELLATION_CREATE` |
| 取消申請核准／退回 | `POST` | `/approval-requests/{id}/approve`、`/approval-requests/{id}/return` | `SO_CANCELLATION_APPROVE` |
| 建立關聯 PO 草稿（P4） | `POST` | `/sales-orders/{id}/purchase-order-drafts` | `PO_CREATE` |
| 通知本次出貨（P5） | `POST` | `/sales-orders/{id}/delivery-orders` | `DO_CREATE` |

清單的 `keyword` 比對 SO 編號與客戶訂單編號。

建立或修改 SO 的主要輸入：

```json
{
  "customerId": 3,
  "customerOrderNumber": "CUS-PO-1008",
  "responsibleSalesUserId": 12,
  "requestedDeliveryDate": "2026-10-20",
  "deliveryMethod": "DELIVERY",
  "deliveryAddress": "台中市西屯區工業區一路 1 號",
  "contactName": "王先生",
  "contactPhone": "0912000000",
  "remark": null,
  "lines": [
    {
      "customerItemMappingId": 15,
      "quantity": 10,
      "unit": "PCS"
    }
  ],
  "version": 0
}
```

- `version` 只在修改時帶入；送審與放棄的 request 只有 `version`。
- `responsibleSalesUserId` 未填時為建立者；建立者沒有 `SALES` 角色時必須指定。
- `deliveryMethod` 為 `DELIVERY / CUSTOMER_PICKUP`；外送時送審須填寫地址、收貨聯絡人與電話。草稿可暫缺交期與交貨資料。
- 修改採整份取代：明細全部重新送出。

送審時由後端重新取得 `ACTIVE` 價格、單位換算與付款條件並保存交易快照；前端不得直接指定核准後使用的未稅單價。

SO 詳情 response 的重點欄位：

| 欄位 | 說明 |
| --- | --- |
| `soNumber`、`status`、`version` | 編號、狀態與目前版本 |
| `paymentTerm`、`untaxedAmount`、`taxAmount`、`totalAmount` | 送審後為快照；草稿與退回時依目前主檔試算，缺價格時金額為 `null` |
| `warnings` | 草稿與退回時列出送審前須處理的問題，例如缺少價格或換算 |
| `lines[].supply` | 核准後才有值：`reservedQty`、`plannedNotOrderedQty`、`inTransitQty`、`shippedQty`、`cancelledQty`、`unplannedQty`，皆為基準單位 |

建立或修改取消申請：

```json
{
  "reason": "客戶縮減需求",
  "lines": [
    {
      "salesOrderLineId": 301,
      "quantity": 2
    }
  ],
  "version": 0
}
```

取消數量使用該 SO 明細的訂購單位，後端依送審時的換算倍率換算為基準數量。`version` 只在修改時帶入；送審與撤回的 request 只有 `version`。

SO 與取消申請的錯誤碼：

| 錯誤碼 | HTTP | 情境 |
| --- | --- | --- |
| `SALES_ORDER_NOT_FOUND`、`SALES_ORDER_CANCELLATION_NOT_FOUND` | 404 | 資源不存在 |
| `SO_STATUS_CONFLICT` | 409 | SO 狀態不允許該操作 |
| `SO_CUSTOMER_INACTIVE` | 409 | 建立或修改時客戶未啟用 |
| `SO_RESPONSIBLE_SALES_INVALID` | 400 | 負責業務不是啟用中、且有效權限包含 `SO_CREATE` 的使用者 |
| `SO_MAPPING_CUSTOMER_MISMATCH` | 400 | 客戶料號對應不屬於該客戶 |
| `SO_LINE_INVALID` | 400 | 明細重複、小數位不符或不屬於該 SO |
| `SO_DELIVERY_INFO_INCOMPLETE` | 400 | 送審時交期或交貨資料缺漏 |
| `SO_LINES_REQUIRED` | 400 | 送審時沒有明細 |
| `SO_MASTER_DATA_INACTIVE` | 409 | 送審或核准時客戶、對應或公司料號未啟用 |
| `SO_UNIT_INVALID` | 409 | 單位不是基準單位也沒有設定換算 |
| `SO_ACTIVE_PRICE_REQUIRED` | 409 | 該單位沒有 `ACTIVE` 價格 |
| `SO_PRICE_REVOKED` | 409 | 核准時引用價格已撤銷（D-033） |
| `SO_CANCELLATION_STATUS_CONFLICT` | 409 | 取消申請狀態不允許該操作 |
| `SO_CANCELLATION_PENDING_EXISTS` | 409 | 該 SO 已有未完成的取消申請 |
| `SO_CANCELLATION_QUANTITY_EXCEEDED` | 409 | 取消量超過可取消量，訊息含最多可取消數量 |

建立關聯 PO 草稿：

```json
{
  "supplierId": 1,
  "lines": [
    {
      "salesOrderLineId": 1,
      "quantity": 3,
      "unit": "PCS"
    }
  ]
}
```

後端必須鎖定並重新計算尚待規劃供應量，不能只相信畫面數字。

通知出貨 request：

```json
{
  "plannedShipmentDate": "2026-10-18",
  "lines": [
    {
      "salesOrderLineId": 1,
      "plannedQuantity": 10,
      "unit": "PCS"
    }
  ]
}
```

SO 詳情 response 直接包含各明細的已預留、已規劃未下單、採購在途及尚待規劃等供應狀態，不另提供供應狀態專用 API。後端將預計出貨量換算成基準單位，並檢查同一 SO 明細所有有效 DO 草稿合計不超過預留。

### 6.3 SO 細部權限與預設角色

| 權限 | 預設角色 |
| --- | --- |
| `SO_VIEW` | SALES、SALES_MANAGER、PURCHASING、PURCHASING_MANAGER、MANAGER |
| `SO_CREATE`、`SO_UPDATE`、`SO_SUBMIT` | SALES、SALES_MANAGER |
| `SO_APPROVE` | SALES_MANAGER、MANAGER（替代核准） |
| `SO_CANCELLATION_CREATE`、`SO_CANCELLATION_SUBMIT` | SALES、SALES_MANAGER |
| `SO_CANCELLATION_APPROVE` | SALES_MANAGER、MANAGER（替代核准） |

## 7. PO、收貨與庫存契約

### 7.1 PO

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 清單／詳情 | `GET` | `/purchase-orders`、`/purchase-orders/{id}` | `PO_VIEW` |
| 待收貨 PO | `GET` | `/purchase-orders?receivingStatus=OPEN` | `GOODS_RECEIPT_VIEW` |
| 建立一般補庫 PO | `POST` | `/purchase-orders` | `PO_CREATE` |
| 修改草稿 | `PATCH` | `/purchase-orders/{id}` | `PO_UPDATE` |
| 送審 | `POST` | `/purchase-orders/{id}/submit` | `PO_SUBMIT` |
| 下載正式 PDF | `GET` | `/purchase-orders/{id}/document` | `PO_DOCUMENT_VIEW` |
| 放棄草稿 | `POST` | `/purchase-orders/{id}/cancel-draft` | `PO_UPDATE` |
| 取消已核准 PO | `POST` | `/purchase-orders/{id}/cancel` | `PO_CLOSE` |
| 短收結案 | `POST` | `/purchase-orders/{id}/close-short` | `PO_CLOSE` |

取消或短收 request：

```json
{
  "reason": "供應商確認無法交付剩餘數量",
  "version": 6
}
```

核准 PO 使用共用核准 API。核准成功後才產生正式 PDF 並列入採購在途。取消已核准 PO 僅允許沒有任何已過帳收貨的 PO；部分收貨後只能短收結案。下載正式 PDF 時，後端先驗證 `PO_DOCUMENT_VIEW` 與資料範圍、保存下載連結核發紀錄，再回傳 `302 Redirect` 至 3 分鐘有效的 S3 Presigned URL。

### 7.2 收貨入庫

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 收貨紀錄 | `GET` | `/goods-receipts`、`/goods-receipts/{id}` | `GOODS_RECEIPT_VIEW` |
| 過帳收貨 | `POST` | `/purchase-orders/{id}/goods-receipts` | `GOODS_RECEIPT_POST` |

過帳收貨 request：

```json
{
  "receivedDate": "2026-10-10",
  "lines": [
    {
      "purchaseOrderLineId": 1,
      "receivedQuantity": 10,
      "unit": "PCS",
      "acceptedQuantity": 9,
      "damagedQuantity": 1,
      "nonconformingQuantity": 0,
      "exceptionReason": "外箱破損"
    }
  ]
}
```

送出過帳前，畫面須再次顯示 PO、品項、實收、合格、損壞及規格不符數量，並要求使用者確認。過帳成功後，收貨紀錄不得修改或刪除；本次交付版本不提供收貨沖銷，庫存調整也不得用來偽裝更正 PO 收貨進度。

倉管以 `GET /purchase-orders?receivingStatus=OPEN` 取得待收貨清單；此查詢只回傳驗收所需的 PO 與未結數量，不因使用 `purchase-orders` 路徑而開放 PO 採購單價或完整 PO 管理資料。

### 7.3 庫存與調整

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 庫存餘額 | `GET` | `/inventory/balances?keyword=` | `INVENTORY_VIEW` |
| 商品庫存歷程 | `GET` | `/inventory/items/{companyItemId}/transactions` | `INVENTORY_TRANSACTION_VIEW` |
| 預留明細 | `GET` | `/inventory/items/{companyItemId}/reservations` | `INVENTORY_RESERVATION_VIEW` |
| 一致性檢查 | `GET` | `/inventory/consistency-check` | `INVENTORY_VIEW` |
| 調整詳情 | `GET` | `/inventory/adjustments/{id}` | `INVENTORY_TRANSACTION_VIEW` 或 `INVENTORY_ADJUSTMENT_POST` |
| 過帳調整 | `POST` | `/inventory/adjustments` | `INVENTORY_ADJUSTMENT_POST` |
| 反向更正 | `POST` | `/inventory/adjustments/{id}/reverse` | `INVENTORY_ADJUSTMENT_POST` |

餘額清單回傳可出貨實體數量 `onHandQty`、有效預留 `reservedQty`、可用庫存 `availableQty` 與安全庫存；預留明細包含 SO 編號與每筆預留事件。一致性檢查回傳不一致項目清單，空清單代表全部一致。

調整 request：

```json
{
  "reasonType": "COUNT_DIFFERENCE",
  "description": "月末盤點差異",
  "lines": [
    {
      "companyItemId": 1,
      "quantityChangeBase": -2
    }
  ]
}
```

建立期初庫存 request：

```json
{
  "reasonType": "OPENING_BALANCE",
  "description": "系統導入期初庫存",
  "lines": [
    {
      "companyItemId": 1,
      "quantityChangeBase": 100
    }
  ]
}
```

反向更正 request：

```json
{
  "reason": "期初登錄錯誤",
  "version": 0
}
```

反向更正不需輸入數量，後端讀取原調整的明細並產生數量相反的新調整；原調整轉為 `REVERSED`。

調減不得超過可用庫存，也不得侵蝕有效預留。`OPENING_BALANCE` 數量必須為正，每個公司料號只允許一次，且只能在尚無庫存異動及預留時建立。已過帳收貨輸入錯誤不得以庫存調整代替；正式收貨沖銷列為後續擴充。

庫存錯誤碼：

| 錯誤碼 | HTTP | 情境 |
| --- | --- | --- |
| `INVENTORY_ADJUSTMENT_NOT_FOUND` | 404 | 調整不存在 |
| `INVENTORY_ADJUSTMENT_LINES_INVALID` | 400 | 同一料號重複、數量為 0 或小數位不符 |
| `INVENTORY_OPENING_BALANCE_NOT_ALLOWED` | 409 | 期初為負數，或料號已有異動或預留 |
| `INVENTORY_INSUFFICIENT` | 409 | 調整後實體數量小於 0 |
| `INVENTORY_RESERVATION_WOULD_BE_BREACHED` | 409 | 調減會低於有效預留，訊息列出受影響的 SO |
| `INVENTORY_ADJUSTMENT_NOT_REVERSIBLE` | 409 | 已更正過，或本身是更正紀錄 |

庫存細部權限與預設角色：

| 權限 | 預設角色 |
| --- | --- |
| `INVENTORY_VIEW` | SALES、SALES_MANAGER、PURCHASING、PURCHASING_MANAGER、WAREHOUSE、WAREHOUSE_MANAGER、MANAGER |
| `INVENTORY_TRANSACTION_VIEW` | WAREHOUSE、WAREHOUSE_MANAGER、MANAGER |
| `INVENTORY_RESERVATION_VIEW` | SALES_MANAGER、PURCHASING、PURCHASING_MANAGER、WAREHOUSE、WAREHOUSE_MANAGER、MANAGER |
| `INVENTORY_ADJUSTMENT_POST` | WAREHOUSE_MANAGER |

## 8. DO 契約

### 8.1 畫面資料

WH-03 只顯示倉管履約所需資料，不提供非必要的客戶價格、PO 採購單價或完整 SO 編輯功能。至少包含：

- DO 編號、來源 SO 編號、客戶、交貨資料與預計出貨日。
- 預計出貨量、實際出貨量與可過帳數量。
- 公司料號、品名、儲位備註及交易／基準單位。
- 正式 PDF 狀態、實際出貨日及過帳紀錄。

### 8.2 API 對照

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 清單／詳情 | `GET` | `/delivery-orders`、`/delivery-orders/{id}` | `DO_VIEW` |
| 修改草稿預計出貨量 | `PATCH` | `/delivery-orders/{id}` | `DO_PICK` |
| 完成揀貨並產生 PDF | `POST` | `/delivery-orders/{id}/ready` | `DO_READY` |
| 下載正式 PDF | `GET` | `/delivery-orders/{id}/document` | `DO_DOCUMENT_VIEW` |
| 出貨過帳 | `POST` | `/delivery-orders/{id}/post` | `DO_POST` |
| 過帳前作廢 | `POST` | `/delivery-orders/{id}/void` | `DO_VOID` |

完成揀貨：

```json
{
  "lines": [
    {
      "deliveryOrderLineId": 1,
      "actualQuantity": 10,
      "unit": "PCS"
    }
  ],
  "version": 2
}
```

草稿階段保存的是預計出貨量；完成揀貨時才保存實際出貨量。每筆實際出貨量必須大於 0 且不得超過預計出貨量，少出的差額繼續保留在來源 SO 的有效預留中。正式 DO PDF 依實際出貨量產生。

出貨過帳：

```json
{
  "actualShipmentDate": "2026-10-12",
  "version": 3
}
```

後端須再次鎖定庫存、預留及有效 DO 明細，確認數量後才能扣庫存、使用預留並建立唯一應收草稿。

下載正式 DO PDF 時，後端先驗證 `DO_DOCUMENT_VIEW` 與資料範圍、保存下載連結核發紀錄，再回傳 `302 Redirect` 至 3 分鐘有效的 S3 Presigned URL。

## 9. 應收、收款與核銷契約

### 9.1 應收

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 清單／詳情 | `GET` | `/receivables`、`/receivables/{id}` | `RECEIVABLE_VIEW` |
| 確認正式應收 | `POST` | `/receivables/{id}/confirm` | `RECEIVABLE_CONFIRM` |
| 更正外部發票資料 | `PATCH` | `/receivables/{id}/external-invoice` | `RECEIVABLE_INVOICE_CORRECT` |

確認 request：

```json
{
  "externalInvoiceNumber": "AB12345678",
  "externalInvoiceDate": "2026-10-12",
  "version": 1
}
```

更正 request：

```json
{
  "externalInvoiceNumber": "AB12345679",
  "externalInvoiceDate": "2026-10-12",
  "reason": "原發票號碼輸入錯誤",
  "version": 2
}
```

更正 API 不接受金額、付款條件、到期日或來源 DO 欄位。

### 9.2 收款與核銷

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 客戶未收應收 | `GET` | `/customers/{id}/open-receivables` | `RECEIVABLE_VIEW` |
| 收款清單／詳情 | `GET` | `/payments`、`/payments/{id}` | `PAYMENT_VIEW` |
| 確認收款與核銷 | `POST` | `/payments` | `PAYMENT_CONFIRM` |
| 作廢收款 | `POST` | `/payments/{id}/void` | `PAYMENT_VOID` |

確認收款 request：

```json
{
  "clientRequestId": "8f12d18a-5f8d-4b13-a8bd-a688359fd014",
  "customerId": 1,
  "receivedDate": "2026-10-20",
  "amount": 31500,
  "method": "BANK_TRANSFER",
  "referenceNumber": "BANK-1020-001",
  "allocations": [
    {
      "receivableId": 1,
      "allocatedAmount": 10500
    },
    {
      "receivableId": 1,
      "allocatedAmount": 21000
    }
  ]
}
```

所有核銷應收必須屬於同一客戶，分配總額必須等於收款金額，且不得超過各應收未收餘額。畫面可依到期日由早到晚預先分配，但最後結果由財務確認。`clientRequestId` 由前端為每次收款操作產生且全系統唯一；同一識別碼重送時不得建立第二筆收款。

## 10. 儀表板與 AI 契約

### 10.1 儀表板

| 資料 | Method | Path | 說明 |
| --- | --- | --- | --- |
| 營運總覽 | `GET` | `/analytics/overview` | 淨接單、營收、未出貨、缺貨、交易降溫與逾期應收摘要 |
| 接單與營收 | `GET` | `/analytics/sales` | 趨勢、前後期比較及客戶／商品營收貢獻 |
| 缺貨與採購 | `GET` | `/analytics/shortages` | 可用、預留、已規劃未下單、採購在途、近 30 日平均日出貨量、可支撐天數、平均採購週期、建議安全庫存與交期風險 |
| 交易降溫 | `GET` | `/analytics/customer-cooling` | 客戶交易週期、近期變化及來源 |
| 應收與逾期 | `GET` | `/analytics/receivables` | 未收、即將到期、逾期與明細 |

共用 query：

```text
from=2026-10-01
to=2026-10-31
customerId=
companyItemId=
responsibleSalesUserId=
```

即時狀態型指標不受一般期間篩選時，response 必須標示 `asOf` 與採用的分析基準日。下鑽項目須提供來源類型及資源 ID，前端依使用者權限導向明細。

`/analytics/overview` 固定回傳六張核心指標卡、一組近六個月淨接單與營收趨勢，以及缺貨履約、交易降溫、應收三組待處理清單。`/analytics/sales` 分別提供營收增加及減少最多的前三名客戶與商品；不得回傳跨商品計算的全公司平均出貨單價。前期營收為 0 時，變化率回傳 `null` 並附上 `NO_PRIOR_BASELINE` 說明。

`/analytics/overview` 不取代 HOME-01，也不另外建立待辦中心。前端若在儀表板顯示「我的待辦」入口或數量，應連至 HOME-01；實際可執行項目仍依目前使用者權限由工作台及各模組查詢取得。

缺貨分析的計算基準與資料不足處理以 [06-analytics-and-dashboard.md](06-analytics-and-dashboard.md) 為準。建議安全庫存只供決策參考，不得由查詢或 AI 摘要自動改寫主檔。

### 10.2 AI 營運摘要

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 產生摘要 | `POST` | `/ai/operational-summaries` | `AI_SUMMARY_GENERATE` |
| 查看摘要 | `GET` | `/ai/operational-summaries/{id}` | `AI_SUMMARY_VIEW` |

request 只傳分析範圍，不接受使用者直接提供 SQL 或任意資料庫查詢：

```json
{
  "from": "2026-10-01",
  "to": "2026-10-31",
  "sections": [
    "SALES",
    "SHORTAGE",
    "CUSTOMER_COOLING",
    "RECEIVABLE"
  ]
}
```

後端先依儀表板相同服務取得結構化指標，再將限定 DTO 提供給 AI。response 至少包含摘要、重要異常、可能原因、建議處理順序、來源連結、資料期間及產生時間。AI 不得直接修改任何交易資料。

摘要只由 Web 畫面按需產生，request 沿用當下儀表板的期間與篩選條件。每次呼叫均建立一筆摘要紀錄；本次不提供排程、Email 或 PDF 輸出。AI 失敗時回傳失敗狀態與可顯示訊息，儀表板查詢仍須正常運作。

正式 response 使用固定 Structured Output：

```json
{
  "id": 1,
  "status": "COMPLETED",
  "executiveSummary": "string",
  "importantChanges": [],
  "risks": [],
  "possibleCauses": [],
  "recommendedActions": [],
  "sourceReferences": [],
  "dataLimitations": [],
  "from": "2026-10-01",
  "to": "2026-10-31",
  "generatedAt": "2026-10-05T10:00:00+08:00"
}
```

各分析項目包含 `title`、`description`、`severity`、`evidence` 與 `sourceReferenceIds`。後端須驗證每個來源識別碼均存在於本次提供給 AI 的來源集合，再依使用者權限組成站內連結；不得直接採信 AI 產生的 URL 或資源 ID。

## 11. LINE 綁定與 AI 建單契約

### 11.1 Web API

| 操作 | Method | Path | 權限 |
| --- | --- | --- | --- |
| 產生綁定碼 | `POST` | `/line-bindings/codes` | `LINE_AI_SO_DRAFT_CREATE` |
| 查看我的綁定 | `GET` | `/line-bindings/me` | 已登入 |
| 解除我的綁定 | `DELETE` | `/line-bindings/me` | 已登入 |
| 管理綁定 | `GET/DELETE` | `/admin/line-bindings`、`/admin/line-bindings/{id}` | `LINE_BINDING_MANAGE` |

### 11.2 LINE webhook 與確認流程

```text
POST /integrations/line/webhook
```

處理順序：

1. 驗證 LINE 簽章與 event ID 防重。
2. 依 LINE userId 查找有效綁定、使用者狀態及 `LINE_AI_SO_DRAFT_CREATE` 權限。
3. 將單則完整訊息交由 AI 解析為限定 Structured Output。
4. 後端比對客戶、客戶料號、公司料號、單位及價格。
5. 回傳缺漏／歧義提示，或回傳解析結果預覽與一次性確認 token。
6. 只有原發起者在期限內確認後，才呼叫與 Web 相同的 SO 建立服務。

資料缺漏或歧義時，不保存跨訊息會話上下文；使用者須重新送出完整內容。缺少 `ACTIVE` 價格可建立帶警示的 SO 草稿，但該 SO 不得送審。

## 12. 畫面操作呈現原則

- 操作按鈕同時依權限與資源狀態呈現；後端仍是最終判斷者。
- 被禁止的操作若有助於使用者理解，可保留停用按鈕並顯示原因，例如「SO 尚未核准，不能建立 PO」。
- 核准頁需清楚顯示建立者、本輪送審者、重要交易資料、警示及歷次意見。
- SO 詳情的供應狀態使用一致欄位，不讓業務從多個頁面自行拼湊。
- 倉管畫面只呈現履約所需資訊；財務畫面以含稅應收、未收餘額及到期日為主。
- 任何成功命令後，前端以後端回傳的最新狀態及版本更新畫面，不自行推測狀態。
- PDF 下載只針對已保存的正式檔案；後端完成授權並記錄下載連結核發後，才產生 3 分鐘有效的 S3 Presigned URL，且不得保存或記錄完整短效網址。核發紀錄不代表使用者已完成下載；已作廢文件須有明確標示，不得當作有效文件使用。

## 13. 前後端整合順序

建議按可驗證的垂直流程整合：

1. 登入、目前使用者與權限。
2. 客戶、公司料號、客戶料號、價格及供應商主檔。
3. SO 草稿、送審、核准及庫存預留。
4. 關聯 PO、一般補庫 PO、核准、PDF 與採購在途。
5. 收貨過帳前確認、入庫及預留補足。
6. DO 揀貨、PDF、交車過帳與庫存扣減。
7. 應收確認、收款與核銷。
8. 四組儀表板、下鑽與 AI 營運摘要。
9. LINE 綁定與 LINE AI 建立 SO 草稿。

每完成一個階段，應使用真實 API、資料庫交易與權限驗證進行整合測試，不以純前端模擬資料視為流程完成。

## 14. 待實作時確認的技術參數

以下屬技術實作選擇，不改變本文件的產品行為：

- JWT access token 與 refresh token 的有效時間及撤銷方式。
- 查詢 API 的實際索引、最大分頁筆數及模糊搜尋策略。
- 權限代碼的完整 seed 清單及各角色預設對應（帳號權限見 D-036，主檔權限見第 5.3 節，SO 與庫存權限見 6.3、7.3 節，其餘模組實作時補齊）。
- API 文件以 SpringDoc OpenAPI 產生的 schema 與範例。
- LINE 確認 token 與 webhook event ID 的保存期限，以及收款 `clientRequestId` 的重送回應策略。

上述項目於實體資料庫、Spring Security 與整合實作時定案；若改變對外 request、response 或錯誤行為，須同步更新本文件與決策紀錄。

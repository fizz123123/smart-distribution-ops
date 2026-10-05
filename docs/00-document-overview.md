# 文件導覽

> 文件版本：v1.0  
> 建立日期：2026-09-29  
> 最後更新：  
> 用途：供團隊協作、業師審閱及 Git 版本管理。

## 文件架構

| 文件 | 定位 | 主要內容 |
| --- | --- | --- |
| [01-product-requirements.md](01-product-requirements.md) | 產品需求說明 | 專案定位、需求來源、核心痛點、交付目標與功能 |
| [02-business-processes.md](02-business-processes.md) | 業務流程 | 跨角色端到端流程、控制原則、例外與示範情境 |
| [03-functional-modules.md](03-functional-modules.md) | 功能模組 | 模組拆分、主要功能與跨模組交接 |
| [04-roles-and-permissions.md](04-roles-and-permissions.md) | 角色與權限 | 角色責任、核准邊界與權限矩陣 |
| [05-domain-rules.md](05-domain-rules.md) | 領域規則 | 名詞、價格、單位、庫存、採購供應、財務與單據規則 |
| [06-analytics-and-dashboard.md](06-analytics-and-dashboard.md) | 營運分析 | 淨接單、營收、缺貨風險、交易降溫與應收分析 |
| [07-ai-features.md](07-ai-features.md) | AI 功能 | LINE AI 建單與 AI 營運摘要 |
| [08-user-stories.md](08-user-stories.md) | User Story Backlog | 核心 User Story |
| [09-decision-log.md](09-decision-log.md) | 決策紀錄 | 已確認的重要範圍與設計決策、採用理由及影響 |
| [10-data-model.md](10-data-model.md) | 邏輯資料模型 | 核心實體、關係、資料責任、重要限制與 ER 圖 |
| [11-state-transitions-and-operations.md](11-state-transitions-and-operations.md) | 狀態與操作規格 | 主檔及交易狀態、角色操作、前置條件、結果與失敗原因 |
| [12-api-and-screen-contracts.md](12-api-and-screen-contracts.md) | API 與畫面契約 | 畫面清單、API 操作、權限、主要輸入輸出、錯誤與整合順序 |
| [13-system-architecture.md](13-system-architecture.md) | 系統架構 | AWS 部署、系統元件、後端模組、外部整合、安全與部署流程 |
| [14-implementation-plan.md](14-implementation-plan.md) | 實作規劃 | 垂直切片、開發順序、前後端協作、階段完成條件與里程碑 |
| [15-acceptance-test-plan.md](15-acceptance-test-plan.md) | 測試與驗收計畫 | 測試資料、權限與交易案例、端到端腳本、證據及通過條件 |

## 文件控管方式

- 開始迭代後，只要修改影響需求、流程、規則、範圍、設計或驗收結果，就在下表記錄修改日期、文件、修改者與修改內容。
- 錯字、排版、連結及不改變語意的修正不需要登錄，實際提交者、時間與逐行差異由 Git 保存。
- 重要方案的採用理由與影響仍記錄於 [09-decision-log.md](09-decision-log.md)，修改紀錄不重複說明完整決策過程。

## 重要版本紀錄

目前所有文件皆為初始 `v1.0`，尚無版本異動紀錄。下列第一列僅供格式示範，不代表實際修改；開始實作後可依相同格式新增紀錄。

| 修改日期 | 文件 | 修改者 | 修改內容 |
|---|---|---|---|
| （範例）2026-10-15 | 05-domain-rules.md | 林高至 | 依實作結果調整庫存預留規則，並同步更新受影響的流程與驗收案例 |

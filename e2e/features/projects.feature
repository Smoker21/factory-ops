# language: zh-TW
功能: 專案 CRUD 與成員
  作為 ADMIN,我需要能建立專案、檢視成員、修改基本資料。

  背景:
    假設 我以 admin.system 登入

  情境: 建立專案後出現在列表
    當 我打開「專案管理」
    並且 我點擊「新增專案」
    並且 我填入
      | 名稱     | 所屬組織         | 描述         |
      | E2E 示範 | assembly-section | 回歸測試用    |
    並且 我送出
    那麼 列表中應該出現「E2E 示範」且狀態為 "DRAFT"
    並且 後端 GET /v1/projects 應該包含此專案

  情境: 開啟專案詳情顯示 owner 與成員
    假設 已存在一個由 admin.system 建立、含 manager.wang 為 MEMBER 的專案 "權限測試"
    當 我打開「權限測試」的詳情頁
    那麼 我應該看到 owner 欄位顯示 "System Admin"
    並且 成員列表應包含 "王經理"

  情境: 不存在的專案 ID 顯示 NotFound
    當 我直接造訪 "/projects/000000000000000000000000"
    那麼 我應該看到 404 提示「找不到專案」

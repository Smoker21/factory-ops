# language: zh-TW
功能: RBAC 邊界
  確保 OPERATOR 看不到僅 ADMIN/ORG_ADMIN 才能執行的按鈕,
  且即使繞過 UI 直接打 API 也會被擋下;
  並驗證 token refresh / 失效流程。

  情境: OPERATOR 在專案列表頁看不到「新增專案」按鈕
    假設 我以 operator.li(OPERATOR) 登入
    當 我打開「專案管理」
    那麼 我應該看不到「新增專案」按鈕

  情境: OPERATOR 直接 POST /v1/projects 應回 403
    假設 我以 operator.li(OPERATOR) 登入
    當 我直接 POST /v1/projects 帶合法 body
    那麼 後端應回 403
    並且 errors 陣列應提示權限不足

  情境: 過期 access token 自動 refresh 後請求繼續成功
    假設 我以 admin.system 登入,access token 已超過 expiry
    當 我打開任一受保護 API 頁面
    那麼 client 應自動呼叫 /v1/auth/refresh 一次
    並且 後續 GET 應回 200(用新 access token)

  情境: refresh token 也失效時被導回登入頁
    假設 我以 admin.system 登入,access 與 refresh token 都已失效
    當 我打開任一受保護 API 頁面
    那麼 我應該被導回 "/login"
    並且 瀏覽器 cookies 應被清空

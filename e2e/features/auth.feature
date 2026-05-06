# language: zh-TW
功能: 登入、登出、Session 保護
  作為一個工廠值班團隊成員,
  我需要能用「組織代號 + 帳號 + 密碼」登入系統,
  並在 session 失效或登出時被導回登入頁。

  背景:
    假設 後端與前端皆已在 docker compose 啟動
    並且 已有 seed 資料(組織 "taichung-fab"、admin.system / Admin@123456789)

  @smoke
  情境: 正確認證後跳轉到每日工作看板
    當 我打開登入頁
    那麼 「組織代號」欄位應預填為 "taichung-fab"
    當 我填入帳號 "admin.system" 與密碼 "Admin@123456789"
    並且 我按下「登入」
    那麼 我應該被導向到 "/"
    並且 頂部應顯示我的姓名 "System Admin"
    並且 localStorage 應有 "factory_ops_access_token"

  情境: 密碼錯誤顯示通用錯誤訊息
    當 我打開登入頁並用 "admin.system" / "wrong" 登入
    那麼 我應該停留在 "/login"
    並且 應該看到「帳號或密碼錯誤,請重試」
    並且 localStorage 不應該有 token

  情境: 缺 orgCode 直接被 client validation 攔下
    當 我打開登入頁
    並且 我清空組織代號欄位
    並且 我按下「登入」
    那麼 應該看到「組織代號必填」
    並且 不應該有 POST 到 /v1/auth/login 的網路請求

  情境: 未登入造訪受保護頁面會被導回登入頁,登入後繼續導向原目標
    假設 我尚未登入(localStorage 已清空)
    當 我直接造訪 "/projects"
    那麼 URL 應變為 "/login"
    當 我用 admin.system 登入
    那麼 URL 應變為 "/projects"

  情境: 登出清除 token 並導回登入頁
    假設 我以 admin.system 登入
    當 我點擊使用者選單中的「登出」
    那麼 URL 應變為 "/login"
    並且 localStorage 不應該有 token

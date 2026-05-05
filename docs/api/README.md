# API 文件

## 互動式 Swagger UI（本機開發）

啟動後端後，在瀏覽器開啟：

```
http://localhost:8080/q/swagger-ui
```

即可直接在 UI 中呼叫 API、查看 request/response schema。

## 靜態 HTML 文件

`docs/api/index.html` 是由 `docs/spec/openapi.yaml` 自動生成的 Redoc 靜態文件。
開啟方式：

```bash
# 直接在瀏覽器開啟
open docs/api/index.html
# 或
start docs/api/index.html   # Windows
```

## 重新生成

```bash
npx redoc-cli bundle docs/spec/openapi.yaml -o docs/api/index.html
```

## 補充說明

### 認證流程

1. `POST /v1/auth/login` — 帶 `orgCode`、`accountName`、`password`，取得 `accessToken`（15 分鐘有效）+ `refreshToken`（7 天有效）
2. 後續所有 API 在 `Authorization: Bearer <accessToken>` header 帶 token
3. `POST /v1/auth/refresh` — 帶 `refreshToken`，取得新的 token pair
4. `POST /v1/auth/logout` — 將 token 加入黑名單（此後不可使用）

### 錯誤代碼總表

所有錯誤回應遵循 [RFC 7807 Problem Details](https://tools.ietf.org/html/rfc7807) 格式：

```json
{
  "type": "https://factoryops.example.com/errors/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Task with id '64a1b2c3d4e5f6a7b8c9d0e1' not found",
  "instance": "/v1/tasks/64a1b2c3d4e5f6a7b8c9d0e1"
}
```

| HTTP Status | 說明 |
|---|---|
| 200 | 成功 |
| 201 | 建立成功 |
| 204 | 刪除成功（無回應 body） |
| 400 | 請求格式錯誤 |
| 401 | 未認證或 token 過期 |
| 403 | 已認證但無權限（RBAC 拒絕） |
| 404 | 資源不存在 |
| 409 | 衝突（例：重複 code、狀態機違反） |
| 422 | 業務規則驗證失敗（帶詳細 violations） |
| 429 | 超過 rate limit |
| 500 | 伺服器錯誤 |

### 常見問題

**Q: 為什麼 login 需要帶 orgCode？**
A: 系統為多租戶架構，不同 root org 下可以有相同的 accountName（INV-29）。orgCode 用於確定用戶所屬的 root org。

**Q: cursor pagination 如何使用？**
A: 列表端點回傳 `pageInfo.nextCursor`，下次請求帶 `?cursor=<value>` 繼續取下一頁。`pageInfo.hasNextPage=false` 時表示已到最後一頁。

**Q: 附件上傳流程？**
A: `POST /v1/attachments/presign` 取得 MinIO presigned URL → 直接 PUT 上傳到 MinIO → 完成後呼叫 `POST /v1/attachments/confirm` 完成 metadata 寫入。

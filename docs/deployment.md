# 部署指引

**版本**: 1.0.0
**最後更新**: 2026-05-04

---

## 1. 快速啟動（Docker Compose）

### 先備條件

- Docker Engine 24.0+（含 Compose V2）
- 至少 4GB 可用記憶體

### 啟動步驟

```bash
# 1. Clone 專案
git clone https://github.com/<your-org>/factory-ops.git
cd factory-ops

# 2. 設定環境變數
cp .env.example .env
# 編輯 .env，至少修改以下值：
#   MINIO_ROOT_PASSWORD   — 使用強密碼
#   JWT_PUBLIC_KEY_PATH   — prod 必填（見第 4 節）
#   JWT_PRIVATE_KEY_PATH  — prod 必填（見第 4 節）
#   CORS_ORIGINS          — 設為前端 URL

# 3. 啟動（首次建 image 約 5-10 分鐘）
docker compose up -d

# 4. 確認所有服務健康
docker compose ps
# 應看到所有服務 Status 為 healthy 或 Up

# 5. 開啟應用
# 前端：http://localhost:5173
# Swagger UI：http://localhost:8080/q/swagger-ui（dev 模式）
# MinIO Console：http://localhost:9001
```

### Dev 模式啟動（暴露所有 port + seed data + Swagger UI）

```bash
# 使用 dev 覆寫啟動（會啟用種子資料）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

Dev 模式的預設帳號（由 DevDataSeeder 建立）：

| accountName | password | role | orgCode |
|---|---|---|---|
| admin.system | Admin@123456789 | ADMIN | fab-alpha |
| op.wang | Password@123456 | OPERATOR | fab-alpha |

**注意**：production 啟動前必須停用 DevDataSeeder（`FACTORY_OPS_SEED_ENABLED=false`）。

---

## 2. 環境變數說明

所有環境變數列於 `.env.example`：

| 變數 | 預設值 | 說明 | 必填（prod） |
|---|---|---|---|
| `MONGO_HOST_PORT` | 27017 | MongoDB host 暴露 port | 否 |
| `MINIO_ROOT_USER` | minioadmin | MinIO root 帳號 | 是 |
| `MINIO_ROOT_PASSWORD` | minioadmin | MinIO root 密碼 | 是（強密碼） |
| `MINIO_BUCKET` | factory-ops-attachments | 附件儲存 bucket 名稱 | 否 |
| `CORS_ORIGINS` | http://localhost:5173 | 逗號分隔的允許 origin | 是 |
| `HR_MODE` | mock | `mock` 或 `real` | 否 |
| `JWT_PUBLIC_KEY_PATH` | — | RSA 公鑰容器內路徑 | 是 |
| `JWT_PRIVATE_KEY_PATH` | — | RSA 私鑰容器內路徑 | 是 |
| `SWAGGER_ENABLED` | false | 啟用 Swagger UI | 否 |
| `BACKEND_HOST_PORT` | 8080 | Backend host port | 否 |
| `FRONTEND_HOST_PORT` | 5173 | Frontend host port | 否 |

---

## 3. Production 部署

### 使用 prod 覆寫

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Production 覆寫額外設定：
- MongoDB / NATS port 不對外暴露
- 資源限制（backend 768MB, mongo 1GB, nats 256MB）
- `restart: always` policy

### Production Checklist

- [ ] `.env` 中 `JWT_PUBLIC_KEY_PATH` 與 `JWT_PRIVATE_KEY_PATH` 指向真實 RSA 金鑰（見第 4 節）
- [ ] `MINIO_ROOT_PASSWORD` 已改為強密碼（≥ 12 字元，含大小寫英數符號）
- [ ] `CORS_ORIGINS` 設為真實前端 domain（例 `https://ops.example.com`）
- [ ] `SWAGGER_ENABLED=false`（或透過 VPN 限制存取）
- [ ] `FACTORY_OPS_SEED_ENABLED=false`（prod profile 預設已關）
- [ ] `HR_MODE` 確認為 `real` 並設定正確的 HR 服務 URL
- [ ] MongoDB replica set 健康確認：`docker exec -it <mongo-container> mongosh --eval "rs.status()"`
- [ ] Backend health check 通過：`curl http://localhost:8080/q/health/ready`
- [ ] MinIO bucket 已建立且 lifecycle 政策已設定

---

## 4. JWT 金鑰管理

### 初次生成金鑰（首次部署）

```bash
# 生成 2048-bit RSA key pair
openssl genrsa -out privateKey.pem 2048
openssl rsa -in privateKey.pem -pubout -out publicKey.pem

# 確認金鑰格式正確
openssl rsa -in privateKey.pem -check
```

### 掛載金鑰到容器

```yaml
# docker-compose.prod.yml 覆寫範例
services:
  backend:
    environment:
      JWT_PUBLIC_KEY_PATH: /run/secrets/jwt-public-key
      JWT_PRIVATE_KEY_PATH: /run/secrets/jwt-private-key
    volumes:
      - /path/to/keys/publicKey.pem:/run/secrets/jwt-public-key:ro
      - /path/to/keys/privateKey.pem:/run/secrets/jwt-private-key:ro
```

### JWT 金鑰輪換流程

1. 生成新的 RSA key pair（`openssl genrsa` 如上）
2. 更新 `.env`：`JWT_PUBLIC_KEY_PATH`、`JWT_PRIVATE_KEY_PATH` 指向新金鑰路徑
3. 重啟 backend service：`docker compose restart backend`
4. **注意**：輪換後現有 JWT 立即失效（使用者需重新登入）。建議在低峰期執行，並提前通知使用者。
5. 安全移除舊金鑰檔案

**重要安全說明**：
- `*.pem` 已加入 `.gitignore`，永遠不可 commit 到 git
- 生產金鑰僅儲存在部署機器或 secret manager（HashiCorp Vault、AWS Secrets Manager 等）
- `backend/src/main/resources/jwt/` 目錄下的 `privateKey.pem` / `publicKey.pem` 僅供本機開發使用

---

## 5. 備份與還原

### MongoDB 備份

```bash
# 備份（容器內執行）
docker exec factory-ops-mongo-1 \
  mongodump --uri="mongodb://localhost:27017/factory_ops?replicaSet=rs0" \
  --out /backup/$(date +%Y%m%d)

# 或備份到 host
docker exec factory-ops-mongo-1 mongodump \
  --uri="mongodb://localhost:27017/factory_ops?replicaSet=rs0" \
  --archive | gzip > backup_$(date +%Y%m%d).gz
```

### MongoDB 還原

```bash
# 從壓縮 archive 還原
gzip -dc backup_20260504.gz | docker exec -i factory-ops-mongo-1 \
  mongorestore --uri="mongodb://localhost:27017/factory_ops?replicaSet=rs0" \
  --archive --drop
```

### MinIO 備份（bucket sync）

```bash
# 安裝 mc client，設定 alias
mc alias set prod http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD

# 同步到備份位置（可替換為 S3 / 另一台 MinIO）
mc mirror prod/factory-ops-attachments /backup/minio/
```

---

## 6. HR 整合切換（mock → real）

1. 確認真實 HR 服務的 API endpoint（需符合 `docs/adr/0007-user-hr-integration.md` §v1.3 Amendment 規格）
2. 在 `.env` 設定：
   ```
   HR_MODE=real
   HR_REAL_URL=https://hr.example.com/api
   ```
3. 重啟 backend：`docker compose restart backend`
4. 驗證：`curl -H "Authorization: Bearer <token>" http://localhost:8080/v1/users?keyword=admin`

---

## 7. 已知限制與注意事項

- **MongoDB Transaction**：需要 replica set（docker-compose 預設已配置 `rs0`）。Standalone MongoDB 不支援 multi-document transaction。
- **JWT 存儲**：目前 access token 存於 frontend localStorage（XSS 風險）。M4 P1 backlog 項目：改為 httpOnly cookie。
- **OutboxPoller**：retryCount 上限為 5，超過後目前只記錄 log。M4 P2 backlog 項目：實作 dead-letter queue。
- **Cursor Pagination**：目前列表端點回傳 `PageInfo(null, false)`（M4 P1 backlog）。大量資料時建議使用 MongoDB 直接查詢或等待下版本修復。

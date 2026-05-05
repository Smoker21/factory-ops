# 維運手冊

**版本**: 1.0.0
**最後更新**: 2026-05-04

---

## 1. Health Check 端點

Quarkus SmallRye Health 提供以下端點：

| 端點 | 說明 | 用途 |
|---|---|---|
| `GET /q/health` | 綜合健康狀態（live + ready） | Kubernetes readiness + liveness |
| `GET /q/health/live` | 存活檢查（進程存活） | Kubernetes liveness probe |
| `GET /q/health/ready` | 就緒檢查（可接收流量） | Kubernetes readiness probe |
| `GET /v1/health` | 簡化版（UI 用） | 前端確認後端可用 |

回應範例：

```json
{
  "status": "UP",
  "checks": [
    { "name": "MongoDB connection health check", "status": "UP" },
    { "name": "Database connection health check", "status": "UP" }
  ]
}
```

Docker Compose 中 backend 的 healthcheck 使用 `/q/health/ready`。

---

## 2. 重要 Metric 監控指標

目前系統以結構化 JSON log 為主要觀測手段（production profile 啟用 `quarkus-logging-json`）。未來可接 OpenTelemetry（已加依賴，待啟用）。

### 建議監控指標

| 指標 | 來源 | 告警門檻 |
|---|---|---|
| **列表 API p95 回應時間** | nginx access log / APM | > 500ms 警告，> 2s 嚴重 |
| **Outbox 積壓筆數** | `db.outbox_events.countDocuments({status:"PENDING"})` | > 500 警告，> 2000 嚴重 |
| **登入失敗次數（per IP）** | backend log `event=AUTH_FAIL` | > 10次/分鐘/IP |
| **JWT 401 比例** | backend log `status=401` | > 5% 請求 |
| **MongoDB replica lag** | `rs.printSecondaryReplicationInfo()` | > 10s |
| **MinIO 磁碟使用率** | MinIO Console → Metrics | > 80% |

### Log 查詢範例（JSON 結構化 log）

```bash
# 找出所有認證失敗
docker compose logs backend | jq 'select(.message | contains("AUTH_FAIL"))'

# 查看 p95 回應時間
docker compose logs frontend | grep '"status"' | jq '.response_time' | sort -n | awk 'NR==int(NR*0.95)'
```

---

## 3. Log 格式說明

Production profile 啟用結構化 JSON log（`quarkus.log.console.json=true`）。

### 欄位說明

| JSON Key | 型別 | 說明 |
|---|---|---|
| `timestamp` | ISO 8601 | 日誌時間（UTC） |
| `level` | string | `INFO` / `WARN` / `ERROR` |
| `loggerName` | string | Java class 全名 |
| `message` | string | 人類可讀訊息 |
| `requestId` | string | 追蹤 ID（若有） |
| `userId` | string | 操作者 userId（若已認證） |
| `rootOrgId` | string | 租戶 ID |
| `exception` | object | 例外 stack trace（若有） |

**安全規則**：`passwordHash`、`refreshToken`、JWT 私鑰永不出現在 log 中（由 `toString()` override 與 logger 設定保證）。

---

## 4. 常見問題排查

### 4.1 MongoDB 無法連線

**症狀**：backend 啟動後 health check 回傳 DOWN，log 出現 `MongoTimeoutException`

**排查步驟**：

```bash
# 確認 mongo 容器運行中
docker compose ps mongo

# 確認 replica set 已初始化
docker compose exec mongo mongosh --eval "rs.status()" | grep stateStr

# 重新初始化 replica set（若 mongo-rs-init 失敗）
docker compose up mongo-rs-init

# 確認 backend 使用正確 connection string
docker compose exec backend env | grep MONGO_URI
```

**常見原因**：
- `mongo-rs-init` 服務在 mongo 健康之前就執行（調整 depends_on）
- `replicaSet=rs0` 未加入 connection string

### 4.2 JWT 401 錯誤

**症狀**：API 請求回傳 401，log 出現 `JWT verification failed`

**排查步驟**：

```bash
# 確認 JWT 公鑰已掛載
docker compose exec backend ls -la /jwt/ 2>/dev/null || \
  docker compose exec backend env | grep JWT

# 驗證 token（使用 jwt.io 或命令列）
echo "<token>" | cut -d. -f2 | base64 -d 2>/dev/null | jq

# 檢查 token audience
# access token 應為 factory-ops-access
# refresh token 應為 factory-ops-refresh
```

**常見原因**：
- Production 環境 JWT key 路徑未正確掛載
- Token 已過期（access 15 分鐘，refresh 7 天）
- Dev key 在 production 被 `RUN find /deployments -name "*.pem" -delete` 清除但未注入新 key

### 4.3 Webhook 死信（事件未發送）

**症狀**：outbox_events collection 有大量 `status="FAILED"` 文件

**排查步驟**：

```bash
# 查看 outbox 積壓
docker compose exec mongo mongosh --eval \
  'db.getSiblingDB("factory_ops").outbox_events.countDocuments({status:"PENDING"})'

# 查看最近失敗的 events
docker compose exec mongo mongosh --eval \
  'db.getSiblingDB("factory_ops").outbox_events.find({status:"FAILED"}).sort({updatedAt:-1}).limit(5)'

# 確認 NATS 連線
docker compose exec backend env | grep NATS
docker compose exec nats nats server info
```

**常見原因**：
- NATS 服務不健康或重啟後 JetStream stream 消失
- Webhook URL 無法到達（網路問題或目標服務 down）
- retryCount > 5 後進入 FAILED 狀態（P1 backlog：實作 dead-letter queue）

### 4.4 前端空白或 API 無法呼叫

**症狀**：前端頁面空白，瀏覽器 console 出現 `CORS` 或 `net::ERR_CONNECTION_REFUSED`

**排查步驟**：

```bash
# 確認 backend 可達
curl http://localhost:8080/q/health/ready

# 確認 CORS 設定
curl -H "Origin: http://localhost:5173" \
     -H "Access-Control-Request-Method: GET" \
     -X OPTIONS http://localhost:8080/v1/tasks -v 2>&1 | grep -i access-control

# 確認前端 API base URL
docker compose exec frontend cat /etc/nginx/conf.d/default.conf | grep proxy_pass
```

**常見原因**：
- `CORS_ORIGINS` 未包含前端實際 URL
- nginx proxy 設定錯誤（`/api/` proxy 到 `http://backend:8080/`）
- Backend 尚未就緒（frontend depends_on backend healthy）

---

## 5. 備份排程建議

| 資料 | 頻率 | 保留期 | 方式 |
|---|---|---|---|
| MongoDB factory_ops | 每日 02:00 | 30 天 | mongodump + gzip → 異地儲存 |
| MinIO bucket | 每日 03:00 | 90 天 | mc mirror → S3/B2 |
| JWT key pair | 每次輪換時 | 永久（加密儲存） | secret manager |

---

## 6. 效能調校參考

| 設定 | 檔案 | 說明 |
|---|---|---|
| MongoDB connection pool | `application.properties` | `quarkus.mongodb.max-pool-size` 預設 100 |
| Outbox batch size | `application.properties` | `factory.ops.outbox.poll.batch-size=100` |
| JVM heap | Dockerfile ENV | `-XX:MaxRAMPercentage=75.0` |
| nginx worker processes | `nginx.conf` | 預設 auto（配合 CPU 核數） |

---

## 7. 升級流程

1. 閱讀 `CHANGELOG.md` 確認 breaking changes
2. 備份 MongoDB（見第 5 節）
3. 更新 image tag（`docker-compose.yml` 或 `.env`）
4. `docker compose pull`
5. `docker compose up -d --no-deps backend frontend`（零停機 rolling 替換）
6. 觀察 `docker compose logs -f backend` 確認啟動成功
7. 確認 health check：`curl http://localhost:8080/q/health/ready`

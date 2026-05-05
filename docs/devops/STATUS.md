# DevOps / Doc-Devops STATUS

**里程碑**: 4 — 第三棒（文件 + CI/CD）
**agent**: doc-devops
**完成日期**: 2026-05-04
**狀態**: COMPLETED

---

## 完成工作摘要

### 測試設定修改

- `backend/src/test/resources/application.properties`：啟用 `devservices.replica-set-name=rs0`（Testcontainers 啟動 MongoDB replica set）；移除 `devservices.enabled=false`
- 5 個 @QuarkusTest 測試類別移除 `@Disabled`（CI Docker 環境可正常執行）：
  - `OrganizationServiceTest`（class level @Disabled 移除）
  - `TaskServiceTest`（class level @Disabled 移除）
  - `DispatchServiceTest`（class level @Disabled 移除）
  - `AuthResourceTest`（3 個 method @Disabled 移除）
  - `E2eSmokeTest`（1 個 method @Disabled 移除）

### Docker / DevOps 產出

| 檔案 | 說明 |
|---|---|
| `backend/Dockerfile` | multi-stage（Gradle 8.10.2 JDK21 → Eclipse Temurin 21 JRE）；移除 dev .pem；USER 1001；healthcheck |
| `frontend/Dockerfile` | multi-stage（Node 20 Alpine → nginx 1.27 Alpine）；nginx user；healthcheck |
| `frontend/nginx.conf` | SPA fallback + `/api/` proxy + security headers |
| `docker-compose.yml` | 完整 6 服務圖（mongo + mongo-rs-init + mongo-idx-init + nats + minio + minio-init + backend + frontend） |
| `docker-compose.dev.yml` | dev 覆寫（seed data / Swagger / dev JWT keys） |
| `docker-compose.prod.yml` | prod 覆寫（resource limits / restart:always / internal network） |
| `.env.example` | 所有必要 env（18 個變數 + 說明） |
| `docker/mongo-rs-init.js` | MongoDB replica set 初始化（冪等） |
| `docker/minio-init.sh` | MinIO bucket 建立（冪等） |

### CI/CD 產出

| 檔案 | 觸發條件 |
|---|---|
| `.github/workflows/ci.yml` | PR / push to main / develop |
| `.github/workflows/release.yml` | tag v* |
| `.github/workflows/codeql.yml` | push to main / PR / 每週一 |

### 文件產出

| 檔案 | 說明 |
|---|---|
| `CHANGELOG.md` | Keep a Changelog 格式，M1~M4 全記錄 |
| `CONTRIBUTING.md` | GitHub Flow + Conventional Commits + PR checklist |
| `docs/architecture.md` | C4 Container 圖 + 模組職責 + sequence diagrams（3 個）+ 部署架構 |
| `docs/deployment.md` | Docker compose 啟動 + env 說明 + JWT rotation + 備份還原 + prod checklist |
| `docs/operations.md` | Health check + 監控指標 + log 格式 + 排查指南 + 效能調校 |
| `docs/api/index.html` | Redoc 靜態 HTML（從 openapi.yaml 自動生成，2.3MB） |
| `docs/api/README.md` | API 文件入口 + 認證流程 + 錯誤代碼總表 + FAQ |
| `README.md` | 更新快速開始 + CI badge + 部署連結 + M4 ✅ |
| `STATUS.md`（根目錄） | M4 COMPLETED 標記 + P1/P2 backlog 列表 |

### 規格修正

- `docs/spec/openapi.yaml` line 2801：description 含 `{ }` 的 YAML 格式錯誤修正（影響 redoc-cli 生成）

---

## 本機驗證結果

| 項目 | 結果 |
|---|---|
| `./gradlew test --tests "com.factoryops.unit.*"` | 65/65 PASS |
| `npm test` (frontend) | 66/66 PASS |
| `docker compose -f docker-compose.yml -f docker-compose.dev.yml config` | VALID |
| `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` | VALID |
| Docker image build（backend / frontend） | 未執行（本機 Docker daemon 未啟動） |
| @QuarkusTest 整合測試（MongoDB DevServices） | 未執行（本機 Docker daemon 未啟動） |

整合測試（@QuarkusTest）已移除 `@Disabled`，CI 環境（ubuntu-latest）有 Docker，應可正常執行。

---

## 已知限制與 Backlog

詳見根目錄 `STATUS.md` P1/P2 backlog 清單。

主要 deferred 項目：
- P1：JWT 存儲改為 httpOnly cookie（目前 localStorage）
- P1：cursor pagination 真實實作（目前回傳 null cursor）
- P1：OutboxPoller dead-letter queue（目前超過 5 次僅 log）
- P2：Trivy scan 在 image 未 push 時無法執行（CI job `security-scan` 僅在 main branch 跑）
- P2：`docker compose up` 需要約 5-10 分鐘首次 build — 可考慮預建 image 到 GHCR

---

## 給使用者的注意事項

1. **git repo 尚未初始化**：`.github/workflows/` 已建立，但 CI 需要 `git init && git push` 後才能觸發。
2. **JWT dev keys**：`docker-compose.dev.yml` 掛載 `backend/src/main/resources/jwt/` 下的 example pem。首次啟動 dev 環境時需確認 `privateKey.pem` / `publicKey.pem` 存在（從 `.example.pem` 複製即可）。
3. **`docker compose up`（基底 compose）**：生產模式需設定 `JWT_PUBLIC_KEY_PATH` 與 `JWT_PRIVATE_KEY_PATH`。Dev 快速啟動請使用 `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`。

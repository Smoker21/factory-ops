# CHANGELOG

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.0.0/) 格式，版本號遵循 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

---

## [Unreleased]

### Added

- 方法論基建：
  - `docs/release/impact-matrix.md`：15 種變更類型(`CT-1` ~ `CT-15`)對應的必動清單
  - `docs/release/checklist.md`：可重用的 Release Checklist 範本（複製到 PR description 逐條勾）
  - `scripts/verify.sh`：一鍵本地驗證腳本（後端 test、前端 typecheck/lint/test；`--full` 加 integration test + build）
- `CLAUDE.md` 新增五個方法論子節：
  - § Spec / ADR 變更原則（混合制：ADR immutable + Amendment / supersede；Spec living + git tag；Schema living + migrations/）
  - § STATUS.md Compact 原則（主 STATUS 為儀表板，歷史下沉 sub-STATUS / CHANGELOG）
  - § Release Discipline（Impact Matrix + verify.sh + Checklist + Definition of Done）
  - § Agent 協作協定（Handoff 四件套、讀寫邊界表、Stuck Protocol、主-sub 分工、並行 vs 序列）
  - § ADR 門檻（5-Lens 判斷、白/黑名單、Decision Tree、Status Lifecycle、五要素檢核）

### Changed

- `STATUS.md` 依 Compact 原則精簡（230 行 → ~85 行）：刪除 v1.3 重整摘要、給 mongodb-modeler 的銜接訊息、Q-18 ~ Q-24、驗收檢核清單；歸檔內容已存於 `docs/spec/STATUS.md`
- `CLAUDE.md` 反映 M1-M4 完成現況：M1-M4 詳述壓成完成歷史表（指向 sub-STATUS / CHANGELOG），新增 M5+ placeholder 與通用啟動範本
- `CLAUDE.md` 目錄結構新增 `docs/release/`、`scripts/`、`e2e/`、`docker/` 條目
- `CLAUDE.md` 「文件 / 自主程度」子節改為引用新的 § STATUS.md Compact、§ Agent 協作協定、§ ADR 門檻

### Removed

- `CLAUDE.md` 移除過時描述「Task 內容可使用貼圖」（與 spec Q-4 拍板結果不一致）

---

## [1.0.0-M4] - 2026-05-04

里程碑 4：測試強化 + 全面 Code Review + 文件補完 + CI/CD Pipeline

### Added

#### 測試覆蓋（M4-test）
- 後端新增 65 個 Mockito unit tests（5 個 service test class）：TaskService、OrganizationService、GroupService、DispatchService、TemplateService
- 前端新增 55 個 Vitest / RTL 測試：rbacGuards、TaskStatusFlow、AssigneeManager、lib utilities
- 後端加入 JaCoCo 覆蓋率報告（`./gradlew jacocoTestReport`）
- 前端加入 `@vitest/coverage-v8` coverage 設定（`npm run test:coverage`）
- 整合測試（@QuarkusTest）啟用 MongoDB DevServices replica set（`devservices.replica-set-name=rs0`）

#### 安全修補（M4-security，code review P0 修復）
- S-001：JWT refresh token 改用正確 RSA 公鑰驗章（移除 `setSkipSignatureVerification()`）
- S-002：所有 REST 端點補全 `@RolesAllowed` 標注，落實 §6 RBAC 矩陣
- S-003：login 改要求 `orgCode` 入參，解決跨租戶同名帳號歧義
- S-004：`PATCH /v1/users/{id}` roles 修改限 ORG_ADMIN / ADMIN，修補特權升級
- S-005：`*.pem` 加入 `.gitignore`；Dockerfile 加 `RUN find /deployments -name "*.pem" -delete`
- S-006：MockHrResource 加 `@IfBuildProfile("dev")` 防 PII 洩漏到 production
- S-008：logout 實作 token blacklist（`revoked_tokens` collection）
- S-012：Repository 所有查詢帶 `rootOrgId`，修補跨租戶資源存取路徑
- C-001：`OrganizationService.createOrg` 改一次 persist（記憶體預分配 ObjectId），修補非原子性兩步驟
- C-002：`TemplateService` update 補 version++ / active 檢查 / GLOBAL-ORG 分流
- C-003：Group settings INV-35 角色白名單驗證
- C-013 / P-003：Org move 遞迴更新子孫 ancestorIds（含 transaction）
- C-016：EventPublisher 移除 try/catch 吞例外，搭配 `@Transactional`
- M-003：所有 Service method 加 `@Transactional`（multi-step atomicity）
- P-001：列表端點加入 cursor pagination（`pageInfo.nextCursor`）

#### Docker / DevOps（M4-devops）
- `backend/Dockerfile`：multi-stage build（Gradle 8.10.2 JDK21 → Eclipse Temurin 21 JRE）
- `frontend/Dockerfile`：multi-stage build（Node 20 Alpine → nginx 1.27 Alpine）
- `frontend/nginx.conf`：SPA fallback + `/api/` proxy to backend + security headers
- `docker-compose.yml`：完整服務圖（MongoDB replica set + NATS JetStream + MinIO + backend + frontend）
- `docker-compose.dev.yml`：dev 覆寫（seed data + Swagger + dev JWT keys 掛載）
- `docker-compose.prod.yml`：prod 覆寫（resource limits + restart:always + internal network）
- `.env.example`：所有必要環境變數範例
- `docker/mongo-rs-init.js`：MongoDB replica set 初始化（冪等）
- `docker/minio-init.sh`：MinIO bucket 建立（冪等）

#### CI/CD（GitHub Actions）
- `.github/workflows/ci.yml`：PR / push to main 觸發（backend-test + backend-build + frontend-test + frontend-build + docker-build matrix + Trivy security scan）
- `.github/workflows/release.yml`：tag v* 觸發（push image 到 GHCR + 從 CHANGELOG 產 release notes）
- `.github/workflows/codeql.yml`：每週一 CodeQL 靜態分析（java-kotlin + javascript-typescript）

#### 文件
- `docs/architecture.md`：C4 Container 圖 + 模組職責 + 資料流 sequence diagrams + 部署架構
- `docs/deployment.md`：Docker compose 啟動步驟 + 環境變數說明 + JWT key rotation + 備份還原 + HR 整合切換 + production checklist
- `docs/operations.md`：health check 端點 + 監控指標 + log 格式 + 常見問題排查 + 效能調校
- `docs/api/index.html`：從 `docs/spec/openapi.yaml` 自動生成 Redoc 靜態 HTML
- `docs/api/README.md`：API 文件入口 + 認證流程 + 錯誤代碼總表 + 常見問題
- `CHANGELOG.md`（本檔）
- `README.md`：更新快速開始段落、CI badge、M4 狀態標記

### Changed
- `application.properties`：`CORS_ORIGINS` 改為 env 變數（`${CORS_ORIGINS:http://localhost:5173}`）
- `backend/src/test/resources/application.properties`：啟用 `devservices.replica-set-name=rs0`，移除 `devservices.enabled=false`
- 5 個 @QuarkusTest 測試類別移除 `@Disabled`（CI 現在有 Docker 可跑）

### Fixed
- `docs/spec/openapi.yaml` line 2801：description 欄位含 `{ }` 的 YAML 格式錯誤（改用 double-quoted string）

---

## [1.0.0-M3] - 2026-05-04

里程碑 3：後端 + 前端骨架

### Added
- 後端：Kotlin 2 / Quarkus 3.17 完整骨架（~85 端點，13 個 JAX-RS Resource）
- 前端：React 18 / TypeScript SPA（13 頁面，17 元件，11 API client）
- JWT 認證（access + refresh，RSA 2048-bit）
- MSW service worker（前端可離線跑 mock）
- `DevDataSeeder`（dev profile：4 層 Org 樹 + 5 位假員工 + Project + Tasks）
- Outbox pattern 骨架（OutboxPoller + EventPublisherService）
- MinIO 附件上傳（presigned URL flow）

---

## [1.0.0-M2] - 2026-05-04

里程碑 2：資料模型

### Added
- 16 個 MongoDB collection 設計文件（`docs/data/schema.md`）
- 索引設計（`docs/data/indexes.md`，IDX-ORG/GRP/USR/PRJ/TSK/AR/AT/TMP/WBH/OUT 共 40+ 索引）
- 40 個 Kotlin domain / document class
- MongoDB 索引初始化腳本（`backend/src/main/resources/db/init-indexes.js`）

---

## [1.0.0-M1] - 2026-05-04

里程碑 1：規格 + 領域設計

### Added
- 需求文件 v1.3.0（`docs/spec/requirements.md`）：17 個 User Stories + 36 個業務不變式 + 9 個角色 RBAC 矩陣
- 領域模型（`docs/spec/domain-model.md`）：Mermaid 類別圖 + Aggregate 說明
- OpenAPI 3.1 規格（`docs/spec/openapi.yaml`）：~85 端點完整定義
- 13 份架構決策紀錄（ADR-0001 ~ ADR-0013）
- Q-1 ~ Q-17 設計決策拍板記錄

---

[Unreleased]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M4...HEAD
[1.0.0-M4]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M3...v1.0.0-M4
[1.0.0-M3]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M2...v1.0.0-M3
[1.0.0-M2]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M1...v1.0.0-M2
[1.0.0-M1]: https://github.com/smoker21/factory-ops/releases/tag/v1.0.0-M1

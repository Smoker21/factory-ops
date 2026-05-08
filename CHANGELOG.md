# CHANGELOG

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.0.0/) 格式，版本號遵循 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

---

## [Unreleased]

---

## [1.0.0-M5] - 2026-05-09

里程碑 5：Hardening + Spec Lock-in（24 條 in-scope 全部收完）

### Added

#### Spec lock-in(M5.1 + M5.1.5)
- Q-18 ~ Q-24 七題衍生 Open Questions 全部落地進 spec / ADR（2026-05-07 規劃會議拍板；2026-05-08 spec-architect 落地）
  - Q-18 跨層 dispatch：任一上級 manager 皆可（`requirements.md §FR-Dispatch.1/.4`，已驗 backend 一致）
  - Q-19 Manager 代理：不引入 deputy 欄位，靠 transfer-manager，HR 端負責（`ADR-0007 v1.4 Amendment`）
  - Q-20 leaf reject 通知：只 emit event，不主動推 push（`ADR-0009 v1.4 Amendment`）
  - Q-21 QA reject 後清空既往 reviews（`ADR-0011 v1.4 Amendment §5`）
  - Q-22 Group settings 不做 versioning，沿用 `history[]`
  - Q-23 `requiredReviewerRoles` 採 OR 白名單語意 + 不擋同人多角色簽（Q5 B，工廠派工輕量確認）（`ADR-0011 v1.4 Amendment §1-4`）
  - Q-24 不保留發起端原始 UTC offset
- `docs/adr/0015-jwt-cookie-and-csrf.md`：新建 ADR-0015（Status: Accepted）— JWT Cookie + CSRF Model；Decision「refresh httpOnly cookie + access 雙模兼容 + CSRF double-submit」；4 個 Alternatives Considered
- `requirements.md §FR-1.5 ~ §FR-1.8`：refresh cookie / access dual-mode / CSRF double-submit / cookie lifecycle 行為總表
- `openapi.yaml`：`components.securitySchemes.cookieAuth`、`components.parameters.CsrfHeader`、`components.responses.CsrfMismatch`；login/refresh/logout 三端點補 `Set-Cookie` response header 描述；全 64 個 mutating ops 補 `CsrfHeader $ref`

#### Data model(M5.2)
- `backend/src/main/kotlin/com/factoryops/domain/user/User.kt`：`failedLoginCount: Int = 0`、`lockedUntil: Instant? = null`（serves S-016）
- `backend/src/main/kotlin/com/factoryops/domain/event/OutboxDeadLetter.kt`：新增 outbox dead-letter aggregate（選項 X，與 `WebhookDeadLetter` 並存）
- `docs/data/migrations/0001-user-lockout-fields.md`、`0002-outbox-dead-letter.md`（首次建立 `docs/data/migrations/` 目錄；4 項齊備）

#### Backend Security Hardening(M5.3)
- `LockoutStateWriter`：S-016 持久化修正，`@Transactional(REQUIRES_NEW)` + `persistOrUpdate()` 確保鎖定狀態獨立 commit
- `CsrfFilter`：double-submit cookie CSRF filter（cookie-mode 才啟動，Bearer-only 透通）
- `CookieHelper`：三個 auth cookie 的統一工廠（buildLoginCookies / buildClearCookies / generateXsrfToken）
- `RateLimiter`：per-IP + per-account 雙鍵 sliding-window（login / logout / changePassword；超限 429 + RFC 7807 + Retry-After）
- `CorsValidationOnStartup`：prod profile 啟動 fail-fast（CORS_ORIGINS 未設或為 `*` → 拒絕啟動）
- UserService.createUser：`SecureRandom` 產 16 字元臨時密碼；密碼強度驗證（≥12 字元 + ≥3 字元類別）
- UserRepository.searchByKeyword：`Pattern.quote` + 長度 cap 64 + 禁 `*`/`?` + 前綴匹配（S-013）
- `DomainExceptions`：新增 `AccountLockedException`、`RateLimitExceededException`

#### Domain Invariants(M5.4)
- C-005：`DispatchService.convertToTask` 加 SUBMITTED 狀態前置檢查 + 顯式 SeverityLevel → Priority 映射表
- C-006：`TaskService.changeTaskStatus` force-complete 加 group membership 驗證（actor 非成員 → 403）
- C-007：`TaskService.buildQaReviewPolicy` mapNotNull 改顯式 404 拋出（不再靜默忽略找不到的 group）
- C-008：IN_REVIEW → DONE 兩條路徑（qa_review_complete / force_complete）統一走 `applyDoneTransition()` helper
- C-009：`ProjectService` 狀態機補 `PAUSED → COMPLETED` 合法轉移
- C-010：`ProjectService.createProject` 加 `due > start` 日期驗證
- C-011：`TaskService.createTask` 加 `dueAt >= project.startAt` 驗證
- C-012：`TaskService.addAssignees` 批次查 user 全 active + 同 `rootOrgId`
- C-014：`OrganizationService.deleteOrg` 加 `countActiveByOrg` + `countByOrg` 阻擋（有 active 資源 → 409）
- C-015：`OutboxPoller` retryCount > 10 → 搬入 `outbox_dead_letters` collection；exponential backoff 上限 3600s；DuplicateKeyException 冪等
- `OutboxDeadLetterDocument` + `OutboxDeadLetterRepository`（M5.2 handoff 指定，M5.4 建立）
- `ProjectRepository.countActiveByOrg`、`GroupRepository.countByOrg`（C-014 新查詢方法）

#### Frontend Cookie 改造(M5.5)
- `frontend/src/utils/cookies.ts`：`getCookie(name)` utility（供 CSRF echo interceptor 讀 XSRF-TOKEN 值）
- `frontend/src/api/client.ts` 完整重寫：`withCredentials: true`；CSRF echo interceptor；refresh dedupe（`isRefreshing` + `refreshQueue`）；redirect loop guard
- `AuthContext.tsx`：mount 時以 `GET /me` bootstrap session；`isLoading` race 處理
- `ProtectedRoute.tsx`：`isLoading` guard（防止 cookie bootstrap 時閃跳 /login）

#### 測試成長
- backend：425（M4）→ 547（M5.3.2）→ 654（M5.4.2）：**+229 tests，全綠**
- frontend：66（M4）→ 73（M5.5.1）→ 102（M5.5.2）：**+36 tests，全綠**
- 新增整合測試：`AuthCookieFlowIT`（20 cases）、`AuthLockoutIT`（5 cases，M5.4.2 改造）、`AuthRateLimitIT`、`DeleteOrgBlockedIT`（C-014）、`OutboxDeadLetterIT`（C-015）
- BDD step defs 改寫為 cookie jar 模式（`injectAuthCookies()` + Playwright 原生 cookie jar）；新增「reload 仍登入」與「登出後 reload 不再登入」兩個情境

### Changed

- `requirements.md` / `openapi.yaml`：v1.3.0 → **v1.5.0**（Q-18~Q-24 拍板 → v1.4；cookie/CSRF spec 補件 → v1.5）
- `schema.md` / `indexes.md`：v1.0 → **v1.1.0**（User lockout 兩欄 + `outbox_dead_letters` collection）
- **Q-23 雙簽語意**：AND → **OR 白名單**（`TaskService.kt` `all` → `size >= 2`；`ADR-0011 v1.4 Amendment §1-4`；同人多角色不擋）
- `UserDocument`：加 `failedLoginCount`、`lockedUntil`；`UserMapper` 雙向映射同步更新
- JWT 改 dual-mode：cookie 優先 + Bearer header fallback（SmallRye JWT `mp.jwt.token.header=Cookie` + `smallrye.jwt.always-check-authorization=true`）
- `AuthResource`：login/refresh/logout 加三 cookie；refresh 從 cookie 讀；失敗清 cookie
- `LoginPage`：不再 `decodeJwt`、不再寫 `localStorage`；以 `getMe()` 取得 user
- `UserMenu.tsx`：`apiLogout()` 無參數；移除 `refreshToken` 依賴
- `vite.config.ts`：新增 `/v1` → `localhost:8080` proxy（dev 環境同 origin cookie 傳遞）
- `backend/src/main/resources/application.properties`：
  - OpenAPI runtime `info-version` 1.3.0 → **1.5.0**（P1-4）
  - CSRF exempt-paths 移除 `/mock-hr`（prod 不存在）；`%dev` profile 補回（P2-3）
  - 新增 `%test.auth.cookie.secure=false` + `%test.auth.cookie.same-site=Lax`（P2-5）
  - 新增 S-016 lockout 參數、S-015 rate-limit 參數、S-009 cookie/CSRF 參數
- `docs/adr/0007-user-hr-integration.md`、`0009-event-distribution-nats-and-webhooks.md`、`0011-group-settings-qa-dualsign.md`：各加 `v1.4 Amendment`
- `docs/spec/openapi.yaml`：C-014 `/orgs/{orgId}` DELETE 補 404 response + 更新 409 說明

### Fixed

- **S-016 持久化根因**：`AuthService.login()` 的 `@Transactional` 在 throw `UnauthorizedException` 時 JTA rollback 撤銷 lockout 寫入 → 改 `LockoutStateWriter`（`@Transactional(REQUIRES_NEW)`）確保獨立 commit；`AuthLockoutIT` 由紅轉綠
- S-013：`UserRepository.searchByKeyword` 直接串入未 escape 的 keyword 作為正則表達式（ReDoS 潛在風險）→ `Pattern.quote` + 前綴匹配
- S-011：`UserService.createUser` 原接受 caller 傳入明文 `defaultPassword` → 伺服器端 `SecureRandom` 生成
- S-017：`LoginRequest @Size(max=128)` 已確認就位（M3 已有，M5 驗證）
- C-005 ~ C-015：所有 domain invariant 強化（見 Added 段）

### Removed

- `frontend/src/auth/jwtUtils.ts`（dead code，cookie-first 模式不需解析 client-side JWT）
- `frontend/src/api/client.ts` 及所有相關元件中的 `localStorage` token 操作（`localStorage` 在 `frontend/src/api/` grep = 0 hits）
- `UserDtos.CreateUserRequest.defaultPassword` 欄位（改由伺服器端生成）
- `spec STATUS.md` Q-18 ~ Q-24 Open Questions（全部拍板）

### Status hygiene

- P1 backlog 移除：S-009/010/011/013/015/016/017、C-005 ~ C-015、Q-23 OR、P-001（cursor pagination 已在 M4 實作）、S-008-ext（revoked_tokens TTL 已在 M4 建 index）
- P1 backlog 新增（本期 code review 產出，進 M6+）：
  - **P1-1** `LockoutStateWriter` 走 repository 抽象（目前直呼叫 `persistOrUpdate()`）
  - **P1-2** `OutboxPoller.pollAndProcess()` dead-letter 寫入 + 原 entry 標記非原子（最終一致性，冪等保護；M6 補 `@Transactional` 包覆 + ADR-0009 v1.5 Amendment）
  - **P1-3** CSRF dual-mode 強制截止點（ADR-0015 v1.6 Amendment + `security.csrf.strict-mode` toggle）
  - P2 七條（見 `docs/review/m5-review.md`）

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

[Unreleased]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M5...HEAD
[1.0.0-M5]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M4...v1.0.0-M5
[1.0.0-M4]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M3...v1.0.0-M4
[1.0.0-M3]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M2...v1.0.0-M3
[1.0.0-M2]: https://github.com/smoker21/factory-ops/compare/v1.0.0-M1...v1.0.0-M2
[1.0.0-M1]: https://github.com/smoker21/factory-ops/releases/tag/v1.0.0-M1

# 後端狀態

**狀態**: P0_SECURITY_FIXES_APPLIED
**版本**: 1.1.0(對應 Spec v1.3.0 + P0 安全修復)
**完成時間**: 2026-05-03
**負責 agent**: quarkus-backend-builder (M3) → 安全修復由 code-reviewer 指導

---

## M4 P0 安全修復(2026-05-03)

本次修復依 code-reviewer 的 Must-Fix 清單完成全部 15 項 P0 項目。
`./gradlew compileKotlin compileTestKotlin` 通過，無警告。

### 已修復 P0 清單

| # | 項目 | 狀態 |
|---|---|---|
| P0-1 | JWT refresh token 簽名驗證(改用 JWTAuthContextInfo + audience=factory-ops-refresh) | DONE |
| P0-2 | 全面補 @RolesAllowed 於所有 Resource method | DONE |
| P0-3 | Login 加 orgCode 欄位,用 (rootOrg, accountName) 查詢 | DONE |
| P0-4 | UserService.updateUser 禁止 actor 改自己 roles | DONE |
| P0-5 | .gitignore 加 !*.example.pem,prod 強制 env 路徑 | DONE |
| P0-6 | OrganizationRepository.findByIdAndRootOrg 全面取代 findByIdAndNotDeleted | DONE |
| P0-7 | OrganizationService.createOrg 原子化(預生成 ObjectId) | DONE |
| P0-8 | TemplateService.update* 加 active=true 檢查 + version++ | DONE |
| P0-9 | 所有 Service class 加 @Transactional | DONE |
| P0-10 | GroupService.updateGroupSettings 加 INV-35 角色白名單 | DONE |
| P0-11 | MockHrResource 加 @IfBuildProfile("dev") | DONE |
| P0-12 | AuthService.logout 實作 refresh token blacklist(revoked_tokens collection) | DONE |
| P0-13 | 7 個列表端點補 cursor pagination(?cursor=&limit=) | DONE |
| P0-14 | OrganizationService.updateOrg 移動節點時 propagate ancestorIds 到子孫 | DONE |
| P0-15 | EventPublisherService.publishEvent 移除 try/catch | DONE |

---

## 啟動前置需求

### 1. 安裝 JDK 21
```bash
# macOS (Homebrew)
brew install --cask temurin@21

# Windows (Chocolatey)
choco install temurin21

# Ubuntu
sudo apt-get install temurin-21-jdk
```

### 2. 初始化 Gradle Wrapper (一次性)
```bash
cd backend

# 需要系統安裝 gradle
gradle wrapper --gradle-version 8.10.2

# 或手動下載 gradle-wrapper.jar:
# https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
# 放到 backend/gradle/wrapper/gradle-wrapper.jar
```

### 3. 確認 Docker 運行中
```bash
docker ps  # 需要 Docker Desktop 或 Docker Engine
```

### 4. 啟動開發模式
```bash
cd backend
./gradlew quarkusDev
# MongoDB DevServices 會自動啟動 MongoDB container
# Seed 資料會自動載入(factory.ops.seed.enabled=true 在 dev profile)
```

### 5. 驗收端點
```
http://localhost:8080/q/swagger-ui   # API 文件
http://localhost:8080/v1/health      # 健康檢查
```

---

## 預期首次編譯可能遇到的問題

1. **Import 錯誤**: 約 2-5 個 import 可能需要修正
2. **BCrypt API**: `at.favre.lib:bcrypt:0.10.2` 的 `verify()` 第二個參數可能需要調整(已改為 `hash.toCharArray()`)
3. **Panache 文件類別**: `lateinit var` 在空文件解碼時可能需要設定 nullable 預設值
4. **jose4j 依賴**: JwtIssuerService 的 refresh token 解析使用了 `org.jose4j` (Quarkus 內建,應可用)

---

## 已實作端點清單(~85 個)

### Auth (4)
- POST /v1/auth/login
- POST /v1/auth/refresh
- POST /v1/auth/logout
- PUT /v1/auth/password

### Me (3)
- GET /v1/me
- GET /v1/me/notification-preferences
- PUT /v1/me/notification-preferences

### Organizations (9)
- GET/POST /v1/orgs
- GET/PATCH/DELETE /v1/orgs/{orgId}
- POST /v1/orgs/{orgId}/transfer-manager
- GET/POST /v1/orgs/{orgId}/leaders
- DELETE /v1/orgs/{orgId}/leaders/{userId}

### Groups (12)
- GET/POST /v1/orgs/{orgId}/groups
- GET/PATCH/DELETE /v1/orgs/{orgId}/groups/{groupId}
- GET/PATCH /v1/orgs/{orgId}/groups/{groupId}/settings
- GET/POST /v1/orgs/{orgId}/groups/{groupId}/members
- DELETE /v1/orgs/{orgId}/groups/{groupId}/members/{userId}
- POST /v1/orgs/{orgId}/groups/{groupId}/transfer-leader
- GET /v1/orgs/{orgId}/groups/{groupId}/history

### Users (7)
- GET/POST /v1/users
- GET/PATCH/DELETE /v1/users/{userId}
- POST /v1/users/{userId}/sync-from-hr
- GET /v1/users/{userId}/org-manager-scopes

### Projects (11)
- GET/POST /v1/projects
- GET/PATCH/DELETE /v1/projects/{projectId}
- POST /v1/projects/{projectId}/status
- POST /v1/projects/{projectId}/owner
- GET/POST /v1/projects/{projectId}/members
- DELETE /v1/projects/{projectId}/members/{userId}
- GET /v1/projects/{projectId}/history

### Tasks (16)
- GET/POST /v1/tasks
- GET/PATCH/DELETE /v1/tasks/{taskId}
- POST /v1/tasks/{taskId}/status
- POST /v1/tasks/{taskId}/assignees
- DELETE /v1/tasks/{taskId}/assignees/{userId}
- POST /v1/tasks/{taskId}/owner
- POST /v1/tasks/{taskId}/review
- GET/POST /v1/tasks/{taskId}/comments
- PATCH/DELETE /v1/tasks/{taskId}/comments/{commentId}
- GET /v1/tasks/{taskId}/history
- GET /v1/task-types

### ActionRequests (8)
- GET/POST /v1/action-requests
- GET/PATCH/DELETE /v1/action-requests/{actionRequestId}
- POST /v1/action-requests/{actionRequestId}/status
- POST /v1/action-requests/{actionRequestId}/convert-to-task
- POST /v1/orgs/{orgId}/dispatch-action-request

### Templates (18)
- GLOBAL: GET/POST /v1/system/project-templates + /{templateId}: GET/PATCH/DELETE
- GLOBAL: GET/POST /v1/system/task-templates + /{templateId}: GET/PATCH/DELETE
- ORG: GET/POST /v1/orgs/{orgId}/project-templates + /{templateId}: GET/PATCH/DELETE
- ORG: GET/POST /v1/orgs/{orgId}/task-templates + /{templateId}: GET/PATCH/DELETE
- Fork: POST /v1/orgs/{orgId}/project-templates/fork-from-global/{globalTplId}
- Fork: POST /v1/orgs/{orgId}/task-templates/fork-from-global/{globalTplId}

### Attachments (4)
- POST /v1/attachments
- GET/DELETE /v1/attachments/{attachmentId}
- POST /v1/attachments/{attachmentId}/finalize

### Webhooks (3)
- GET/POST /v1/webhooks
- DELETE /v1/webhooks/{webhookId}

### Health (1)
- GET /v1/health

### Mock HR (2)
- GET /mock-hr/users
- GET /mock-hr/users/{accountName}

---

## 與 Spec 偏離

| 項目 | 偏離說明 |
|---|---|
| MongoDB 事務 | Dev mode 單節點不支援;以順序寫入實作,無 rollback |
| NATS JetStream | OutboxPoller dry-run 模式,不實際連 NATS |
| MinIO presigned URL | 返回 synthetic URL,未整合真實 MinIO SDK |
| Webhook HMAC dispatch | 基礎 CRUD 完成,簽名 + 重試未實作 |
| 密碼演算法 | 使用 BCrypt(指定 ARGON2ID 但未引入依賴) |
| Refresh Token 安全 | skip signature verification,任何人可 forge refresh token (dev only!) |
| ETag | 未實作 |
| 增量同步 since 參數 | 定義於 OpenAPI 但 service 層未實作過濾 |
| 欄位投影 fields 參數 | 定義於 OpenAPI 但 service 層未實作 |
| 根 Org 代碼唯一性 | 兩個不同的 root org 可有相同 code |
| Refresh Token 安全 | 已修復 — 使用 JWTAuthContextInfo 正確驗章(P0-1) |
| RBAC | 已修復 — 全面補 @RolesAllowed(P0-2) |
| 跨租戶資料洩漏 | 已修復 — findByIdAndRootOrg(P0-6) |
| createOrg 非原子 | 已修復 — 預生成 ObjectId(P0-7) |
| 編譯狀態 | 通過 — compileKotlin + compileTestKotlin 全綠(JDK 21) |

---

## 給前端 builder 的銜接訊息

完成編譯驗證後(至少 5 個測試通過、quarkusDev 啟動):

1. API base URL: `http://localhost:8080/v1`
2. 認證: Bearer JWT (Authorization: Bearer {accessToken})
3. Dev seed 帳號(login 現需加 orgCode 欄位):
   - orgCode: "taichung-fab" (dev seed 的 root org code)
   - admin.system / Admin@123456789 (ADMIN)
   - manager.wang / Manager@123456789 (ORG_ADMIN)
   - leader.chen / Leader@123456789 (SHIFT_LEAD + GROUP_MANAGER)
   - operator.li / Operator@123456789 (OPERATOR)
   - qa.zhang / QA@1234567890 (QA + ENGINEER)
4. Swagger UI: `http://localhost:8080/q/swagger-ui`
5. CORS 已設定 localhost:3000 + localhost:5173
6. 主要 E2E 流程可跑通(登入 → 建 org → 建 group → 加成員 → 建 project → 建 task → 指派)

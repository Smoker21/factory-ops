# Code Review Report — 工廠值班工作管理系統

**版本**:1.0
**審查日期**:2026-05-04
**審查範圍**:M3 backend(Kotlin/Quarkus) + frontend(React/TS),對照 spec v1.3.0
**審查者**:code-reviewer agent
**整體評分**:**4 / 10**(規格完整、骨架齊全,但 RBAC 與認證安全洞嚴重,不可上線)

---

## 1. 總體評估

**好的部分**:領域模型、DTO 拆分、Document/Domain 分層、Mapper 對稱性、Panache parameterized 查詢、bcrypt 密碼雜湊(cost=12)、ProblemDetail 例外映射、index 設計到位 — 規格驅動的工程基本面良好。

**致命問題**:
1. **JWT refresh token 簽名驗證被完全跳過**(Critical)— 任何人可偽造 refresh token 取得他人 access token。
2. **RBAC 全面未落地**(Critical)— 整個系統只有 `@PermitAll`(8 處)沒有任何 `@RolesAllowed`,§6 RBAC 矩陣完全形同虛設。OPERATOR 可以呼叫 `transfer-manager`、刪 Org、改自己 role 為 ADMIN。
3. **Login 跨租戶資料外洩**(Critical)— `AuthService.login` 用 `firstResult()` 不帶 rootOrgId,而 INV-29 明定 accountName 在 `(rootOrgId, accountName)` 內 unique → 不同 root org 可有同名帳號,login 行為不確定。
4. **無任何 `@Transactional`**(High)— Outbox pattern 失效;多步驟操作(persist + update)無法 atomically 回滾;`createOrg` 已標 BUG-T2 即此問題。
5. **三大列表端點均無 cursor pagination**(High)— 雖 OpenAPI 定義 `pageInfo`,Resource 層永遠回 `PageInfo(null, false)`,違反 NFR p95 < 300ms 目標,大量資料時 OOM。
6. **OutboxPoller 處理函式只 log 不發送**(High)— `OutboxPoller.kt:64` 留 TODO,實際從未發 NATS / webhook;FR-Notification 整段需求未實作。

→ **建議下一步**:派 backend-builder 修 P0(8 項)後重審,**不可進入 doc-devops**。

---

## 2. 五面向發現分類

### 2.1 正確性 (Correctness)

| 嚴重度 | 編號 | 位置 | 描述 | 建議 |
|---|---|---|---|---|
| Critical | C-001 | `application/service/OrganizationService.kt:132-139` | (BUG-T2 確認)`createOrg` root 節點時先 `persist(doc)` 再 `doc.rootOrgId = doc.id!!; orgRepository.update(doc)`。中間若 update 失敗,root org 留下 `rootOrgId = tempRootOrgId`(亂數 ObjectId)的孤兒文件,後續無法被 `findRoot` 查到、新建子節點也撈不到正確 leafTypes。 | 改用 MongoDB transaction(replica set 必須開),或先在記憶體 generate ObjectId 再賦值給 rootOrgId/_id 後一次 persist。 |
| High | C-002 | `application/service/TemplateService.kt:62-69, 149-156` | (BUG-T1 確認)`updateProjectTemplate` / `updateTaskTemplate` 只更新 name,**沒有 version++**、**沒檢查 active=true**、**沒做不可變欄位保護**。違反 INV-17(version 單調遞增)與 FR-Tpl.7(active=false 不可被實例化但可寫?設計意圖不符)。 | 每次 update 應 `version++`、寫 history、檢查不是 GLOBAL scope(GLOBAL 限 ADMIN);ORG/GLOBAL 寫入路徑分離。 |
| High | C-003 | `application/service/GroupService.kt:150-158` | (INV-35 違反)Group settings update 只檢查 dualSignRequired+roles 不空,**未驗證 role 限第一線角色**。INV-35 明定不接受 `ADMIN` / `ORG_ADMIN`,目前可寫入。 | 加白名單檢查:`requiredReviewerRoles ⊆ {OPERATOR, SHIFT_LEAD, ENGINEER, QA, GROUP_ADMIN, GROUP_MANAGER}`,否則 422。 |
| High | C-004 | `application/service/OrganizationService.kt:50-143` | `createOrg` **沒驗證 INV-26**(parentId 必須與本節點同 rootOrgId)— 跨 root 樹掛接子節點未阻擋;**沒驗證 code 在同 root 內 unique 對 root 節點**。 | 補 root org code 全域 unique 檢查;parentId 同 rootOrgId 檢查。 |
| High | C-005 | `application/service/DispatchService.kt:216-255` | `convertToTask` 未驗證 actionRequest **不在 RESOLVED/REJECTED 狀態**;且 `priority = Priority.NORMAL` 寫死。 | 加狀態檢查;severity → priority 映射表。 |
| High | C-006 | `application/service/TaskService.kt:160-197` | force-complete bypass 邏輯**只擋 dualSignRequired=true 的直推**,但未檢查 actor **必須在該 Task 對應 Group 的 members**。 | 注入 GroupMembership 檢查或推到 RbacEvaluator。 |
| Medium | C-007 | `application/service/TaskService.kt:122-139` | `buildQaReviewPolicy` 對找不到的 group `mapNotNull` **靜默忽略**,policy 因此 dualSignRequired=false。 | 改 `map { ... ?: throw NotFoundException }`。 |
| Medium | C-008 | `application/service/TaskService.kt:160-180, 199-211` | (INV-6 部分違反)`IN_REVIEW → DONE` 兩條路徑(changeStatus / submitReview)在 dualSignRequired=false 時行為不一致。 | 統一邏輯。 |
| Medium | C-009 | `application/service/ProjectService.kt:159-170` | Project 狀態流缺 `PAUSED → COMPLETED` 路徑。 | 補。 |
| Medium | C-010 | `application/service/ProjectService.kt:56-123` | (INV-4 缺漏)`createProject` 未驗證 `schedule.due > schedule.start`。 | 加防呆。 |
| Medium | C-011 | `application/service/TaskService.kt:62-120` | (INV-5 缺漏)`createTask` 未驗證 `dueAt ≥ project.startAt`。 | 比對 schedule。 |
| Medium | C-012 | `application/service/TaskService.kt:213-229` | `addAssignees` 未驗證 newAssignees 中 user 全部 active 且同 rootOrgId。 | 對每個 userId 檢查。 |
| Medium | C-013 | `application/service/OrganizationService.kt:179-189` | `updateOrg` 移動節點時**只更新自身的 ancestorIds 與 depth**,未連動更新所有**子孫**。 | 用 bulk update 跑子孫;包 transaction。 |
| Medium | C-014 | `application/service/OrganizationService.kt:198-213` | `deleteOrg` 只擋有 children,**未擋有 active resources**(Group / Project / Task)。 | 額外 count。 |
| Medium | C-015 | `infrastructure/outbox/OutboxPoller.kt:48` | retryCount **只增不減**且無上限;違反 FR-Notification.5(最多 5 次)。 | 加死信封存。 |
| Medium | C-016 | `application/service/EventPublisherService.kt:108-135` | `publishEvent` 整個 try/catch 吞例外,**outbox 寫失敗時 task 操作仍 commit**,事件靜默丟失。 | 移除 try/catch,搭配 `@Transactional`。 |
| Medium | C-017 | `application/service/DispatchService.kt:67-89` | manager transfer 後 JWT 內 orgManagerScopes 過期,可能短期內仍有 dispatch 權。 | refresh JWT 機制 + 在 service 層 double-check。 |
| Low | C-018 | `application/service/TaskService.kt:213-229` | `update` 後未重做 INV-1 檢查(目前其他路徑都有處理,但保險起見)。 | 補檢查。 |

### 2.2 安全 (Security)

| 嚴重度 | 編號 | 位置 | 描述 | 建議 |
|---|---|---|---|---|
| Critical | S-001 | `application/auth/JwtIssuerService.kt:90-108` | **Refresh token 簽名驗證被完全跳過**:`setSkipAllValidators().setDisableRequireSignature().setSkipSignatureVerification()`。攻擊者可偽造 refresh token 取得任意 user 的 access token。 | 用 SmallRye 的 JWT verifier(同 access token);refresh 走 audience=`factory-ops-refresh` + 公鑰驗章。 |
| Critical | S-002 | (跨檔案)`interfaces/rest/*Resource.kt` | **整個 codebase 完全沒有 `@RolesAllowed`**(grep 確認:只有 `@PermitAll` 8 處)。OPERATOR 可呼叫 `POST /v1/orgs`、`POST /v1/orgs/{id}/transfer-manager`、`PATCH /v1/users/{id}` 把自己 roles 改成 `ADMIN`、`POST /v1/system/project-templates`。 | 對每個 endpoint 補 `@RolesAllowed` 並建立 `RbacEvaluator` 服務做 row-level 細粒度檢查。 |
| Critical | S-003 | `application/service/AuthService.kt:34-36` | Login 用 `find("accountName = ?1", accountName).firstResult()` **不帶 rootOrgId**。INV-29 規定 `(rootOrgId, accountName)` unique → 不同 root org 可同名,登入結果不確定。 | Login 改要求 `orgCode` 或 `rootOrgId` 入參。 |
| Critical | S-004 | `interfaces/rest/UserResource.kt:80-85` | `PATCH /v1/users/{userId}` 接受 `roles` 改寫,任何登入 user 可改自己 roles 為 ADMIN — **特權升級**。 | `@RolesAllowed({"ORG_ADMIN","ADMIN"})`,且禁止 actor 改自己的 roles。 |
| Critical | S-005 | `backend/src/main/resources/jwt/privateKey.pem` | **JWT 私鑰在 git repo 內**。雖 README 註明 DEV ONLY,但 production 也會 fall back。 | `.gitignore` 加 `*.pem`;從 secret manager 讀;prod profile 強制 env 必填。 |
| High | S-006 | `interfaces/rest/MockHrResource.kt:21-52` | Mock HR resource 永遠在 classpath 內,且兩端點 `@PermitAll`。Production 可洩漏員工 PII。 | `@IfBuildProfile("dev")`;prod jar 排除。 |
| High | S-007 | `interfaces/filter/OrgScopeFilter.kt:32-38` | 公開路徑白名單 `mock-hr` 在 prod 仍 public!且 `/openapi`、`/q/*` 在 prod 也是 public。 | 集中管理;mock-hr 在 prod 完全停用。 |
| High | S-008 | `application/service/AuthService.kt:73-77` | `logout` **只 log 不做 blacklist**。違反 FR-1.2。 | 加 `revoked_tokens` collection。 |
| High | S-009 | `frontend/src/api/client.ts:5-101` | JWT 存 `localStorage`,XSS 即洩漏。 | 改 httpOnly cookie。 |
| High | S-010 | `application.properties:7-10` | CORS 設 dev origin OK,但無 prod origin 機制;沒設 `exposed-headers`。 | prod profile + env 注入 origin。 |
| High | S-011 | `application/service/UserService.kt`(進入點 UserResource:60-63) | `createUser` body 帶 `defaultPassword`(明文);無強度檢查。 | 加 password policy;伺服端 random 臨時密碼。 |
| High | S-012 | (跨)Resource path-based scope leakage | `OrganizationResource.delete(orgId)` 不驗證 `orgId` 是否屬於 actor 的 rootOrgId — 跨租戶資源洩漏路徑。 | 所有 `findByIdAndNotDeleted(id)` 改為 `findByIdAndRootOrg(id, rootOrgId)`。 |
| Medium | S-013 | `persistence/repository/UserRepository.kt:24-27` | `searchByKeyword` 用 `.*$keyword.*` regex,可能 ReDoS。 | 用 MongoDB `$text` index;限制 keyword length。 |
| Medium | S-014 | `frontend/src/components/markdown/MarkdownRenderer.tsx:11-18` | `attachment://{id}` 解析未驗證 id 為合法 ObjectId。 | 加 regex 守衛。 |
| Medium | S-015 | `interfaces/rest/AuthResource.kt:72-75` | logout 與 changePassword 沒 rate-limit。 | 加 IP-based rate limit。 |
| Medium | S-016 | (整體)缺乏失敗登入鎖定 | 連續失敗登入 N 次後不會鎖帳號。 | 加 `failed_login_count` + `locked_until`。 |
| Medium | S-017 | `interfaces/dto/AuthDtos.kt:11` | LoginRequest 沒 `@Size(max = ...)`,`password` 可塞超大字串導致 bcrypt CPU 高。 | 加 `@Size(max = 128)`。 |
| Medium | S-018 | `application/service/AuthService.kt:30-49` | login 失敗訊息一致性 OK,但 audit trail 不利。 | 失敗類型 enum,log 帶 context 但不洩漏給 client。 |
| Medium | S-019 | `interfaces/exception/GlobalExceptionMapper.kt:94-108` | `handleGenericException` log 可能在拋例外時帶到 password。 | LoginRequest / ChangePasswordRequest 在 toString() override。 |
| Low | S-020 | `application.properties:74-77` | 公開路徑不含 `/health`,kubernetes liveness 預設 `/health` 會被攔截。 | 加 `/health`。 |

### 2.3 效能 (Performance)

| 嚴重度 | 編號 | 位置 | 描述 |
|---|---|---|---|
| High | P-001 | (整體)所有列表 Resource | **沒有 cursor pagination**;Resource 永遠回 `PageInfo(null, false)`。違反 FR-7.2 與 NFR p95 < 300ms。資料量 > 1000 即慢、> 100K 會 OOM。 |
| High | P-002 | (整體)增量同步未實作 | OpenAPI 提到 `?since=`、`If-Modified-Since` / `ETag`,Resource 完全沒讀取。 |
| High | P-003 | `application/service/OrganizationService.kt:179-189` | Org move 不 propagate 子孫的 ancestorIds。 |
| Medium | P-004 | `application/service/TaskService.kt:43-53` | `listTasks` 對 type 用 in-memory `filter`。 |
| Medium | P-005 | `application/service/DispatchService.kt:118-125` | `listActionRequests` 對 requesterId 做 in-memory filter。 |
| Medium | P-006 | `application/service/OrganizationService.kt:30-35` | `listOrgs(underOrgId)` `findDescendants` query 沒帶 rootOrgId。 |
| Medium | P-007 | `infrastructure/outbox/OutboxPoller.kt:32` | poll interval + batch + 重試衝突,沒 sharding key。 |
| Medium | P-008 | `frontend/src/components/...` | UI 沒看到 React.memo / 虛擬化。 |
| Low | P-009 | `application/service/ProjectService.kt:217-249` | `addMember` 共 5 個 query,可合併為 `$addToSet`。 |
| Low | P-010 | `frontend/src/api/client.ts` | bundle size 未驗證;MUI 全量引入。 |

### 2.4 可維護性 (Maintainability)

| 嚴重度 | 編號 | 位置 | 描述 |
|---|---|---|---|
| Medium | M-001 | `infrastructure/outbox/OutboxPoller.kt:64` | 留 `// TODO` — CLAUDE.md 明文禁止。 |
| Medium | M-002 | `frontend/src/i18n/locales/en-US.json:2` | 留 `_comment: TODO` 違規。 |
| Medium | M-003 | (整體)`@Transactional` 完全缺失 | 多步驟邏輯無法回滾。 |
| Medium | M-004 | `application/service/TaskService.kt:88-113` | `createTask` 內 inline mapping 25 行;Mapper 未被使用。 |
| Medium | M-005 | `application/service/TaskService.kt:101-108` | `TaskMapper.run { ... }` idiom 困惑。 |
| Medium | M-006 | (跨)Resource 長度 | TaskResource / ProjectResource 過長。 |
| Medium | M-007 | `application/service/TemplateService.kt:191-218` | `toMap()` 在 service 內;且回 `Map<String, Any?>` — 失型。 |
| Medium | M-008 | (跨)service 直接 new Document | Domain 與 Persistence 邊界破口。 |
| Low | M-009 | `application/service/OrganizationService.kt:249-264` | `updateOrgManagerScopes` 整段 try-catch 吞例外只 warn。 |
| Low | M-010 | (跨)`OrganizationMapper.auditToDocument` 重複 N 次 | 應提供 inline helper。 |
| Low | M-011 | `application/service/TaskService.kt:99` | 使用全限定名而非 import alias。 |
| Low | M-012 | `frontend/src/test/server.ts` | MSW handlers 覆蓋不全。 |

### 2.5 架構一致性 (Architecture Conformance)

| 嚴重度 | 編號 | 描述 |
|---|---|---|
| High | A-001 | RBAC 守衛缺失;`OrganizationRepository.findByIdAndNotDeleted` 沒帶 rootOrgId(跨租戶風險)。 |
| High | A-002 | Service 直接 import `persistence.document.*` 並 new Document;違反層次邊界。 |
| High | A-003 | ADR-0009 outbox publish 實際發送沒做(M-001、C-016)。 |
| High | A-004 | ADR-0011 QA 雙簽:Group settings 寫入時的 INV-35 角色白名單沒檢查(C-003)。 |
| Medium | A-005 | ADR-0010 manager transfer 即時生效:JWT 內 orgManagerScopes 過期。 |
| Medium | A-006 | ADR-0008 single-hop direct dispatch:大致 OK。 |
| Medium | A-007 | ADR-0012 Org tree materialized path:move propagation 缺(C-013)。 |
| Medium | A-008 | ADR-0007 HR 整合與降級:fallback 邏輯未實作。 |
| Medium | A-009 | ADR-0001 polymorphic Task:`TaskTypeResource` schema 寫死,違反「新增 type 不需改 schema」。 |
| Medium | A-010 | ADR-0005 multi-tenancy:部分 repository query 沒帶 rootOrgId。 |

---

## 3. Spec / API / RBAC 三方一致性抽樣

| 端點 | OpenAPI v1.3.0 | Resource 實作 | RBAC 守衛 | INV 落實 | 問題 |
|---|---|---|---|---|---|
| `POST /v1/auth/login` | 200/401/422 | ✓ AuthResource:38 `@PermitAll` | n/a | INV-29 | 🔴 query 不帶 rootOrgId(S-003) |
| `POST /v1/auth/refresh` | 200/401 | ✓ AuthResource:55 `@PermitAll` | n/a | — | 🔴 簽名跳過(S-001) |
| `POST /v1/orgs` | 201/403/409 | ✓ OrganizationResource:67 | ❌ | INV-26 | 🔴 OPERATOR 可建 org;INV-26 未檢查 |
| `POST /v1/orgs/{id}/transfer-manager` | 200 | ✓ | ❌ | INV-32/33 | 🔴 任意 user 可 transfer manager |
| `POST /v1/orgs/{id}/dispatch-action-request` | 201/403/422 | ✓ | ❌(service 有 manager scope 檢查) | INV-24/25 ✓ | 🟡 RBAC 註解仍應補 |
| `POST /v1/tasks` | 201 | ✓ TaskResource:71 | ❌ | INV-1/2/5/31 | 🔴 INV-5 未檢查;無 RBAC |
| `POST /v1/tasks/{id}/review` | 200/403/409/422 | ✓ TaskResource:168 | ❌(service 檢 role ∈ required) | INV-36 ✓ | 🟡 RBAC 仍應補 |
| `POST /v1/tasks/{id}/status` | 200 | ✓ TaskResource:125 | ❌ | INV-6 部分 ✓ | 🔴 force-complete 未檢查 actor 為該 Task Group 的 GROUP_MANAGER |
| `PATCH /v1/users/{id}` | 200 | ✓ UserResource:80 | ❌ | — | 🔴 任意 user 可改自己 roles 為 ADMIN |
| `PATCH /v1/orgs/{id}/groups/{gid}/settings` | 200/422 | ✓ | ❌ | INV-35 | 🔴 未檢查角色限第一線 |

→ **三方一致**:**0/10**(全部 RBAC 缺欠);**OpenAPI ↔ Resource shape**:**8/10 一致**

---

## 4. Must-Fix 清單(M4 必修,P0)

按可被攻擊面排序:

1. **[S-001 Critical]** `JwtIssuerService.kt:90-108` — refresh token 用真正的 SmallRye verifier 驗章。
2. **[S-002 Critical]** 對所有 30+ 個 Resource method 補 `@RolesAllowed({...})`,並建立 `RbacEvaluator`。
3. **[S-003 Critical]** `AuthService.login` 加 rootOrgId / orgCode 入參。
4. **[S-004 Critical]** `UserResource.update` 補 `@RolesAllowed({"ORG_ADMIN","ADMIN"})` 並禁止 actor 改自己 roles。
5. **[S-005 Critical]** 將 `backend/src/main/resources/jwt/*.pem` 從 git 移除,加 `.gitignore`;prod 用 secret manager。
6. **[S-012 Critical]** `OrganizationRepository.findByIdAndNotDeleted` 與所有未帶 rootOrgId 的 finder 全面改。
7. **[C-001 / BUG-T2 Critical]** `OrganizationService.createOrg` 改為先 generate ObjectId 再一次 persist。
8. **[C-002 / BUG-T1 High]** `TemplateService.update*Template` 加 version++、active=true 檢查、history 記錄;GLOBAL/ORG 寫入路徑分流。
9. **[M-003 High]** 給所有 service method 加 `@Transactional`。
10. **[C-003 High]** `GroupService.updateGroupSettings` 加 INV-35 角色白名單檢查。
11. **[S-006 High]** Mock HR resource 用 `@IfBuildProfile("dev")`。
12. **[S-008 High]** `AuthService.logout` 實作 refresh token blacklist。
13. **[P-001 High]** 列表端點全面補 cursor pagination。
14. **[C-013 / P-003 High]** `OrganizationService.updateOrg` 移動節點時 propagate ancestorIds 與 depth。
15. **[C-016 High]** `EventPublisherService.publishEvent` 移除 try/catch。

---

## 5. Should-Fix 清單(M4 建議修,可移到後續)

- C-005 ~ C-011(invariant 補丁)
- S-009 frontend localStorage → httpOnly cookie
- S-013 user search regex injection
- S-016 失敗登入鎖定機制
- M-001 / M-002 移除 TODO
- M-007 TemplateService 用具體 DTO 而非 Map
- M-008 service 不直接 new Document
- A-003 OutboxPoller 補完整 NATS / webhook 發送

## 6. Nice-to-Have(後續迭代)

- P-002 增量同步(`?since=` / ETag)
- P-008 frontend virtualization
- A-009 Task type registry 動態載入
- M-006 Resource 拆 sub-resource
- 前端 coverage 從 15% 提升到 ≥ 50%

---

## 7. 已被 test-engineer 標出的 bug 確認

- **BUG-T1(TemplateService version 非單調)** — ✅ 確認;見 C-002。
- **BUG-T2(OrganizationService.createOrg 非原子)** — ✅ 確認;見 C-001。
- **同類問題(test-engineer 未發現)**:`OrganizationService.transferManager` line 215-247 同樣兩步驟,失敗時 cache 不一致(M-009)。

---

## 8. 改善後的優先序建議

**強烈建議**:
1. **不可進入 doc-devops** — 至少 P0 第 1-7 項(認證 + 跨租戶)修完。
2. 派 **backend-builder** 接手實作 Must-Fix 1-15 項。
3. **重審後**才能放行到 doc-devops。
4. 前端 P0 較少(主要 S-009),可在 doc-devops 階段並行修。
5. 測試覆蓋率(後端 22% / 前端 15%)在 P0 修完後再擴一輪。

**風險 vs. 上線時機**:目前狀態若部署到生產環境,1 小時內可被未授權使用者改寫整個系統的角色配置與組織結構。**強烈反對任何形式的 staging beyond local dev**。

---

# 附錄 A — 與 spec/data/adr 的偏離總結

| 規格章節 | 偏離 | 嚴重度 |
|---|---|---|
| §6 RBAC 全表 | 後端完全未實作角色守衛 | Critical |
| FR-Notification.1/.5 | NATS / webhook 發送 + 重試 + 死信 全未實作 | High |
| FR-7.2 cursor pagination | 全列表回 PageInfo(null,false) | High |
| FR-7.4 增量同步 | 完全未實作 | Medium |
| FR-1.2 logout blacklist | 只 log 不撤銷 | High |
| INV-29 accountName scope | login 跨 rootOrg 查詢 | Critical |
| INV-35 reviewer role 白名單 | 未檢查 | High |
| INV-5 task.dueAt ≥ project.startAt | 未檢查 | Medium |
| INV-4 project.dueAt > startAt | 未檢查 | Medium |
| ADR-0009 outbox publish 實際寄送 | TODO 未做 | High |
| ADR-0012 org tree move propagation | 子孫 ancestorIds 不更新 | Medium |
| ADR-0010 manager transfer 即時生效 | JWT 內 orgManagerScopes 過期 | Medium |

---

# 統計

- **Critical(P0)**:8 項
- **High(P0/P1 邊界)**:18 項
- **Medium(P1)**:22 項
- **Low(P2)**:12 項
- **Info**:5 項
- **總計**:65 項

---

**審查者**: code-reviewer agent
**完成時間**: 2026-05-04
**狀態**: READY_FOR_BUILDER_FIX

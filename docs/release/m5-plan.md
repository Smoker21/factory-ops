# 里程碑 5(M5):Hardening + Spec Lock-in

**版本目標**:`v1.0.0-M5`
**主題**:收 P1 Backlog 安全 / 一致性債 + 拍板衍生 Open Questions Q-18 ~ Q-24
**啟動條件**:M4 已驗收(2026-05-04 完成),BDD 20/20 綠,CI 全綠
**預估規模**:中(無新功能,純內功;7 ~ 10 個工作天視 S-009 cookie 改造深度)

---

## 1. 動機

M4 結束後 STATUS.md 留下:

- **22 條 P1 Backlog**(code-reviewer 在 M4 點名)
- **7 個衍生 Open Questions**(Q-18 ~ Q-24,本期已先以「預設答案」內隱於 spec)

不先收掉的代價:

1. 每個新功能都要繞過或踩到這些洞(舉例:做 Daily Work Board 用到 reviewer 角色,結果 C-007 mapNotNull 靜默忽略找不到的 group,UI 看到空白卻沒錯誤訊息)
2. Q-18 ~ Q-24 愈晚拍板,愈多程式碼依賴「預設行為」,反轉成本愈高
3. 安全項(S-009 / S-016 / S-015 / S-011)目前是已知漏洞,壓著上線 = 已知風險

> **本里程碑刻意不加任何使用者可見的新功能**,功能延伸放 M6+。

---

## 2. 範圍(scope freeze)

### 2.1 In-scope(本期一定要收)

| 分類 | 條目 | 來源 |
|---|---|---|
| **Spec lock-in** | Q-18 / Q-19 / Q-20 / Q-21 / Q-22 / Q-23 / Q-24 | `docs/spec/STATUS.md` 衍生 |
| **Security P1** | S-009、S-010、S-011、S-013、S-015、S-016、S-017 | M4 review |
| **Domain invariants P1** | C-005、C-006、C-007、C-008、C-009、C-010、C-011、C-012、C-014、C-015 | M4 review |

合計 **24 條**(7 spec + 7 security + 10 invariant)。

### 2.2 已確認**先前已完成、需從 STATUS 移除**

| 條目 | 證據 |
|---|---|
| **P-001** Cursor pagination | 6 個 service 都已實作 `nextCursor = if (hasMore) items.lastOrNull()?.id?.toHexString() else null`(`UserService:56`、`ProjectService:73`、`OrganizationService:64`、`DispatchService:147`、`GroupService:72`、`TaskService:77`)。STATUS 文字「目前回傳 null cursor」過時。 |
| **S-008-ext** revoked_tokens TTL | `backend/src/main/resources/db/init-indexes.js:711-722` 已建 `idx_revoked_token_ttl` `{ expireAfterSeconds: 0 }` over `expiresAt`。 |

→ M5.6 release 階段**從 STATUS.md 刪除**這兩條,並補進 CHANGELOG `[Unreleased]` 的「Status hygiene」段。

### 2.3 Out-of-scope(明確 defer 到 M6+)

| 條目 | 原因 |
|---|---|
| **P-002** `?since=` / ETag / If-Modified-Since | 屬 NFR p95 改善,不是 correctness;且需要全列表端點協同改 schema(回傳 `lastModifiedAt`),工作量大,單獨開一棒值得 |
| **C-017** transfer-manager 後 JWT scope 過期 | P2,影響面僅 Org admin |
| **C-018** addAssignees 後 INV-1 owner 重檢 | P2,場景罕見 |
| **P-004 ~ P-010** | P2 效能項 |
| **S-014 / S-018 / S-019 / S-020** | P2 安全雜項 |
| **M-001 ~ M-007** | P2 雜項清理 |
| Notification / Webhook 實作 | M6 候選主題 |
| Daily Work Board UI 完成 | M6 候選主題 |

---

## 3. Sub-phase 切分(序列執行)

```
M5.1 (spec)  →  M5.2 (data prep, 一棒收完)  →  M5.3 (security)  →  M5.4 (invariants)  →  M5.5 (frontend)  →  M5.6 (compact + release)
```

> 不採並行:M5.3 / M5.4 都改 backend service 層,讀寫邊界重疊;雖可細切但成本大於收益。
>
> **Agent 邊界**(對應 CLAUDE.md § Agent 協作協定):
> - `domain/` Kotlin data class、`docs/data/`(schema / indexes / migrations)— **mongodb-modeler 專屬寫**
> - `application/`、`interfaces/`、`persistence/`(除 document 結構)— quarkus-backend-builder
> - 因此 M5 把所有 domain / data 變動**集中在 M5.2 一棒**,由 mongodb-modeler 一次拍板;後續 M5.3 / M5.4 純消費已就位的欄位 / document。
> - 使用者拍板:M5.0/M5.0a/M5.0b 拆分 → **合一棒**(2026-05-07 Q4 答覆 B);好處是省一次 handoff,代價是 M5.2 內部要列清所有 data 變動。

### 3.1 M5.1 — Spec v1.4 lock-in

**負責 agent**:`spec-architect`
**接手起點**:`docs/spec/STATUS.md` § Open Questions Q-18~Q-24
**讀寫邊界**:僅 `docs/spec/`、`docs/adr/`

**範圍**:把使用者已拍板的七題答覆寫進 spec / ADR(Q-18~Q-24 全部已於 2026-05-07 規劃會議拍板,如下表)。

> **Q-21 / Q-23 主路徑驗證(2026-05-07,主 agent 規劃時做)**:
> - **Q-21**(清空):`TaskService.kt:339-341` 已實作 `if (decision == REJECTED) doc.qaReviews = emptyList()` → **與拍板一致,無 code change**
> - **Q-23**(OR):`TaskService.kt:353-354` 目前是 AND 實作 `requiredReviewerRoles.all { approvedRoles.contains(it) }` → **拍板採 OR,需 code change(列入 M5.4)**
> - **Q-23 衍生**「同 user 不可一次擔多角色」:使用者拍板**不擋**(Q5 B,2026-05-07);理由「工廠派工不是品保雙簽,只是工作確認」。**毋須加 INV,毋須 code change**

| 題目 | 拍板答覆(2026-05-07) | 動到的檔案 | 是否需 code change |
|---|---|---|---|
| Q-18 跨層 dispatch 範圍 | **任一上級 manager 皆可** | `requirements.md` §FR-Dispatch、`openapi.yaml` description | spec-architect 驗 dispatch authz code 是否已符合;若否登錄 M5.4 |
| Q-19 Manager 休假代理 | **不設 deputy 欄位,靠 transfer-manager**(代理人由 HR 服務的代理系統決定) | `requirements.md` §FR-Org、`ADR-0007 Amendment`(註明 HR 端負責 deputy) | 否 |
| Q-20 leaf reject 通知 originator | **只 emit event,不主動推 push** | `domain-model.md` sequence、`ADR-0009 Amendment` | 否(event 已從 outbox 發) |
| Q-21 Review reject 後既往 reviews | **清空** | `requirements.md` §FR-QA、`ADR-0011 Amendment` | 否(已驗證 code 一致) |
| Q-22 Group settings versioning | **不做(沿用 history append)** | `requirements.md` §FR-Group | 否 |
| Q-23 `requiredReviewerRoles` 語意 | **OR**(白名單,非必需清單)+ **不擋同人多角色簽**(Q5 B) | `ADR-0011 v1.4 Amendment` 重寫語意說明、`requirements.md` §INV / §FR-QA | **是**:M5.4 改 `TaskService.kt:353-354` `all` → `any`,並補測試 |
| Q-24 儲存層保留原始 offset | **不保留** | `requirements.md` §FR-Time | 否 |

**Q-23 OR 語意明文化(spec-architect 必須在 ADR-0011 Amendment 寫清楚)**:

- `requiredReviewerRoles` 是「**白名單**」:列出的角色才能簽,但不要求每一個都到齊
- `dualSignRequired = true` 仍要求 ≥ 2 筆 qaReviews,但角色組合任意(可同 role 兩筆,可不同 role 各一筆)
- **同一人可以同時填多個角色簽**(本系統為工廠派工確認,刻意輕量;Q5 B 拍板)
- audit log / qaReviews append-only history 完整保留(輕量但可追溯)

**Impact Matrix CT-N**:CT-8(Q-23 INV / 語意改寫)、CT-15(其餘文字補強)。無 schema migration。

---

### 3.2 M5.2 — Data model preparation(合棒)

**負責 agent**:`mongodb-modeler`
**動因**:M5.3 的 S-016 鎖定機制 + M5.4 的 C-015 outbox dead-letter 都要動 `domain/` 與 `docs/data/`,**依 CLAUDE.md § Agent 讀寫邊界,quarkus-backend-builder 嚴禁直接改**。本期把所有 data 層變動集中一棒(2026-05-07 Q4 拍板 B,合棒)。
**接手起點**:本檔 §3.2 + M5.1 spec-architect 若有衍生 domain 變動需求(目前已知無)

**範圍**(兩塊變動):

**Block A — User lockout 欄位**(serves S-016)
- `backend/src/main/kotlin/com/factoryops/domain/identity/User.kt`:加 `failedLoginCount: Int = 0`、`lockedUntil: Instant? = null`(均選擇性,以避免既存資料破壞)
- `docs/data/schema.md`:`users` collection 欄位表加上述兩欄、語義、預設值
- `docs/data/indexes.md`:評估是否需 `{ rootOrgId: 1, lockedUntil: 1 }` sparse 索引(用於背景解鎖 job;若不做 job → 不建索引)
- `docs/data/migrations/0001-user-lockout-fields.md`(**首次建立 `docs/data/migrations/` 目錄**),內容四項齊:變更摘要 / `schemaVersion` bump / 回填策略 / rollback
- `backend/src/main/resources/db/init-indexes.js`:若採用上述索引,加進去

**Block B — Outbox dead-letter document**(serves C-015)
- 既有 `domain/webhook/WebhookDeadLetter.kt` 是 webhook-specific;**outbox 通用 dead-letter** 需新 document `OutboxDeadLetter` 或擴充既有那條 — 由 mongodb-modeler 拍板兩擇一:
  - 選項 X:新增 `domain/event/OutboxDeadLetter.kt`,WebhookDeadLetter 維持不動
  - 選項 Y:把 `WebhookDeadLetter` rename / generalize 成 `OutboxDeadLetter`,migration script 一次性搬資料
- `docs/data/schema.md`:加入 / 更新 dead-letter collection 設計
- `docs/data/indexes.md`:對應索引(`{ rootOrgId: 1, createdAt: 1 }` 用於 ops 查詢)
- `docs/data/migrations/0002-outbox-dead-letter.md`:既存 `outbox_entries` 中 `retryCount > 10` 一次性搬移;若選 Y,連帶搬 webhook_dead_letter
- `backend/src/main/resources/db/init-indexes.js`:對應新索引

**Impact Matrix CT-N**:CT-1(aggregate 欄位 / 新 document)+ CT-5(若加索引)+ schema living doc 同步。

**驗收 checklist**:
- [ ] `User.kt` 兩個新欄位有預設值(避免破壞既存 doc)
- [ ] dead-letter document 設計就位(選項 X / Y 由 mongodb-modeler 在交付時說明)
- [ ] `schema.md` / `indexes.md` 反映新狀態(CLAUDE.md 要求 living doc)
- [ ] `docs/data/migrations/0001-*.md`、`0002-*.md` 完備(摘要 / schemaVersion / 回填 / rollback 四項齊)
- [ ] 既存資料搬移腳本可冪等
- [ ] `scripts/verify.sh` 綠

**產出**:
- `docs/spec/requirements.md` v1.4.0(in-place,bump header version)
- `docs/spec/openapi.yaml` v1.4.0(若 Q-20 加 notification 預留 webhook event 描述)
- `docs/adr/0009-event-distribution-nats-and-webhooks.md` 加 v1.4 Amendment(Q-20)
- `docs/adr/0011-group-settings-qa-dualsign.md` 加 v1.4 Amendment(Q-21、Q-23)
- `docs/spec/STATUS.md`:Q-18~Q-24 從 Open Questions 移除
- `git tag spec-v1.4`(由 doc-devops M5.6 統一打)

**驗收 checklist**:
- [ ] `docs/spec/STATUS.md` 已無 Q-18~Q-24 Open Questions
- [ ] requirements.md / openapi.yaml header version = 1.4.0
- [ ] 對應 ADR 有 v1.4 Amendment(若行為改變)
- [ ] `scripts/verify.sh` 綠(spec 階段不會碰程式碼,但 lint / typecheck 仍要綠)

---

### 3.3 M5.3 — Backend Security Hardening

**負責 agent**:`quarkus-backend-builder`(主)→ `test-engineer`(同棒補測)
**前置依賴**:**M5.2 已驗收**(User 新欄位 + dead-letter document 已就位)
**接手起點**:本檔 §3.3 + `docs/review/code-review-report.md` 對應條目
**讀寫邊界**:`backend/`(**除 `domain/` 外**可寫)、`backend/src/main/resources/`(application.properties)

**範圍**(7 條):

| ID | 動作 | Impact Matrix |
|---|---|---|
| **S-016** | 連續登入失敗鎖定(**消費 M5.2 提供的 `User.failedLoginCount` / `lockedUntil` 兩欄**):AuthService 進來先檢查鎖、登入失敗 increment、≥ 閾值(5)鎖定 N 分鐘(15);登入成功 reset。閾值 / 時長進 `application.properties`。 | CT-12(僅消費 M5.2 已建立的欄位;欄位 / migration 屬 M5.2) |
| **S-011** | 移除明文 default password:UserService.createUser 用 `SecureRandom` 產 16 字元臨時密碼,bcrypt 後存,plain text 只回傳一次給呼叫者(audit log 不記)。改密碼端點驗強度(長度、字元類別)。 | CT-2(回傳結構)/ CT-15 |
| **S-015** | logout / changePassword / login 加 rate-limit:用 Bucket4j(或 in-memory ConcurrentHashMap + window;選輕量者)per-IP + per-account 雙鍵。回 429 + RFC 7807。 | CT-2 / CT-12 |
| **S-013** | UserRepository.searchByKeyword:輸入 escape `Pattern.quote`,長度 cap(64),禁用 `*`/`?`(改為前綴匹配)。 | CT-15 |
| **S-017** | LoginRequest password 加 `@Size(max=128)` Bean Validation。 | CT-15 |
| **S-010** | application.properties:若 `quarkus.profile=prod` 且 `CORS_ORIGINS` 未設或為 `*` → 啟動時 fail-fast。 | CT-12 |
| **S-009** | **JWT 改 httpOnly cookie**(跨 backend / frontend,本棒做 backend 端,M5.5 做 frontend 端):access token 短壽(15 分);refresh 改 httpOnly + Secure + SameSite=Strict cookie;CSRF token via header(double-submit);AuthResource set-cookie / 清 cookie;前端 `client.ts` 重寫由 M5.5 完成。 | CT-2 / CT-12 / CT-11 |

**S-009 注意**:這是 M5 風險最高的一項,必須:
1. **先在 spec 章節 §FR-Auth 明寫 cookie / CSRF 模型**(回到 M5.1 補,由 spec-architect 加;若 M5.1 已關棒,本期 spec-architect re-engage)
2. 後端與前端同步改(M5.5 才接,但本棒先把 backend 端口改完並用 Postman 驗)
3. BDD e2e 改用瀏覽器 cookie 而非 localStorage(M5.5 改)

**產出**(**僅 application/interfaces/ 層 + config**;domain 欄位與 migration 屬 M5.2):
- `backend/src/main/kotlin/.../service/AuthService.kt`(+ lockout 邏輯、rate-limit、cookie set/clear)
- `backend/src/main/kotlin/.../resource/AuthResource.kt`(+ Set-Cookie、CSRF 驗證 filter)
- `backend/src/main/kotlin/.../service/UserService.kt`(+ password 強度、移除明文 default)
- `backend/src/main/kotlin/.../repository/UserRepository.kt`(+ regex escape;**只改查詢方法本體,不改 document 欄位**)
- `backend/src/main/kotlin/.../config/CorsConfig.kt`(+ prod fail-fast)
- `backend/src/main/resources/application.properties`(rate-limit / lockout 閾值、CORS prod 校驗開關)
- `backend/src/test/kotlin/.../*Test.kt`(test-engineer 補)

**驗收 checklist**:
- [ ] S-009 ~ S-017 全部修復(逐條對 review report 留證)
- [ ] `scripts/verify.sh --full` 綠
- [ ] 既有 BDD 20/20 仍綠(若 cookie 改造,可能需要 step def 微調)
- [ ] **未修改** `backend/.../domain/`(由 M5.2 完成)
- [ ] Impact Matrix CT-2 / CT-12 / CT-15 必動清單**全勾**

---

### 3.4 M5.4 — Domain Invariants + Q-23 OR Hardening

**負責 agent**:`quarkus-backend-builder` → `test-engineer`
**前置依賴**:**M5.2 已驗收**(dead-letter document + User 欄位就位)、M5.1 已驗收(Q-23 OR 已寫進 spec / ADR)
**接手起點**:本檔 §3.4 + `docs/review/code-review-report.md` 對應條目
**讀寫邊界**:`backend/application/`、`backend/interfaces/`(**不動 `domain/`**;若發現需加 `require {}` invariant,停下回報主 agent → 改派 mongodb-modeler 補)
**例外**(**已記錄,後續 milestone 沿用此例外處理**):本棒可改 `docs/spec/openapi.yaml` 的 **error response code 補強**(如 C-014 deleteOrg 加 409 response),純錯誤碼補強不算語意變更,毋須 spec-architect 介入。**若發現 path / schema / requestBody 需改 → 停下回報主 agent,改派 spec-architect**。

**範圍**(11 條 = 10 條 invariants + Q-23 OR 語意切換):

| ID | 修法摘要 |
|---|---|
| **C-005** | DispatchService.convertToTask:加 `require(ar.status == ACCEPTED)`,priority 映射顯式表(non-null,缺對應 → throw `IllegalStateException`)。 |
| **C-006** | TaskService.forceComplete:加 `groupMembershipRepository.exists(actor, task.groupId)` 檢查。 |
| **C-007** | TaskService.buildQaReviewPolicy:`mapNotNull` 改 `map { ... ?: throw NotFoundException("Group $id not found") }`。 |
| **C-008** | TaskService:IN_REVIEW → DONE 兩條路徑(自動 dualSign 滿足 vs 手動 mark done)抽 private 方法統一。 |
| **C-009** | ProjectService:狀態機加 `PAUSED → COMPLETED` 合法轉移(目前缺)。 |
| **C-010** | ProjectService.createProject:加 `require(due == null || due > start)`。 |
| **C-011** | TaskService.createTask:加 `require(dueAt == null || dueAt >= project.startAt)`。 |
| **C-012** | TaskService.addAssignees:批次查 user 全 active + 同 `rootOrgId`(reject 任一不符)。 |
| **C-014** | OrganizationService.deleteOrg:`projectRepository.countActiveByOrg` + `groupRepository.countByOrg` > 0 → 409。 |
| **C-015** | OutboxPoller(**消費 M5.2 拍板的 dead-letter document**):retryCount > 10 → move to dead-letter collection;exponential backoff 上限 1h。本棒只改 `OutboxPoller.kt` / `EventPublisherService.kt` / repository。 |
| **Q-23 OR** | TaskService 雙簽判定(`TaskService.kt:353-354`):從 `requiredReviewerRoles.all { approvedRoles.contains(it) }` 改成 OR 白名單語意 — `qaReviews.size >= 2(若 dualSignRequired) && qaReviews.all { it.reviewerRole in policy.requiredReviewerRoles }`(暫定;最終實作以 M5.1 ADR-0011 v1.4 Amendment 為準)。**不擋同人多角色**(Q5 B);測試案例同步補上「同人連簽兩 role 算過」+「兩個非白名單角色簽不算過」。 |

**Impact Matrix CT-N**:大宗 CT-8 / CT-15;C-009 屬 CT-4(狀態流);C-014 屬 CT-15 + 影響 OpenAPI 401 → 409。

**產出**:
- 對應 service 程式修改
- `backend/src/test/kotlin/.../*ServiceTest.kt` 每條補單元測試(test-engineer)
- 若 C-009 / C-014 影響 OpenAPI error response → 同步更新 `docs/spec/openapi.yaml`(由 quarkus-backend-builder 在自己 patch 範圍內就近改;**特例**:此處放寬 spec-architect 邊界,因為純 error code 補強而非語意改動)

**驗收 checklist**:
- [ ] 11 條全部修復 + 單元測試
- [ ] `scripts/verify.sh --full` 綠
- [ ] BDD 20/20 仍綠 + 補新 BDD case for C-009 PAUSED→COMPLETED、C-014 deleteOrg blocked、Q-23 OR 雙簽判定(白名單成立 / 白名單外不成立)
- [ ] Impact Matrix 對應 CT-N 全勾

---

### 3.5 M5.5 — Frontend Hardening

**負責 agent**:`react-frontend-builder` → `test-engineer`
**接手起點**:M5.3 cookie 改造後端已接好的 endpoint 規格

**範圍**(主要由 S-009 driving):

| 動作 | 細節 |
|---|---|
| `frontend/src/api/client.ts` 重寫 | 移除 localStorage access/refresh token;改用 `credentials: "include"` 跟 cookie 走;加 CSRF header(從 cookie `XSRF-TOKEN` 讀並 echo) |
| `LoginPage` / `LogoutFlow` | 配合新 endpoint;成功登入後不再儲存 token,`/me` 直接走 cookie |
| BDD step defs | `e2e/steps/auth.steps.ts` 改用瀏覽器內建 cookie jar(Playwright 預設支援);移除 localStorage 操作 |
| MSW handler | `frontend/src/mocks/handlers.ts` 配合 Set-Cookie response header |

**Impact Matrix CT-N**:CT-11(frontend 路由 / API 客戶端),CT-12(若 dev/prod 環境變數新增)。

**產出**:
- `frontend/src/api/client.ts`(重寫)
- `frontend/src/api/auth.ts`(login/logout response 結構變)
- `frontend/src/pages/LoginPage.tsx`、`AppLayout.tsx`(token 引用點)
- `frontend/src/__tests__/**`(test-engineer 補)
- `e2e/steps/auth.steps.ts`、`e2e/support/world.ts`(BDD)
- `frontend/src/mocks/handlers.ts`

**驗收 checklist**:
- [ ] `localStorage` grep 結果在 `client.ts` 為 0 hit(其他元件若有快取偏好等仍可保留)
- [ ] 手動 / e2e 驗:登入 → reload tab → 仍登入(cookie 持續);登出 → reload → 不登入
- [ ] BDD 20/20 仍綠
- [ ] `scripts/verify.sh --full` 綠

---

### 3.6 M5.6 — Compact + Release

**負責 agent**:`code-reviewer`(複審)→ `doc-devops`(收斂 + tag)
**接手起點**:M5.1 ~ M5.5 全綠

**範圍**:

1. **code-reviewer 複審**:
   - 對 M5.1 ~ M5.5 全部 diff 寫 `docs/review/m5-review.md`(P0 必修;若有 → 退回對應 builder 修)
   - 重點:S-009 cookie / CSRF 安全模型、C-015 dead-letter 是否真的會被消費、Q-23 OR 切換是否所有 caller 路徑都對齊

2. **doc-devops 收斂**:
   - **STATUS.md compact**:
     - 里程碑表格加 `M5 ✅ COMPLETED`
     - P1 Backlog 移除已修(S-009/010/011/013/015/016/017、C-005~C-015、Q-18~24)
     - **同步刪除已知過時項**:P-001、S-008-ext(本檔 §2.2 證據)
   - **CHANGELOG.md `[1.0.0-M5]`** 完整列 Added / Changed / Fixed / Removed
   - 跑完 `docs/release/checklist.md` 全綠 → tag `git tag v1.0.0-M5` + `git tag spec-v1.4`
   - 更新 `docs/release/impact-matrix.md`(若本期發現 matrix 漏項 → 補)

**驗收 checklist**:
- [ ] `docs/review/m5-review.md` 結論 PASS(無 P0)
- [ ] `STATUS.md` 行數比 M4 結束時更短(compact 強制)
- [ ] `CHANGELOG.md` `[Unreleased]` 已搬到 `[1.0.0-M5]`
- [ ] `docs/release/checklist.md` 全部勾完(複本進 PR description)
- [ ] git tag `v1.0.0-M5`、`spec-v1.4` 已建

---

## 4. Definition of Done(整個 M5)

- [ ] 25 條 in-scope 項目全部修復或拍板(7 spec + 7 security + 10 invariants + 1 Q-23 OR code change)
- [ ] `scripts/verify.sh --full` 全綠
- [ ] BDD 20/20(或更多,如新增 C-009 / C-014 / Q-23 OR case)綠
- [ ] code-reviewer 複審無 P0
- [ ] 文件四同步:requirements.md v1.4 / openapi.yaml v1.4 / schema.md(User lockout + dead-letter)/ CHANGELOG `[1.0.0-M5]`
- [ ] STATUS.md compact 完成,P1 Backlog 已清乾淨
- [ ] git tag `v1.0.0-M5`、`spec-v1.4` 已建

---

## 5. 啟動指令(逐棒)

```text
# 啟動 M5.1
> 啟動里程碑 M5.1(Spec v1.4 lock-in)。
> 負責 agent:spec-architect。
> 接手起點:docs/release/m5-plan.md §3.1。
> 產出:requirements.md v1.4 / openapi.yaml v1.4 / 對應 ADR Amendment / spec STATUS 更新。
> Q-18~Q-24 已拍板(見 §3.1 表),你的工作是把答覆寫進 spec / ADR,不再做決策;若你在落地過程中發現答覆與 code 衝突,停下回報。
> 額外驗證:Q-18 dispatch authorization code 是否已支援「任一上級」;若否登錄 M5.4 Backlog。
> 完成後依 Agent 協作協定停下等驗收。

# M5.1 驗收後啟動 M5.2
> 啟動里程碑 M5.2(Data model preparation,合棒)。
> 負責 agent:mongodb-modeler。
> 接手起點:docs/release/m5-plan.md §3.2。
> 範圍:Block A User lockout 兩欄 + migration 0001;Block B OutboxDeadLetter document 設計(選項 X / Y 自行拍板)+ migration 0002。
> 產出:User.kt 加兩欄、(可能新增)OutboxDeadLetter.kt、schema.md / indexes.md 同步、docs/data/migrations/0001 + 0002。
> 完成後停下等驗收。

# M5.2 驗收後啟動 M5.3
> 啟動里程碑 M5.3(Backend Security Hardening)。
> 負責 agent:quarkus-backend-builder,完成後 test-engineer 補測。
> 接手起點:docs/release/m5-plan.md §3.3 + docs/review/code-review-report.md。
> 前置依賴:M5.2 已驗收(User 兩個新欄位就位)。
> 範圍:S-009 / S-010 / S-011 / S-013 / S-015 / S-016 / S-017。
> 邊界:本棒不得改 backend/.../domain/;若發現需改 → 停下回報。
> 注意:S-009 cookie 改造同時牽動 frontend(M5.5 接);本棒先把 backend 端做完並用 Postman 驗 set-cookie / CSRF。
> 完成後停下等驗收。

# M5.3 驗收後啟動 M5.4
> 啟動里程碑 M5.4(Domain Invariants + Q-23 OR Hardening)。
> 負責 agent:quarkus-backend-builder → test-engineer。
> 接手起點:docs/release/m5-plan.md §3.4。
> 前置依賴:M5.2 已驗收(dead-letter document 就位)、M5.1 已驗收(Q-23 OR 已寫進 ADR-0011 Amendment)。
> 範圍:C-005 ~ C-015(10 條)+ Q-23 OR 切換 TaskService.kt:353-354 = 11 條。
> 邊界:不改 domain/;openapi.yaml 僅可動「error response code 補強」例外,其餘變更停下回報。
> 完成後停下等驗收。

# M5.4 驗收後啟動 M5.5
> 啟動里程碑 M5.5(Frontend Hardening)。
> 負責 agent:react-frontend-builder → test-engineer。
> 接手起點:docs/release/m5-plan.md §3.5 + 後端 M5.3 已接好的新 auth endpoint。
> 完成後停下等驗收。

# M5.5 驗收後啟動 M5.6
> 啟動里程碑 M5.6(Compact + Release)。
> 1. code-reviewer:全期 diff 複審 → docs/review/m5-review.md
> 2. doc-devops:STATUS compact / CHANGELOG bump 1.0.0-M5 / Release Checklist / git tag
> 完成後停下等最終驗收。
```

---

## 6. 風險與回退

| 風險 | 觸發點 | 回退策略 |
|---|---|---|
| S-009 cookie 改造 cross-domain CORS 卡住 | dev / prod domain 不同 | (使用者 Q2 拍板 A 完整實作;若真卡住才動降級)— 降級:access token 仍 localStorage,但縮短 TTL 至 15 分 + refresh httpOnly cookie;S-009 完整版 defer M6 |
| C-015 dead-letter 影響 outbox 既有 retryCount 既存資料 | migration 風險 | 由 M5.2 mongodb-modeler 在 `docs/data/migrations/0002-outbox-dead-letter.md` 處理,既存 `retryCount > 10` 一次性搬移 |
| BDD step def 因 cookie 改而大量改寫 | M5.5 工作量爆 | 若超過 4h → 拆 M5.5a(client.ts + 1 個 BDD path)/ M5.5b(其餘) |
| Q-23 OR 切換漏 caller 路徑 | M5.4 風險 | M5.6 code-reviewer 必複審所有 `qaReviewPolicy` / `qaReviews` reference;test-engineer 在 M5.4 補對立測試案例 |

---

**最後更新**:2026-05-07
**作者**:主 agent(本檔由規劃會議產出,實作由各 sub-agent 接手)

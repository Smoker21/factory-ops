# M5 全期 Code Review

**範圍**:M5.1 ~ M5.5(7 個 sub-phase 含 M5.1.5)
**審查日期**:2026-05-09
**審查者**:code-reviewer agent(M5.6.1)
**整體評分**:8.5 / 10
**結論**:**PASS**(無 P0;P1 清單為架構債紀錄,不阻擋 release)
**下一步**:啟動 M5.6.2(doc-devops 收斂 + tag v1.0.0-M5)

---

## P0(必修,阻擋 release)

**無 P0。**

理由:24 條 in-scope 全部找得到實作 + 對應測試;test-engineer 預列的 5 個 reviewer focus 經逐項驗證後皆屬 P1/P2 等級的架構觀察或文件落差,均不構成「會在 prod 造成資安失效或資料不一致」的紅旗。

---

## P1(建議修但不擋 release)

### P1-1 `LockoutStateWriter` 繞過 repository 抽象的長期一致性債

**位置**:`backend/src/main/kotlin/com/factoryops/infrastructure/security/LockoutStateWriter.kt:52, 64`

**問題**:直接呼叫 `userDoc.persistOrUpdate()`(PanacheMongoEntity instance method),繞過 `UserRepository` 抽象。專案其他所有 service 都遵循「DB write 走 repository」慣例。

**影響**:
- 未來若 `UserRepository.update()` 加入欄位 mask、audit hook、cache invalidation、optimistic concurrency 等切面,`LockoutStateWriter` 會悄悄漏掉
- 失敗 path:若 `persistOrUpdate()` 拋例外(transient mongo error / 寫衝突),REQUIRES_NEW transaction 滾回,但 `AuthService.login()` 仍會丟 `UnauthorizedException`,使用者看到普通 401 — counter 在 transient error 下會悄悄丟失。這不是資安退化,但違反「失敗閉合」可觀察性

**建議**(M6 重整):
1. `UserRepository` 加 `updateLockoutState(userDoc: UserDocument)` 專用方法,封裝 `persistOrUpdate()`;`LockoutStateWriter` 改呼叫該方法
2. `recordFailedAttempt` 在 `persistOrUpdate()` 外圍加 `try / catch`,catch 後 `logger.error`
3. ADR 補一節「為何 lockout 寫入路徑特殊」

**派回**:不退回;進 M6 backlog(派 mongodb-modeler + quarkus-backend-builder 協同)。

### P1-2 `OutboxPoller.pollAndProcess()` 缺 `@Transactional`,dead-letter 寫入 + 原 entry 標記非原子

**位置**:`backend/src/main/kotlin/com/factoryops/infrastructure/outbox/OutboxPoller.kt:50, 66-71`

**問題**:dead-letter `insert()` 與 `outboxRepository.update(event)`(setting `processedAt`)在 `for` loop 中分兩步呼叫,未包在 transaction 內。

**影響**:
- standalone Mongo:無 transaction 支援,本就 best-effort
- replica-set prod:若 dead-letter insert 成功但 `outboxRepository.update` 失敗 → 下個 poll cycle 重試 → catch path 重執行 `moveToDeadLetter()` → `idx_odl_original_outbox_unique` 觸發 `DuplicateKeyException` → 第 113 行 catch 並 ignore → 最終一致性是對的,因為 idempotent。**不是 correctness bug**,但 spec 對「outbox / dead-letter 一致性」的承諾應該由 ADR 寫清楚

**建議**:
1. 在 `pollAndProcess()` 或 `moveToDeadLetter() + processedAt set` 段加 `@Transactional` 包覆
2. ADR-0009 v1.4 Amendment 補「dead-letter 移動的最終一致性與 idempotency 保證」說明

**派回**:不退回;M6 candidate。

### P1-3 CSRF dual-mode bypass 的長期清算路徑不清晰

**位置**:`backend/src/main/kotlin/com/factoryops/infrastructure/security/CsrfFilter.kt:69-73`

**問題**:filter 檢查「請求是否帶 `XSRF-TOKEN` cookie」決定是否啟動 CSRF 校驗。Bearer-only 客戶端透通。dual-mode 兼容期合理,但**沒有強制截止日**。

**攻擊面評估**:
- 瀏覽器無法自動帶 `Authorization: Bearer ...` header;CSRF 攻擊需瀏覽器驅動
- 攻擊者要刻意送「無 cookie + 偽造 Bearer JWT」→ 需要 RSA private key,等同 game-over,dual-mode 不擴大攻擊面
- **目前安全攤位 OK**;但若日後 prod 環境只剩 cookie client,filter 應強制要求 cookie 存在(避免攻擊者刻意刪 cookie 繞 CSRF)

**建議**:
1. ADR-0015 v1.6 Amendment:「M6 全 cookie 切換完成後,CsrfFilter rule 3 改為『無 cookie → 拒絕(403)』」
2. application.properties 加 `security.csrf.strict-mode`(default false),M6 切 true 後即拒絕無 cookie 的 mutating request

**派回**:不退回;M6 candidate。

### P1-4 OpenAPI runtime metadata version 過時

**位置**:`backend/src/main/resources/application.properties:77`

**問題**:`quarkus.smallrye-openapi.info-version=1.3.0`,但 `docs/spec/openapi.yaml` 已 1.5.0。

**影響**:cosmetic;但 release 後若有人查 `/openapi` 看到 1.3.0 會困惑。

**建議**:doc-devops M5.6 release 時 bump 至 1.5.0,一行修改。

**派回**:**doc-devops M5.6 順手修**(屬 release housekeeping)。

---

## P2(可 defer 到 M6+)

### P2-1 ADR-0015 未文件化 401 vs 403 順序

`docs/adr/0015-jwt-cookie-and-csrf.md`:SmallRye JWT 在 Vert.x layer(priority ~1000)優先於 JAX-RS CsrfFilter(priority 900);cookie token 過期 + CSRF mismatch → 先 401。docs/backend/STATUS.md 已備註此行為,但 ADR-0015 未明文。

### P2-2 C-007 `mapNotNull` 仍存在於 role parsing 路徑

`TaskService.kt:170`、`GroupMapper.kt:56` 的 `Role.valueOf` 用 `mapNotNull { runCatching { ... }.getOrNull() }`,若 DB 中存了不可解析的 role string 會被靜默丟掉。寫入時驗證已存在,prod 不會出事;但 enum 改名時會悄悄變空。

### P2-3 CsrfFilter exempt-paths default 含 `/mock-hr`

`/mock-hr` 在 prod 不存在(`@IfBuildProfile("dev")`),不需 exempt;應只在 `%dev` profile override。

### P2-4 CsrfFilter exempt path 純 prefix match

`exemptPaths.any { path.startsWith(it) }` — 改 `path == it || path.startsWith("$it/")` 加邊界檢查。

### P2-5 application.properties cookie 缺 `%test` profile override

`%dev` 有 override,`%test` 沒有。建議補 `%test.auth.cookie.secure=false`。

### P2-6 `OutboxPoller` retryCount 邊界微妙

`> 10` 表示 retryCount=11 才搬移;m5-plan 字面易誤讀,建議 doc 補一句「閾值 10 表示 retryCount 已達 11 後的下一輪 catch 才觸發搬移」。

### P2-7 `application.properties` 未把 OpenAPI / Swagger 標 dev 限定

`quarkus.swagger-ui.always-include=true` 在 prod 也 enabled;M6 加 `%prod.quarkus.swagger-ui.always-include=false`。

---

## 已驗證 PASS 項

### Spec lock-in(Q-18 ~ Q-24)— 7/7 PASS

| Q | 落地證據 | 驗證 |
|---|---|---|
| Q-18 | `requirements.md §FR-Dispatch.1/.4`、`DispatchService.kt:74-76` | PASS |
| Q-19 | `requirements.md §FR-Org.12`、`ADR-0007 v1.4 Amendment` | PASS |
| Q-20 | `requirements.md §FR-Dispatch.7`、`ADR-0009 v1.4 Amendment` | PASS |
| Q-21 | `TaskService.kt:407` `doc.qaReviews = emptyList()` + status 回 IN_PROGRESS | PASS |
| Q-22 | `requirements.md §FR-Group.11` 沿用 `Group.history[]` | PASS |
| Q-23 OR | `TaskService.kt:424-425` + `ADR-0011 v1.4 Amendment §1-4` 完整公式 | PASS |
| Q-24 | `requirements.md §4 NFR` 時區 row | PASS |

### Security P1(S-009 ~ S-017)— 7/7 PASS

| S | 證據 | 驗證 |
|---|---|---|
| S-009 | `CookieHelper.kt`、`CsrfFilter.kt`、`AuthResource.kt`、`client.ts` 重寫、`application.properties` cookie/CSRF config | PASS(P1-3 為長期清算) |
| S-010 | `CorsValidationOnStartup.kt:32-54` | PASS |
| S-011 | `UserService.kt:26-68`(`SecureRandom`、`validatePasswordStrength`) | PASS |
| S-013 | `UserRepository.kt:37-60`(`Pattern.quote` + 64 char cap + reject `*`/`?` + `^prefix`) | PASS |
| S-015 | `RateLimiter.kt`、`AuthService.kt:62-66, 142-147, 183-188` | PASS |
| S-016 | `LockoutStateWriter.kt`、`AuthService.kt:80-101`、`AuthLockoutIT` 由紅轉綠 | PASS(P1-1 為架構債) |
| S-017 | `AuthDtos.kt:17`(`@Size(max=128)` 已就位) | PASS |

### Domain Invariants P1(C-005 ~ C-015)— 10/10 PASS

| C | 證據 | 驗證 |
|---|---|---|
| C-005 | `DispatchService.kt:252-262`(SUBMITTED guard)+ `:301-306`(severityToPriority 顯式表) | PASS |
| C-006 | `TaskService.kt:217-227`(group membership 檢查) | PASS |
| C-007 | `TaskService.kt:162-166`(找不到 group throw NotFoundException) | PASS |
| C-008 | `TaskService.kt:271-292`(`applyDoneTransition` shared helper) | PASS |
| C-009 | `ProjectService.kt:199`(PAUSED → COMPLETED 加入合法集) | PASS |
| C-010 | `ProjectService.kt:117-121`(due <= start throw) | PASS |
| C-011 | `TaskService.kt:111-116`(dueAt < project.startAt throw) | PASS |
| C-012 | `TaskService.kt:299-309`(active + same rootOrgId 驗證) | PASS |
| C-014 | `OrganizationService.kt:282-290`(409 ConflictException) | PASS |
| C-015 | `OutboxPoller.kt:66-119`、`OutboxDeadLetterDocument`、`OutboxDeadLetterRepository` | PASS(P1-2 為原子性說明缺) |

### M5.5 Frontend Cookie 改造 — PASS

- `client.ts`:`localStorage` grep = 0 hit、`withCredentials: true`、CSRF echo interceptor、refresh dedupe(`isRefreshing` + `refreshQueue`)、redirect loop guard
- `AuthContext.tsx`:bootstrap GET /me、`isLoading` race 處理、`cancelled` flag
- `cookies.ts` getCookie utility(9 cases 含邊界)
- BDD step defs 改造完成(待 docker 環境驗證)

### M5.2 Data Model — PASS

- `User.kt` 加 `failedLoginCount: Int = 0`、`lockedUntil: Instant? = null`(預設值不破壞既有資料)
- `OutboxDeadLetter.kt` + `OutboxDeadLetterDocument.kt` 並存於 `WebhookDeadLetter` 旁(選項 X)
- migrations `0001-user-lockout-fields.md` + `0002-outbox-dead-letter.md` 4 項齊
- `idx_odl_original_outbox_unique` 用 `partialFilterExpression` 而非 `sparse`

### Test 量級成長

- backend 425 (M4) → 547 (M5.3.2) → 654 (M5.4.2):**+229 tests, 全綠**
- frontend 66 (M4) → 73 (M5.5.1) → 102 (M5.5.2):**+36 tests, 全綠**
- 覆蓋率(jacoco):上次 M4 phase 2 達 60% line / 45% branch;M5 期間沒 retract,M5.6 doc-devops 跑 jacoco 確認

### 文件四同步

- `requirements.md` v1.5.0 ✓
- `openapi.yaml` v1.5.0 ✓(雙模 / CSRF / Set-Cookie 描述齊;64 mutating ops `$ref CsrfHeader`)
- `schema.md` v1.1.0(User lockout + OutboxDeadLetter 已加)✓
- `CHANGELOG.md` `[Unreleased]` 留 marker(M5.6 doc-devops 搬到 `[1.0.0-M5]`)

### 邊界 / 命名 / 函式長度

- 命名清晰、KDoc 說明 rationale、無 println debug、無 hardcode secret、無註解掉的 dead code(`jwtUtils.ts` 已刪)

---

## 未來追蹤(M6+ candidate)

1. **dual-mode 移除路徑** — ADR-0015 補 v1.6 Amendment + `security.csrf.strict-mode` toggle(P1-3)
2. **`OutboxDeadLetter` vs `WebhookDeadLetter` unify 評估**
3. **`LockoutStateWriter` 走 Repository 抽象**(P1-1)
4. **OutboxPoller `@Transactional` 補強**(P1-2)
5. **OpenAPI runtime info-version bump**(P1-4)— doc-devops M5.6 順手修
6. **`mapNotNull` 在 Role.valueOf 路徑改 strict map**(P2-2)
7. **CSRF exempt-paths boundary check 改 `path == it || path.startsWith("$it/")`**(P2-4)
8. **Swagger UI / OpenAPI prod 隱藏**(P2-7)

---

## 統計

- **P0 必修問題**:0
- **P1 建議改進**:4(均屬架構債紀錄,有 mitigations,不阻擋 release)
- **P2 風格 / 文件**:7

---

## 結論與下一步建議

**結論:PASS,可進 M5.6.2(doc-devops 收斂 + tag v1.0.0-M5)。**

無 P0,**無需退回 builder**。P1 列表全部屬於「目前運作正確、長期應重整」的架構債,符合 M5 目標(收 P1 backlog),不該再推遲 release。已將 P1-1 ~ P1-4 寫入「未來追蹤」,doc-devops M5.6 release 可順手處理 P1-4 / P2-3 / P2-5 的低成本修正。

### 對 doc-devops M5.6 的具體建議(release 階段順手修)

1. **`backend/src/main/resources/application.properties:77`** bump `info-version=1.5.0`(P1-4)
2. **STATUS.md compact**:依 m5-plan.md §2.2 移除 P-001 / S-008-ext 過時項;移除全部 M5.x 驗收 checklist;P1 backlog 的 S-009/010/011/013/015/016/017、C-005~C-015、Q-23 OR 全部移除
3. **CHANGELOG `[1.0.0-M5]`** 完整列 Added(LockoutStateWriter / OutboxDeadLetter / CookieHelper / CsrfFilter / RateLimiter / CorsValidationOnStartup)、Changed(Q-23 OR 切換、`UserDocument` lockout 兩欄、frontend cookie 改造)、Fixed(S-016 持久化、所有 P1 列表)、Removed(`jwtUtils.ts`)
4. **新增 P1 backlog 條目**(本 review 產出):
   - **P1-1 LockoutStateWriter 走 repo 抽象**
   - **P1-2 OutboxPoller @Transactional 原子性**
   - **P1-3 CSRF dual-mode 強制截止點**(ADR-0015 v1.6 Amendment)
5. **git tag**:`v1.0.0-M5` + `spec-v1.5`(M5.1 拍板時 m5-plan 寫 `spec-v1.4`,但實際 spec 已 v1.5.0;建議 tag `spec-v1.5` 對齊真實版本)

---

**狀態**:READY_FOR_M5.6.2 — 主 agent 可直接派 doc-devops。

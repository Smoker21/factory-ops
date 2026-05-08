# 測試工程 STATUS

**狀態**: ✅ M5 COMPLETED
**完成時間**: 2026-05-09
**負責 agent**: test-engineer（M5.3.2 / M5.4.2 / M5.5.2）

---

## M5 baseline

| 指標 | M4 baseline | M5 完成 |
|---|---|---|
| 後端 tests | 425 | **654**（+229） |
| 前端 tests | 66 | **102**（+36） |
| 後端 LINE coverage | 60% | ≥60%（未退化） |
| BDD scenarios | 18 | 20+（新增 reload/登出後 reload 情境） |

### 主要新增測試（M5）

| 測試 | 說明 |
|---|---|
| `AuthCookieFlowIT`（20 cases） | S-009 cookie/CSRF 完整整合測試 |
| `AuthLockoutIT`（5 cases） | S-016 lockout 整合測試（M5.4.2 改造為 @BeforeEach reset） |
| `AuthRateLimitIT` | S-015 rate-limit 整合測試（429 + Retry-After） |
| `DeleteOrgBlockedIT` | C-014 HTTP DELETE 409 + RFC 7807 |
| `OutboxDeadLetterIT` | C-015 MongoDB 持久化 + OutboxPoller 流程 |
| `LockoutStateWriterTest` | S-016 LockoutStateWriter 業務邏輯 |
| `DispatchServiceC005Test` | C-005 SUBMITTED + 4×SeverityLevel→Priority |
| `TaskServiceM5InvariantTest` | C-006 ~ C-008、C-011、C-012、Q-23 OR 完整矩陣 |
| `ProjectServiceM5InvariantTest` | C-009、C-010 + 全狀態機 parameterized scan |
| `OrganizationServiceC014Test` | C-014 deleteOrg 409 |
| `OutboxPollerDeadLetterTest` | C-015 dead-letter 門檻邊界 / idempotency / backoff 公式 |
| frontend: `client.test.ts`（14）、`auth.test.ts`（7）、`AuthContext.test.tsx`（6）、`cookies.test.ts`（9） | M5.5.2 cookie/CSRF 前端覆蓋 |

**權威歷史**: `CHANGELOG.md [1.0.0-M5]`。

---

## 未解決問題

1. **BDD 在無 docker 環境無法執行**：step defs 已改寫為 cookie-aware 並通過 TypeScript 編譯；CI 環境（ubuntu-latest + docker）可直接跑
2. `AuthLockoutIT` @BeforeEach 用 `userRepository.listAll()` 找帳號略有效能問題（test 環境可接受）

---

## 給下一棒 starter context

M5 所有測試需求均已滿足。M6 接手時，新功能需依 Impact Matrix CT-N 補對應測試；BDD E2E 在 CI 環境（docker compose）驗證。

# ADR-0011: Group-level QA 雙簽工作流(Group Settings 客製化 + Snapshot at Task Creation)

**狀態**: Accepted
**日期**: 2026-05-04
**決策者**: spec-architect(依使用者 v1.3 拍板的 Q-7)
**相關需求**: §FR-3 Task 狀態流、Q-7、INV-31

---

## Context(背景)

Q-7 拍板:**Task 完成驗收的 QA 雙簽機制由 GROUP_MANAGER 在 Group settings 設定**。也就是說,QA 雙簽不是全廠統一規則,而是**Group 層級可開關 / 客製**的工作流選項。例:

- 「裝配課」要求所有 Task 經 QA 雙簽才能 DONE
- 「品保課」自身就是品保,不需要再雙簽

需要決策:
1. Group settings 的欄位設計
2. 雙簽 reviewer 角色清單的語意(AND / OR)
3. Group settings 變更時對**進行中** Task 的影響(snapshot vs live policy)
4. Review action 的 API 形式(獨立 endpoint 還是 status 變更的特例)
5. Reviewer 資格驗證(角色 + Group 成員身份)

---

## Decision(決策)

### 1. Group 加 `settings.qa` 子文件

```kotlin
data class Group(
    // ... 既有欄位
    val settings: GroupSettings,
    // ...
)

data class GroupSettings(
    val qa: QaSettings,
    val extras: Map<String, Any> = emptyMap(),  // 預留
)

data class QaSettings(
    val dualSignRequired: Boolean = false,
    val requiredReviewerRoles: List<Role> = emptyList(),
)
```

預設值:`{ qa: { dualSignRequired: false, requiredReviewerRoles: [] } }`(與 v1.2 行為一致 = 不雙簽)。

### 2. `requiredReviewerRoles[]` 的語意:**AND**(每個列示角色都需各一筆 review)

> 規則:Task 進入 `IN_REVIEW` 後,必須蒐集到「**列示中每個角色都至少一位 reviewer 簽核通過**」才能進入 `DONE`。「雙簽」一詞本身已隱含「兩個不同身份的簽核」。

例:
- `requiredReviewerRoles: ["QA"]` → 需要 1 位 QA 角色 user 完成 review action
- `requiredReviewerRoles: ["QA", "SHIFT_LEAD"]` → 需要 1 位 QA + 1 位 SHIFT_LEAD(兩位不同 user;**不可同一人兼具雙角色一次過關**)
- `requiredReviewerRoles: []` 但 `dualSignRequired: true` → 視為設定錯誤,Group settings 寫入時拒絕(422)

> **若使用者期望 OR 語意**(任一角色簽核即可)請於 Q-23 回覆,本 ADR 將被修訂。

### 3. **Snapshot at Task Creation 策略**(對應 INV-31)

當 Task 建立時(以及由 ActionRequest convert 時),server 從**所屬 Project 的所有 `groupIds[]` 對應 Group 的 settings.qa**做運算,**snapshot** 到 Task 上的 `qaReviewPolicy`:

```kotlin
data class Task(
    // ... 既有
    val qaReviewPolicy: QaReviewPolicy,
    val qaReviews: List<QaReviewEntry>,   // append-only
)

data class QaReviewPolicy(
    val dualSignRequired: Boolean,
    val requiredReviewerRoles: List<Role>,
    val snapshotAt: Instant,
    val sourceGroupIds: List<GroupId>,    // 衍生來源,稽核用
)

data class QaReviewEntry(
    val reviewerId: UserId,
    val reviewerRole: Role,        // 該次 review 套用的角色(必須在 policy 列示中)
    val decision: ReviewDecision,  // APPROVED / REJECTED
    val reason: String?,
    val at: Instant,
)

enum class ReviewDecision { APPROVED, REJECTED }
```

**多 Group 的合併規則**:Project 可能掛多個 Group,合併規則為「**任一 Group 要求雙簽則 Task 雙簽**」(嚴格優先):
- `dualSignRequired = OR(各 group.settings.qa.dualSignRequired)`
- `requiredReviewerRoles = ⋃(各 group.settings.qa.requiredReviewerRoles)` 取聯集後去重

### 4. **Group settings 變更不影響已存在 Task**(對應 INV-31)

- Group settings 修改後,**只影響此後新建立的 Task**
- 進行中 Task 仍使用其 `qaReviewPolicy` snapshot
- 若希望套用新 policy,需以新 Task 取代(不提供「重新 snapshot」API,避免破壞 in-flight 工作流)

### 5. Review Action API

```text
POST /tasks/{taskId}/review
  body: {
    decision: APPROVED | REJECTED,
    role: <Role>,           // reviewer 主張這次以哪個角色 review;必須是 task.qaReviewPolicy.requiredReviewerRoles 之一
    reason?: string
  }
  → 驗證:
      1. task.status == IN_REVIEW
      2. actor 具備所主張的 role
      3. actor 對該 task 有 R 權限(同 Group 或被指派)
      4. 同 (taskId, reviewerId, role) 不可重複(已 review 過則 409)
      5. APPROVED:append QaReviewEntry;REJECTED:append + status 回退至 IN_PROGRESS(語意「require changes」)
  → 副作用:
      若 APPROVED 後已蒐集 policy 要求的所有角色 → server 自動推進 status 為 DONE,
      並 emit factory-ops.task.completed
      若 REJECTED → 回退 status 至 IN_PROGRESS,記錄 history,
      emit factory-ops.task.review-rejected
```

> **Reject 是否會把先前 APPROVED 清空?** 預設**會清空**(進入 IN_PROGRESS 等同流程重啟,既往 review 失效)。**僅** `qaReviews[]` history 保留,新一輪 review 重新蒐集。
> 此設計可能爭議,故同時開 Q-21:reject 流程行為定案。

### 6. 手動推進的 fallback

若 GROUP_MANAGER 因故需強制推進(例:reviewer 全部離職),可透過既有 `POST /tasks/{taskId}/status` 強制設為 DONE,但 server **必須**:
- 驗證 actor 為 GROUP_MANAGER 角色於該 Task 的 Group
- `history[]` 加註 `action: TASK_FORCE_COMPLETED`、`payload.bypassReason: required`
- emit `factory-ops.task.completed` 帶 `bypassed: true` flag

---

## Consequences

### 正面
- **逐 Group 客製,語意精準**:符合工廠不同班組差異化作業
- **Snapshot 策略隔離設定變更影響**:進行中 Task 不會被「政策中途換」造成卡關
- **AND 語意嚴謹**:符合「雙簽」字面期待
- **Review history 完整**:每次簽核都進 `qaReviews[]`,可稽核

### 負面
- **多 Group 合併語意需明確**:採聯集/最嚴格優先,易理解但要文件化清楚
- **Reject 清空 reviews 可能引發爭議**:由 Q-21 確認
- **Settings 變更不影響舊 Task**:若使用者修改 settings 是為了「立即套用全部」,可能與預期不符(需文件提醒)
- **Snapshot 增加 Task 文件大小**:`qaReviewPolicy` ~ 100 bytes / `qaReviews[]` 預估 ≤ 5 筆,可接受

---

## Alternatives Considered

### A. Live policy(每次 review 即時讀 Group settings)
- **優點**:不必 snapshot
- **缺點**:Group settings 半途修改會中斷進行中工作;違反「policy frozen at task creation」最佳實踐
- **不採用**

### B. 全廠統一(Org root settings)
- **優點**:簡單
- **缺點**:Q-7 明確指定 Group 層級
- **不採用**

### C. OR 語意(任一角色簽即可)
- **優點**:更彈性
- **缺點**:「雙簽」字面語意不符
- **不採用**(留 Q-23 待用戶最終拍板)

### D. 用獨立 collection 存 reviews
- **優點**:Task 文件不膨脹
- **缺點**:預估每 Task ≤ 5 筆 review,embed 成本可接受
- **不採用 MVP**

### E. 強制 IN_REVIEW 路徑(取消「IN_PROGRESS → DONE 簡易模式」)
- **優點**:行為一致
- **缺點**:dualSignRequired = false 時應允許簡易完成
- **不採用**:保留兩條路徑,由 policy 控

---

## Compliance / Validation

### Group settings 寫入時驗證
- `dualSignRequired = true` 但 `requiredReviewerRoles` 為空 → 422
- `requiredReviewerRoles` 含 `ADMIN` / `ORG_ADMIN` 等系統角色 → 422(限第一線角色:`OPERATOR` / `SHIFT_LEAD` / `ENGINEER` / `QA` / `GROUP_ADMIN` / `GROUP_MANAGER`)

### Task 建立時 Snapshot
- 計算 `qaReviewPolicy` from Project.groupIds[].settings.qa
- 寫入 `task.qaReviewPolicy` + `task.qaReviews = []`
- `snapshotAt = now`

### Status 流增補
- 若 `qaReviewPolicy.dualSignRequired = true`:
  - `IN_PROGRESS → DONE`(簡易 path)被禁止;只能 `IN_PROGRESS → IN_REVIEW → DONE`(via review API)
  - `IN_REVIEW → DONE` 直接呼叫 status API 被禁止;須走 review action 蒐集完成才會自動轉

### 測試要求
- Unit:多 Group 合併規則
- Integration:單一 QA review pass 流程、雙角色 review pass、reject reset、bypass force-complete
- Failure:reviewer 不具角色、重複 review、過早 status 推進

---

## Notes for Next Stage(mongodb-modeler)

### Schema
- `groups.settings.qa.dualSignRequired: bool`
- `groups.settings.qa.requiredReviewerRoles: [Role]`
- `tasks.qaReviewPolicy: { dualSignRequired, requiredReviewerRoles, snapshotAt, sourceGroupIds }`
- `tasks.qaReviews: [{ reviewerId, reviewerRole, decision, reason, at }]`

### 索引
- `tasks`:`{ rootOrgId: 1, status: 1, "qaReviewPolicy.dualSignRequired": 1 }` sparse(找出待 review 的 Task 看板)
- 不需要 `groups.settings` 索引(讀時走 `_id`)

### Domain Events(NATS)
- `factory-ops.group.settings-qa-updated`
- `factory-ops.task.review-submitted`
- `factory-ops.task.review-rejected`
- `factory-ops.task.completed`(原有,加 `bypassed: bool` 屬性 if applicable)

### Implementation 提醒
- `qaReviewPolicy` snapshot 時「合併多 Group settings」的計算建議放 application service,不放 Group aggregate(避免 Group 知道別的 Group)
- `IN_PROGRESS → DONE` 簡易 path 僅在 `dualSignRequired = false` 開放;狀態機驗證需讀 task.qaReviewPolicy

---

## v1.4 Amendment(2026-05-08)— `requiredReviewerRoles` 改為 OR 白名單 + Reject 清空 + 同人多角色簽允許(Q-21 / Q-23 / Q5 B 拍板)

> **本 Amendment 推翻 v1.3 §2 的 AND 語意**(原 §2「`requiredReviewerRoles[]` 的語意:AND」),並重寫 §5 的 Reject 行為說明。原 §2 / §5 文字保留以記錄歷史。新行為以本 Amendment 為準。

### 背景

v1.3 ADR-0011 §2 採 AND 語意(每個列示角色都需各一筆 review 才算過關),並在 §5 留 Q-21 待確認 reject 行為。**2026-05-07 規劃會議拍板**:

- **Q-21**:reject 後**清空** `qaReviews[]`,task 回 `IN_PROGRESS`(語意「重新走流程」);`history[]` 保留稽核軌跡。
- **Q-23**:`requiredReviewerRoles` 改為 **OR 白名單**(列示中的角色才能簽,但**不要求每一角色到齊**);`dualSignRequired = true` 仍要求 `qaReviews.size >= 2`,但**角色組合任意**。
- **Q5 B**(同會議,衍生於 Q-23 的範疇問題):**同一 user 可以以不同角色多次簽核並各計入一筆**。

### Why(關鍵 — 為什麼推翻 AND)

**本系統的 QA review 是「工廠派工輕量確認」,不是「品保 GMP 雙簽」**。

- **GMP / FDA 等品保場景**:雙簽要求兩位**獨立** reviewer(防共謀、防個人疏失導致放行不合格品),AND 語意 + 同人不可雙角色才有意義。
- **本系統實際使用情境**:第一線 OPERATOR / SHIFT_LEAD / ENGINEER 在派工流程結尾互相確認工作完成度,目的是「**確保有人 sanity check**」,而非「滿足法規要求兩位獨立簽核者」。
- **AND 語意的代價**:若 Group 設定 `requiredReviewerRoles: ["QA", "SHIFT_LEAD"]`,則所有 Task 都必須湊齊一位 QA + 一位 SHIFT_LEAD 才能 DONE。當夜班 / 假日 QA 不在場時,工作會卡住等到隔天 — 與工廠 7×24 運轉的 NFR 衝突。
- **OR 白名單的好處**:**白名單仍限制「只有列示角色才能簽」**(非任何人都可簽),但**不強制每個角色都到場**;`dualSignRequired = true` 用 count 限制(>= 2 筆)而非角色組合確保「至少有兩個人 sanity check 過」。同人多角色簽放寬以應對單班 / 小組值班情境。
- **歷史可追溯仍保留**:`(reviewerId, role)` unique 確保不會「同人同角色重簽兩次」灌水,所有動作仍進 `history[]` append-only。

### Decision(取代 v1.3 §2)

#### 1. `requiredReviewerRoles` = OR 白名單

`Group.settings.qa.requiredReviewerRoles` 是**白名單**,規定**誰有資格簽**這個 Group 的 Task review:

- **寫入時驗證**(Review Action 那一筆):`role ∈ requiredReviewerRoles` → 否則 422 `role_not_required`
- **不要求每個角色都到齊**:無 AND 強制
- **OR 並非「任一角色簽即可」單一語意**:仍與 `dualSignRequired` 互動 — 見下「Auto-complete 條件」

#### 2. Auto-complete 條件(取代 v1.3 「蒐集完所有 required roles」)

當 review APPROVED 寫入後,server 立即評估:

```
auto_complete = (
    (qaReviewPolicy.dualSignRequired ? qaReviews.size >= 2 : qaReviews.size >= 1)
    AND
    qaReviews.all { it.reviewerRole in qaReviewPolicy.requiredReviewerRoles }
    AND
    qaReviews.all { it.decision == APPROVED }
)
```

成立 → server 自動推進 `status = DONE` + emit `factory-ops.task.completed`。
不成立 → 留在 `IN_REVIEW` 等下一筆。

> 註:第二、三個 AND 子句由 (a) 寫入時白名單驗證 + (b) reject 路徑會清空所有 qaReviews 共同保證,實作上等同檢查 size 即可。

#### 3. 角色組合自由 + 同人多角色簽允許(Q-23 + Q5 B)

`dualSignRequired = true` 時,以下組合**全部合法**:

| 情境 | 是否成立 | 說明 |
|---|---|---|
| QA + SHIFT_LEAD(兩位不同 user 各一筆) | ✓ | 經典「跨角色雙簽」 |
| QA + QA(兩位不同 user 都簽 QA 角色) | ✓ | 同一角色兩筆,白名單成立 |
| 同人 QA + 同人 SHIFT_LEAD(該 user 具雙身份) | ✓ | **同人多角色簽允許**(Q5 B) |
| 同人 QA + 同人 QA(同一 user 同一角色) | ✗ | 違反 `(reviewerId, role)` unique → 409 |
| QA + OPERATOR(白名單只有 ["QA", "SHIFT_LEAD"]) | ✗ | OPERATOR 不在白名單 → 寫第二筆時 422 |

#### 4. INV-36 重寫(取代 v1.3 INV-36)

**新 INV-36**:`qaReviews[]` 中 `(reviewerId, role)` unique(同一 user 同一角色不可重簽,避免重覆計數);**同一 user 可以以不同角色多次簽核並各計入一筆 qaReviews**(本系統為工廠派工輕量確認,非 GMP 品保雙簽)。

#### 5. Reject 行為(Q-21 拍板,確認 v1.3 §5 預設行為)

當 review `decision = REJECTED`:

1. **`qaReviews[]` 清空**(`= emptyList()`)
2. `status` 從 `IN_REVIEW` 回退至 `IN_PROGRESS`
3. `history[]` append `TASK_REVIEW_REJECTED` entry(payload 含 reject 角色 + reason)— **稽核軌跡完整保留**
4. emit `factory-ops.task.review-rejected`
5. 後續 review 如同重新走流程

**理由**:reject 等同「請改」,進入 IN_PROGRESS 後可能會修改 Task 內容、re-assign,既往 APPROVED 簽核基於舊內容,清空避免「半個 review 殘留」造成 audit 混淆。

### Consequences

**正面**:
- 工廠夜班 / 假日值班不會被「等 QA」卡住
- Auto-complete 條件可以單一公式表達,易實作 + 易測試
- 同人多角色簽支援值班輕量場景(一人兼多職,工廠常態)
- 白名單仍提供權限邊界(非任何人都可簽,只限列示角色)

**負面**:
- **不適用於 GMP / 法規嚴格場景**(本系統明確不是品保雙簽工具,有此需求的 Group 應另設外部品保流程)
- 同人多角色簽可能被濫用(同一 user 兼具兩 role 即可一人完成 dualSign)— 緩解:Group settings 寫入時若想要嚴格,可以**只列一個角色**,讓 OR 白名單自然退化成「只此一角色可簽」+ count 限制(等同單簽);若需嚴格雙人,需另設組織制度(本系統不擋)
- 推翻原 AND 語意 → 既有測試 / 文件需同步改寫(M5.4 backend code change + test 改寫)

**後續工作**:
- **M5.4 backend code change**(`backend/src/main/kotlin/com/factoryops/application/service/TaskService.kt:354`):從 `qaReviewPolicy.requiredReviewerRoles.all { approvedRoles.contains(it) }` 改為 OR 白名單 + count 公式(見上 §2)
- M5.4 test-engineer 補測:同人多角色簽成立、白名單外角色拒、同 role 兩筆成立、reject 清空後再 approve 不會誤判 auto-complete
- domain-model.md §4.13 sequence 補「同人多角色」「同 role 兩筆」情境(已隨 v1.4 同步)

### Compliance / Validation(覆蓋 v1.3 同名章節對應條目)

#### Group settings 寫入時(沿用 v1.3,**不變**)
- `dualSignRequired = true` 但 `requiredReviewerRoles` 為空 → 422
- `requiredReviewerRoles` 含 `ADMIN` / `ORG_ADMIN` 等系統角色 → 422

#### Review action 寫入時(v1.4 修訂)
- `role ∈ task.qaReviewPolicy.requiredReviewerRoles` → 否則 422 `role_not_required`
- actor 具備所主張的 role
- actor 對該 task 有 R 權限
- **`(taskId, reviewerId, role)` unique**(同人同 role 不可重簽,409)— 注意:**同人不同 role 允許**(Q5 B)
- decision = REJECTED → 清空 qaReviews + status 回 IN_PROGRESS(Q-21)
- decision = APPROVED → 評估 auto_complete 公式(本 Amendment §2)

#### 測試要求(v1.4 補強,M5.4 test-engineer 落實)
- Unit:
  - 多 Group 合併規則(沿用)
  - **OR 白名單 + dualSignRequired auto_complete 公式**(本 Amendment §2)
  - **同人多角色簽**(同 user 以 QA + SHIFT_LEAD 各簽一筆 → DONE)
  - **同 role 兩筆**(QA + QA,兩位不同 user → DONE)
  - **同人同 role 重簽**(同 user 連簽兩次同 role → 409)
  - **白名單外角色簽**(OPERATOR 簽但白名單只有 QA → 422)
- Integration:
  - reject 清空 + 後續 approve 重新蒐集流程
  - bypass force-complete(沿用)

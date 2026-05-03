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

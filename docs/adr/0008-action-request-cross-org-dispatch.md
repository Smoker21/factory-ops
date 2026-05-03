# ADR-0008: ActionRequest 跨層派工(Single-hop Direct Dispatch to Leaf)

**狀態**: Accepted(v1.3 全面改寫,取代 v1.2「Dispatch + 多層 Relay」雙模式)
**日期**: 2026-05-04
**決策者**: spec-architect(依使用者 v1.3 拍板的 Q-14)
**相關需求**: §1.2 第 4 個情境、§1.3 末段、FR-Dispatch、INV-24 / INV-25、Epic I(US-I1 ~ US-I6)

---

## v1.3 變更摘要

- **刪除「逐層 Relay」流程**:不再有「廠長派處長,處長 relay 給部長,部長 relay 給課長」這種多跳鏈
- **改為「Direct Dispatch to Leaf」單跳**:任何具備 ORG_MANAGER 身份的 user(任一層 manager,不限 root)直接把 ActionRequest 派到其子孫節點中的某個 leaf Org
- **`targetOrgId` 必為 leaf**:由 server 強制驗證
- **刪除欄位 / 端點 / 狀態**:`relayChain[]` / `RELAYED` 子狀態 / `POST /action-requests/{id}/relay` 全數移除
- **保留 `originatingOrgId`**:用於追蹤與回報「誰派的」

---

## Context(背景)

§1.3:

> 「組織工作委派,上級組織的 Manager 與其指定的代理人,可以對下級組織指派(Action Request),Action Request 負責人預設為組織負責人」

§1.2 第 4 個情境字面為「廠長 → 部門經理 → 課長」三跳鏈,但 Q-14 用戶明確拍板:

> 「Root 只會指派 Action Request 到 Leaf」

進一步推論為:**派工是直接派到執行單位(leaf)**,中間管理層在 §1.2 例子中只是「人鏈傳達」(走流程,口頭確認、會議交辦),**不是系統建模的中間狀態**。系統不應該強迫每層 manager 都在 UI 上做一次 relay 操作。

需要決策:
1. 刪除 relay 後,中介管理層的可見性如何維持?
2. 派工權限如何定義(哪些 manager 對哪些 leaf 有權派)?
3. 「originating」一詞在單跳模型下的語意?
4. 既有 ActionRequest 「自課內提報」流程是否衝突?

> Q-14 沒有明說「只有 root 才能派」,僅強調「目標必為 leaf」。本 ADR 採取「**任一上級 manager 皆可 dispatch 到其子孫中的 leaf**」(對應 §1.3 字面,以及一個合理推論:中間層 manager 角色本身才有意義)。若用戶最終決定**只有 root 才能派**,Q-18 將驅動本 ADR 修訂。

---

## Decision(決策)

### 1. ActionRequest aggregate 欄位(v1.3)

```kotlin
data class ActionRequest(
    val id: ActionRequestId,
    val rootOrgId: OrgId,
    val projectId: ProjectId?,         // 跨層 dispatch 起初無 Project;leaf triage 時關聯
    val originatingOrgId: OrgId,       // 發起者「所掛」的 Org 節點(可任一層,即 actor 的 manager scope 中與 target 有 ancestry 的最近一個)
    val targetOrgId: OrgId,            // ★ 取代 v1.2 的 assignedToOrgId;必為 leaf
    val title: String,
    val descriptionMarkdown: String,
    val severity: Severity,
    val status: ActionRequestStatus,
    val requesterId: UserId,
    val ownerId: UserId?,              // 預設 = targetOrg leader 規則;見 ADR-0010
    val linkedTaskId: TaskId?,
    val attachments: List<AttachmentRef>,
    val history: List<HistoryEntry>,
    val submittedAt: Instant,
    val resolvedAt: Instant?,
    val deletedAt: Instant?,
    val schemaVersion: Int,
)
```

**從 v1.2 移除**:
- `assignedToOrgId` 欄位(改名為 `targetOrgId`,且必為 leaf)
- `relayChain: List<RelayHop>`
- `RelayHop` VO 整體刪除

**狀態機**:`SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED / REJECTED`(同 v1.2 但**移除 `RELAYED` 假設,本來在 v1.2 也是用 SUBMITTED 涵蓋,現在更純粹**)。

### 2. 唯一派工 API:`POST /orgs/{targetOrgId}/dispatch-action-request`

```text
POST /orgs/{targetOrgId}/dispatch-action-request
  body:
    title: string                (required)
    descriptionMarkdown: string
    severity: { level, reason? } (required)
    attachments: AttachmentRef[]
    dueAt: ISO 8601 + offset?
    ownerId: UserId?             (條件必填:當 targetOrg.leaderIds.length > 1)

  Server-side:
    1. 驗證 targetOrg 存在且未軟刪
    2. 驗證 targetOrg.type ∈ root.settings.leafTypes        → 否則 422 target_must_be_leaf
    3. 驗證 actor 對 targetOrg 有 dispatch 權限             → 否則 403
       規則:存在某個 ancestor(包含 targetOrg 自己或其祖先;通常是 targetOrg 的某個祖先)A 使得
              Organization[A].managerId == actor.userId
              且 A.rootOrgId == targetOrg.rootOrgId(同 root tree)
       選定 originatingOrgId = 該 A(若多個,取距離 targetOrg 最近的)
    4. 計算預設 ownerId(對應 ADR-0010 §3 規則):
       n = targetOrg.leaderIds.size
       n == 0 → 409 target_org_no_leader
       n == 1 → ownerId = targetOrg.leaderIds[0](忽略 body.ownerId 或要求等於該值)
       n >= 2 → 必須 body.ownerId ∈ targetOrg.leaderIds;否則 422
    5. 建立 ActionRequest:
         status = SUBMITTED, projectId = null, requesterId = actor.userId,
         linkedTaskId = null, attachments = body.attachments
    6. emit factory-ops.action-request.dispatched
```

**移除**:`POST /action-requests/{id}/relay`(v1.3 不存在)

### 3. 接收端:leaf Org 的 GROUP_MANAGER / GROUP_ADMIN convert-to-task

維持既有流程:`POST /action-requests/{id}/convert-to-task`,但現在不必再驗證「assignedToOrgId 是 leaf」(因為 `targetOrgId` 本來就是 leaf),驗證簡化為「actor 是 targetOrg 下某 Group 的 GROUP_MANAGER / GROUP_ADMIN」。

### 4. 權限規則(對應 INV-24 改寫)

```text
Dispatch:
  · 必要條件 1:actor 至少是某個 Org 節點 A 的 manager(Organization[A].managerId == actor.userId)
  · 必要條件 2:targetOrg 是 A 的子孫(嚴格 / 包含「A 自己 == targetOrg」邊界):
                        A == targetOrg                       → A == targetOrg 且 targetOrg 是 leaf
                                                               (合法:同一 leaf 的 manager 也可 dispatch 給自己 leaf)
                        targetOrg ∈ descendants(A)           → 一般情況
  · 必要條件 3:targetOrg.type ∈ root.settings.leafTypes
  · 必要條件 4:同 rootOrgId

Reject 退回(可選):
  · GROUP_MANAGER / GROUP_ADMIN 在 targetOrg 下的 Group 可呼叫 POST /action-requests/{id}/status
    把 status 設為 REJECTED;系統 emit factory-ops.action-request.rejected
  · 是否回寫到 originator 並通知,見 Q-20(暫定行為:emit event,originator 透過訂閱看到)
```

### 5. originatingOrgId 的語意(單跳模型下)

- `originatingOrgId` = **發派者所掛的 Org 節點**(actor 是該節點的 manager,且該節點是 targetOrg 的祖先)
- 跨層的「中間管理人鏈」(部門經理、處長等)**不在系統內顯式建模**;若需呈現「廠長派的 → 處長知道 → 部長知道」這種訊息流,由 NATS event 訂閱者 / Webhook 自行擴散
- 因此 §1.2 的「廠長 → 部門經理 → 課長」例子,在系統中表現為:**廠長一筆 dispatch,target = 課,originating = FAB(廠);中間管理人透過事件訂閱、報表得知**

### 6. 不使用 Saga / 跨 aggregate transaction

- 跨層 dispatch 為單一 ActionRequest 建立 + 後續 convert-to-task,皆為單文件 atomic
- ActionRequest ↔ Task 雙向關聯靠 reactor + outbox(同 v1.2)

---

## Consequences

### 正面
- **流程更貼近實務**:工廠中跨層派工通常是「直接交到課」+「中間人口頭知道」,刻意建模 relay 反而是 over-engineering
- **狀態機簡潔**:不再需要區分 SUBMITTED 是「在 originating」還是「relay 中」
- **欄位精簡**:刪除 `relayChain` 與 `RelayHop` VO,Task / ActionRequest aggregate 都更輕
- **權限模型清晰**:單一檢查「actor 是某祖先的 manager」+「target 是 leaf」
- **Q-14 對齊**:嚴格遵守「target 只能是 leaf」

### 負面
- **失去「逐層流轉」可視化**:若上層管理鏈想看到「我這層收到了什麼」,需透過 event 訂閱與報表處理;但這在實務上反而貼近「人鏈訊息」性質
- **Schema migration**:v1.2 的 `assignedToOrgId` / `relayChain` → v1.3 必須處理(本期無歷史資料,規範遷移即可)
- **若用戶確實要 multi-hop relay**,本 ADR 需重大修訂(Q-14 已明確,應屬封閉問題)

---

## Alternatives Considered

### A. 維持 v1.2 的 Dispatch + Relay 雙模式
- **優點**:中間層每層都有「我收到了」的紀錄
- **缺點**:每層 manager 都得做一次操作,工廠實務不會這麼做
- **不採用**:Q-14 明確收斂

### B. 改為「上層只能派下一級子節點(逐層)」+ 強制 Relay 鏈
- **優點**:管理層級嚴謹
- **缺點**:同 A,實務不需
- **不採用**

### C. 只允許 root manager dispatch
- **優點**:簡單、權力集中
- **缺點**:中間層 manager 的角色淪為虛設;§1.3「上級組織的 Manager」字面允許任一上級
- **本期不採用**;留 Q-18 待用戶最終確認

### D. Dispatch 帶 `viaOrgIds[]` 列出途經管理鏈
- **優點**:仍可呈現「處長、部長都知道」
- **缺點**:`viaOrgIds[]` 沒有強驗證資料(非真的有人按下確認),反成稽核噪音
- **不採用**:若需要,讓報表 / Notification 層計算 ancestry 即可

---

## Compliance / Validation

### 寫入時驗證(server)
- `targetOrg.type ∈ root.settings.leafTypes` → 否則 422 `target_must_be_leaf`
- `targetOrg` 在 actor 某 manager scope 的子孫(含同一節點) → 否則 403 `not_authorized_to_dispatch`
- `targetOrg` 與 actor 同 `rootOrgId` → 否則 403(隱含於 multi-tenant 隔離)
- ownerId 套用 ADR-0010 §3 規則
- 寫入 `history[]`:`action: ACTION_REQUEST_DISPATCHED`、`payload: { originatingOrgId, targetOrgId, ownerId }`

### 自課內提報(原既有流程)
- `POST /action-requests` 維持:`requesterId = actor`,`originatingOrgId = actor 所屬 leaf Org`,`targetOrgId = 同`(自己派給自己),`projectId` 必填
- 對應 INV-24 仍成立(target == origin == leaf)

### 稽核
- audit_logs:每次 dispatch / reject / convert-to-task 各一筆
- NATS topics:
  - `factory-ops.action-request.dispatched`
  - `factory-ops.action-request.submitted`(自課內,沿用)
  - `factory-ops.action-request.triaged`
  - `factory-ops.action-request.rejected`(新)
  - `factory-ops.action-request.resolved`

### 測試
- Unit:ancestry 判定、leaf 驗證、owner 規則(0/1/N)
- Integration:廠長(FAB.managerId)派到「裝配課」(SECTION leaf)成功
- Integration:廠長派到「製造處」(DIVISION 非 leaf) → 422
- Integration:廠長派到別人廠的 leaf → 403(跨 root)
- Integration:中間層 manager(處長)派到其子孫 leaf → 成功
- Integration:課的 GROUP_MANAGER reject → status REJECTED,event emit

---

## Notes for Next Stage(mongodb-modeler)

### Schema 變更(自 v1.2)
- `action_requests`:
  - 移除 `assignedToOrgId`、`relayChain`
  - 加 `targetOrgId`(必填,必 leaf)
  - `projectId` 仍 nullable(自課內提報為填,跨層派為空到 triage)
- 不再有 `RelayHop` 內嵌 VO

### 索引
- `action_requests`:
  - `{ rootOrgId: 1, originatingOrgId: 1, status: 1 }`(發起者查自派)
  - `{ rootOrgId: 1, targetOrgId: 1, status: 1 }`(承接者查在我這的)
  - `{ rootOrgId: 1, projectId: 1, status: 1 }` sparse
  - `{ rootOrgId: 1, requesterId: 1 }`

### Permission helper(無變動,簡化)
```kotlin
fun isAncestorOrSelf(possibleAncestor: OrgId, target: OrgId, rootOrgId: OrgId): Boolean {
    // 從 target 沿 parentId 往上 walk,看是否遇到 possibleAncestor
    // O(depth) ≤ orgMaxDepth = 5
}

fun canDispatch(actor: User, target: Organization): Boolean =
    target.type in root.settings.leafTypes &&
    actor.orgManagerScopes.any { mgrScope ->
        isAncestorOrSelf(possibleAncestor = mgrScope, target = target.id, rootOrgId = target.rootOrgId)
    }
```

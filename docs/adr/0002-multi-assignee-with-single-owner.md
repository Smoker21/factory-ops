# ADR-0002: 多人指派 + 單一負責人模型

**狀態**: Accepted
**日期**: 2026-05-03
**決策者**: spec-architect
**相關需求**: FR-3.3、FR-5、INV-1、INV-2、INV-3、US-B2、US-B3、US-B4

---

## Context(背景)

工廠值班場景的任務協作有兩個現實:

1. **協作多元**:一件「設備檢查」常常 2-3 位組員一起做(主執行 + 協助 + QA 旁觀),所以需要**多位 assignees**。
2. **責任單一**:管理上必須有**唯一一位**對結果負責(白板上是「誰的名字」)。多人共同負責 = 沒人負責,在工廠 KPI 與稽核上不可接受。

此外:
- **負責人會中途交接**(交班、請假、外調),需要清楚記錄誰在何時把責任轉給誰、為什麼。
- **`@me` 視圖**:Operator 在手機上需要分清楚「我負責的」 vs 「我參與的」兩個列表(US-B3)。
- **權限**:某些動作(關閉 Task、變更狀態)只允許負責人,某些動作(留言、回報進度)所有 assignees 都可。

需要決策的點:
- `ownerId` 與 `assignees` 的資料結構
- `ownerId` 是否必須 ∈ `assignees`
- 變更負責人的 API 形態
- assignment 是 embedded 還是獨立 collection

---

## Decision(決策)

### 1. 資料結構:`ownerId` + `assignees[]`(都 embedded 在 Task 內)

```kotlin
data class Task(
    // ...
    val ownerId: UserId,            // 必填、不可空
    val assignees: List<UserId>,    // 必含 ownerId、minSize = 1、distinct
    // ...
)
```

- `ownerId` 是**單獨欄位**(不只放在 `assignees` 裡)— 強調語意、查詢方便、避免「誰是 owner」要看陣列順序。
- `assignees` 是 `List<UserId>`(distinct),排序語意上不重要。
- **Invariant**: `ownerId ∈ assignees`(由 Aggregate 保護,不允許狀態違反)。

### 2. Assignment 不獨立 collection,直接 embed

Project↔User 關聯獨立成 `Membership` collection(Project 成員可達數十人、跨多 Project,有自己的 lifecycle);
但 Task↔User 不另開 collection,理由:
- 一個 Task 的 assignees 通常 ≤ 5 人,embed 不會膨脹。
- 取 Task 時直接拿到完整 assignment,不需多次查詢。
- 變更頻率低(指派完就少動)。
- 跨 Task 查「我參與的所有 Task」用 `{ assignees: userId }` 索引即可。

### 3. 變更指派的 API:三個獨立端點

```
POST   /tasks/{id}/assignees       # 加入(批次)
DELETE /tasks/{id}/assignees/{uid} # 移除(不能移除目前 owner)
POST   /tasks/{id}/owner           # 移轉負責人(可選擇舊 owner 是否保留為 assignee)
```

**為何不用 PUT 整包?**
- 整包 PUT 無法表達「我只想加一個」的意圖,前端要先 GET 再 PUT,容易出 race condition。
- 個別動作易於記錄歷程(`AssigneeAdded`、`AssigneeRemoved`、`OwnerTransferred` 三個獨立事件)。
- 權限粒度更精細:加 assignee 與移轉 owner 可有不同 RBAC 規則。

### 4. 移轉負責人的語意

```
POST /tasks/{id}/owner
{
  "newOwnerId": "u-002",
  "keepPreviousOwnerAsAssignee": true,
  "reason": "夜班交接"
}
```

行為:
1. 驗證 `newOwnerId` 是 active user。
2. 若 `newOwnerId ∉ assignees`,自動加入(等同隱含 `addAssignee`)。
3. 把 `ownerId` 改成 `newOwnerId`。
4. 若 `keepPreviousOwnerAsAssignee = false`,把舊 owner 從 assignees 移除。
5. 寫入 `OwnerTransferred` 歷程(payload 含 from / to / reason)。
6. 發 `OwnerTransferred` Domain Event(供通知、儀表板更新)。

### 5. 移除 assignee 的限制

`DELETE /tasks/{id}/assignees/{uid}`:
- 若 `uid == ownerId`,回 409 Conflict,訊息「請先移轉負責人後再移除」。
- 若移除後 `assignees.size == 0`,回 409 Conflict(雖然 owner 不能被直接移除,雙重保險)。

---

## Consequences(後果)

### 正面
- **語意清楚**:`ownerId` 一目了然,不必依賴陣列位置。
- **查詢快**:
  - 「我負責的」:`{ ownerId: userId }`
  - 「我參與的」:`{ assignees: userId }`(multikey index)
  - 「我參與但非負責的」:`{ assignees: userId, ownerId: { $ne: userId } }`
- **稽核完整**:每次指派變動都有獨立事件 / 歷程,可重建任何時間點的指派狀態。
- **API 直覺**:三個動作對應三個端點,RESTful 且符合「一個動作一個 endpoint」的習慣。

### 負面
- **冗餘**:`ownerId` 出現兩次(獨立欄位 + assignees 中)。空間成本可忽略,但寫入邏輯要小心兩邊同步。緩解:Aggregate root 在每個 mutation 後 self-validate;repository 層加 schema validator。
- **前端要實作三個動作**:相比一個 PUT 多一些 UI 工作。緩解:這也是優點 — 前端的「移除指派」按鈕可以針對 owner 變灰。

---

## Alternatives Considered(評估過的替代方案)

### A. 只用 `assignees[0]` 當 owner(陣列首位)
- **優點**:資料結構簡單
- **缺點**:語意脆弱、查詢語法醜(`assignees.0`)、無法用 multikey index 同時 cover 兩種查詢
- **不採用**

### B. 沒有 `ownerId`,只有 `assignees` + `role`(每個 assignee 帶 role)
```
assignees: [
  { userId: u1, role: OWNER },
  { userId: u2, role: ASSIST }
]
```
- **優點**:可表達多種角色
- **缺點**:
  - 「找 owner」要 filter array;「我負責的」查詢醜
  - 違反 invariant 的可能(0 個 OWNER 或 2 個 OWNER)
  - 工廠場景 90% 只需要 owner / assignee 兩種,過度設計
- **不採用**:可未來再演進

### C. Assignment 獨立 collection
```
{ _id, taskId, userId, role, assignedAt, assignedBy }
```
- **優點**:容易記錄歷史、跨 Task 查詢可優化
- **缺點**:Task 詳情要 join、indirect、N+1 風險、與 MongoDB 文件導向不合
- **不採用**:除非 assignees 預期 > 50 人 / Task,目前不需要

### D. `ownerId` 不必 ∈ `assignees`
- **優點**:減少冗餘
- **缺點**:
  - 「我所有相關的 Task」查詢要 `$or: [{ownerId}, {assignees}]`
  - 語意混亂:owner 算不算 assignee?業務上一定算
- **不採用**:強制 invariant 反而簡化下游

---

## Compliance / Validation

- Aggregate 在每個 mutation 結束時呼叫 `validateInvariants()`:
  ```kotlin
  require(ownerId in assignees) { "ownerId must be in assignees" }
  require(assignees.isNotEmpty()) { "assignees must not be empty" }
  require(assignees.distinct().size == assignees.size) { "assignees must be distinct" }
  ```
- 每次 owner 變更必發 `OwnerTransferred` 事件,事件 payload 包含 `fromUserId`、`toUserId`、`reason`、`occurredAt`。
- API 層的 DTO 在反序列化後也跑同樣的 validation(early fail)。

---

## Notes for Next Stage

- `tasks` 上的索引必須包含 `{ ownerId: 1, status: 1 }` 與 `{ assignees: 1, status: 1 }`(multikey)。
- DTO 與 domain class 都要對 `assignees` 強制 distinct(`Set` 還是 `List` + validator 都可,但對外 API 是 List 維持序列穩定)。

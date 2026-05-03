# ADR-0010: Organization 單一 Manager + 多位 Leaders 模型

**狀態**: Accepted
**日期**: 2026-05-04
**決策者**: spec-architect(依使用者 v1.3 拍板的 Q-15)
**相關需求**: §1.3、FR-Org、FR-Dispatch、INV-25、Epic I
**取代**: v1.2 中「`Organization.leaderId` 單一 + `ORG_MANAGER` 角色多人」的對偶設計

---

## Context(背景)

v1.2 設計為:每個 Organization 節點有 **單一 `leaderId`** 與 **多位 `ORG_MANAGER`(角色)**。使用者於 Q-15 拍板**反向**:

> 「ORG_Manager 只有一位,Leader 有多位」

因此每個 Organization 節點的對偶為:**單一 manager(經理本人,負責跨層派工授權)+ 多位 leaders(共同承擔該節點業務責任的負責人;例如部長 + 副部長共同對 ActionRequest 負責)**。

§1.3 原文「Action Request 負責人預設為組織負責人」中的「組織負責人」一詞,在 leaders 多人情況下出現語意空缺,本 ADR 明確補完。

需要決策:
1. 欄位設計(`managerId` vs `leaderIds[]` 各自儲存方式)
2. ActionRequest 派工時 `ownerId` 預設規則(0 / 1 / N 位 leader 的處理)
3. Manager 與 leader 角色的綁定機制
4. Manager 休假/變更時的處理(代理人機制?直接轉移?)
5. 與既有 RBAC、JWT claims、`User.orgManagerScopes[]` 的相容性

---

## Decision(決策)

### 1. Organization 節點欄位設計

```kotlin
data class Organization(
    val id: OrgId,
    val rootOrgId: OrgId,
    val parentId: OrgId?,
    val type: String,
    val name: String,
    val code: String,
    val managerId: UserId?,        // ← 單值,nullable;該節點唯一的 ORG_MANAGER(經理本人)
    val leaderIds: List<UserId>,   // ← 0..N;組織負責人(可有副座、共同主管)
    // ... timezone / locale / settings(僅 root)、history[]、schemaVersion 同前
)
```

- `managerId`:**最多一位**;為該節點唯一具備「跨層派工授權」的 user
- `leaderIds[]`:**0..N**;業務責任承擔者;ActionRequest 預設 `ownerId` 從這個集合中取

### 2. `ORG_MANAGER` 角色的綁定方式

`ORG_MANAGER` 角色的綁定**改為衍生於 `Organization.managerId`**,不再直接存於 `User.roles[]`:

- **權威來源**:`Organization.managerId`(指向某個 user)
- **`User.orgManagerScopes[]`**:**衍生欄位**;由 `Organization` 反查「該 user 是哪些節點的 `managerId`」並寫入(每次相關 Org 寫入或 user 登入時重算 / 快取)
- **`User.roles[]`**:若 `orgManagerScopes` 非空,自動含 `ORG_MANAGER`;空則不含

> **理由**:把「誰是經理」這個資訊收歸 Organization aggregate 一處,避免 User.roles + Organization 兩處要同步。

### 3. ActionRequest 派工時的 `ownerId` 預設規則(對應 INV-25 改寫)

當 ORG_MANAGER 對某 leaf Org `targetOrg` 派 ActionRequest 時:

```text
let n = targetOrg.leaderIds.size

if n == 0  → server 回 409  target_org_no_leader   (要求先指派 leader)
if n == 1  → ownerId = targetOrg.leaderIds[0]      (server 自動設定)
if n >= 2  → 派工方須在 dispatch payload 內指定 ownerId
              · ownerId 必須 ∈ targetOrg.leaderIds
              · 否則 server 回 422 owner_must_be_specified(未提供)
                          或 422 owner_not_in_leaders(不在集合中)
```

### 4. Manager / Leader 變動 API

| API | 用途 | 授權 |
|---|---|---|
| `POST /orgs/{orgId}/transfer-manager` | 變更 `managerId`(包含設置、轉移、清空) | `ORG_ADMIN` / `ADMIN` |
| `GET  /orgs/{orgId}/leaders` | 列出 `leaderIds[]` 對應的 User | 同 Org 讀權限 |
| `POST /orgs/{orgId}/leaders` | 新增一位 leader(以 userId) | `ORG_ADMIN` / `ADMIN` |
| `DELETE /orgs/{orgId}/leaders/{userId}` | 移除一位 leader | `ORG_ADMIN` / `ADMIN` |

(**移除** v1.2 的 `/orgs/{orgId}/transfer-leader` 端點,語意被 leaders 操作族取代。)

### 5. Manager 休假處理:**不引入正式代理人欄位**

§1.3 原文提及「上級組織的 Manager **與其指定的代理人**」。Q-15 答覆未提代理人,本 ADR 將「代理人」概念以下述兩個既有機制覆蓋,**不**新增 `deputyManagerId` 欄位:

1. **Operations 流程**:Manager 休假時,先呼叫 `transfer-manager` 把 `managerId` 換成暫代者,休假結束再 `transfer-manager` 回去。每次轉移皆寫入 `Organization.history[]` 供稽核。
2. **業務代理**:同節點的 `leaderIds[]` 即為共同負責人,跨層派工抵達時可由派工方指定 owner;這層即為「leader 級代理」。

> 若使用者後續確認「需要正式 deputy 欄位以保留 Manager 身份同時授權他人代行 dispatch」,本 ADR 會被 Q-19 的回答取代。**目前以無 deputy 為設計**。

### 6. 與 `Organization.history[]` / 稽核的整合

每次 `managerId` 變動或 `leaderIds[]` 增減,於 `history[]` 追加一筆 `HistoryEntry`(action 例:`MANAGER_TRANSFERRED` / `LEADER_ADDED` / `LEADER_REMOVED`),actor 為操作者 user。發出 Domain Events:

- `factory-ops.org.manager-transferred`
- `factory-ops.org.leader-added`
- `factory-ops.org.leader-removed`

(取代 v1.2 的 `factory-ops.org.leader-transferred`。)

### 7. `User.orgManagerScopes[]` 改為衍生 / 快取欄位

- 寫入時機:Organization 的 `managerId` 變更後,啟動 reactor 更新涉及 user 的 `orgManagerScopes`
- 讀取時機:JWT 簽發或 `/me` API 即時組合(若 reactor 尚未追上,仍可即時計算)
- DB 層仍保留欄位(冗餘)以加速 RBAC 判定;mongodb-modeler 可選擇不建索引,而是每次寫入時 atomic 更新

---

## Consequences

### 正面
- **語意符合使用者期望**:Q-15「Manager 一位、Leader 多位」直譯成欄位
- **派工 owner 明確**:0/1/N 規則完整,無歧義
- **Manager 權威集中**:`ORG_MANAGER` 角色的綁定不再分散在 `User.roles[]` 與 Organization 兩處
- **與 §1.3「組織負責人」相容**:在 multi-leader 情境下以「leaderIds 之一」明確補完語意
- **稽核完整**:Manager / Leader 異動全進 `history[]` 與 audit log

### 負面
- **多 leader 派工的 UX 多一步**:派工方須在 dispatch UI 上選 owner(若 leader > 1);可在前端做預選 / 建議,但語意上必填
- **舊 schema 遷移**:v1.2 的 `Organization.leaderId` 單值 → v1.3 `managerId` + `leaderIds[]`
  - **遷移建議**:`leaderIds = [oldLeaderId]`,`managerId = oldLeaderId`(若該 user 同時也有 v1.2 ORG_MANAGER 角色綁此節點)
- **`User.orgManagerScopes[]` 衍生欄位需保持同步**:reactor 需可靠處理 Organization manager 變更事件;若 reactor 失效,RBAC 計算仍可從 Organization aggregate 即時推導(防呆)

---

## Alternatives Considered

### A. 維持 v1.2(單 leader + 多 ORG_MANAGER)
- **不採用**:與 Q-15 答覆相反

### B. `managerId` 也允許 0..N
- **優點**:更彈性
- **缺點**:Q-15 明確說「只有一位」;且多人 manager 會讓「誰能派工」的稽核混亂
- **不採用**

### C. `leaderIds[]` 限制最多 N 人(例 ≤ 3)
- **優點**:預防失控
- **缺點**:工廠實際情境難說具體上限
- **不採用**:不設上限,但建議前端 UI 加 5 人軟上限警示(本期不寫入規則)

### D. 引入正式 `deputyManagerId` 代理人欄位
- **優點**:呼應 §1.3「指定的代理人」字面語意
- **缺點**:Q-15 未提;再加一個欄位後,「誰能 dispatch」的判定鏈變長(manager OR deputy)
- **本期不採用**:留 Q-19 待用戶確認。若用戶確認需要,本 ADR 會被覆蓋

### E. ActionRequest 多人 owner(`ownerIds[]`)
- **優點**:對應 multi-leader
- **缺點**:嚴重違反 §1.3「ownerId 必填且僅一位」核心 invariant(INV-1 同邏輯適用)
- **不採用**

---

## Compliance / Validation

### 寫入時驗證
- `Organization.managerId` 若非 null,該 user 必須:
  - 屬於同 `rootOrgId`
  - `active = true`
  - 不是已軟刪
- `Organization.leaderIds[]` 同上;且不可重複;不可為空陣列(允許 0 個)
- `transfer-manager` 與 leader CRUD 必須產生 `history[]` entry 與 Domain Event

### 派工時驗證
- `dispatch-action-request` 收到時:
  - `targetOrg.deletedAt == null`
  - `targetOrg.type ∈ root.settings.leafTypes`
  - actor 是 targetOrg 某個祖先(含自身)的 manager(透過 `Organization.managerId == actor.userId` 判定)
  - 套用 §3 owner 規則;違反規則回 409 / 422 對應錯誤碼

### 測試要求
- Unit:`leaderIds.size == 0 / 1 / N` 三組情境的 owner 判定
- Unit:`transfer-manager` 後 `User.orgManagerScopes[]` 反查正確
- Integration:多次 manager 轉移後 audit log 完整
- Failure:派工到 0 leader 的 leaf → 409;multi-leader 未指定 owner → 422

---

## Notes for Next Stage(mongodb-modeler)

### Schema 變更
- `organizations`:
  - 移除 `leaderId`(舊單值)
  - 加 `managerId: UserId?`
  - 加 `leaderIds: List<UserId>`(預設 `[]`)
- `users`:
  - `orgManagerScopes` 仍保留,但定位為 **denormalized cache**;權威來源是 `organizations.managerId` 反查
  - 不再以 `User.roles[]` 包含 `ORG_MANAGER` 為授權依據;授權判定走 `orgManagerScopes`(由 cache 或即時計算)

### 索引
- `organizations`:
  - `{ rootOrgId: 1, managerId: 1 }` sparse(快速反查「我是哪些節點 manager」)
  - `{ rootOrgId: 1, leaderIds: 1 }` sparse multikey(查 user 是哪些節點 leader)
- `users`:`{ rootOrgId: 1, orgManagerScopes: 1 }` sparse multikey

### Migration(若已有 v1.2 資料)
```kotlin
// v1.2 → v1.3
organizations.find().forEach { org ->
    val v12LeaderId = org["leaderId"]
    org.set("managerId", v12LeaderId)        // 暫定 leader 也是 manager
    org.set("leaderIds", listOfNotNull(v12LeaderId))
    org.unset("leaderId")
}
```

(此遷移於本期 spec 規劃中無實際歷史資料,僅供未來參考。)

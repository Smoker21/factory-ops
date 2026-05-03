# ADR-0004: Organization 樹 + 平面 Group(取代 v1.1 的 Group 多層樹)

> **檔名說明**:本 ADR 的檔名保留為 `0004-polymorphic-group-with-type.md` 以維護外部連結;**內文已於 v1.2 完整重寫**,標題改為「Organization 樹 + 平面 Group」。原 v1.1 內容(Group 多層樹 polymorphic)已被取代。

**狀態**: Accepted (v1.2,Supersedes v1.1 的同檔內容)
**日期**: 2026-05-03
**決策者**: spec-architect
**相關需求**: §1.3 系統概述、FR-Org、FR-Group、INV-12 / INV-13 / INV-21 / INV-22 / INV-23、Q-2(拍板,v1.2 重新拍板)、US-G1 ~ US-G9

---

## Context(背景)

v1.1.1 系統概述 §1.3 釐清了組織與工作的雙層概念:

> 「Organization 為組織樹狀圖,描寫群組之間的關係,但群組之間沒有隸屬關係。Group 是攤平的工作群組,其關係透過 Organization tree 進行管理。在領域模型上需要另外做 Organization 樹。」

> 「預設 Organizattion 為 廠(Fab) → 處(Division)→ 部門(Department)→ 課(Section),只有 Section 這層有實際工作與指派任務需求,其餘三層都是組織管理所需。」

這對 v1.1 的設計帶來根本變化:

- **v1.1 的 Group 是多層樹**(`Group.parentId`,polymorphic by `DEPARTMENT` / `SECTION` / `LINE` …):用單一 collection 表達組織階層 + 工作群組
- **v1.1.1 拆成兩個概念**:
  - **Organization 樹**(管理面):FAB → DIVISION → DEPARTMENT → SECTION,**只有 leaf(Section)能承載工作**;非 leaf 層只做管理(Template、人員報表權限、跨層派工)
  - **Group(工作面)**:平面結構,屬於唯一 leaf Organization,polymorphic by `type`(DEFAULT / LINE / TEAM / SHIFT…)

**為何 v1.1.1 要這樣分?**(從業務面回推):
1. **語意清晰**:管理階層(處/部/課)與工作群組(產線、班別、任務小組)是不同的概念,合併會混淆
2. **權限模型自然**:上級 Org 對下級 Org 有「派工 / 看報表」權,但不直接管下級 Group 的成員(那是 GROUP_MANAGER 的事)
3. **工作組織的彈性**:課內可能有多種工作群組(預設組、產線、班別、跨課 TEAM),這些不是部門子節點,而是課的成員透過不同 Group 的視角組合
4. **跨層派工**:廠長 → 處長 → 部長 → 課長的 dispatch chain 在 Org 樹上很自然;若 Group 是樹,「跨樹層」的語意會混淆

---

## Decision(決策)

### 主決策一:Organization 為 polymorphic 樹,只有 leaf 層做工作

```kotlin
data class Organization(
    val id: OrgId,
    val rootOrgId: OrgId,            // 該節點所屬樹的 root;root 自己時 = id
    val parentId: OrgId?,            // null 僅 root
    val type: String,                // FAB / DIVISION / DEPARTMENT / SECTION / 自訂
    val name: String,
    val code: String,                // 同 rootOrgId 內 unique
    val leaderId: UserId?,           // 該節點負責人;跨層 dispatch 預設 ActionRequest owner
    val timezone: String?,           // 僅 root 有效
    val locale: String?,             // 僅 root 有效
    val settings: OrgSettings?,      // 僅 root 有效
    val history: List<HistoryEntry>,
    // ...
)

data class OrgSettings(
    val orgMaxDepth: Int = 5,
    val leafTypes: List<String> = listOf("SECTION"),
    val attachmentMaxBytes: Long,
    val extras: Map<String, Any?>,
)
```

- 預設 4 層:FAB → DIVISION → DEPARTMENT → SECTION
- 深度上限預設 5(由 `root.settings.orgMaxDepth` 控制,可調 1–10)
- **Leaf 由 type 決定,而非「在樹中是否為終端節點」**:`isLeaf = (type ∈ root.settings.leafTypes)`;預設 `leafTypes = ["SECTION"]`
- 只有 leaf type 的 Organization 可以承載 Group / Project / Task

### 主決策二:Group 變平面結構,屬唯一 leaf Organization

```kotlin
data class Group(
    val id: GroupId,
    val rootOrgId: OrgId,
    val organizationId: OrgId,      // 必為 leaf Organization
    val type: String,               // DEFAULT / LINE / TEAM / SHIFT / 自訂(無 DEPARTMENT / SECTION)
    val name: String,
    val code: String,               // 同 rootOrgId 內 unique
    val attributes: Map<String, Any?>,
    val leaderId: UserId?,
    val history: List<HistoryEntry>,
    // ... 注意:沒有 parentId
)
```

**內建 Group type**(從 v1.1 移除 `DEPARTMENT` / `SECTION`,因為這兩個概念已上移到 Organization):
- `DEFAULT` — leaf Org 建立時自動產生的預設工作組(= Section 直屬)
- `LINE` — 產線
- `TEAM` — 臨時編組 / 任務小組
- `SHIFT` — 班別

### 主決策三:Group 之間不直接表達階層

若需階層意義(「裝配課的所有 Group」「製造處下所有 Group」),由 Organization 樹隱含表達:

```
查兩個 Group 的「組織關係」:
  → 各自查 organizationId(leaf Org)
  → 沿 Organization 樹回溯找共同祖先
```

API 路徑保持 `/orgs/{rootOrgId}/groups`(`orgId` 在 path 中為 root),query 加 `?organizationId=` 過濾屬某 leaf Org 的 Groups,加 `?underOrgId=` 過濾屬某非 leaf Org 子孫的 Groups(server 沿樹計算)。

### Invariants(server enforce)

| INV | 規則 |
|---|---|
| INV-12 | Organization 樹不可成環 |
| INV-13 | Organization 樹深度 ≤ root settings.orgMaxDepth |
| INV-21 | Project / Task 必須屬於 leaf Organization |
| INV-22 | Group 必須屬於唯一 leaf Organization |
| INV-23 | Group 為平面結構,無 parentId |
| INV-26 | Organization parentId 必須與本節點同 rootOrgId |
| INV-30 | 軟刪除非 leaf 節點時必須先處理子孫;軟刪 root cascade |

### 查詢策略

- **Organization 樹**:adjacency list(`parentId`)+ 可選 materialized path(`/<rootId>/<divId>/<deptId>/<secId>`,加 prefix index)
- **「我所屬的所有 Group」**:`db.group_memberships.find({ userId, leftAt: null })` → `groupId[]`
- **「製造處底下所有 leaf Org」**:`$graphLookup` from 製造處 by `parentId` → 篩 `type ∈ leafTypes` → 拿 `organizationId[]`
- **「製造處底下所有 Group」**:沿 ↑ 取 leaf Org 集合 → `db.groups.find({ rootOrgId, organizationId: { $in: [...] } })`
- **「我可見的 Project」**:`projects.find({ rootOrgId, $or: [{ memberIds: me }, { ownerId: me }, { groupIds: { $in: myGroupIds } }] })`

---

## Consequences(後果)

### 正面
- **語意清晰**:管理階層與工作群組分離,符合工廠真實組織與 §1.3 業務描述
- **權限模型自然**:`ORG_MANAGER` 對應 Org 節點,`GROUP_MANAGER` 對應 Group;不互相侵蝕
- **跨層派工天然支援**:ActionRequest 沿 Org 樹流動,語意非常乾淨(見 ADR-0008)
- **Group 變簡單**:沒有 parent / cycle / 深度問題,寫入路徑更直接
- **新增 Org type 零成本**:type + leafTypes 配置即可
- **Group 的多型仍保留**:DEFAULT / LINE / TEAM / SHIFT 仍以 type discriminator 表達

### 負面
- **查詢時要先沿 Org 樹找 leaf,再列出 leaf 下 Groups**:多一層索引;但 Org 樹節點數量小(每 root org 估 < 200 nodes),可接受
- **權限計算需考慮 Org 路徑**:JWT 帶 `orgPath[]`,server 比對「target org 是否在 user 所掛 org 節點的子孫」要做 ancestry 查詢(但路徑短,O(depth))
- **migration 成本(若已上線)**:Group v1.1 → v1.2 要拆解(Group `parentId` 鏈轉成 Organization 節點,leaf Group 變 DEFAULT)— **本期是 spec-only,DB 尚未上線,無實際 migration**
- **「跨課 TEAM」需要的設計**:`TEAM` Group 仍只能屬一個 leaf Org(MVP);若需 TEAM 跨多個 leaf Org,需另外擴(列入 Q-13 待確認)

---

## Alternatives Considered(評估過的替代方案)

### A. 沿用 v1.1 的 Group 多層樹
- **優點**:單一 collection、單一 polymorphic、邏輯一致
- **缺點**(也就是為何放棄):
  - 與 §1.3「組織管理 vs 工作」雙層概念不符
  - 上級「Org Manager」概念無法乾淨表達(Group 中誰是「處長」?)
  - 跨層派工 chain 不直觀(沿 Group `parentId` 鏈 + type 過濾,語意混淆)
  - 班別、產線、TEAM 與「部門 / 課」混在一棵樹很怪
- **不採用**:§1.3 已明確要求拆分

### B. 把所有東西放進 Organization 一個 aggregate(Organization tree 直接掛 User)
- **優點**:極簡 — 一棵樹搞定
- **缺點**:
  - 無法表達「同一 leaf Org 下的多個工作群組」(產線、班別、TEAM)
  - 跨課 TEAM 完全做不到
  - 班別、產線本就不是組織節點,硬塞語意扭曲
- **不採用**:Group 的存在價值就是「leaf Org 內的工作再切分」

### C. Organization 樹 + Group 樹(兩棵樹)
- **優點**:理論上最大彈性
- **缺點**:雙重階層 → 雙重 invariant、雙重權限計算 → 開發成本高
- **不採用**:§1.3 已明確要求 Group 攤平

### D. Organization 樹 + 平面 Group(本方案,已採用)

### E. 用 Materialized path / Nested Set 表達 Organization 樹
- **優點**:子樹查詢高效
- **缺點**:寫入時要更新所有後代節點的 path
- **本期評估**:Organization 樹節點少(< 200 / root)、寫入頻率低(一年數次組織調整),可採 hybrid:`parentId`(主)+ optional `path`(輔助 prefix 查詢)

---

## Compliance / Validation(合規驗證)

- 寫入 / 更新 Organization 時,server 必須:
  1. 驗證 `parentId` 屬同 `rootOrgId`(若非 null)
  2. 驗證不形成環:`isAncestor(newParent, self)` 必須為 false
  3. 驗證深度 ≤ `root.settings.orgMaxDepth`
  4. 驗證 `code` 同 rootOrgId 內 unique
- 寫入 Group:
  1. 驗證 `Group.organizationId` 對應的 Organization `type` 必須是 leaf type
  2. 驗證 `code` 同 rootOrgId 內 unique
  3. 寫入 leader:該 user 必須有同 Group 的 active GroupMembership(否則回 422)
- 軟刪除 Organization 非 leaf 節點:檢查 active 子孫節點 + 子孫下資源(否則回 409)
- 軟刪除 Group:檢查 active GroupMembership + 進行中 Project / Task(否則回 409)

---

## Notes for Next Stage(給 mongodb-modeler)

### Collections
- `organizations`:獨立 collection;包含 root + 所有子節點(以 `parentId` 區分)
- `groups`:獨立 collection;平面;以 `organizationId` 指向 leaf Org
- `group_memberships`:獨立 collection(同 v1.1 設計)

### 必要索引
- `organizations`:
  - `{ rootOrgId: 1, parentId: 1 }`(列子節點)
  - `{ rootOrgId: 1, type: 1, deletedAt: 1 }`(過濾 leaf / 特定 type)
  - `{ rootOrgId: 1, code: 1 }` unique partial(`deletedAt: null`)
  - `{ rootOrgId: 1, leaderId: 1 }` sparse
  - `{ rootOrgId: 1, path: 1 }` sparse(若採 materialized path)
- `groups`:
  - `{ rootOrgId: 1, organizationId: 1, type: 1 }`(leaf 下列 Group)
  - `{ rootOrgId: 1, code: 1 }` unique partial
  - `{ rootOrgId: 1, leaderId: 1 }` sparse
- `group_memberships`(同 v1.1):
  - `{ rootOrgId: 1, groupId: 1, leftAt: 1 }`
  - `{ rootOrgId: 1, userId: 1, leftAt: 1 }`
  - `{ groupId: 1, userId: 1 }` unique partial,where `leftAt: null`

### 樹查詢
- adjacency list + 可選 materialized path(`/<rootId>/<divId>/<deptId>/<secId>`)
- 子樹列舉:`$graphLookup` 起點 `parentId`,maxDepth = `orgMaxDepth`
- 若日後規模膨脹,考慮 closure table(獨立 `org_ancestors` collection),本期不做

### Helper derived 欄位
- `Organization.isLeaf: boolean`(由 `type ∈ root.settings.leafTypes` 計算;寫入時計算並存)
- 這讓「找所有 leaf Org」可直接 `find({ rootOrgId, isLeaf: true })`,不必每次比對 settings

---

## Amendment(v1.3, 2026-05-04)

依 v1.3 拍板的 Q-15,Organization 節點的「負責人」欄位拆分為:

- **`managerId`(單值,nullable)**:該節點唯一經理(對應 ORG_MANAGER 角色;為跨層 ActionRequest dispatch 授權的權威來源)
- **`leaderIds[]`(0..N)**:該節點業務負責人列表(ActionRequest dispatch ownerId 候選來源)

詳細設計與 ActionRequest owner 規則(0/1/N)見 **ADR-0010**(單 manager + 多 leaders);
跨層 dispatch 改為 **Single-hop Direct Dispatch to Leaf**(Q-14)見 **ADR-0008 v1.3 改寫**。

本 ADR 之 schema 範例中 `Organization.leaderId` 欄位於 v1.3 已被取代,索引 `{ rootOrgId: 1, leaderId: 1 }`(Org 部分)改為:
- `{ rootOrgId: 1, managerId: 1 }` sparse
- `{ rootOrgId: 1, leaderIds: 1 }` sparse multikey

`Group.leaderId`(Group 內組長)維持不變(屬於 Group 內成員概念,與 Organization.leaderIds 為不同概念)。

此外,Group v1.3 加 `settings: { qa: { dualSignRequired, requiredReviewerRoles[] }, extras }` 子文件,用於 Group-level 工作流客製(QA 雙簽);詳見 **ADR-0011**。

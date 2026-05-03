# 階段:規格與架構

**狀態**: READY_FOR_DATA_MODELING
**版本**: 1.3.0(依使用者於 §8 Q-1 ~ Q-17 全套拍板回答重整)
**完成時間**: 2026-05-04
**負責 agent**: spec-architect

---

## v1.3 變更摘要

### 架構級變動(本次)
1. **跨層派工:Single-hop Direct Dispatch**(Q-14 拍板,ADR-0008 全面改寫):
   - 刪除「逐層 Relay」流程、`relayChain[]`、`RELAYED` 子狀態、`POST /action-requests/{id}/relay` 端點
   - `targetOrgId` **必為 leaf Organization**(改名自 v1.2 `assignedToOrgId`)
   - 任一上級 manager(不限 root)直接派到子孫中的 leaf;留 Q-18 待用戶最終確認
2. **Organization 單 manager + 多 leaders**(Q-15 拍板,新 ADR-0010):
   - 移除 `leaderId`(單值),加 `managerId`(單值,nullable)+ `leaderIds[]`(0..N)
   - `ORG_MANAGER` 角色改為**衍生**,由 `Organization.managerId == userId` 反查
   - `User.orgManagerScopes[]` 改定位為衍生 / cache,reactor 維護
   - ActionRequest 預設 ownerId 三規則:0 → 409、1 → 自動、N → 派工方指定
   - 新增 API:`/orgs/{id}/transfer-manager`、`/orgs/{id}/leaders`、`/orgs/{id}/leaders/{userId}`
   - 移除 API:`/orgs/{id}/transfer-leader`(v1.2)
3. **Group QA 雙簽機制**(Q-7 拍板,新 ADR-0011):
   - Group 加 `settings.qa = { dualSignRequired, requiredReviewerRoles[] }`(預設 false / [])
   - Task 建立時 snapshot 為 `task.qaReviewPolicy`;Group settings 變更不影響進行中 Task
   - 多 Group 合併規則:dualSign OR、roles 聯集
   - AND 語意:每個列示角色都需各一筆 review(同 user 不可一次擔多角色;Q-23 OR 替代留待確認)
   - 新增 API:`PATCH /orgs/{id}/groups/{groupId}/settings`、`POST /tasks/{taskId}/review`
4. **跨 leaf 協作禁止**(Q-13 拍板):
   - INV-19 強化:Project `groupIds[]` 必須屬於 Project 自身的同一 leaf Org
   - 刪除「在 RBAC 允許下跨 leaf 共組」字眼
   - US-A5 改寫(夜班 Group 改為同一 SECTION 內的 SHIFT 型 Group)
5. **時間戳記策略明確化**(Q-17):
   - 儲存層:UTC `Instant`(BSON Date,可索引可運算)
   - Wire / 事件 payload:ISO 8601 + offset(例 `2026-05-04T08:30:00+08:00`)
   - 取消 GLOBAL Template 多語系欄位設計;字串欄位允許 Unicode 混合
   - Q-24 留待確認是否需要儲存層保留發起端原始 offset
6. **HR Mock REST 規格細化**(Q-16,ADR-0007 加 v1.3 Amendment):
   - Endpoint / payload / 認證 / 降級 / 字段對應 / 正式串接 checklist 詳列
7. **Daily Work Board**(Q-8,新增 §FR-Frontend):
   - 四區塊:my owned / my assigned / my group's overdue / pending review
   - 不做甘特圖

### Open Questions 拍板狀態(本次)
- Q-1 / Q-2 / Q-3 / Q-4 / Q-5 / Q-6 / Q-7 / Q-8 / Q-9 / Q-10 / Q-11 / Q-12 / Q-13 / Q-14 / Q-15 / Q-16 / Q-17 — **全部拍板**(詳見 requirements.md §8.1)

### 新衍生 Open Questions(待用戶最終確認;不阻擋 mongodb-modeler)
- **Q-18**:跨層 dispatch 發起者範圍 — 任一上級 manager,還是僅 root?(本期傾向前者)
- **Q-19**:Manager 休假代理機制 — 不另設 deputy 欄位,靠 transfer-manager?
- **Q-20**:ActionRequest leaf 端 reject 是否主動通知 originator?
- **Q-21**:QA Review reject 後是否清空既往 reviews?(預設清空)
- **Q-22**:Group settings 是否需專屬 versioning?(預設不做,沿用 history)
- **Q-23**:`requiredReviewerRoles` AND vs OR 語意?(預設 AND)
- **Q-24**:時間戳記儲存層是否保留發起端原始 offset?(預設不保留)

---

## 變動檔案

| 檔案 | 動作 |
|---|---|
| `docs/spec/requirements.md` | **重寫至 v1.3**(§1 不動,§2 起全部更新;§8 表格全部 Q-1~Q-17 拍板答覆補完中文敘述,新增 Q-18~Q-24) |
| `docs/spec/domain-model.md` | **重寫至 v1.3**(Org managerId/leaderIds、Group.settings.qa、Task.qaReviewPolicy/qaReviews、ActionRequest 移除 relayChain、單跳 dispatch sequence、新增 review sequence) |
| `docs/spec/openapi.yaml` | **重大更新至 v1.3**(transfer-manager / leaders / Group settings PATCH / Task review action 新增;relay 端點移除;Organization / Group / Task / ActionRequest schema 對齊) |
| `docs/adr/0007-user-hr-integration.md` | **加 v1.3 Amendment**:HR Mock REST 詳細規格 / 降級 / 正式串接 checklist |
| `docs/adr/0008-action-request-cross-org-dispatch.md` | **v1.3 全面改寫**:Single-hop Direct Dispatch to Leaf;移除 relay 雙模式 |
| `docs/adr/0010-org-manager-and-leaders.md` | **新增**:單 manager + 多 leaders 設計 + ActionRequest owner 規則 |
| `docs/adr/0011-group-settings-qa-dualsign.md` | **新增**:Group QA 雙簽 + Task 建立時 snapshot policy |
| `docs/spec/STATUS.md`(本檔) | **更新至 v1.3** |
| `STATUS.md`(根) | **更新至 v1.3** |

---

## 主要產出

### 規格與設計文件
- `docs/spec/requirements.md` v1.3.0
- `docs/spec/domain-model.md` v1.3.0(含 Mermaid 類圖、Org 樹圖、單跳 dispatch sequence、QA review sequence、狀態機)
- `docs/spec/openapi.yaml` v1.3.0(OpenAPI 3.1)

### Architecture Decision Records
- `0001-polymorphic-task-design.md`
- `0002-multi-assignee-with-single-owner.md`
- `0003-attachment-and-markdown-storage.md`
- `0004-polymorphic-group-with-type.md`(v1.2 重寫)
- `0005-organization-multi-tenancy.md`(v1.2 增補)
- `0006-template-versioning-and-instantiation.md`(v1.2 增補)
- `0007-user-hr-integration.md`(**v1.3 Amendment**:HR Mock REST 完整規格)
- `0008-action-request-cross-org-dispatch.md`(**v1.3 全面改寫**:Single-hop Direct Dispatch)
- `0009-event-distribution-nats-and-webhooks.md`
- `0010-org-manager-and-leaders.md`(**v1.3 新增**)
- `0011-group-settings-qa-dualsign.md`(**v1.3 新增**)

---

## 給 mongodb-modeler 的訊息

### 主要 Aggregates(v1.3)
- `Organization`(多型樹 + **`managerId` 單值 + `leaderIds[]`**;`rootOrgId` tenant 鍵)
- `User`(HR 投影;`accountName` 唯一鍵;**`orgManagerScopes[]` 為衍生 cache**,由 reactor 維護)
- `Group`(平面;屬唯一 leaf Org;**加 `settings.qa`**)
- `GroupMembership`、`Membership`(ProjectMembership)
- `Project`(必屬 leaf Org;**`groupIds[]` 必屬同一 leaf Org**)
- `Task`(**加 `qaReviewPolicy` snapshot + `qaReviews[]` append-only**)
- `ActionRequest`(**改名 `targetOrgId` 必為 leaf;移除 `relayChain[]`**)
- `ProjectTemplate`、`TaskTemplate`(GLOBAL / ORG scope;**取消多語系欄位設計**)
- `Attachment`、`Webhook`、`OutboxEntry`、`WebhookDeadLetter`、`AuditLog`

### 關鍵設計決策(影響 schema)— v1.3 新

1. **Tenant 鍵**:`rootOrgId`(沿用 v1.2)
2. **Organization 樹**:adjacency list(`parentId`)+ 可選 materialized `path`;深度 ≤ 5
3. **Organization derived 欄位**:`isLeaf`(寫入時計算並存)、`depth`、`path`(materialized 可選)
4. **Organization v1.3 重要欄位**:
   - **移除** `leaderId`(舊單值)
   - **加** `managerId: UserId?`(單值,nullable)
   - **加** `leaderIds: List<UserId>`(預設 `[]`)
5. **`User.orgManagerScopes[]`**:**denormalized cache**,權威來源是 `Organization.managerId` 反查;reactor 在 transfer-manager 後同步;不建索引(以 sparse multikey 提供 RBAC fallback)
6. **Group v1.3 重要欄位**:
   - **加** `settings.qa = { dualSignRequired, requiredReviewerRoles[] }`(必填,預設 false / `[]`)
7. **Task v1.3 重要欄位**:
   - **加** `qaReviewPolicy`(VO snapshot,必填)
   - **加** `qaReviews[]`(VO list,append-only,預估 ≤ 5)
8. **ActionRequest v1.3 重要欄位**:
   - **改名** `assignedToOrgId` → `targetOrgId`,且**必為 leaf**
   - **移除** `relayChain[]` 與 `RelayHop` VO
   - **保留** `originatingOrgId`(發起者所掛節點)
9. **Template scope 同集合**:`(scope, rootOrgId, code, version)` partial unique
10. **outbox 模式**:寫 aggregate + outbox 在同個 transaction;worker 推 NATS / Webhook
11. **JWT claims**:`rootOrgId` / `orgPath[]` / `orgManagerScopes[]` / `accountName` / `roles[]` / `groupIds[]`
12. **時間欄位**:儲存 UTC `Instant`(BSON Date);wire ISO 8601 + offset 由 application service 轉換

### Embed vs Reference 取捨提示(v1.3 新增 / 變更)

**Embed**:
- (沿用 v1.2 既有)
- `Organization.leaderIds[]`(短陣列 0..N,預估 ≤ 5)
- `Group.settings.qa`(VO)
- `Task.qaReviewPolicy`(VO snapshot)
- `Task.qaReviews[]`(VO list,預估 ≤ 5 / Task)

**Reference(獨立 collection)**:
- (沿用 v1.2 既有)

### 關鍵索引建議(v1.3 變更摘要)

#### organizations
- `{ rootOrgId: 1, parentId: 1 }`(子節點)
- `{ rootOrgId: 1, type: 1, deletedAt: 1 }`
- `{ rootOrgId: 1, code: 1 }` unique partial
- **`{ rootOrgId: 1, managerId: 1 }`** sparse(反查「我是哪些節點 manager」;v1.3)
- **`{ rootOrgId: 1, leaderIds: 1 }`** sparse multikey(反查「我是哪些節點 leader」;v1.3)
- `{ rootOrgId: 1, isLeaf: 1 }`
- `{ rootOrgId: 1, path: 1 }` sparse(若 materialized)

#### users
- `{ rootOrgId: 1, accountName: 1 }` unique partial
- `{ rootOrgId: 1, employeeNo: 1 }` unique sparse
- `{ rootOrgId: 1, active: 1 }`
- `{ hrSyncedAt: 1 }`

#### groups
- `{ rootOrgId: 1, organizationId: 1, type: 1 }`
- `{ rootOrgId: 1, code: 1 }` unique partial
- `{ rootOrgId: 1, leaderId: 1 }` sparse
- **`{ rootOrgId: 1, "settings.qa.dualSignRequired": 1 }`** sparse(找出有開雙簽的 Group;v1.3)

#### group_memberships(無變更)

#### projects(無變更;但 INV-19 校驗強化)

#### tasks
- `{ rootOrgId: 1, projectId: 1, status: 1, "schedule.due": 1 }`
- `{ rootOrgId: 1, ownerId: 1, status: 1 }`
- `{ rootOrgId: 1, assignees: 1, status: 1 }`(multikey)
- `{ rootOrgId: 1, type: 1, status: 1 }`
- **`{ rootOrgId: 1, status: 1, "qaReviewPolicy.dualSignRequired": 1 }`** sparse(Pending Review 看板;v1.3)
- text:`title + descriptionMarkdown`

#### action_requests(v1.3 變更)
- `{ rootOrgId: 1, originatingOrgId: 1, status: 1 }`(發起者查自派)
- **`{ rootOrgId: 1, targetOrgId: 1, status: 1 }`**(承接者查目前手上;**改名自 v1.2 assignedToOrgId**)
- `{ rootOrgId: 1, projectId: 1, status: 1 }` sparse
- `{ rootOrgId: 1, requesterId: 1 }`

#### project_templates / task_templates(無變更)

#### outbox_entries(無變更)

#### webhooks(無變更)

### Schema 演進
- 全 collection 預留 `schemaVersion: int`(初版 = 1)
- `rootOrgId` non-null(除 `organizations` 自身)
- `Template` 的 `rootOrgId` 在 GLOBAL 時為 null(partial unique 處理)
- `Group.settings` 必填(預設 `{ qa: { dualSignRequired: false, requiredReviewerRoles: [] }, extras: {} }`)
- `Task.qaReviewPolicy` 必填(snapshot,Task 建立時 server 計算寫入)

### 一致性與驗證(v1.3 重點)
- Aggregate invariants 在 Kotlin domain class 層強制(`require {}`)
- Production 環境啟用 MongoDB JSON Schema validator
- `Task.attributes` server JSON Schema 嚴格驗證;`Group.attributes` 寬鬆;`Group.settings.qa` 嚴格(JSON Schema)
- Template version 凍結
- **Org 樹 invariants**:cycle / 深度 / parentId 同 rootOrgId(寫入時驗證)
- **Org leaf 驗證**:Group / Project / ActionRequest.targetOrgId 寫入時驗證對應 Org 是 leaf
- **Org manager / leaders**:user 必須 active 且同 rootOrgId
- **跨層 dispatch 權限**:server 沿樹計算 ancestry(actor manager scope ⊇ targetOrg)
- **QA review snapshot**:Task 建立時 application service 計算合併 policy 並寫入

### 跨租戶隔離(無變更)
- `RootOrgScopedRepository<T>` 抽象,從 SecurityContext 注入 `rootOrgId`
- 禁止「裸 query」
- `$graphLookup` 加 `restrictSearchWithMatch: { rootOrgId: ... }`

### 注意事項(v1.3 重點)
1. `history[]` append-only,**每個寫入操作都應產生 entry**
2. `Task.assignees` distinct
3. ActionRequest ↔ Task 靠 reactor + outbox 維護最終一致
4. `Attachment.ownerResourceId` 可空
5. **時間欄位**:儲存 UTC `Instant`,wire ISO 8601 + offset(轉換在 application / DTO 層)
6. **Organization 樹 invariant 由 server 強制**:cycle / 深度 / 同 rootOrgId
7. **Template scope 切換禁止**:GLOBAL Template 不可改成 ORG(只能 fork);反之亦然
8. **HR 同步失敗不阻塞登入**(graceful degrade,見 ADR-0007)
9. **outbox 寫入與 aggregate 更新必在同 transaction**(否則事件可能遺失)
10. **`User.orgManagerScopes[]` cache 由 reactor 維護**:transfer-manager 寫入後 emit `org.manager-transferred` event,reactor 更新涉及 user 的 cache
11. **`Task.qaReviewPolicy` snapshot 不可變**:Group settings 後續修改不影響該 Task

---

## 待使用者確認的開放問題

### 已拍板(Q-1 ~ Q-17)
詳見 requirements.md §8.1。本次全部拍板。

### 衍生 Open Questions(可平行進行 mongodb-modeler 建模,後續再依答覆微調)

| ID | 問題 | 預設行為 |
|---|---|---|
| Q-18 | 跨層 dispatch 發起者範圍(任一上級 manager vs 僅 root) | 預設:任一上級皆可 |
| Q-19 | Manager 休假代理機制(deputy 欄位 vs transfer-manager) | 預設:無 deputy,靠 transfer-manager |
| Q-20 | leaf 端 reject ActionRequest 是否主動通知 originator | 預設:只 emit event,不主動 in-app notify |
| Q-21 | QA Review reject 是否清空既往 reviews | 預設:清空(重新走流程) |
| Q-22 | Group settings 是否需專屬 versioning | 預設:沿用 history,不另做 |
| Q-23 | requiredReviewerRoles AND vs OR | 預設:AND |
| Q-24 | 時間戳記儲存層是否保留發起端原始 offset | 預設:不保留(UI 用 root tz 顯示) |

---

## 驗收檢核清單(v1.3)

請使用者依下列項目檢視里程碑 1 v1.3 產出:

- [ ] §1 系統概述沒有改動(本次以 §1 為唯一真實來源,§2 起重整)
- [ ] Organization 加 `managerId`(單值)+ `leaderIds[]`(0..N),`leaderId` 移除是否符合 Q-15 預期
- [ ] ActionRequest `targetOrgId` 必為 leaf、移除 relayChain 是否符合 Q-14 預期
- [ ] §1.2 第 4 個情境(廠長 → 部門經理 → 課長)在系統內以 single-hop 模型化(中間人鏈為訊息流,不建模 state)是否可接受
- [ ] Group `settings.qa` 雙簽機制 + Task snapshot at creation 是否符合 Q-7 預期
- [ ] 跨 leaf 協作禁止(INV-19 強化、US-A5 改寫)是否符合 Q-13 預期
- [ ] HR Mock REST 規格(ADR-0007 v1.3 Amendment)細節是否合理
- [ ] 時間戳記策略(儲存 UTC + wire ISO 8601 + offset)是否符合 Q-17 預期
- [ ] 「組織負責人」在 multi-leader 情境下的派工 owner 規則(0/1/N)是否合理
- [ ] 角色 `ORG_MANAGER` 改為衍生(由 Organization.managerId 反查)是否合理
- [ ] OpenAPI v1.3 新端點是否齊全(transfer-manager / leaders / Group settings PATCH / Task review)
- [ ] 7 個衍生 Q(Q-18 ~ Q-24)是否需要先回答再進入里程碑 2

---

## 啟動下一棒的指令(驗收通過後)

```
> Spec v1.3 已驗收(可附帶 Q-18 ~ Q-24 的回答),請用 mongodb-modeler 進行資料建模。
```

# 工廠值班工作管理系統 — 開發狀態

**最後更新**: 2026-05-06
**目前版本**: v1.0.0-M4 / spec v1.3.0 / data-model v1.0.0
**下一個里程碑**: M5+(主題待規劃)

---

## 里程碑進度

| # | 里程碑 | 負責 agent | 狀態 | 詳細紀錄 |
|---|---|---|---|---|
| 1 | 規格 + 領域設計 | spec-architect | ✅ COMPLETED | `docs/spec/STATUS.md` |
| 2 | 資料模型 | mongodb-modeler | ✅ COMPLETED | `docs/data/STATUS.md` |
| 3 | 後端 + 前端骨架 | quarkus-backend-builder → react-frontend-builder | ✅ COMPLETED | `docs/backend/STATUS.md`、`docs/frontend/STATUS.md` |
| 4 | 測試 + 審查 + 文件 + 部署 | test-engineer → code-reviewer → doc-devops | ✅ COMPLETED (2026-05-04) | `docs/test/STATUS.md`、`docs/review/STATUS.md`、`docs/devops/STATUS.md`、CHANGELOG `[1.0.0-M4]` |

各里程碑完成摘要見 `CHANGELOG.md`。

---

## P1 / P2 Backlog(M4 code review 留下)

### P1(下一版本迭代前修)

| 編號 | 位置 | 說明 |
|---|---|---|
| S-009 | `frontend/src/api/client.ts` | JWT 存 localStorage → 改 httpOnly cookie(XSS 風險) |
| P-001 | 所有列表 Resource | Cursor pagination 實作(目前回傳 null cursor,NFR p95 < 300ms 未達) |
| S-016 | AuthService | 連續失敗登入鎖定機制缺失 |
| S-008-ext | RevocationStore | Logout blacklist 後需定期清理過期 token(TTL index) |
| C-005 | DispatchService.convertToTask | convertToTask 未驗證 AR 狀態 + priority 映射 |
| C-006 | TaskService | force-complete 只驗 dualSign,未驗 actor 是 Group member |
| C-007 | TaskService.buildQaReviewPolicy | mapNotNull 靜默忽略找不到的 group → 改 throw |
| C-008 | TaskService | IN_REVIEW → DONE 兩條路徑行為不一致 |
| C-009 | ProjectService | PAUSED → COMPLETED 狀態流缺漏 |
| C-010 | ProjectService.createProject | 未驗證 `due > start` |
| C-011 | TaskService.createTask | 未驗證 `dueAt ≥ project.startAt` |
| C-012 | TaskService.addAssignees | 未驗證 user 全部 active 且同 rootOrgId |
| C-014 | OrganizationService.deleteOrg | 未擋有 active resources(Group/Project) |
| C-015 | OutboxPoller | retryCount 無上限且無 dead-letter |
| P-002 | 所有列表端點 | `?since=` / ETag / If-Modified-Since 增量同步未實作 |
| S-010 | `application.properties` | CORS 設定缺 prod origin env 機制(現已修 env,但 prod override 待驗) |
| S-011 | UserService.createUser | 明文 defaultPassword + 無強度檢查 |
| S-013 | UserRepository.searchByKeyword | regex ReDoS 潛在風險 |
| S-015 | AuthResource | logout / changePassword 無 rate-limit |
| S-017 | LoginRequest | password 缺 @Size(max=128) → bcrypt CPU 高 |

### P2(後續迭代)

| 編號 | 說明 |
|---|---|
| C-017 | transfer-manager 後 JWT orgManagerScopes 過期 |
| C-018 | addAssignees 後未重做 INV-1 owner 檢查 |
| P-004 | TaskService.listTasks type 用 in-memory filter |
| P-005 | DispatchService.listActionRequests requesterId in-memory filter |
| P-006 | listOrgs(underOrgId) 無 rootOrgId 條件 |
| P-007 | OutboxPoller 無 sharding key |
| P-008 | 前端缺 React.memo / 虛擬化 |
| P-009 | ProjectService.addMember 5 個 query 可合 $addToSet |
| P-010 | bundle size 未驗;MUI 全量引入 |
| S-014 | MarkdownRenderer attachment URL 未驗 ObjectId 格式 |
| S-018 | login 失敗 audit trail 不利分析 |
| S-019 | GlobalExceptionMapper log 可能帶到 password |
| S-020 | /health path 未加入公開白名單 |
| M-001 | OutboxPoller 留 TODO(CLAUDE.md 禁止) |
| M-002 | i18n en-US.json 留 _comment: TODO |
| M-004 | TaskService.createTask inline mapping 25 行 |
| M-005 | TaskService TaskMapper.run idiom 困惑 |
| M-006 | GroupService.listGroups in-memory filter |
| M-007 | UserService.searchUsers 無 rootOrgId scope |
| 其餘 Info | 見 `docs/review/code-review-report.md` §2.5 |

---

## 里程碑 1 — v1.3 重整摘要

**狀態**: READY_FOR_REVIEW
**完成時間**: 2026-05-04

### v1.3 變更(本次,基於使用者 Q-1~Q-17 全套拍板)

1. **Single-hop Direct Dispatch to Leaf**(Q-14):刪除多層 Relay 流程;`targetOrgId` 必為 leaf;ActionRequest 移除 `relayChain[]`、`RELAYED` 子狀態、`/relay` 端點;ADR-0008 全面改寫
2. **Organization 單 manager + 多 leaders**(Q-15):移除 `leaderId` 單值;加 `managerId`(單)+ `leaderIds[]`(0..N);ORG_MANAGER 改為衍生;新 ADR-0010
3. **Group QA 雙簽**(Q-7):Group 加 `settings.qa`;Task 建立時 snapshot policy(Group settings 後續變更不影響);新 ADR-0011
4. **跨 leaf 協作禁止**(Q-13):INV-19 強化;US-A5 改寫
5. **時間戳記**(Q-17):儲存 UTC `Instant`,wire ISO 8601 + offset;字串欄位允許 Unicode 多語系混合;取消 GLOBAL Template 多語系欄位設計
6. **HR Mock REST 規格**(Q-16):ADR-0007 加 v1.3 Amendment(完整規格 / 降級 / 正式串接 checklist)
7. **Daily Work Board**(Q-8):新 §FR-Frontend(my owned / my assigned / overdue / pending review 四區塊;不做甘特圖)
8. **Q-1 ~ Q-17 全部拍板並補完中文敘述**

### Q-1 ~ Q-17 拍板狀態(本次)
全部標**拍板**;詳見 docs/spec/requirements.md §8.1 表格(每題附完整中文答覆)。

### 衍生新 Open Questions(待用戶最終確認;不阻擋下一棒)
- Q-18 跨層 dispatch 發起範圍(任一上級 vs 僅 root)
- Q-19 Manager 休假代理(deputy 欄位 vs transfer-manager)
- Q-20 leaf 端 reject 是否主動通知 originator
- Q-21 QA Review reject 是否清空既往 reviews
- Q-22 Group settings 是否需專屬 versioning
- Q-23 requiredReviewerRoles AND vs OR
- Q-24 時間戳記儲存層是否保留發起端原始 offset

### 變動檔案

| 檔案 | 動作 |
|---|---|
| `docs/spec/requirements.md` | **重寫至 v1.3**(§1 不動,§2~§10 全部更新;§8 表格全 Q 補完中文敘述,新增 Q-18~Q-24) |
| `docs/spec/domain-model.md` | **重寫至 v1.3**(Org managerId/leaderIds、Group.settings.qa、Task qa snapshot、ActionRequest 單跳模型、新 sequence diagrams) |
| `docs/spec/openapi.yaml` | **重大更新至 v1.3**(transfer-manager / leaders / Group settings PATCH / Task review;移除 relay;Schema 對齊) |
| `docs/adr/0007-user-hr-integration.md` | **加 v1.3 Amendment**:HR Mock REST 完整規格 |
| `docs/adr/0008-action-request-cross-org-dispatch.md` | **v1.3 全面改寫**:Single-hop Direct Dispatch |
| `docs/adr/0010-org-manager-and-leaders.md` | **新增** |
| `docs/adr/0011-group-settings-qa-dualsign.md` | **新增** |
| `docs/spec/STATUS.md` | **更新至 v1.3** |
| `STATUS.md`(本檔) | **更新至 v1.3** |

### 累計設計決策(v1.3)
1. Bounded Contexts:同 v1.2(Identity & Multi-Tenancy / Workforce / Project Management / Template / Content & Attachment / External Integration / Notification 預留)
2. 主要 Aggregates:`Organization`(樹,managerId+leaderIds)、`User`(HR 投影,orgManagerScopes 衍生)、`Group`(平面,加 settings.qa)、`GroupMembership`、`Membership`、`Project`(同 leaf 強化)、`Task`(加 qaReviewPolicy/qaReviews)、`ActionRequest`(targetOrgId leaf)、`ProjectTemplate`、`TaskTemplate`、`Attachment`、`Webhook`
3. **Organization 多型樹 + 單 manager + 多 leaders**(ADR-0004 + ADR-0010)
4. **Group 多型 + 平面 + settings.qa**(ADR-0004 + ADR-0011)
5. **多租戶**:`rootOrgId`(同 v1.2;ADR-0005)
6. **Template scope**:同 collection + scope 欄位(同 v1.2;ADR-0006)
7. **HR 整合**:Mock REST(ADR-0007 v1.3 Amendment)
8. **跨層派工:Single-hop Direct Dispatch**(ADR-0008 v1.3)
9. **QA 雙簽 + Snapshot at Task Creation**(ADR-0011)
10. **事件分發**:NATS + Webhook + outbox(同 v1.2;ADR-0009)
11. Task 多型(ADR-0001)、指派模型(ADR-0002)、附件(ADR-0003)維持
12. 稽核:每 aggregate embed `history[]`,軟刪除,7 年保留(3 年以上冷儲存)
13. API:cursor pagination、欄位投影、ETag、JWT 帶 rootOrgId/orgPath/orgManagerScopes、RFC 7807 problem+json
14. 時間:儲存 UTC `Instant`,wire ISO 8601 + offset

---

## 給 mongodb-modeler 的銜接訊息

詳見 `docs/spec/STATUS.md`(完整 collections + 索引建議)。**v1.3 重點**:

### Schema 變更摘要(自 v1.2)

#### `organizations`
- **移除** `leaderId`(舊單值)
- **加** `managerId: UserId?`(單值,nullable;對應 ORG_MANAGER 角色權威來源)
- **加** `leaderIds: List<UserId>`(0..N;ActionRequest dispatch ownerId 候選來源)

#### `groups`
- **加** `settings.qa: { dualSignRequired: bool, requiredReviewerRoles: [Role] }`(必填,預設 false / `[]`)

#### `tasks`
- **加** `qaReviewPolicy: { dualSignRequired, requiredReviewerRoles[], snapshotAt, sourceGroupIds[] }`(必填,Task 建立時 snapshot)
- **加** `qaReviews: [{ reviewerId, reviewerRole, decision, reason?, at }]`(append-only,預估 ≤ 5 / Task)

#### `action_requests`
- **改名** `assignedToOrgId` → `targetOrgId`(且 server 強制必為 leaf)
- **移除** `relayChain: [RelayHop]`
- **移除** `RelayHop` 內嵌 VO 整體

#### `users`
- `orgManagerScopes` 仍保留,但**改為 denormalized cache**;權威來源 `Organization.managerId` 反查;reactor 在 transfer-manager 後同步;不建索引(以 sparse multikey 提供 RBAC fallback)

### 新 / 變更索引摘要

| Collection | 索引 | 用途 |
|---|---|---|
| organizations | `{ rootOrgId: 1, managerId: 1 }` sparse | 反查「我是哪些節點 manager」(v1.3 新) |
| organizations | `{ rootOrgId: 1, leaderIds: 1 }` sparse multikey | 反查「我是哪些節點 leader」(v1.3 新) |
| groups | `{ rootOrgId: 1, "settings.qa.dualSignRequired": 1 }` sparse | 找出開雙簽的 Group(v1.3 新) |
| tasks | `{ rootOrgId: 1, status: 1, "qaReviewPolicy.dualSignRequired": 1 }` sparse | Pending Review 看板查詢(v1.3 新) |
| action_requests | `{ rootOrgId: 1, targetOrgId: 1, status: 1 }` | 承接 leaf 查目前手上(改名自 v1.2 assignedToOrgId) |

### 關鍵設計問題請 mongodb-modeler 拍板
1. **Org 樹**:純 adjacency list 還是 hybrid(adjacency + materialized path)?
2. **Template scope 同集合**(spec 已推薦)
3. **User accountName 唯一鍵**:`(rootOrgId, accountName)` partial?
4. **outbox TTL 策略**:30 天 / 60 天 / 永久?
5. **Org 節點 derived `isLeaf` / `path`**:寫入時計算並存(spec 推薦存)
6. **`User.orgManagerScopes[]` cache**:reactor 維護 vs 即時計算? (spec 推薦 reactor + 即時計算 fallback)
7. **`Task.qaReviewPolicy` 是否需 versioning**:目前不需(snapshot 不可變;Group settings 變更不影響已存在 Task,新建 Task 自動拿新 policy)

---

## 待使用者確認的開放問題

### 已全部拍板(Q-1 ~ Q-17)
- 詳見 docs/spec/requirements.md §8.1
- 不阻擋進入里程碑 2

### 衍生待確認(Q-18 ~ Q-24)
- 預設行為已寫入 spec,可平行進行 mongodb-modeler 建模;後續若答覆改變,微調對應章節

| ID | 問題 | 預設行為 |
|---|---|---|
| Q-18 | 跨層 dispatch 發起範圍 | 任一上級皆可 |
| Q-19 | Manager 休假代理 | 無 deputy 欄位 |
| Q-20 | leaf reject 主動通知 originator? | 只 emit event |
| Q-21 | Review reject 清空既往 reviews? | 清空 |
| Q-22 | Group settings 專屬 versioning? | 不做 |
| Q-23 | requiredReviewerRoles 語意 | AND |
| Q-24 | 儲存層保留原始 offset? | 不保留 |

---

## 驗收檢核清單(v1.3)

- [ ] §1 系統概述沒有改動(本次以 §1 為唯一真實來源,§2 起重整)
- [ ] Organization 加 managerId(單值)+ leaderIds[](0..N)是否符合 Q-15 預期
- [ ] ActionRequest targetOrgId 必為 leaf、移除 relayChain 是否符合 Q-14 預期
- [ ] §1.2 第 4 情境以 single-hop 模型化是否可接受
- [ ] Group settings.qa + Task qaReviewPolicy snapshot 是否符合 Q-7 預期
- [ ] 跨 leaf 協作禁止(INV-19 強化)是否符合 Q-13 預期
- [ ] HR Mock REST 規格是否合理(ADR-0007 v1.3 Amendment)
- [ ] 時間戳記策略(UTC 儲存 + ISO 8601+offset wire)是否符合 Q-17 預期
- [ ] OpenAPI v1.3 新端點齊全(transfer-manager / leaders / Group settings PATCH / Task review)
- [ ] 7 個衍生 Q(Q-18 ~ Q-24)是否需先回答

---

## 啟動下一棒的指令(驗收通過後)

```
> Spec v1.3 已驗收(可附帶 Q-18 ~ Q-24 的回答),請用 mongodb-modeler 進行資料建模。
```

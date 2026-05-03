# 工廠值班工作管理系統 — 需求規格書

**版本**: 1.3.0
**最後更新**: 2026-05-04
**負責 agent**: spec-architect

> **v1.3 變更摘要**(依使用者於 §8 對 Q-1 ~ Q-17 的拍板回答全套重整;§1 不動):
> 1. **跨層派工改為 Single-hop Direct Dispatch to Leaf**(Q-14):刪除「多層 Relay」流程;`targetOrgId` 必為 leaf;ActionRequest 移除 `relayChain[]` 與 `RELAYED` 子狀態;對應端點 `/relay` 移除。**任一上級 manager**(不限 root)皆可 dispatch 到其子孫中的 leaf,但留 Q-18 待最終確認。
> 2. **Organization 改為「單 Manager + 多 Leaders」**(Q-15):每節點 `managerId`(單值)+ `leaderIds[]`(0..N);ActionRequest 預設 ownerId 規則重寫(0 → 409、1 → 自動、N → 派工方指定);`ORG_MANAGER` 角色綁定改為「該 user 是某節點 `managerId`」衍生而來;新 ADR-0010。
> 3. **Group 加 `settings.qa` 雙簽機制**(Q-7):`dualSignRequired` + `requiredReviewerRoles[]`;Task 建立時 snapshot 該 policy(不受 Group settings 後續變更影響);新增 `POST /tasks/{taskId}/review` action;新 ADR-0011。
> 4. **跨組協作禁止**(Q-13):刪除「在 RBAC 允許下跨 leaf 共組」字眼;Project `groupIds[]` 必須屬於 Project 自身的同一個 leaf Org;US-A5 改寫(夜班 Group 為同 SECTION 內的 SHIFT 型 Group)。
> 5. **時區感知時間戳記**(Q-17):字串顯示與 API 傳輸用 ISO 8601 + offset(例 `2026-05-04T08:30:00+08:00`);儲存層仍為 UTC `Instant`(運算 / 索引一致),offset 來源由前端依使用者 locale 推導。**取消** GLOBAL Template 多語系欄位設計;所有 string 欄位允許 Unicode 混合多語系輸入。
> 6. **HR Mock REST 規格細化**(Q-16):ADR-0007 加入 Mock REST 完整規格 / 降級策略 / 字段對應 / 正式串接 checklist。
> 7. **每日工作看板**(Q-8):新增 FR-Frontend 章節定義 Daily Work Board 內容(my owned / my assigned / overdue / pending review)。
> 8. **多語系 i18n 策略更新**(Q-17):root locale + 使用者瀏覽器 locale 二擇一;不做 GLOBAL Template `name_zh` / `name_en` 等多欄位設計。
> 9. **角色描述更新**:`ORG_MANAGER` 改為「節點唯一一位」,描述對齊 Q-15。
> 10. **Open Questions 整理**:Q-1 ~ Q-17 全部標**拍板**並補完整中文敘述;新增 Q-18 ~ Q-24 待用戶確認的衍生問題。

---

## 1. 系統概述

> **本章為定稿來源**;§2 起所有需求、Invariants、RBAC、領域模型、API 皆以本章為準。任何衝突以 §1 勝出。

本系統服務工廠**值班團隊**,協助跨班別追蹤專案、任務、現場異常處置與交班事項。系統採 **API-first** 設計,前端先以 React Web(行動裝置友善)交付,後續可擴充原生 App、看板大螢幕、車間 PDA 等多端應用。

### 1.1 業務目標
- 把現場「口頭交辦」「便利貼」「Excel 工單」改為**可追蹤、可稽核**的數位工作流。
- 同一任務可指派給**多位值班人員**,但**僅有一位負責人**對結果負責。
- 在工廠跨班別接續工作時,提供**清楚的責任移轉與歷程記錄**。
- 收集現場「動作需求」(ActionRequest)→ 由領班分派轉換為一到多個 Task,形成完整閉環。
- **支援多廠區 / 多公司部署**(Organization 為頂層 tenant)。
- **第一線 Group 為實際工作組織,Task 只能指派到第一線 Group**
- **以 Organization 作為組織管理單位**,可任意巢狀,反映真實工廠與企業組織,但此組織只負責 Template 管理、人員報表權限管理,或將任務指派給下級組織
- **以 Template(範本)加速重複性 Project / Task 建立**(例月度設備保養、標準異常處置 SOP)。

### 1.2 使用情境舉例
- 夜班發現空壓機異音 → 建立 ActionRequest → 早班班長指派為「異常處置 Task」→ 維修指派負責人 → 完成後關閉並回寫到原 ActionRequest。
- 月度設備保養專案 → **從 ProjectTemplate「月度保養 v3」建立** → 自動帶入 5 個預設 Task → 拆解到產線 Group → 看板追進度 → 逾期自動提醒。
- 跨組臨時任務:廠長把「裝配課」+「夜班 Group」+「品保專員」三個 Group 共同納入 Project,所有成員皆可見。
- 廠級會議時:廠長把「裝射新機台」這個任務交辦給某個部門 → 部門經理收到 ActionRequest 之後,將任務委派給課長 → 課長拆解 ActionRequest 之後交辦給員工進行

### 1.3 組織管理與工作任務
- 只有最底層的Group負責實際工作,Group 內的 Admin 可以進行群組設定,如群組人員與各自負責的角色,員工資料由 HR 服務提供,使用人員 Account_name (Varchar(30))跟 HR 服務做搜尋。
- Group 與 Group 的關係透過 Organization 做關聯管理,只可以容許樹狀組織圖。
- Group 是工作群組,負責實際工作,在實務上,使用課(Section)做為Group的最小單位,但其名稱可能因為不同企業組織而有所差異
- Organization 為組織樹狀圖,描寫群組之間的關係,但群組之間沒有隸屬關係。 Group 是攤平的工作群組,其關係透過 Organization tree 進行管理。在領域模型上需要另外做 Organization 樹。
- 預設 Organizattion 為 廠(Fab) --> 處 (Division)--> 部門 (Department)--> 課 (Section),只有 Section 這層有實際工作與指派任務需求,其餘三層都是組織管理所需。
- 組織工作委派,上級組織的 Manager 與其指定的代理人,可以對下級組織指派 (Action Request) , Action Request 負責人預設為組織負責人

> **v1.3 對 §1 的補充說明(註,不變動 §1 本體)**:
> - §1.2 第 4 個情境「廠長 → 部門經理 → 課長」於 Q-14 拍板後在系統內**模型化為單跳直派**(target = 課,originating = FAB);中間管理層的「人鏈傳達」屬訊息流,由 NATS event 訂閱與報表呈現,不做 relay 狀態欄位。詳見 ADR-0008。
> - §1.3 末段「組織負責人」於 Q-15 拍板後語意為「`leaderIds[]` 之一」;當 leader 多人時派工方須指定 ownerId,單人時自動,0 人則回 409。詳見 ADR-0010。
> - §1.3 末段「指定的代理人」目前以 Operations 流程(休假時 `transfer-manager` 暫代,結束再轉回)實現;不引入正式 `deputy` 欄位。Q-19 待用戶最終確認。

---

## 2. 角色 (Actors)

| 角色代碼 | 中文名稱 | 描述 |
|---|---|---|
| `OPERATOR` | 值班人員 | 第一線操作員,執行被指派的任務、回報進度、提交 ActionRequest |
| `SHIFT_LEAD` | 班長/領班 | 排班與分派任務,審核 ActionRequest,Group 內 Task 管理 |
| `ENGINEER` | 廠務/維修工程師 | 異常處置、設備保養專業執行者 |
| `QA` | 品保 | 異常案件複核、完工驗收;Group 啟用 QA 雙簽時擔任 reviewer |
| `GROUP_ADMIN` | 群組管理員 | Group 內 Template 選用、人員與其角色管理、**Group settings(含 QA 雙簽)維護**;**僅作用於該 Group**(必為 leaf Org 下的 Group) |
| `GROUP_MANAGER` | 群組經理 | Group 經理,可指派 `GROUP_ADMIN` 代理人,其餘權限等於 `GROUP_ADMIN`;為 Group 對外的工作接口(收 ActionRequest、拆解派 Task);**負責 Group settings(含 QA 雙簽)的最終設定權** |
| `ORG_MANAGER` | 組織經理 | **某個 Organization 節點(任一層)的唯一經理**(該節點 `managerId == 該 user.id`);有權對**該節點及其子孫中的 leaf Org** 直接派 ActionRequest;對應 §1.3「上級組織的 Manager」 |
| `ORG_ADMIN` | 組織管理員 | 管理該 Organization 樹下的 User / Group / Template / 系統設定 / Org 節點之 manager 與 leaders;**不能跨 Organization** |
| `ADMIN` | 系統管理員 | 全系統(跨 Organization)使用者與設定;**僅 SaaS 維運使用**;唯一可維護**全域 Template 庫**的角色 |

> 補充:
> - **一位使用者可同時持有多個角色**(例如班長同時也是工程師、`GROUP_MANAGER` 同時也是上級的 `ORG_MANAGER`)。授權以「最大集合」處理。
> - **`ORG_MANAGER` 改為衍生角色**(v1.3):由「該 user 是某些 Organization 節點的 `managerId`」自動衍生,**不再以 `User.roles[]` 直接賦予**。`User.orgManagerScopes[]` 為衍生 / cache 欄位,列出該 user 是哪些節點的 manager。
> - 角色綁定 **scope**:`OPERATOR` / `SHIFT_LEAD` / `ENGINEER` / `QA` / `GROUP_ADMIN` / `GROUP_MANAGER` 綁定 Group;`ORG_MANAGER` 衍生自 Organization 節點 `managerId`(任一層皆可);`ORG_ADMIN` 綁定 Organization 樹根;`ADMIN` 為系統級。
> - 角色 + Group 成員身份 + Organization 路徑共同決定可見範圍(見 §6 RBAC)。

---

## 3. 功能需求 (Functional Requirements)

### FR-Org Organization(組織樹與多租戶)

- **FR-Org.1** 系統最頂層為 `Organization`,所有資源(`User`、`Group`、`Project`、`Task`、`ActionRequest`、`Template`、`Attachment`…)皆歸屬於某個 Organization 樹的 root,以 `rootOrgId` 欄位標記(JWT 中的 `rootOrgId` 即為 root)。
- **FR-Org.2** Organization 屬性:`id`、`rootOrgId`(指向所屬樹的 root,自身為 root 時等於 `id`)、`parentId`(nullable;root 為 null)、`type`、`name`、`code`(同 `rootOrgId` 內 unique)、**`managerId`**(nullable,該節點唯一的經理;對應 ORG_MANAGER 角色)、**`leaderIds[]`**(0..N,該節點業務負責人列表)、`timezone`(僅 root 設定)、`locale`(僅 root 設定)、`settings`(JSON,僅 root 有)、`createdAt`、`updatedAt`、`deletedAt`、`history[]`、`schemaVersion`。
- **FR-Org.3** **Organization 多型 type**(可擴充):
  - `FAB`(廠)
  - `DIVISION`(處)
  - `DEPARTMENT`(部門)
  - `SECTION`(課)— **leaf type,唯一可承載工作的層**
  - 後續可由 ADMIN 透過 root settings 擴充 type 名稱與標記是否為 leaf。
- **FR-Org.4** **預設 4 層**:`FAB` → `DIVISION` → `DEPARTMENT` → `SECTION`;**深度上限預設 5**(由 root `settings.orgMaxDepth` 控制,可調 1–10),保留可擴。
- **FR-Org.5** **Leaf 約束**:
  - 只有 `SECTION` type(或 root settings 註記為 leaf 的 type)的 Organization 可以掛 Group、Project、Task;非 leaf 節點不能直接承載工作資源。
  - 是否為 leaf 由 **type 屬性 + root settings** 共同決定,不只看「在樹中是否為終端節點」(避免半成品樹被誤判)。
- **FR-Org.6** **多 Organization 隔離**:JWT 簽發時帶 `rootOrgId`、`orgPath[]`(從 root 到 user 所屬節點的 OrgId 路徑)、`roles[]`、`groupIds[]`、`orgManagerScopes[]`;所有 API 預設只能存取該 root org 樹下資源。跨樹操作須是 `ADMIN`(系統管理員)。
- **FR-Org.7** 一位 User 屬於唯一 root Organization(MVP 限制),且綁定一個 leaf Org(透過 Group 成員身份隱含);`ORG_ADMIN` 可綁在 root 節點;**任一節點皆可指派一位 manager**(該節點的 `managerId`)— manager 可以是任一同 `rootOrgId` 的 user。
- **FR-Org.8** Organization root 由 `ADMIN` 建立;建立時自動建立第一個 `ORG_ADMIN` 帳號。子節點由 `ORG_ADMIN` 建立。
- **FR-Org.9** Organization 軟刪除:
  - 軟刪除非 leaf 節點時,必須先處理(移除或軟刪)所有子孫節點與其下資源,否則回 409。
  - 軟刪除 root 時 cascade soft-delete 整棵樹下所有資源(藉 `deletedAt`)。
- **FR-Org.10** ORG_ADMIN 可調整 root Organization 的 `settings`(例:`orgMaxDepth`、預設 `dueAt` 工時、附件大小上限)。
- **FR-Org.11** **不能成環**:`parentId` 鏈往上不可遇到自己;`parentId` 必須與本節點同 `rootOrgId`。
- **FR-Org.12** **Manager 與 Leaders 操作**(對應 ADR-0010):
  - `POST /orgs/{orgId}/transfer-manager`(ORG_ADMIN / ADMIN)— 變更 `managerId`(包含設置、轉移、清空)。
  - `GET  /orgs/{orgId}/leaders` / `POST /orgs/{orgId}/leaders` / `DELETE /orgs/{orgId}/leaders/{userId}`(ORG_ADMIN / ADMIN)— 維護 `leaderIds[]`。
  - 每次變動皆寫入 `Organization.history[]` 並 emit Domain Event。
- **FR-Org.13** 系統設計詳見 **ADR-0004**(Org 樹 + 平面 Group)、**ADR-0005**(多租戶)、**ADR-0010**(單 manager + 多 leaders)。

### FR-Group Group(平面工作群組)

- **FR-Group.1** Group 為**平面結構**(無 `parentId`),屬於唯一 leaf Organization(`organizationId` 必為 leaf Org 的 ID);一個 leaf Org 可有多個 Group。
- **FR-Group.2** Group 採 **polymorphic 設計**:單一 collection + `type` 欄位 + `attributes` 子文件。
- **FR-Group.3** **內建 type**(可擴充):
  - `DEFAULT`(預設工作組,= Section 直屬,leaf Org 建立時自動產生一個 `DEFAULT` Group)
  - `LINE`(產線,例「SMT-Line-3」)
  - `TEAM`(臨時編組/任務小組,結束後 deactivate)
  - `SHIFT`(班別,輕量表達早/中/夜班 — 取代 Q-1 完整 Shift 模型)
  - 後續可由 `ORG_ADMIN` 透過 settings 擴充 type 名稱(本期不做 type 自助新增 UI,僅 enum 擴展)。
- **FR-Group.4** Group 屬性:`id`、`rootOrgId`、`organizationId`(必為 leaf Organization)、`type`、`name`、`code`(在 root org 內 unique)、`attributes`(JSON,type-specific)、`leaderId`(nullable,Group 負責人 — **與 Organization.leaderIds 為不同概念**,此處 leaderId 是 Group 內的「組長」)、**`settings`**(`{ qa: { dualSignRequired, requiredReviewerRoles[] }, extras }`)、`createdAt`、`updatedAt`、`deletedAt`、`history[]`、`schemaVersion`。
- **FR-Group.5** **Group 之間無階層關係**;若需階層意義(部門 → 課),由所屬 Organization 樹隱含表達(查兩個 Group 的所屬 leaf Org,沿樹回溯找共同祖先)。
- **FR-Group.6** **Group 成員管理**(GroupMembership):
  - 加入 / 移除成員:`POST/DELETE /orgs/{orgId}/groups/{groupId}/members`
  - 設定 / 移轉 leader(Group 內組長):`POST /orgs/{orgId}/groups/{groupId}/transfer-leader`
  - 一位 User 可同時屬於多個 Group(跨班 / 跨任務小組很常見)
  - 成員資格保留歷程:誰、何時加入 / 離開、由誰操作
  - GroupMembership 屬性:`id`、`rootOrgId`、`groupId`、`userId`、`role`(`MEMBER` / `LEAD` / `OBSERVER`)、`joinedAt`、`leftAt`(nullable)、`createdBy`
- **FR-Group.7** **Leader invariant**:`Group.leaderId` 若非 null,則該 user 必須是該 group 的 active member;移除 leader 的成員身份必須先轉移 leader。
- **FR-Group.8** **與 Project 的關係**:Project 必須掛在某個 leaf Organization 下,可指定 `groupIds[]`(0..N,但這些 Group **必須屬於 Project 自身的同一個 leaf Org**(對應 INV-19;**不允許跨 leaf**,Q-13 拍板));成員可見性根據 RBAC 計算。
- **FR-Group.9** **與 Task 的關係**:Task 必須屬於某個 Project;Task 可見性繼承自所屬 Project 的 `groupIds[]` + `assignees` / `ownerId`。
- **FR-Group.10** Group 軟刪除:該 Group 必須無 active membership(否則回 409)且無進行中的 Project / Task。
- **FR-Group.11** **Group settings 維護**(QA 雙簽,對應 ADR-0011 與 Q-7):
  - `PATCH /orgs/{orgId}/groups/{groupId}/settings`(GROUP_MANAGER / GROUP_ADMIN)。
  - 寫入時驗證:若 `qa.dualSignRequired = true` 則 `qa.requiredReviewerRoles[]` 不可為空(回 422);角色清單僅允許第一線角色(`OPERATOR` / `SHIFT_LEAD` / `ENGINEER` / `QA` / `GROUP_ADMIN` / `GROUP_MANAGER`),不接受 `ADMIN` / `ORG_ADMIN`。
  - **變更不影響進行中 Task**(Task 建立時 snapshot 該 policy,見 FR-3.x)。

### FR-User-HR HR 整合與使用者投影

- **FR-User-HR.1** **唯一識別**:User 以 `accountName`(`VARCHAR(30)`)作為對外查詢鍵,在系統內 unique(`(rootOrgId, accountName)` unique)。
- **FR-User-HR.2** **HR 為來源系統**:員工資料(姓名、工號、部門、Email、聯絡方式、在職狀態)由 HR 服務權威提供;本系統 User 為 **HR User 的本地投影**,只儲存:
  - 必要識別:`id`、`rootOrgId`、`accountName`、`displayName`(snapshot from HR)、`employeeNo`(snapshot)、`email`(snapshot)
  - 系統內專有資料:`roles[]`(不含 `ORG_MANAGER`,該角色衍生)、`orgManagerScopes[]`(衍生欄位,為某些 Organization `managerId == userId` 的節點 ID 集合)、`groupIds[]`(衍生,登入時計算)、`primaryOrgPath[]`(所屬 leaf Org 的路徑,衍生)、`active`、`hrSyncedAt`(最近同步時間)、`createdAt`、`deletedAt`
- **FR-User-HR.3** **新增 User 流程**:`POST /users { accountName }` → server 以 Bearer token 呼叫 HR Mock REST `GET /users/by-account-name/{accountName}` 驗證存在且在職 → 取回 profile → 建立本地投影。HR 不存在則回 422 `hr_user_not_found`。
- **FR-User-HR.4** **同步策略**:登入時觸發輕量同步(若 `hrSyncedAt` 超過 24h);Admin 操作 `POST /users/{userId}/sync-from-hr` 可手動拉最新。Profile 欄位以 HR 為準,`roles[]` / `orgManagerScopes[]` / `GroupMembership` 為本系統管理。
- **FR-User-HR.5** **HR 不可達時的降級**:HR 暫時不可達時,本系統允許**讀取**既有本地投影並登入(以 cached profile 顯示),但**禁止**新增 User 或同步;UI 顯示警示 banner。
- **FR-User-HR.6** **離職處理**:HR 標記 user 離職時,本系統下次同步將 `active = false`,踢出所有 active GroupMembership(產生 `GroupMemberRemoved` 事件),保留歷程。
- **FR-User-HR.7** **Mock REST 規格與正式串接 checklist 詳見 ADR-0007 之 Amendment(v1.3)**。

### FR-Template 範本管理(ProjectTemplate / TaskTemplate)

- **FR-Tpl.1** **ProjectTemplate**:預定義 Project 樣板。
  - 屬性:`id`、`scope`(`GLOBAL` / `ORG`)、`rootOrgId`(GLOBAL 時為 null)、`name`、`code`(同 scope 內 unique)、`descriptionMarkdown`、`defaultMemberRoles`(角色配置)、`taskTemplateRefs[]`(內含 N 個 TaskTemplate 引用 + 預設順序 / 預估工期)、`estimatedDurationDays`、`tags[]`、`version`、`active`、`createdBy`、`createdAt`、`updatedAt`、`deletedAt`、`history[]`、`schemaVersion`。
- **FR-Tpl.2** **TaskTemplate**:預定義 Task 樣板。
  - 屬性:`id`、`scope`(`GLOBAL` / `ORG`)、`rootOrgId`(GLOBAL 時為 null)、`name`、`code`、`type`(對應 Task 的 polymorphic type)、`defaultPriority`、`defaultAttributes`、`descriptionMarkdownTemplate`、`defaultChecklist`、`attachmentHints`、`tags[]`、`version`、`active`、`createdBy`、`createdAt`、`updatedAt`、`deletedAt`、`history[]`、`schemaVersion`。
- **FR-Tpl.3** **Scope 規則**:
  - **GLOBAL**:由 `ADMIN` 維護,所有 Organization 可見、可 fork;`rootOrgId = null`,`code` 在 GLOBAL 內 unique。
  - **ORG**:由該 root Org 的 `ORG_ADMIN` / `GROUP_MANAGER` / `GROUP_ADMIN` 維護;`code` 在該 `rootOrgId` 內 unique;不跨 root org 共享。
- **FR-Tpl.4** **Fork from Global**:`POST /orgs/{rootOrgId}/templates/fork-from-global/{globalTplId}` 將 GLOBAL 範本**深拷貝**為該 Org 的 ORG-scoped 範本(獨立 version chain,後續演進與全域脫鉤);Fork 紀錄保留 `forkedFrom: { templateId, version }` 供稽核。
- **FR-Tpl.5** **Tag 機制**:Templates 可帶多個 `tags[]`(如 `monthly`、`safety`、`equipment`、`shift-handover`);列表 API 支援 `?tag=` 過濾,方便 `GROUP_ADMIN` 選用。
- **FR-Tpl.6** **實例化即 clone**:`POST /projects { fromTemplateId, fromTemplateScope }` / `POST /tasks { fromTemplateId, fromTemplateScope }` 把 template 內容**深拷貝**到新實例(decoupled);實例化後修改不影響 template,template 修改也不影響已建立的實例。
  - 實例會記錄 `templateRef: { templateScope, templateId, templateVersion, instantiatedAt }` 供稽核,**不形成執行期相依**。
  - GLOBAL 範本可被任一 Org 直接實例化(不必先 fork)。
- **FR-Tpl.7** **版本(`version`)**:每次更新 Template 自動 +1,舊 version 仍可查詢。歷史 version 不可刪只能 deactivate。`active = false` 的 Template 不可再被用來實例化。
- **FR-Tpl.8** **權限**:
  - GLOBAL CRUD:`ADMIN` 限定。
  - ORG CRUD:`ORG_ADMIN` / `GROUP_MANAGER` / `GROUP_ADMIN`(僅該 Org 內);`SHIFT_LEAD` 可讀。
  - 實例化:具備該 leaf Org 內 Project / Task 建立權限的角色皆可。
- **FR-Tpl.9** Template 提供 list / get / create / update(產生新 version)/ deactivate API;不提供硬刪除。
- **FR-Tpl.10** **不做多語系欄位**(Q-17 拍板取消):name / description 為單一 Unicode 字串,允許混合多語系輸入(如「月度設備保養 / Monthly Maintenance / Bảo trì hàng tháng」)。
- **FR-Tpl.11** 設計詳見 **ADR-0006**(版本管理 + clone-only + GLOBAL/ORG scope)。

### FR-Dispatch 跨層派工(上級 Org → 下級 leaf Org)— v1.3 全面重寫

- **FR-Dispatch.1** **派工角色**:任一 Organization 節點的 `managerId`(即該節點唯一的 ORG_MANAGER)可向**該節點底下子孫節點中,任一 leaf Organization** 派 ActionRequest。**不限 root,任一上級皆可派**(對應 §1.3「上級組織的 Manager」字面;留 Q-18 待最終確認)。
- **FR-Dispatch.2** **派工 API**:`POST /orgs/{targetOrgId}/dispatch-action-request`,body 包含 `title`、`descriptionMarkdown`、`severity`、`attachments[]`、`dueAt`(可選)、`ownerId`(條件必填,當 targetOrg `leaderIds.length > 1` 時必填)。
- **FR-Dispatch.3** **預設 Owner 規則**(對應 ADR-0010):
  - `targetOrg.leaderIds.length == 0` → 回 409 `target_org_no_leader`(要求先指派 leader)
  - `targetOrg.leaderIds.length == 1` → server 自動 `ownerId = leaderIds[0]`
  - `targetOrg.leaderIds.length >= 2` → 派工方須在 body 指定 `ownerId`,且必須 ∈ `leaderIds`;未指定回 422 `owner_must_be_specified`,不在集合中回 422 `owner_not_in_leaders`
- **FR-Dispatch.4** **目標範圍**:`targetOrgId` **必須是 leaf Organization**(`type ∈ root.settings.leafTypes`);否則回 422 `target_must_be_leaf`。同時 `targetOrgId` 必須是 actor 某個 manager scope 節點的子孫(含相同節點,即 leaf 的 manager 對自己 leaf 派);否則回 403 `not_authorized_to_dispatch`。
- **FR-Dispatch.5** **接收與拆解**:`targetOrg` 為 leaf,該 leaf Org 的 `GROUP_MANAGER` / `GROUP_ADMIN` 將 ActionRequest 透過既有 `convert-to-task` 流程轉成一或多個 Task,Task 屬於該 leaf Org 下的 Group。**不再有「relay 到下一級」流程**(已刪除)。
- **FR-Dispatch.6** **狀態追蹤**:跨層 ActionRequest 走 `SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED / REJECTED`(無 RELAYED);發派者(originating Org 的 manager)可透過 `originatingOrgId` 全程追蹤進度。
- **FR-Dispatch.7** **Reject 退回**:leaf 端 `GROUP_MANAGER` / `GROUP_ADMIN` 可 reject(`status = REJECTED`),emit `factory-ops.action-request.rejected`;**是否要主動通知 originator** 列為 Q-20 待確認(MVP 預設只 emit event)。
- **FR-Dispatch.8** 設計詳見 **ADR-0008**(v1.3 全面改寫:Single-hop Direct Dispatch)。

### FR-Notification 通知策略:NATS 與 Webhook(Q-5 拍板)

- **FR-Notification.1** 系統內部以 **Domain Event** 廣播狀態變化,由 `EventPublisher` 同時發送到:
  1. **NATS**(JetStream):topic 命名 `factory-ops.<context>.<event>`,例:
     - `factory-ops.task.created`
     - `factory-ops.task.assigned`
     - `factory-ops.task.owner-transferred`
     - `factory-ops.task.review-submitted`(新增,QA 雙簽 review)
     - `factory-ops.task.review-rejected`(新增)
     - `factory-ops.task.completed`
     - `factory-ops.action-request.dispatched`
     - `factory-ops.action-request.rejected`(新增)
     - `factory-ops.org.manager-transferred`(取代 v1.2 leader-transferred)
     - `factory-ops.org.leader-added`(新增)
     - `factory-ops.org.leader-removed`(新增)
     - `factory-ops.group.member-added`
     - `factory-ops.group.settings-qa-updated`(新增)
     訊息 payload 使用 §FR-Notification.4 統一 schema。
  2. **Registered Webhooks**:HTTP POST 到使用者註冊的 `targetUrl`,帶 HMAC-SHA256 簽章 header `X-Factory-Ops-Signature`。
- **FR-Notification.2** **不做主動逾期掃描**;逾期(`overdue`)為 derived field,前端依本地時間 + `schedule.due` 計算顯示。
- **FR-Notification.3** **訂閱**:`POST /webhooks` 註冊;`events: ["task.assigned", "task.completed", ...]` 過濾;`secret` 用於 HMAC 簽章。
- **FR-Notification.4** **Event payload schema**(統一):
  ```json
  {
    "eventId": "uuid",
    "eventType": "task.assigned",
    "occurredAt": "2026-05-04T08:30:00+08:00",
    "rootOrgId": "...",
    "orgPath": ["...root", "...division", "...dept", "...section"],
    "aggregateType": "Task",
    "aggregateId": "...",
    "actorId": "...",
    "payload": { /* event-specific */ }
  }
  ```
  > 注意:`occurredAt` 為 ISO 8601 with offset(對應 Q-17 拍板);內部儲存仍為 UTC `Instant`,offset 由 publisher 依 root tz 轉換。
- **FR-Notification.5** **送達保證**:NATS 至少一次(at-least-once);Webhook 失敗指數退避重試(1m / 5m / 30m / 2h / 6h,最多 5 次),最終死信記錄到 `webhook_dead_letters`。
- **FR-Notification.6** **冪等**:消費端以 `eventId` 去重(server 保證同 `eventId` 不重發,但訂閱端仍應做去重)。
- **FR-Notification.7** **行動 App 推播**:Q-10 拍板採 APNs(iOS);Android 後續以 FCM 補充。本期僅預留訂閱介面與 webhook,實際 APNs gateway 留待後續實作。
- **FR-Notification.8** 設計詳見 **ADR-0009**(Event Distribution)。

### FR-1 認證與帳號
- **FR-1.1** 使用者以 `accountName` + 密碼登入,核發 **JWT(access + refresh)**。JWT claims 內含 `userId`、`accountName`、`rootOrgId`、`orgPath[]`(從 root 到 user 所屬 leaf Org 的 ID 路徑,計算自 GroupMembership)、`roles[]`、`groupIds[]`、`orgManagerScopes[]`(衍生自 Organization.managerId 反查)。
- **FR-1.2** 支援登出(blacklist refresh token)。
- **FR-1.3** 支援密碼變更、重設(由 ORG_ADMIN / ADMIN 觸發暫時密碼);若整合 SSO 則由 HR / IDP 接管(MVP 暫不實作)。
- **FR-1.4** 預留 **SSO/LDAP/HR-SSO** 介接點(暫不實作)。

### FR-2 Project 管理
- **FR-2.1** `GROUP_ADMIN` / `GROUP_MANAGER` / `SHIFT_LEAD`(在所屬 Group 內)可建立 Project,設定名稱、描述(markdown)、起訖時間、負責人(`ownerId`)、成員(`memberIds`)、所屬 Group(`groupIds[]`,至少 1 個,**必須屬於 Project 自身的同一個 leaf Org**)。
- **FR-2.2** Project 必須屬於某個 leaf Organization(`organizationId` 必填,且該 Org `type` 必須是 leaf type)。
- **FR-2.3** Project 狀態:`DRAFT` → `ACTIVE` → `PAUSED` → `COMPLETED` / `CANCELLED`。
- **FR-2.4** 系統依「現在時間 vs `dueAt`」自動判定**逾期(overdue)**,以衍生欄位呈現;**不主動推播**(見 FR-Notification.2)。
- **FR-2.5** 可在 Project 下建立 Task 與 ActionRequest。
- **FR-2.6** 變更負責人需記錄歷程(誰、何時、原因)。
- **FR-2.7** 軟刪除(`deletedAt`)。
- **FR-2.8** **`POST /projects` 可帶 `fromTemplateId` + `fromTemplateScope`**,從 Project Template clone 建立(含預設 Task 樣板實例化);GLOBAL scope 範本可直接引用。

### FR-3 Task 管理(多型)— 含 QA 雙簽 review 流程
- **FR-3.1** 每個 Task 屬於唯一 Project(`projectId` 必填),所屬 Project 必須在 leaf Org 下。
- **FR-3.2** Task 多型:以 `type` 字串 + `attributes` 子文件描述各型態特定欄位。**初版內建型態**:
  - `EQUIPMENT_INSPECTION`(設備檢查)
  - `INCIDENT_RESPONSE`(異常處置)
  - `SHIFT_HANDOVER`(交班記錄)
  - 後續可擴展(例如 `MAINTENANCE`、`SAFETY_DRILL`),**新增型態不需改 schema**。
- **FR-3.3** 必填欄位:`title`、`type`、`projectId`、`ownerId`、`assignees[]`(至少 1 位,且必須包含 `ownerId`)、`status`、`priority`。
- **FR-3.4** Task 狀態流:`OPEN` → `IN_PROGRESS` → `BLOCKED` ↔ `IN_PROGRESS` → `IN_REVIEW` → `DONE` / `CANCELLED`。**`IN_PROGRESS → DONE` 簡易直推 path 僅在 `qaReviewPolicy.dualSignRequired = false` 開放**;若需要雙簽,必須走 `IN_REVIEW` + review action。
- **FR-3.5** `priority`:`LOW` / `NORMAL` / `HIGH` / `URGENT`。
- **FR-3.6** 內容欄位 `descriptionMarkdown` 支援 markdown(允許 Unicode 多語系混合輸入)。
- **FR-3.7** 支援多附件(圖片/影片/文件)— 詳見 ADR-0003。
- **FR-3.8** 留言(`comments[]`)以 markdown 撰寫,可標記 `@accountName`。
- **FR-3.9** 支援 `dueAt`(到期時間)、`startAt`、`completedAt`,皆為 UTC `Instant`,API 傳輸時以 ISO 8601 + offset 呈現。
- **FR-3.10** 變更負責人(`transferOwner`)為獨立 API,需要記錄原因與歷程。
- **FR-3.11** **`POST /tasks` 可帶 `fromTemplateId` + `fromTemplateScope`**,從 TaskTemplate clone(含預填 attributes、checklist、附件提示);GLOBAL scope 範本可直接引用。
- **FR-3.12** **QA 雙簽 review 流程**(對應 ADR-0011 與 Q-7):
  - **Snapshot at Task Creation**:Task 建立時(含 ActionRequest convert),server 從所屬 Project 的所有 `groupIds[]` 對應 Group `settings.qa` 計算合併 policy(`dualSignRequired = OR(...)`、`requiredReviewerRoles = ⋃(...)` 取聯集去重),寫入 `task.qaReviewPolicy: { dualSignRequired, requiredReviewerRoles[], snapshotAt, sourceGroupIds[] }`。
  - **Group settings 後續變更不影響已存在 Task**(對應 INV-31)。
  - **Review Action API**:`POST /tasks/{taskId}/review { decision, role, reason? }`
    - decision: `APPROVED` / `REJECTED`
    - role: reviewer 主張這次以哪個角色 review,必須 ∈ `task.qaReviewPolicy.requiredReviewerRoles`
    - 驗證:task.status == `IN_REVIEW`、actor 具該 role、actor 對 task 有 R 權限、(taskId, reviewerId, role) 不可重複(否則 409)
    - APPROVED:append `qaReviews[]` 一筆;若已蒐集 policy 要求的所有 role 則 server 自動推進 status 為 `DONE` + emit `factory-ops.task.completed`
    - REJECTED:append + status 回退至 `IN_PROGRESS`(預設清空既往 reviews,語意「重新走流程」;Q-21 待確認此預設行為)
  - **AND 語意**(對應 ADR-0011 §2):`requiredReviewerRoles` 中**每個角色都需有人簽核**才算過關;同一 user 不可一次同時擔任多角色簽核。Q-23 待確認 OR 替代語意。
  - **Bypass force-complete**:GROUP_MANAGER 可透過 `POST /tasks/{taskId}/status` 強制設為 `DONE`,但須附 `bypassReason`,history 記錄,event 帶 `bypassed: true`。

### FR-4 ActionRequest(動作需求)— v1.3 簡化
- **FR-4.1** 任何角色可在所屬 Group 的 Project 內提出 ActionRequest(描述問題、拍照、選嚴重度);亦可由 ORG_MANAGER 透過 §FR-Dispatch 直接派發到子孫 leaf Org。
- **FR-4.2** 狀態流:`SUBMITTED` → `TRIAGED`(已分派為 Task) → `IN_PROGRESS` → `RESOLVED` / `REJECTED`;**v1.3 移除 `RELAYED` 子狀態**。
- **FR-4.3** `SHIFT_LEAD` / `GROUP_MANAGER` / `GROUP_ADMIN` 可將 ActionRequest **轉換為 Task**(產生雙向關聯 `linkedTaskId` ↔ `originActionRequestId`)。
- **FR-4.4** Task 完成時,連動回寫 ActionRequest 為 `RESOLVED`(可手動覆蓋)。
- **FR-4.5** ActionRequest 屬性:
  - `originatingOrgId`(發起者所掛的 Org 節點;自課內提報時 = 自身 leaf Org;跨層派工時 = actor manager scope 中與 target 有 ancestry 的最近一個)
  - **`targetOrgId`** 取代 v1.2 `assignedToOrgId`(必為 leaf;v1.3 改名強化語意)
  - **移除** `relayChain[]`(不再有 relay)
- **FR-4.6** 與 Task 的設計取捨見 **ADR-0001**(獨立 aggregate);跨層派工設計見 **ADR-0008**(v1.3)。

### FR-5 指派 (Assignment) 與負責人
- **FR-5.1** Task 必須有單一 `ownerId`(必填)。
- **FR-5.2** `assignees[]` 是參與者集合(至少 1 位),**`ownerId` 必須在 `assignees[]` 內**(invariant)。
- **FR-5.3** 提供 `addAssignee` / `removeAssignee` / `transferOwner` 三個獨立 API。
- **FR-5.4** `transferOwner`:可選擇是否將舊 owner 從 `assignees` 移除(預設保留)。
- **FR-5.5** 設計詳見 **ADR-0002**。

### FR-6 附件 / 多媒體
- **FR-6.1** 上傳採兩階段:`POST /attachments`(metadata + presigned URL) → 實際上傳到物件儲存。
- **FR-6.2** 支援檔案類型:`image/*`、`video/mp4`、`application/pdf`、`text/markdown`、Office 文件白名單。
- **FR-6.3** 單檔上限預設 50 MB(可由 root Organization `settings` 調整)。
- **FR-6.4** **本期不提供 Sticker 集合**(Q-4 拍板取消);若需要表情可於 markdown 直接使用 emoji 字符或 Unicode 符號。
- **FR-6.5** 所有附件以 `attachmentId` 在 markdown / 留言中以 `![alt](attachment://{id})` 引用,前端負責解析。
- **FR-6.6** 設計細節見 **ADR-0003**。

### FR-7 列表、查詢、搜尋
- **FR-7.1** Project / Task / ActionRequest 列表支援以下過濾:狀態、優先級、負責人、指派人、型態、Project、organizationId、Group、時間範圍、關鍵字(標題)。
- **FR-7.2** **分頁**:採 cursor-based(以 `_id` 或 `updatedAt` 為游標),mobile 友善。
- **FR-7.3** **欄位投影**:`?fields=id,title,status,ownerId` 減少 mobile 流量。
- **FR-7.4** **增量同步**:支援 `If-Modified-Since` / `ETag`,以及 `?since=<timestamp>` 取增量更新。
- **FR-7.5** 全文檢索(MongoDB text index)— 標題與 markdown 描述。
- **FR-7.6** 所有列表預設**僅回該 user 所屬 Organization 樹的資料**(server 強制 scope)。

### FR-8 通知(以 NATS / Webhook 提供)
> 取代 v1.1 FR-8;見 §FR-Notification。

### FR-9 稽核軌跡 (Audit Log)
- **FR-9.1** 每個 aggregate(Project / Task / ActionRequest / Group / Organization / Template / User)內嵌 `history[]`,記錄 `actor`、`action`、`at`、`payload`(變更摘要)。
- **FR-9.2** 軟刪除不真正刪資料(`deletedAt` 時間戳),所有歷程**保留 7 年**(法遵需求);**超過 3 年**的資料以資料庫匯出方式做冷儲存(Q-9 拍板)。
- **FR-9.3** 系統級操作日誌另以獨立 `audit_logs` collection 儲存(超出 aggregate 的跨資源動作,如登入、權限變更、Org 建立、HR 同步、跨層派工、Webhook 發送結果)。

### FR-Frontend 前端關鍵畫面(Q-8 拍板,新增)

> 後端產出原始 API,前端組合畫面;此節定義**前端必做**的 KPI 畫面,供 react-frontend-builder 規劃。

- **FR-Frontend.1** **Daily Work Board(每日工作看板)** — 每位登入 user 的首頁,需以單一畫面呈現以下四個區塊:
  1. **My Owned Tasks**(`ownerId == me`,`status ∈ {OPEN, IN_PROGRESS, BLOCKED, IN_REVIEW}`)
  2. **My Assigned Tasks**(`assignees ∋ me` 但 `ownerId != me`,同狀態)
  3. **My Group's Overdue Items**(`groupIds ∩ me.groupIds` 非空,`schedule.due < now` 且 `status ∉ {DONE, CANCELLED}`)
  4. **Pending Review**(`status == IN_REVIEW` 且 me 具備 `qaReviewPolicy.requiredReviewerRoles` 中任一角色,且 me ∈ Project.groupIds 或 Project.memberIds)
- **FR-Frontend.2** 看板支援 mobile + tablet,觸控目標 ≥ 44px(對應 NFR);各區塊摺疊 / 展開保留 user 偏好(local storage)。
- **FR-Frontend.3** **不做甘特圖**(Q-8 拍板);可做簡易 Project 進度條(以「子 Task 完成數 / 總數」呈現)。
- **FR-Frontend.4** 時間欄位顯示:依使用者瀏覽器 locale + root Organization timezone 二擇一(預設後者);hover 時顯示 ISO 8601 + offset 完整時間。

---

## 4. 非功能需求 (Non-Functional Requirements)

| 類別 | 需求 |
|---|---|
| **效能** | 列表 API p95 < 300 ms(回 50 筆),單筆讀取 p95 < 100 ms;Org 樹查詢 p95 < 200 ms(深度 ≤ 5) |
| **可用性** | 工廠 7×24 運轉,目標 99.5%,計畫停機需在交班空檔 |
| **行動友善** | 觸控目標 ≥ 44px,支援戴手套操作;3G 網速下首頁 < 3 秒 |
| **時區** | **儲存層**所有時間以 UTC `Instant` 儲存(可索引、可運算);**API 傳輸**與**事件 payload** 一律使用 ISO 8601 + offset(例 `2026-05-04T08:30:00+08:00`,Q-17 拍板);UI 顯示由前端依「使用者瀏覽器 locale」或「root Organization `timezone`」二擇一(預設後者)轉換 |
| **多語系** | 介面預留 i18n,預設 root Organization `locale`(`zh-TW`),後續可擴 en-US / vi-VN(東南亞廠);**字串內容欄位**(name / description / 留言…)允許 **Unicode 混合輸入**(中、英、越南、印尼、韓、日…),**不做多語系欄位設計**(Q-17 拍板取消 GLOBAL Template `name_zh` / `name_en` 設計) |
| **安全** | 全端 HTTPS、JWT 簽章、密碼 bcrypt、敏感欄位不入 log、輸入驗證、跨 root org 隔離、Webhook HMAC 簽章 |
| **稽核** | 所有寫入操作可追溯到單一使用者(必有 `actorId`);**保留 7 年,3 年以上冷儲存**(Q-9 拍板) |
| **可擴充** | Task / Group / Organization 新增 type 不需改 schema;新增 Bounded Context 可獨立部署 |
| **可觀測** | OpenTelemetry trace、結構化 JSON log、Prometheus metrics、NATS 流量監控 |
| **多租戶** | 所有資料以 `rootOrgId` 隔離,索引第一欄為 `rootOrgId` 以利分區擴展 |
| **HR 整合可用性** | HR 短暫不可達(< 1h)不影響登入與讀取;新增 User 與同步在 HR 恢復後可用 |
| **事件送達** | NATS 至少一次,Webhook 重試 5 次後死信 |

---

## 5. 業務規則 / Invariants

| ID | 規則 |
|---|---|
| INV-1 | Task `ownerId` 必填,且必須出現在 `assignees[]` 中 |
| INV-2 | Task `assignees[]` 至少 1 位 |
| INV-3 | Task 變更負責人時,必須產生 `OwnerTransferred` 歷程記錄 |
| INV-4 | Project `dueAt` 必須晚於 `startAt`(若兩者皆存在) |
| INV-5 | Task `dueAt` 不可早於所屬 Project 的 `startAt` |
| INV-6 | 狀態流必須依規定路徑轉換(例如不能從 `OPEN` 直接跳 `DONE`);**若 Task `qaReviewPolicy.dualSignRequired = true`,則 `IN_PROGRESS → DONE` 直推路徑禁止,必須走 IN_REVIEW + review action 蒐集完成** |
| INV-7 | ActionRequest 轉為 Task 後,雙向關聯 ID 必須一致 |
| INV-8 | 已 `COMPLETED` / `CANCELLED` 的 Project 不能新增 Task |
| INV-9 | 軟刪除的資源不能被指派或變更狀態 |
| INV-10 | 附件大小 ≤ root Organization 設定上限,且 mime type 在白名單內 |
| INV-11 | 所有 aggregate 必須有 `rootOrgId`(Organization 自身除外);跨 root org 引用不允許 |
| INV-12 | **Organization 樹不可成環**:任一節點的 ancestor chain 不得包含自己 |
| INV-13 | **Organization 樹深度 ≤ root `settings.orgMaxDepth`**(預設 5,可調 1–10) |
| INV-14 | Group `leaderId` 若非 null,該 user 必須是同 Group 的 active GroupMembership |
| INV-15 | **Group 軟刪除前必須無 active GroupMembership 與進行中 Project / Task** |
| INV-16 | GroupMembership 同 (groupId, userId) 在同一時間最多一筆 active(`leftAt = null`) |
| INV-17 | Template `version` 單調遞增,既存 version 不可修改;`active = false` 的 Template 不可被實例化 |
| INV-18 | Project / Task 從 Template 實例化後,內容與 Template 解耦(後續修改互不影響) |
| INV-19 | **Project `groupIds[]` 中每個 Group 必須屬於 Project 自身的同一個 leaf Org**(`Group.organizationId == Project.organizationId`),且未軟刪;**不允許跨 leaf**(Q-13 拍板) |
| INV-20 | User 屬於唯一 `rootOrgId`(MVP);跨 root org 操作須是 `ADMIN` 系統角色 |
| INV-21 | **Project / Task 必須屬於 leaf Organization**(`Project.organizationId` 對應的 Org `type` 必須在 root settings `leafTypes` 內) |
| INV-22 | **Group 必須屬於唯一 leaf Organization**(`Group.organizationId` 對應 Org 必為 leaf) |
| INV-23 | **Group 為平面結構**,無 `parentId`;Group 之間無階層關係 |
| INV-24 | **ActionRequest `targetOrgId` 必為 leaf Organization**(`type ∈ root.settings.leafTypes`),且必須是 actor 某個 manager scope 節點(含相同節點)的子孫(自課內提報時 actor 即在該 leaf 的 Group 內,語義一致) |
| INV-25 | **ActionRequest 預設 `ownerId` 規則**(對應 ADR-0010):`targetOrg.leaderIds` 0 → 409 `target_org_no_leader`;1 → 自動 `ownerId = leaderIds[0]`;N → 派工方須指定 `ownerId ∈ leaderIds`,未指定 → 422 `owner_must_be_specified` |
| INV-26 | **Organization `parentId` 必須與本節點同 `rootOrgId`**;不能成環(同 INV-12) |
| INV-27 | **GLOBAL scope Template 的 `rootOrgId` 必為 null**;**ORG scope Template 的 `rootOrgId` 必非 null** |
| INV-28 | **GLOBAL Template `code` 在 GLOBAL 範圍內 unique**;**ORG Template `code` 在 `(rootOrgId, scope=ORG)` 內 unique** |
| INV-29 | **User `accountName` 在 `(rootOrgId)` 內 unique**;新增時必須通過 HR 驗證 |
| INV-30 | **Organization 軟刪除非 leaf 節點時,必須先處理所有子孫節點**(否則 409);軟刪 root 等同 cascade soft-delete 整棵樹 |
| INV-31 | **Task `qaReviewPolicy` 於 Task 建立(或 ActionRequest convert)時 snapshot,之後 Group settings 變更不影響該 Task**;policy `dualSignRequired = OR(各 group)`、`requiredReviewerRoles = ⋃(各 group)` 聯集去重 |
| INV-32 | **Organization 每節點最多一位 manager**(`managerId` 為單值欄位;同一節點不能有多個 manager) |
| INV-33 | **Organization `managerId` / `leaderIds[]` 中的 user 必須與 Organization 同 `rootOrgId` 且 `active = true`**;若 user 軟刪或離職,需先 transfer-manager / 移除 leader 才允許其 deactivate(reactor 自動處理移除) |
| INV-34 | **`User.orgManagerScopes[]` 為衍生 / cache 欄位**;權威來源是 `Organization.managerId == userId` 反查;不可由前端直接寫入 |
| INV-35 | **Group settings 寫入時**:若 `qa.dualSignRequired = true` 則 `qa.requiredReviewerRoles[]` 不可為空;角色清單只允許第一線角色(`OPERATOR` / `SHIFT_LEAD` / `ENGINEER` / `QA` / `GROUP_ADMIN` / `GROUP_MANAGER`),否則 422 |
| INV-36 | **同一 user 不可同時以多個角色滿足同一個 Task 的 review 要求**(`qaReviews[]` 中 `(reviewerId, role)` unique;若 user 具備兩個 required role,需以兩位不同 user 各自簽一筆) |

---

## 6. 角色權限矩陣 (RBAC)

> 標記:`C` 建立 / `R` 讀取 / `U` 更新 / `D` 軟刪除 / `A` 指派 / `T` 移轉負責人 / `X` 狀態變更 / `M` 成員管理 / `F` Fork / `DP` Dispatch / `RV` Review(QA 雙簽) / `—` 無權限
>
> **可見範圍標注**:
> - `(同 Group)` = 該 user 與資源至少共享一個 active Group
> - `(被指派)` = user 在 `assignees[]` 或 `ownerId` 中
> - `(子孫 Org)` = 資源所在 Org 是 user 所掛 manager 節點的子孫
> - `(全 root)` = 不限 Group,但仍限制在同一 root Organization 樹內
> - **預設前提**:同一 root Organization 內。跨 root org 一律 `—`(除 `ADMIN` 系統角色)

| 資源 / 動作 | OPERATOR | SHIFT_LEAD | ENGINEER | QA | GROUP_ADMIN | GROUP_MANAGER | ORG_MANAGER | ORG_ADMIN | ADMIN |
|---|---|---|---|---|---|---|---|---|---|
| **Organization 樹**: 讀取自身 root | R | R | R | R | R | R | R(子孫) | R(全 root) | R(全) |
| **Organization 節點**: 建立 / 更新 / 軟刪 | — | — | — | — | — | — | — | C/U/D | C/U/D |
| **Organization root**: 建立 | — | — | — | — | — | — | — | — | C |
| **Organization root**: 更新 settings | — | — | — | — | — | — | — | U | U |
| **Organization 節點**: transfer-manager | — | — | — | — | — | — | — | T(全 root) | T |
| **Organization 節點**: leaders CRUD | — | — | — | — | — | — | — | C/D(全 root) | C/D |
| **Group**: 建立(限 leaf Org) | — | — | — | — | C | C | — | C | — |
| **Group**: 讀取 | R(同 Group) | R(同 Group) | R(同 Group) | R(同 Group) | R(自 Group) | R(自 Group + 子孫 Org 內) | R(子孫 Org) | R(全 root) | R |
| **Group**: 更新基本資料 | — | — | — | — | U(自 Group) | U(自 Group) | — | U(全 root) | — |
| **Group**: 更新 settings(QA 雙簽) | — | — | — | — | U(自 Group) | U(自 Group) | — | U(全 root) | — |
| **Group**: 軟刪除 | — | — | — | — | — | D(自 Group) | — | D(全 root) | — |
| **Group**: 加/移除成員 | — | — | — | — | M(自 Group) | M(自 Group) | — | M(全 root) | — |
| **Group**: 移轉 leader(組長) | — | — | — | — | — | T(自 Group) | — | T(全 root) | — |
| **Project**: 建立 | — | C(同 Group) | — | — | C(自 Group) | C(自 Group) | — | C(全 root) | — |
| **Project**: 讀取 | R(同 Group) | R(同 Group) | R(同 Group) | R(同 Group) | R(自 Group) | R(自 Group + 子孫) | R(子孫 Org) | R(全 root) | R |
| **Project**: 更新基本資料 | — | U(同 Group) | — | — | U(自 Group) | U(自 Group) | — | U(全 root) | — |
| **Project**: 變更狀態 | — | X(同 Group) | — | — | X(自 Group) | X(自 Group) | — | X(全 root) | — |
| **Project**: 軟刪除 | — | — | — | — | — | D(自 Group) | — | D(全 root) | — |
| **Task**: 建立 | C(本人為 owner) | C(同 Group) | C(同 Group) | C(同 Group) | C(自 Group) | C(自 Group) | — | C(全 root) | — |
| **Task**: 讀取 | R(被指派) | R(同 Group) | R(被指派) | R(同 Group) | R(自 Group) | R(自 Group + 子孫) | R(子孫 Org) | R(全 root) | R |
| **Task**: 更新內容 | U(被指派) | U(同 Group) | U(被指派) | U(同 Group) | U(自 Group) | U(自 Group) | — | U(全 root) | — |
| **Task**: 變更狀態 | X(被指派) | X(同 Group) | X(被指派) | X(同 Group) | X(自 Group) | X(自 Group) | — | X(全 root) | — |
| **Task**: 加入/移除指派 | — | A(同 Group) | — | — | A(自 Group) | A(自 Group) | — | A(全 root) | — |
| **Task**: 移轉負責人 | — | T(同 Group) | — | — | T(自 Group) | T(自 Group) | — | T(全 root) | — |
| **Task**: **QA Review action** | RV(若具 required role) | RV(若具 required role) | RV(若具 required role) | RV(若具 required role) | RV(若具 required role) | RV(若具 required role) | — | RV(全 root) | — |
| **Task**: 強制 force-complete(bypass) | — | — | — | — | — | X(自 Group) | — | X(全 root) | — |
| **Task**: 軟刪除 | — | — | — | — | — | D(自 Group) | — | D(全 root) | — |
| **ActionRequest**: 提交(同 Group 內) | C | C | C | C | C | C | — | C | — |
| **ActionRequest**: Triage(convert-to-task) | — | C(同 Group) | — | — | C(自 Group) | C(自 Group) | — | C(全 root) | — |
| **ActionRequest**: Reject | — | X(同 Group) | — | X(同 Group) | X(自 Group) | X(自 Group) | — | X(全 root) | — |
| **ActionRequest**: **跨層 Direct Dispatch(發起)** | — | — | — | — | — | — | DP(自掛及子孫 leaf) | DP(全 root) | — |
| **ProjectTemplate**(ORG): CRUD | — | R | — | — | C/R/U/D | C/R/U/D | — | C/R/U/D | — |
| **TaskTemplate**(ORG): CRUD | — | R | — | — | C/R/U/D | C/R/U/D | — | C/R/U/D | — |
| **Template**(GLOBAL): CRUD | — | — | — | — | — | — | — | — | C/R/U/D |
| **Template**(GLOBAL): 讀取 | R | R | R | R | R | R | R | R | R |
| **Template**: Fork from GLOBAL → ORG | — | — | — | — | F | F | — | F | F |
| **Template**: 實例化(建 Project / Task 時) | 依 Project / Task 建立權限 | | | | | | | | |
| **Attachment**: 上傳 | C(自身擁有資源) | C | C | C | C | C | — | C | — |
| **User**(同 root Org)管理 | — | — | — | — | — | — | — | C/R/U/D | C/R/U/D |
| **User**: HR 同步 | — | — | — | — | — | — | — | U(自 root) | U(全) |
| **User**(跨 root Org)管理 | — | — | — | — | — | — | — | — | C/R/U/D |
| **Webhook**: 註冊 / 移除(同 root) | — | — | — | — | — | — | — | C/R/D | C/R/D |

> 註:
> - **「自 Group」**= GROUP_ADMIN / GROUP_MANAGER 所掛 Group;**「自掛及子孫 leaf」**= ORG_MANAGER 所掛 Org 節點及其子孫中的 leaf Org。
> - **GROUP_MANAGER vs GROUP_ADMIN**:GROUP_MANAGER 額外可指派 GROUP_ADMIN 代理人 + 強制 force-complete Task,其餘權限相同。
> - **`ORG_MANAGER` 衍生於 `Organization.managerId`**,不直接管 Group 內事務;只負責跨層 ActionRequest direct dispatch。
> - **Review action(RV)**:跨角色,只要 actor 具備 `task.qaReviewPolicy.requiredReviewerRoles` 中任一角色 + 對該 Task 有 R 權限,即可 review。
> - **`ADMIN` 為系統級**,僅 SaaS 維運;`ORG_ADMIN` 為該 root org 樹的最高權限,涵蓋全 root org 範圍。

---

## 7. User Stories

### Epic A:Project 管理
- **US-A1** 作為 **GROUP_MANAGER**,我想要建立 Project 並設定起訖時間與負責人,以便團隊有清楚的工作邊界。
- **US-A2** 作為 **SHIFT_LEAD**,我想要在 Project 下拆解多個 Task,以便派工到組員。
- **US-A3** 作為 **GROUP_MANAGER**,我想要看到 Project 是否逾期,以便及早介入。
- **US-A4** 作為 **GROUP_MANAGER**,我想要暫停 / 取消 Project,以便處理停線等異常情況。
- **US-A5**(v1.3 改寫)作為 **GROUP_MANAGER**,我想要把 Project 同時掛到「裝配課的 DEFAULT Group」與「裝配課的夜班 SHIFT Group」兩個 Group(**同一 SECTION 內**),以便日班與夜班成員共同協作該 Project 的 Task。注意:Q-13 拍板**不允許跨 leaf**;Group 必須屬同一 SECTION leaf Org。

### Epic B:Task 多型與指派
- **US-B1** 作為 **SHIFT_LEAD**,我想要建立「設備檢查」「異常處置」「交班記錄」等不同型態的 Task,以便對應實際工作場景。
- **US-B2** 作為 **SHIFT_LEAD**,我想要把 Task 同時指派給 3 位組員但只有 1 位是負責人,以便團隊協作但責任清楚。
- **US-B3** 作為 **OPERATOR**,我想要看到「指派給我」與「我是負責人」兩個分開列表,以便優先處理我負責的事項。
- **US-B4** 作為 **SHIFT_LEAD**,我想要中途把 Task 負責人從 A 轉給 B,以便交接。
- **US-B5** 作為 **OPERATOR**,我想要在手機上戴手套點開 Task 並回報進度,以便不必脫手套打字。
- **US-B6**(新增)作為 **GROUP_MANAGER**,我想要為我的 Group 開啟「QA 雙簽」設定,以便該 Group 之後新建的 Task 必須走 QA review 才能 DONE,確保品質。
- **US-B7**(新增)作為 **QA**,我想要看到 Pending Review 清單並逐一 approve / reject,以便完成驗收職責。

### Epic C:ActionRequest 與閉環
- **US-C1** 作為 **OPERATOR**,我想要在發現空壓機異音時拍照並提交 ActionRequest,以便讓班長知道。
- **US-C2** 作為 **SHIFT_LEAD**,我想要把 ActionRequest 轉為「異常處置 Task」並指派給工程師,以便進入處理流程。
- **US-C3** 作為 **OPERATOR**,我想要看到我提的 ActionRequest 後續處理進度,以便心中有數。

### Epic D:多媒體與內容
- **US-D1** 作為 **OPERATOR**,我想要在 Task 內容用 markdown 撰寫並插入照片,以便記錄完整現場情境。
- **US-D2** 作為 **GROUP_MANAGER**,我想要附上 PDF 規範文件給 Task,以便執行人員有依據。

### Epic E:稽核與交班
- **US-E1** 作為 **QA**,我想要看到任何 Task 的完整變更歷程,以便異常追溯。
- **US-E2** 作為 **SHIFT_LEAD**,我想要建立「交班記錄」Task 把未完成事項打包,以便下一班接續。

### Epic F:行動裝置與離線(部分)
- **US-F1** 作為 **OPERATOR**,我想要在訊號不穩時可以離線檢視最近 50 筆 Task,以便繼續工作(後續實作)。
- **US-F2** 作為 **OPERATOR**,我想要 PDA 掃 QR 直接開啟設備對應的 Task,以便縮短操作時間(後續實作)。

### Epic G:組織樹與平面群組
- **US-G1** 作為 **ADMIN**,我想要建立新的 Organization root(廠)並指派 ORG_ADMIN,以便 SaaS 上線新工廠。
- **US-G2** 作為 **ORG_ADMIN**,我想要設定 root Organization 的時區、語系、深度上限,以便符合本廠習慣。
- **US-G3** 作為 **ORG_ADMIN**,我想要建立 Organization 樹(廠 → 處 → 部 → 課),以便對應真實組織。
- **US-G4** 作為 **ORG_ADMIN**,我想要在課(SECTION,leaf)下建立多個 Group(DEFAULT、SMT-Line-3、夜班…),以便彈性表達工作組合。
- **US-G5** 作為 **GROUP_MANAGER**,我想要把組員加入 / 移出我帶的 Group,以便處理人員異動。
- **US-G6** 作為 **GROUP_MANAGER**,我想要把 Group leader(組長)角色移轉給副班長,以便休假期間業務不中斷。
- **US-G7** 作為 **OPERATOR**,我想要看到我同時屬於哪些 Group(可能跨班),以便明白自己的責任範圍。
- **US-G8** 作為 **ORG_ADMIN**,我想要查看某 leaf Org 下所有成員、Group、進行中的 Project,以便管理績效。
- **US-G9** 作為 **ORG_ADMIN**,我想要透過 HR `accountName` 搜尋並匯入新員工,以便快速建立帳號。
- **US-G10**(新增)作為 **ORG_ADMIN**,我想要為「製造處」這個 DIVISION 節點指派一位 manager(處長)+ 兩位 leaders(處長 + 副處長),以便 ActionRequest 派工時有明確的 owner 候選清單。
- **US-G11**(新增)作為 **ORG_ADMIN**,當原處長休假時,我想要 `transfer-manager` 把 manager 暫時換成副處長,休假結束再轉回去,以便流程不中斷。

### Epic H:範本管理
- **US-H1** 作為 **GROUP_MANAGER**,我想要把「月度設備保養」Project 流程存成 ProjectTemplate(含 5 個預設 Task),以便每月一鍵複製。
- **US-H2** 作為 **GROUP_ADMIN**,我想要建立「異常處置標準 SOP」TaskTemplate(預填嚴重度判定矩陣 / 根因欄位),以便確保流程一致。
- **US-H3** 作為 **SHIFT_LEAD**,我想要從 Template 建立 Project 並修改個別欄位(實例化後解耦),以便處理特殊情況。
- **US-H4** 作為 **GROUP_MANAGER**,我想要修改 Template 不影響已建立的 Project,但新建的 Project 用最新 version,以便逐步迭代 SOP。
- **US-H5** 作為 **ORG_ADMIN**,我想要 deactivate 舊 Template,以便淘汰過時 SOP 但保留歷史紀錄。
- **US-H6** 作為 **ADMIN**,我想要維護全域 Template 庫(GLOBAL scope),並打上 tags,以便所有 Org 可以引用。
- **US-H7** 作為 **GROUP_ADMIN**,我想要從全域 Template 庫透過 tag(如 `safety`)挑選範本,並 fork 為本 Org 客製版,以便符合本廠流程。
- **US-H8** 作為 **GROUP_ADMIN**,我想要直接從 GLOBAL Template 一鍵建立 Project(不必先 fork),以便快速採用標準流程。

### Epic I:跨層派工(v1.3 全面改寫,Single-hop)
- **US-I1**(改寫)作為 **廠長(FAB 層 manager)**,我想要把「裝射新機台」這個任務 **直接** dispatch 給「裝配課」(SECTION leaf),以便該課承接後拆解為 Task;中間部門經理、處長透過事件訂閱 / 報表得知,**不需要在系統上做 relay 操作**。
- **US-I2**(改寫)作為 **製造處長(DIVISION 層 manager)**,我想要把跨產線異常 dispatch 給「裝配課」(我子孫的 SECTION leaf),以便讓該課接手處理。**不再有「relay」流程**;系統判定我是「製造處」manager + 裝配課是製造處子孫 leaf,即允許。
- **US-I3**(刪除)~~部門經理 → 課的 relay~~(v1.3 不再有此操作)
- **US-I4** 作為 **裝配課的 GROUP_MANAGER**,我收到上級 dispatch 來的 ActionRequest,我想要把它 triage 為一個或多個 Task,指派給課內組員,以便進入執行流程。
- **US-I5** 作為 **發起的廠長**,我想要看到這個 ActionRequest 目前的 status(可能仍是 SUBMITTED 等待 leaf triage,或已 TRIAGED 成 Task),以便追蹤進度。
- **US-I6**(改寫)作為 **某層 manager**,當我休假時,我想要 ORG_ADMIN 透過 `transfer-manager` 把 `managerId` 暫時換成代理者,休假結束再轉回去,以便流程不中斷;同節點若有多位 leaders 共同對 ActionRequest 負責,則派工方依 leaderIds 指定 owner。
- **US-I7**(新增)作為 **發派多 leader 節點的 manager**,當下游 leaf Org 有多位 leaders 時,我想要在 dispatch 表單上選擇本次的 owner(必須 ∈ leaderIds),以便明確責任分配。

### Epic J:HR 整合
- **US-J1** 作為 **ORG_ADMIN**,我想要輸入新員工的 `accountName` 系統會自動從 HR 拉取資料,以便不重複輸入。
- **US-J2** 作為 **OPERATOR**,我登入時系統自動同步我的最新 HR 資料(姓名變更、部門調整),以便 UI 顯示正確。
- **US-J3** 作為 **ADMIN**,我想要看到 HR 服務暫時不可達的警示,以便提早處理(系統 graceful degrade,既有用戶仍可登入)。

### Epic K:通知
- **US-K1** 作為 **第三方系統開發者**,我想要訂閱 NATS topic `factory-ops.task.completed`,以便接收完工事件做後續處理。
- **US-K2** 作為 **ORG_ADMIN**,我想要註冊一個 webhook URL 接收 `task.assigned` 事件,以便整合 Slack 通知。
- **US-K3** 作為 **ORG_ADMIN**,我想要看到 webhook 死信記錄,以便處理失敗。

### Epic L:每日工作看板(新增,Q-8)
- **US-L1** 作為 **OPERATOR**,我想要在登入後第一個畫面就看到我今日要做的事(我負責的 Task / 我被指派的 Task / 我所在 Group 的逾期事項 / 待我 review 的 Task),以便不必到處點。
- **US-L2** 作為 **QA**,我想要在看板上看到 Pending Review 區塊,逐一點開做雙簽 review,以便快速完成驗收職責。

---

## 8. 待使用者確認的問題 (Open Questions)

### 8.1 已拍板問題(Q-1 ~ Q-17)

| ID | 問題 | 影響範圍 | 狀態 / 拍板答覆 |
|---|---|---|---|
| Q-1 | **班別 (Shift) 是否要納入本期 MVP?** | 領域模型 | **拍板:不做完整排班模型;以 Group + `type=SHIFT` 輕量表達**;`attributes` 內存班別代碼、開始 / 結束本地時間。後續若需排班表(Roster)再開新 Epic。 |
| Q-2 | **組織結構**:採 Organization polymorphic 樹 + 平面 Group? | 權限與查詢 | **拍板(v1.2 確立、v1.3 沿用):Organization 為 polymorphic 樹**(FAB / DIVISION / DEPARTMENT / SECTION,只有 leaf 做工作)+ **平面 Group**(屬唯一 leaf Org,無 parent-child)。詳見 ADR-0004。 |
| Q-3 | **附件儲存**?| 部署架構 | **拍板:落地用 MinIO(自架 S3 相容物件儲存)**,以 S3 SDK 操作;部署 docker-compose 起一個 MinIO instance。詳見 ADR-0003。 |
| Q-4 | **貼圖 (sticker) 集合是否要做?** | 資源管理 | **拍板:本期不做 Sticker**;若需要表情可使用 markdown 內 Unicode emoji 字符。整份規格已移除「sticker」設計細節。 |
| Q-5 | **逾期通知策略**? | 通知服務 | **拍板:不做逾期定時掃描;改以 Domain Event + NATS(JetStream)+ Webhook(HMAC-SHA256 簽章)雙通道散播**;逾期僅作為前端衍生欄位。詳見 ADR-0009。 |
| Q-6 | **跨廠多租戶**? | 全資料模型 | **拍板:正式採用 `rootOrgId` 多 Organization 設計**,所有資料 root-org-scoped,索引第一欄為 `rootOrgId`,JWT 帶 `rootOrgId` + `orgPath[]`。詳見 ADR-0005。 |
| Q-7 | **Task 完成驗收是否需要 QA 雙簽?** | 工作流 | **拍板:由 GROUP_MANAGER 在 Group settings 設定**(`settings.qa.dualSignRequired`、`settings.qa.requiredReviewerRoles[]`)。Task 建立時 snapshot 該 policy,設定後續變更**不影響**已存在 Task。AND 語意(列示中每個角色都需簽核;多 Group 合併規則為聯集)。詳見 ADR-0011 與 INV-31 / INV-35 / INV-36。**衍生 Q-21 / Q-23 待最終確認 reject 行為與 OR 替代語意**。 |
| Q-8 | **看板 (Kanban) / 甘特圖**是否要做? | 前端範圍 | **拍板:不做甘特圖;只做每日工作看板(Daily Work Board)**(my owned / my assigned / overdue / pending review 四區塊)。詳見 §FR-Frontend.1。 |
| Q-9 | **資料保留期限**? | 維運 | **拍板:稽核資料保留 7 年(法遵需求);超過 3 年的歷史資料以資料庫匯出做冷儲存**(降低主庫負擔)。實作上以 archiving job 定期將 `history[]` 中超過 3 年的 entry 匯出為冷儲存格式(參考 ADR-DevOps 後續訂)。 |
| Q-10 | **行動 App 推播管道**? | 通知服務 | **拍板:iOS 採 APNs;Android 後續以 FCM 補充**(本期僅預留 webhook 訂閱介面,實際 push gateway 留待 Mobile App 實作里程碑)。 |
| Q-11 | **Template 是否跨 Org 共享?** | Template / 多租戶 | **拍板:Template 庫由 ADMIN 設定為 GLOBAL scope,可加多組 tag**(如 `safety` / `monthly` / `equipment`),各 Org 可**直接實例化**或 **fork 為 ORG scope** 後獨立演進(version chain 脫鉤)。詳見 ADR-0006。 |
| Q-12 | **Org 樹深度與「只有第一層組織可以設定工作」釐清** | Organization | **拍板:深度上限預設 5(由 root settings 控制);「第一層組織」一詞指 leaf 層**(預設 SECTION,可由 root settings.leafTypes 擴充);只有 leaf type 的 Organization 可承載 Group / Project / Task。 |
| Q-13 | **跨 leaf Org 的 Project 是否允許?** | 跨組協作 | **拍板:跨組協作不會發生**。Project 必須屬於唯一 leaf Org;`groupIds[]` 中所有 Group **必須屬於 Project 自身的同一個 leaf Org**(`Group.organizationId == Project.organizationId`)。完全刪除 v1.2「在 RBAC 允許下跨 leaf 共組」字眼;對應 INV-19 強化、US-A5 改寫(夜班 Group 改為同 SECTION 內 SHIFT 型 Group)。 |
| Q-14 | **「第一層組織」雙關詞最終釐清**:工作層是 root 還是 leaf? | Organization | **拍板:Leaf 才能工作,Root 只會指派 ActionRequest 到 Leaf**。導出兩個重大設計變動:(a) ActionRequest `targetOrgId` 必須是 leaf;(b) 移除「逐層 Relay 鏈」流程,改為 **Single-hop Direct Dispatch to Leaf**(刪除 `relayChain[]` / `RELAYED` 子狀態 / `POST .../relay` 端點)。詳見 ADR-0008(v1.3 全面改寫)。**衍生 Q-18 待用戶確認**:任一上級 manager 都能 dispatch,還是只有 root manager 能?(本期傾向前者) |
| Q-15 | **ORG_MANAGER 與 leader 的關係?** | 角色與派工 | **拍板:ORG_MANAGER 每節點只有一位,Leader 有多位**。對應設計變動:(a) Organization 加 `managerId`(單值,nullable)+ `leaderIds[]`(0..N);(b) `ORG_MANAGER` 角色改為衍生(由 Organization.managerId 反查);(c) ActionRequest 預設 ownerId 規則重寫(0 → 409、1 → 自動、N → 派工方指定);(d) 新增 leaders CRUD API + transfer-manager API,**移除** v1.2 `transfer-leader` Org 端點。詳見 ADR-0010。**衍生 Q-19 待確認**:正式 deputy 機制是否需要欄位,還是靠 transfer-manager 流程? |
| Q-16 | **HR API 介面細節**? | HR 整合 | **拍板:本期 MVP 採 HR Mock REST API**;規格細節(endpoint、payload、認證、降級、字段對應、正式串接 checklist)詳見 ADR-0007 之 v1.3 Amendment。**正式串接時務必檢視欄位對應**。 |
| Q-17 | **GLOBAL Template 多語系與時間戳記策略**? | Template / i18n / 時區 | **拍板:統一使用 UTF-8 字集,允許混合多國語系輸入**(中、英、越、印尼…);**取消** GLOBAL Template `name_zh` / `name_en` / `name_vi` 等多欄位設計(name / description 為單一 Unicode 字串)。**時間戳記**:API 傳輸與事件 payload 用 ISO 8601 + offset(例 `2026-05-04T08:30:00+08:00`),**儲存層仍為 UTC `Instant`**(可索引、可運算);UI 顯示時依「使用者瀏覽器 locale」或「root Organization timezone」二擇一(預設後者)。**衍生 Q-24 待確認**:儲存是否需保留發起端原始 offset(如夜班跨日讀取的可讀性)? |

### 8.2 由本次拍板衍生的新 Open Questions(待使用者最終確認)

| ID | 問題 | 影響範圍 | 預設行為(若使用者不另指示) |
|---|---|---|---|
| Q-18 | **ActionRequest 跨層 dispatch 的發起角色範圍**:任一具備 manager 身份的上級節點都能直派,還是**僅** root(FAB)層 manager 可派?Q-14 答覆只強調「目標必為 leaf」,未限定發起者;§1.3 字面允許任一上級。本 spec 採前者(任一上級可派),但若用戶本意為「派工權集中在廠級」需重大修訂。 | RBAC / FR-Dispatch | **預設**:任一上級 manager 皆可 dispatch 到其子孫 leaf。否則 `ORG_MANAGER` 綁中間層便失去意義。 |
| Q-19 | **Manager 休假代理機制**:§1.3 提及「指定的代理人」,Q-15 答覆未明示。本 ADR-0010 不引入正式 `deputyManagerId` 欄位,改以 Operations 流程(`transfer-manager` 暫代,結束再轉回)。是否需要正式 deputy 欄位? | FR-Org | **預設**:無正式 deputy 欄位;靠 transfer-manager 操作。若加 deputy 將涉及 RBAC、`User.orgManagerScopes[]` 衍生規則調整。 |
| Q-20 | **ActionRequest 在 leaf 端被 Reject 後的回流**:目前設計是 leaf 的 GROUP_MANAGER 可 reject,系統 emit `factory-ops.action-request.rejected` event;**是否要主動通知 originator**(例如生成一個「派工被退回」的 in-app notification 或 email)?還是 originator 自行訂閱 event 處理? | 通知策略 / FR-Dispatch | **預設**:只 emit event,不做主動推播 in-app notification。Webhook / 第三方訂閱者可自行處理。 |
| Q-21 | **QA Review reject 的行為**:目前 ADR-0011 預設「reject 後 task 回 IN_PROGRESS,既往 reviews 清空,新一輪 review 重新蒐集」。**這是「重新走流程」語意**;另一可能語意是「保留先前 APPROVED,只清掉某些角色 review」。 | 工作流 / FR-3.12 | **預設**:reject 清空所有既往 reviews,task 回 IN_PROGRESS。 |
| Q-22 | **Group settings 的歷史記錄**:目前以 `Group.history[]` 紀錄變更;是否需要為 settings 子文件做專屬 versioning(同 Template)?Snapshot at task creation 已避免影響進行中 Task,但設定本身的歷史是否需可查詢? | FR-Group / 稽核 | **預設**:不另做 versioning,沿用 `Group.history[]`(append-only),變更時 entry 帶 before/after diff payload。 |
| Q-23 | **QA 雙簽 reviewer 角色清單語意**:`requiredReviewerRoles: ["QA", "SHIFT_LEAD"]` 是 **AND**(每角色各一筆 review)還是 **OR**(任一角色簽即可)?本期採 AND(對應「雙簽」字面)。 | 工作流 / FR-3.12 | **預設**:AND 語意。若用戶要 OR,改 ADR-0011 + INV-36。 |
| Q-24 | **時間戳記儲存策略**:本期採「儲存 UTC `Instant`,傳輸 ISO 8601 + offset」(NFR / Q-17 說明);是否需要在儲存層額外保留**發起端原始 offset**(例如夜班 22:00 提交跨日,後續查看時不被換算成 UTC 後變得不直觀)? | NFR 時區 / 資料模型 | **預設**:不在儲存層保留 originating offset;UI 用 root tz 顯示即可。若 Q-24 確認需要,加 `<field>OffsetMinutes: int` 副欄位。 |

---

## 9. 範圍外 (Out of Scope, MVP)

- 排班 (Shift Roster) 完整建模(只用 Group + type=SHIFT 輕量表達,不做排班表)
- 報表 / BI dashboard(只開放 raw 資料 API)
- 知識庫 / SOP 文件管理(僅以附件 + Template 支援)
- 行動原生 App(僅以 API 預留;APNs / FCM gateway 後續實作)
- AI 輔助分類、語音輸入
- Group type 自助新增 UI(本期僅 enum 擴展)
- Organization type 自助新增 UI(本期僅 enum 擴展)
- 跨 root Organization 協作邀請(MVP 一位 User 屬於唯一 root org)
- **跨 leaf Org 的 Project**(Q-13 拍板禁止,維持 Out of Scope)
- 跨 root Org 的 Project / ActionRequest dispatch(MVP 一律同 root 內)
- Sticker(Q-4 拍板取消)
- 主動逾期推播 / 排程掃描(Q-5 改 NATS+Webhook)
- HR 雙向同步(本系統僅讀 HR;不寫回 HR)
- 甘特圖(Q-8 拍板不做)
- Manager 正式代理人欄位(Q-19 暫不做,靠 transfer-manager)

---

## 10. 詞彙表 (Glossary)

| 詞 | 解釋 |
|---|---|
| **Organization** | 組織節點,polymorphic by `type`(FAB / DIVISION / DEPARTMENT / SECTION / 自訂),以 `parentId` 形成樹 |
| **root Organization** | 一棵 Organization 樹的根(通常 type = FAB),所有資源以 `rootOrgId` 隔離 |
| **leaf Organization** | type 為 leaf(預設 SECTION)的 Organization;**唯一可承載 Group / Project / Task / 接收 dispatch 的層** |
| **Organization Manager** | 某 Organization 節點的唯一經理(該節點 `managerId`),具備跨層 ActionRequest dispatch 授權 |
| **Organization Leader(s)** | 某 Organization 節點的業務負責人列表(`leaderIds[]`,0..N),為 ActionRequest dispatch 預設 ownerId 候選 |
| **Organization 樹深度** | 從 root 到該節點的邊數;上限由 root settings.orgMaxDepth 控制(預設 5) |
| **Group** | 平面工作群組,屬於唯一 leaf Org;polymorphic by `type`(DEFAULT / LINE / TEAM / SHIFT / 自訂) |
| **Group Settings(QA)** | Group-level 工作流客製,含 `qa.dualSignRequired` + `qa.requiredReviewerRoles[]` |
| **GroupMembership** | User 與 Group 的關聯,記錄成員資格、角色、加入/離開時間 |
| **Group Leader** | Group 內的組長(`Group.leaderId`),必須是該 Group 的 active 成員;**與 Organization.leaderIds 為不同概念** |
| **Project** | 一個有起訖時間的工作集合,屬於唯一 leaf Org,所有 `groupIds[]` 必屬同一 leaf Org |
| **Task** | 具體要做的事,有單一負責人與多位參與者,有型態,可能要走 QA review |
| **Task QA Review Policy(snapshot)** | Task 建立時從 Project.groupIds[].settings.qa 合併 snapshot 的 dualSignRequired + requiredReviewerRoles;後續 Group settings 變更不影響此 snapshot |
| **ActionRequest** | 現場提出的「需要動作」需求,經 triage 後可轉成 Task;**支援單跳跨層 dispatch 到 leaf** |
| **Direct Dispatch(單跳跨層派工)** | 任一上級 manager 把 ActionRequest 直接派到子孫 leaf Org(targetOrgId 必為 leaf);**v1.3 取代 v1.2 的 Dispatch + Relay 雙模式** |
| **originatingOrgId** | ActionRequest 發起者所掛的 Org 節點(actor 是該節點的 manager,且該節點是 targetOrg 的祖先) |
| **targetOrgId** | ActionRequest 的接收 leaf Organization(v1.3 取代 v1.2 `assignedToOrgId`) |
| **Owner** | Task / Project 的單一負責人(`ownerId`) |
| **Assignee** | Task 的參與者(包含 owner) |
| **Shift** | 班別(早/中/夜),以 Group + `type=SHIFT` 輕量表達 |
| **ProjectTemplate / TaskTemplate** | Project / Task 的範本,有 GLOBAL / ORG 兩種 scope |
| **scope (Template)** | `GLOBAL`(系統全域)或 `ORG`(屬某 root Org) |
| **Fork (Template)** | 將 GLOBAL 範本深拷貝為某 Org 的 ORG-scoped 範本,獨立演進 |
| **Template Version** | Template 每次更新累加 1,實例化時 snapshot 該 version |
| **Aggregate** | DDD 中由根實體控制一致性邊界的一組物件 |
| **rootOrgId** | root Organization ID,所有資料模型的 tenant 鍵 |
| **orgPath** | 從 root 到某節點的 OrgId 陣列(JWT 內) |
| **orgManagerScopes** | User 的衍生 / cache 欄位,列出該 user 是哪些 Organization 節點的 `managerId` |
| **accountName** | User 對 HR 服務查詢用的識別字(VARCHAR(30)),系統 `(rootOrgId, accountName)` unique |
| **HR 投影 (HR projection)** | 本系統 User 為 HR 系統使用者的本地快照,profile 欄位以 HR 為權威 |
| **NATS** | 訊息中介(JetStream),用於發布 Domain Events |
| **Webhook** | 使用者註冊的 HTTP callback,訂閱事件;以 HMAC 簽章保護 |
| **Domain Event** | 跨 aggregate 的業務事件,以 NATS + Webhook 雙通道散播 |
| **Daily Work Board(每日工作看板)** | 前端首頁,呈現 my owned / my assigned / overdue / pending review 四區塊(Q-8 拍板) |
| **ISO 8601 + offset** | 時間 wire format(例 `2026-05-04T08:30:00+08:00`,Q-17 拍板) |

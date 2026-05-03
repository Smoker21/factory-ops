# ADR-0006: Template 版本管理與實例化策略(Clone + GLOBAL/ORG Scope)

**狀態**: Accepted(v1.2 補註:GLOBAL scope + Fork 機制 + tags)
**日期**: 2026-05-03(v1.2 增補同日)
**決策者**: spec-architect
**相關需求**: FR-Tpl、INV-17、INV-18、INV-27、INV-28、Q-11(拍板,v1.2 重新拍板)、US-H1 ~ US-H8

---

## v1.2 增補:GLOBAL scope + Fork from Global

> v1.1 Q-11 留待確認;v1.1.1 拍板:「Template 庫由 Admin 設定,可以增加多組 tag,方便 Group admin 選取使用」。

### 變動點

1. **Template 加 `scope` 欄位**:`GLOBAL` / `ORG`
   - `GLOBAL`:`rootOrgId = null`,由 `ADMIN` 維護;**所有 Org 可見、可實例化、可 fork**
   - `ORG`:`rootOrgId` 必填,屬該 root org;由該 Org 的 ORG_ADMIN / GROUP_MANAGER / GROUP_ADMIN 維護
   - 兩者**同一 collection**(`project_templates` / `task_templates`)以 `scope` 欄位區分
   - 唯一性:`(scope, rootOrgId, code, version)` unique

2. **Tags 機制**:`tags: List<String>`
   - GLOBAL Templates 由 ADMIN 維護時打標(`safety`、`maintenance`、`shift-handover`、`equipment`…)
   - ORG Admin 在前端可用 tag 篩選找到合適的 GLOBAL 範本
   - ORG 自建範本也可有 tags(本 org 內分類用)

3. **Fork 機制**:`POST /orgs/{rootOrgId}/templates/fork-from-global/{globalTplId}`
   - 將 GLOBAL Template 深拷貝為該 Org 的 ORG-scoped Template(獨立 version chain)
   - Fork 後兩者**完全脫鉤**:GLOBAL 演進不影響已 fork 的 ORG 版本
   - Fork 紀錄保留 `forkedFrom: { sourceTemplateId, sourceVersion, forkedAt, forkedBy }` 供稽核

4. **直接實例化 GLOBAL**(不必先 fork):
   - `POST /projects { fromTemplateId, fromTemplateScope: GLOBAL }` 可直接從 GLOBAL Template clone 建立 Project
   - 適用於「ADMIN 提供的標準 SOP 直接適用」場景
   - 實例的 `templateRef` 記錄 `templateScope: GLOBAL`

5. **TaskTemplateRef 跨 scope**(在 ProjectTemplate 內):
   - GLOBAL ProjectTemplate 內的 `taskTemplateRefs` **只能引用 GLOBAL TaskTemplate**(避免污染)
   - ORG ProjectTemplate 內的 `taskTemplateRefs` 可引用同 root org 的 ORG TaskTemplate **或** GLOBAL TaskTemplate

### 為何採同一 collection + scope 欄位(而非雙 collection)

| 因素 | 同 collection + scope | 雙 collection |
|---|---|---|
| 列出「可選範本」(GLOBAL+ORG) | 一次查詢 | 需 `$unionWith`,複雜 |
| 唯一性約束 | partial unique by `(scope, rootOrgId, code, version)` | 各自 unique |
| 索引維護 | 單一集 | 雙倍 |
| Fork 操作 | 同集內 insert,事務簡單 | 跨集 insert,需 distributed tx |

### 權限矩陣(摘要,完整見 §6 RBAC)

| 動作 | ADMIN | ORG_ADMIN | GROUP_MANAGER | GROUP_ADMIN | SHIFT_LEAD |
|---|---|---|---|---|---|
| GLOBAL CRUD | ✓ | — | — | — | — |
| GLOBAL 讀取 | ✓ | ✓ | ✓ | ✓ | ✓ |
| 從 GLOBAL Fork → ORG | ✓ | ✓ | ✓ | ✓ | — |
| ORG CRUD | ✓ | ✓(自 root) | ✓(自 Group) | ✓(自 Group) | R only |
| 實例化(GLOBAL or ORG) | ✓ | ✓ | ✓ | ✓ | ✓ |

### 索引(更新)

- `project_templates` / `task_templates`:
  - `{ scope: 1, rootOrgId: 1, code: 1, version: 1 }` unique partial
  - `{ scope: 1, rootOrgId: 1, active: 1, updatedAt: -1 }`
  - `{ scope: 1, tags: 1 }`(GLOBAL 與 ORG 共用 tag 篩選)
  - `{ scope: 1, rootOrgId: 1, code: 1, active: 1, version: -1 }` partial(`active: true`)— 找最新 active version
  - GLOBAL 查詢時 filter `{ scope: "GLOBAL" }`(rootOrgId 為 null);ORG 查詢時 filter `{ scope: "ORG", rootOrgId: <X> }`

### 多語系(留 Q-17)

- GLOBAL Template 由 ADMIN 維護,Org locale 可能不同(zh-TW / en-US / vi-VN)
- 本 ADR 不處理多語系;`name` / `descriptionMarkdown` 為單一字串
- 若日後需要,加 `i18n: Map<Locale, { name, description }>` VO 或獨立 collection
- MVP 預設以 zh-TW 維護

---

## 以下為 v1.1 原內容(scope = ORG 的 Template 設計仍為基礎)

---

## Context(背景)

工廠有大量重複性工作:月度設備保養、季度安全演練、標準異常處置 SOP。每次手動建立 Project / Task 浪費時間且容易遺漏細節。引入 **Template(範本)** 機制可:

- 把重複工作流標準化
- 一鍵建立(包含預設 Task 清單、checklist、附件提示)
- 版本演進(SOP 改版時保留歷史紀錄)

需要設計時面對的選擇:

1. **實例化是 clone 還是 reference?**(實例修改 / Template 修改互相影響的問題)
2. **版本如何管理?**(改 Template 會不會破壞已建立的 Project?)
3. **跨 Org 共享?**(全域 Template 庫 vs Org-scoped)

---

## Decision(決策)

### 1. 實例化採 **Clone(深拷貝)**,實例與 Template 完全解耦

當 `POST /projects { fromTemplateId }` 或 `POST /tasks { fromTemplateId }` 時:
1. 後端讀取 Template 的指定 version(預設最新 active version)
2. **深拷貝**所有可變內容(name、description、attributes、checklist、taskTemplateRefs…)到新實例
3. 新實例帶 `templateRef: { templateType, templateId, templateVersion, instantiatedAt }` **僅作 audit snapshot**,不形成執行期相依
4. 之後修改 Template 不影響此實例;修改實例不影響 Template

ProjectTemplate 內含 N 個 TaskTemplate 引用 → 實例化 Project 時,對每個 TaskTemplate 也做 clone 建立 Task。

### 2. 版本管理:**append-only,既存 version 凍結**

- `Template.version` 從 1 開始,單調遞增
- 每次 `PATCH` 視為「發布新 version」,**舊 version 不可修改、不可刪除**
- 實際儲存策略(由 mongodb-modeler 決定):
  - **方案 A**:同 collection 多 document(`{ id: T1, version: 1 }`、`{ id: T1, version: 2 }`),用 partial unique index 維持唯一性
  - **方案 B**:當前 version 在 `templates` collection,歷史 version 在 `template_history` collection
  - **本 ADR 不強制**,推薦方案 A(查詢一致、減少 collection 數)
- `active = false`(deactivate)的 Template 不可被新實例引用,但歷史實例的 `templateRef` 仍可追溯
- 不提供硬刪除(只能 deactivate),滿足稽核需求

### 3. Template 屬於 Org,**不跨 Org 共享(MVP)**

- 每個 Template 必帶 `orgId`,只能在該 Org 內被引用
- 為何不跨 Org?
  - SOP 與工廠特性高度相關(設備、流程、語言)
  - 跨 Org 共享可能洩漏商業敏感資訊
  - 本期 MVP 簡化問題範圍
- 留 Q-11 給使用者:後續是否需要「全域 Template 庫」由 ADMIN 維護,Org 可 fork 使用?

### 4. Template 不可被 Project / Task **執行期 reference**

明確排除「Project 持有 templateId,渲染時 join Template 內容」的設計。理由見 Consequences。

---

## Consequences(後果)

### 正面
- **使用者預期符合直覺**:從 Template 建立後,改 Template 不會「神秘地」改變我已開始的工作。
- **歷史可追溯但不脆弱**:`templateRef` snapshot 讓你知道「這 Project 從哪個 Template v3 建的」,但 Template 真的被刪也不影響此 Project 運作。
- **Template 演進無顧慮**:可以大膽修改 Template,不用擔心破壞 production 中的 Project。
- **稽核完整**:每個版本都凍結,可看「2025 年的月度保養 SOP 是什麼樣」。
- **效能好**:讀 Project / Task 不需要 join Template。
- **跨 Org 隔離天然**:Template 屬 Org,搭配 ADR-0005 的 multi-tenancy 一致。

### 負面
- **資料重複**:每個實例 clone 一份內容,DB 變大。緩解:工廠場景 Template 內容不大(< 10 KB / Template),clone 成本可接受。
- **批次更新不便**:如果 SOP 改了想「同步更新所有進行中 Project」,clone 模型做不到。緩解:這通常是業務上錯誤的需求(進行中工作不應被 SOP 改版打斷);若真有需求,提供「重新從 Template 套用」的明確 action(需 user 確認)。
- **Template 的 Task 結構與 Task 結構部分重複**:`TaskTemplate` 與 `Task` 欄位高度相似但不完全相同。緩解:接受這個重複,clone 邏輯集中在一個 mapper。
- **版本爆炸風險**:每次 PATCH 都建新 version,有人會手抖改 N 次。緩解:UI 提供「儲存草稿」(active = false 的 v1)→ 「發布」(active = true) 兩段式工作流(細節留前端決定)。

---

## Alternatives Considered(評估過的替代方案)

### A. Reference(實例執行期持有 templateId,讀取時 join)
- **優點**:資料不重複;Template 改了實例自動更新
- **缺點**:
  - 違反使用者直覺(進行中 Project 突然變了?)
  - 必須限制 Template 一旦被引用就不能改(降低彈性)
  - 讀取效能差(每次都要 join)
  - 跨 Context dependencies 增加
- **不採用**

### B. Clone + Reference 混合(共同欄位 reference,變動欄位 clone)
- **優點**:理論上兼顧
- **缺點**:複雜度爆炸;什麼欄位是「共同」很主觀;心智模型混亂
- **不採用**

### C. 不做版本(每次 update 直接覆寫)
- **優點**:簡單
- **缺點**:無法追溯歷史 SOP;失去稽核能力
- **不採用**:工廠法遵需要保留 SOP 演進歷史

### D. 真正的硬刪除版本(可刪)
- **優點**:DB 不爆
- **缺點**:稽核失敗(「2024 年我們用的 SOP v2 內容是什麼?」找不到)
- **不採用**:append-only 是稽核底線

### E. Template 跨 Org 全域共享(SaaS 平台 Template 庫)
- **優點**:省事(平台預設提供「設備保養 SOP 範本」)
- **缺點**:工廠特性差異大,通用 SOP 反而無用;商業敏感
- **不採用 MVP**:留 Q-11 給後期討論

---

## Compliance / Validation(合規驗證)

### 寫入時
- `version` 自動由 server 計算(最新 active version + 1),client 不可指定
- 既存 version 的 document **唯讀**(repository 層強制)
- `active = false` 的 Template:`POST /projects { fromTemplateId: X }` 必須回 409 `template_inactive`
- `TaskTemplate.type` 必須是已註冊的 Task type(否則回 422)
- `TaskTemplate.defaultAttributes` 必須通過該 type 的 JSON Schema 驗證

### 實例化時
- Server 驗證使用者有權限引用該 Template(同 rootOrgId、`ORG_ADMIN` / `GROUP_MANAGER` / `GROUP_ADMIN` / `SHIFT_LEAD`;v1.2 角色重整,原 `MANAGER` 已移除)
- Clone 後 server 自動填 `templateRef = { templateType, templateId, templateVersion, instantiatedAt }`
- 實例化失敗(例某 TaskTemplate 已 deactivated)整個 transaction rollback

### 稽核
- Template CRUD 全部進 `history[]`(誰、何時、改了什麼欄位、新 version 號)
- 實例化進 Project / Task 的 `history[]`(`INSTANTIATED_FROM_TEMPLATE` action)

---

## Notes for Next Stage(給 mongodb-modeler)

### Collections
- `project_templates`、`task_templates` 各自獨立 collection
- 版本儲存採方案 A(同 collection 多 document),由 `(orgId, code, version)` unique 索引維持唯一

### 索引
- `project_templates`:
  - `{ orgId: 1, code: 1, version: 1 }` unique
  - `{ orgId: 1, active: 1, updatedAt: -1 }`(列最新 active templates)
  - `{ orgId: 1, code: 1, active: 1 }` partial(找 active 最新 version 用)
- `task_templates`:同 ProjectTemplate

### 查詢「最新 active version」
```js
db.project_templates.find({
  orgId,
  code,
  active: true,
  deletedAt: null
}).sort({ version: -1 }).limit(1)
```
建議加 helper index:`{ orgId: 1, code: 1, active: 1, version: -1 }` partial(`active: true`)

### Snapshot 欄位
- 在 `Project` / `Task` document 內 embed `templateRef` VO,不引用 Template document
- `templateRef` 欄位選 `{ templateType, templateId, templateVersion, instantiatedAt }` 四個 fix 欄位,不展開 Template 內容(內容已 clone 到實例本身)

# ADR-0001: Task 多型設計策略

**狀態**: Accepted
**日期**: 2026-05-03
**決策者**: spec-architect
**相關需求**: FR-3、INV-6、US-B1

---

## Context(背景)

工廠值班場景的 Task 不是單一形態,常見至少有:
- **設備檢查**(EQUIPMENT_INSPECTION):checklist + 量測值
- **異常處置**(INCIDENT_RESPONSE):嚴重度 + 根因分析 + 矯正措施 + 停線時間
- **交班記錄**(SHIFT_HANDOVER):交班代碼 + 未完成事項清單

未來可能還有「保養」「安全演練」「教育訓練」「樣品送驗」等型態。每個型態:
- 有共同核心欄位(title、status、priority、ownerId、assignees…)
- 有各自獨特的結構化資料
- 可能有不同的工作流(例:異常處置強制走 IN_REVIEW、交班記錄不需要)

我們需要選擇 **多型實作策略**,同時滿足:
1. **新增型態不需改 schema、不需 DB migration**
2. **共同欄位的查詢方便**(列表、過濾、計數)
3. **特定欄位的驗證可靠**(不能任意亂塞)
4. **API 表達清晰**(前端能根據型態 render 不同表單)
5. **後端行為可差異化**(每個型態有自己的 policy)

ActionRequest 是相關但不同的概念,本 ADR 的次要決策是 ActionRequest **是否為 Task 的子型態**。

---

## Decision(決策)

### 主決策:單一 collection + `type` 欄位 + `attributes` 子文件

採用 **Single Collection Inheritance**(也稱 STI / discriminator pattern):

```kotlin
data class Task(
    val id: TaskId,
    val projectId: ProjectId,
    val type: String,                       // discriminator
    val attributes: Map<String, Any?>,      // type-specific 結構化資料
    // 共同欄位
    val title: String,
    val status: TaskStatus,
    val priority: Priority,
    val ownerId: UserId,
    val assignees: List<UserId>,
    // ...
)
```

**Server 端的型態註冊機制**:

```kotlin
interface TaskTypePolicy {
    val typeCode: String
    val label: String
    val attributesSchema: JsonSchema    // 寫入前驗證 attributes
    val requiresReview: Boolean         // 是否強制 IN_REVIEW
    fun onComplete(task: Task): List<DomainEvent>  // 完成時的副作用
}

// 每個 type 一個 @ApplicationScoped class,自動被 TaskTypeRegistry 收集
```

**API 表達**:

```json
POST /tasks
{
  "type": "EQUIPMENT_INSPECTION",
  "title": "空壓機 #1 月度檢查",
  "ownerId": "u-001",
  "assignees": ["u-001", "u-002"],
  "attributes": {
    "equipmentId": "AIR-001",
    "checklistTemplateId": "CL-AIR-V2"
  }
}
```

OpenAPI 用 `attributes: { type: object, additionalProperties: true }`,搭配 `/task-types` 端點公開每個型態的 JSON Schema 給前端動態 render。

### 次決策:ActionRequest 為**獨立 aggregate**,不是 Task 子型態

ActionRequest 與 Task 雙向關聯(`linkedTaskId` ↔ `originActionRequestId`),但獨立 collection。

---

## Consequences(後果)

### 正面
- **新增型態零成本**:加一個 `TaskTypePolicy` 實作即可,DB 不動。
- **共同查詢容易**:`tasks` 同 collection,`(status, dueAt)` 索引可服務所有型態。
- **API 一致**:前端只要實作一個 `Task` model,渲染靠 `type` 切換 sub-form。
- **行為差異化**:`TaskTypePolicy` strategy 解耦,新增規則(例:異常處置完成必須附 root cause)只改一個檔。
- **驗證可靠**:雖然 `attributes` 在 DB 是 free-form,但 server 用 JSON Schema 強制驗證。

### 負面
- **`attributes` 在 DB 層無 schema 約束**:依賴 server 驗證。若繞過 server 直接寫入 DB(例:腳本)會失準。緩解:DB 層加 schema validation rule(MongoDB JSON Schema validator)在 production 啟用。
- **跨型態欄位的索引兩難**:若某型態的 `attributes.equipmentId` 需要查詢,必須額外建 sparse index,且要記得回頭加。緩解:統計常用查詢欄位後再決定是否「升級」為 first-class field。
- **OpenAPI 表達不夠精確**:`attributes` 是 free-form,前端拿不到自動補完。緩解:`/task-types` 公開 JSON Schema,前端用它生成表單與驗證。

---

## Alternatives Considered(評估過的替代方案)

### A. 每個型態一個 collection(table-per-type)
- 例:`equipment_inspection_tasks`、`incident_response_tasks`…
- **優點**:強 schema、索引精確
- **缺點**:跨型態查詢需要 `$unionWith`(複雜且慢);新增型態要建 collection;權限與索引重複維護
- **不採用**:工廠場景常需要跨型態列表(「我所有未完成 Task」),這個 cost 太高

### B. 共同 `Task` + 各型態獨立子文件 collection(reference)
- `tasks` 存共同欄位,`task_inspection_details` 等存細節,以 `taskId` 關聯
- **優點**:schema 清楚
- **缺點**:多次查詢、無 atomic update、列表時 N+1;對 MongoDB 文件導向違背
- **不採用**

### C. OpenAPI `oneOf` + JSON Schema discriminator(完整型別表達)
- API 層用 `oneOf: [InspectionTask, IncidentTask, ...]`
- **優點**:type-safe
- **缺點**:每加型態必改 OpenAPI 與所有 client SDK;違反「新增型態零成本」目標
- **部分採用**:`/task-types` 動態回 schema 取代靜態 `oneOf`

### D. ActionRequest 作為 Task 的子型態(`type: ACTION_REQUEST`)

> **Note**:Group 採用相同的「單一 collection + `type` discriminator + `attributes` 子文件」策略(取代 Department / Section 兩層硬寫死),詳見 **ADR-0004**。Group 因為有階層需求多了一個 `parentId` self-reference 欄位。
- **優點**:少一個 aggregate;雙向關聯不需要
- **缺點**:
  - 兩者狀態流不同(ActionRequest 有 SUBMITTED / TRIAGED / REJECTED;Task 有 OPEN / IN_PROGRESS / DONE)
  - 兩者**權限模型不同**(ActionRequest 任何人可提,Task 只有特定角色可建)
  - 「triage 後產生新 Task」的流程不順(同一 collection 內自我引用,且狀態語意混淆)
  - ActionRequest 沒有 `assignees`、`ownerId` 概念(在被 triage 前)
- **不採用**:語意上是兩個不同的東西,合併會讓狀態機與權限變複雜

---

## Compliance / Validation(合規驗證)

- 寫入 Task 前,`TaskTypeRegistry` 必須能找到對應 `TaskTypePolicy`,否則回 422 `unknown_task_type`。
- `attributes` 必須通過該 policy 的 JSON Schema 驗證,否則回 422 `attributes_invalid`。
- 新增型態的 PR 必須:
  1. 新增 `XxxTaskTypePolicy` 實作
  2. 加上對應 JSON Schema 檔
  3. 加上至少一個 unit test
  4. 在 `docs/spec/domain-model.md` 補上型態說明

---

## Notes for Next Stage(給 mongodb-modeler)

- `tasks` collection 上必建索引:
  - `{ projectId: 1, status: 1, dueAt: 1 }`
  - `{ ownerId: 1, status: 1 }`
  - `{ assignees: 1, status: 1 }`(multikey)
  - `{ type: 1, status: 1 }`
  - text index on `title + descriptionMarkdown`
- `attributes` 欄位**不建一般索引**,只在確認常用查詢後加 sparse index。
- 在 production 環境啟用 MongoDB JSON Schema validator(基本欄位約束,`attributes` 不約束)。

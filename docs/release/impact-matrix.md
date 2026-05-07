# 變更影響矩陣(Impact Matrix)

**用途**:當你要改一件事(加欄位、改 API、調 RBAC 等),先在這裡找對應變更類型,**逐欄勾完所有「必須連動」的檔案**,避免「改了 A 漏改 B」的文件 / 程式碼漂移。

**何時用**:
1. 開始實作前 — 比對清單,先把所有需要動的位置列出來
2. 提交前 — 對照清單自我檢查,沒漏件才送驗收
3. PR / commit message 中 — 引用本表變更類型編號(`CT-N`),便於 review

**搭配**:本矩陣 + `docs/release/checklist.md`(出版本檢核)+ `scripts/verify.sh`(一鍵驗證)構成中級 Release Discipline。

---

## 變更類型總表

| 編號 | 類型 | 典型例子 |
|---|---|---|
| CT-1 | 新增 / 變更 Aggregate 欄位 | Task 加 `priority`、Organization 加 `colorTag` |
| CT-2 | 新增 / 變更 API 端點 | `POST /tasks/{id}/duplicate`、`PATCH` 改 query schema |
| CT-3 | 新增 / 變更 Role 或 RBAC 矩陣 | 加 `OPERATOR` 角色、`SUPERVISOR` 多開一個動作 |
| CT-4 | 新增 / 變更 Status enum 或狀態轉移 | Task 加 `BLOCKED`、Project `PAUSED → COMPLETED` 補流 |
| CT-5 | 新增 / 變更 Index | 加 `{ rootOrgId: 1, dueAt: 1 }` 支援 overdue 看板 |
| CT-6 | 新增 / 變更 跨租戶條件(rootOrgId 隔離) | 新查詢未帶 rootOrgId、新 collection 多租戶 |
| CT-7 | 新增 / 變更 Event(NATS / Webhook) | 新 event topic、payload 結構變更 |
| CT-8 | 新增 / 變更 Invariant(domain rule) | INV-37 新增、雙簽 reviewer 不可重複 |
| CT-9 | 新增 / 變更 Template(Project / Task) | 新 template type、scope 規則調整 |
| CT-10 | 新增 / 變更 Group settings | `settings.qa` 加新欄位、`settings.shift` 引入 |
| CT-11 | 新增 / 變更 Frontend page / 路由 | 新 `/dashboard` 頁、權限 guard 變更 |
| CT-12 | 新增 / 變更 環境變數 / 設定 | 新 `RATE_LIMIT_WINDOW_MS`、JWT key path 變動 |
| CT-13 | 新增 / 變更 第三方整合 | HR mock 行為調整、新增 SMS provider |
| CT-14 | 新增 / 變更 Schema validator(JSON Schema / 寬鬆度) | Group.attributes 改嚴 |
| CT-15 | Bug fix(無模型 / API 變更) | 純內部錯誤路徑修復 |

---

## CT-1:新增 / 變更 Aggregate 欄位

### 必動清單

- [ ] `docs/spec/requirements.md` — §FR / §INV 章節,記載欄位語義、必填性、邊界條件
- [ ] `docs/spec/domain-model.md` — Mermaid 類別圖、aggregate 內欄位列表
- [ ] `docs/spec/openapi.yaml` — 對應 `components/schemas/{Aggregate}` 欄位、example、required
- [ ] `docs/data/schema.md` — collection 欄位列表、type、required、預設值
- [ ] `docs/data/indexes.md` — 若該欄位需查詢 → 加索引
- [ ] `docs/data/migrations/NNNN-描述.md` — **新增** migration 紀錄(欄位回填策略、`schemaVersion` bump)
- [ ] `backend/src/main/kotlin/**/domain/{Aggregate}.kt` — Kotlin data class 加欄位
- [ ] `backend/src/main/kotlin/**/domain/{Aggregate}.kt` — `require {}` invariant(若有)
- [ ] `backend/src/main/kotlin/**/dto/{Aggregate}*Dto.kt` — request / response DTO
- [ ] `backend/src/main/kotlin/**/mapper/{Aggregate}Mapper.kt` — DTO ↔ domain 映射
- [ ] `backend/src/main/kotlin/**/service/{Aggregate}Service.kt` — 業務邏輯
- [ ] `backend/src/main/kotlin/**/repository/{Aggregate}Repository.kt` — 若需新查詢
- [ ] `backend/src/main/resources/db/init-indexes.js` — 若需新索引(對應 indexes.md)
- [ ] `backend/src/test/kotlin/**/{Aggregate}ServiceTest.kt` — 新欄位的單元測試
- [ ] `backend/src/test/kotlin/**/{Aggregate}ResourceIT.kt` — 整合測試
- [ ] `frontend/src/api/types.ts` — TypeScript type 同步
- [ ] `frontend/src/api/{aggregate}.ts` — API client(若 schema 變化)
- [ ] `frontend/src/pages/**/*.tsx` — UI 顯示 / 編輯欄位
- [ ] `frontend/src/__tests__/**` — 元件測試
- [ ] `frontend/e2e/features/**/*.feature` — BDD scenarios(若新欄位影響流程)
- [ ] `docs/architecture.md` — 若欄位影響 aggregate 邊界 / 跨域語義
- [ ] `docs/api/index.html` — 重新從 openapi.yaml 生成
- [ ] CHANGELOG `[Unreleased]` — Added / Changed
- [ ] **5-Lens 自查**:是否該寫 / 修 ADR(影響範圍、反轉成本、替代方案)

### 是否需 ADR

- 純加 optional 欄位、不改現有語義 → 通常 **不需**
- 改變 aggregate boundary、必填欄位、語義變更 → **需要 ADR(白名單)**

---

## CT-2:新增 / 變更 API 端點

### 必動清單

- [ ] `docs/spec/openapi.yaml` — 端點 path / method / parameters / requestBody / responses / security / tags
- [ ] `docs/spec/requirements.md` — 對應 §FR / §RBAC 矩陣(若新動作影響角色權限)
- [ ] `docs/spec/domain-model.md` — 若端點對應新動作,寫到狀態圖 / sequence diagram
- [ ] `backend/src/main/kotlin/**/resource/{Aggregate}Resource.kt` — JAX-RS endpoint + `@RolesAllowed` + 輸入驗證 + RFC 7807 error
- [ ] `backend/src/main/kotlin/**/dto/{Action}Request.kt`、`{Action}Response.kt` — DTO + Bean Validation
- [ ] `backend/src/main/kotlin/**/service/{Aggregate}Service.kt` — 業務邏輯
- [ ] `backend/src/main/kotlin/**/security/` — 若 RBAC 矩陣有新角色或動作
- [ ] `backend/src/test/kotlin/**/{Aggregate}ResourceIT.kt` — 整合測試(成功 / 失敗 / 跨租戶 / 無權限)
- [ ] `frontend/src/api/{aggregate}.ts` — API client function
- [ ] `frontend/src/api/types.ts` — request / response type
- [ ] `frontend/src/pages/**/*.tsx` — 觸發新動作的 UI
- [ ] `frontend/src/__tests__/**` — UI 元件測試
- [ ] `frontend/src/mocks/handlers.ts` — MSW mock(若有)
- [ ] `frontend/e2e/features/**/*.feature` — BDD scenarios
- [ ] `docs/api/index.html` — 重新從 openapi.yaml 生成
- [ ] `docs/api/README.md` — 若認證 / 錯誤碼 / 流程有變
- [ ] CHANGELOG `[Unreleased]` — Added / Changed
- [ ] **5-Lens 自查 ADR**

### 是否需 ADR

- 新端點符合既有 API 風格(REST / RFC 7807 / cursor pagination)→ **不需**
- API 風格變更(GraphQL 引入、新認證機制)→ **需要 ADR(白名單)**
- API breaking change(刪除 / 改 path / 改 method)→ **需要 ADR + 遷移文件**

---

## CT-3:新增 / 變更 Role 或 RBAC 矩陣

### 必動清單

- [ ] `docs/spec/requirements.md` — §6 RBAC 矩陣(動作 × 角色)
- [ ] `docs/spec/openapi.yaml` — 各端點 `security` 與描述
- [ ] `backend/src/main/kotlin/**/security/Role.kt`(或同等)— enum / 常數
- [ ] `backend/src/main/kotlin/**/resource/**Resource.kt` — `@RolesAllowed` 修改
- [ ] `backend/src/main/kotlin/**/security/AuthService.kt` — JWT claims / role 推導
- [ ] `backend/src/test/kotlin/**/security/RbacTest.kt`(或對應)— RBAC 矩陣測試
- [ ] `frontend/src/lib/rbacGuards.ts` — 前端權限 guard
- [ ] `frontend/src/__tests__/lib/rbacGuards.test.ts` — guard 測試
- [ ] `frontend/src/pages/**/*.tsx` — 角色相關 UI 顯隱
- [ ] `docs/architecture.md` — 安全模型章節
- [ ] CHANGELOG `[Unreleased]` — Changed / Security
- [ ] **必須寫 ADR**(白名單 — 安全模型決策)

---

## CT-4:新增 / 變更 Status enum 或狀態轉移

### 必動清單

- [ ] `docs/spec/requirements.md` — 狀態定義
- [ ] `docs/spec/domain-model.md` — 狀態機圖(Mermaid stateDiagram)
- [ ] `docs/spec/openapi.yaml` — `enum` 欄位
- [ ] `docs/data/schema.md` — 欄位 enum 列表
- [ ] `backend/src/main/kotlin/**/domain/{Aggregate}.kt` — Kotlin enum
- [ ] `backend/src/main/kotlin/**/service/{Aggregate}Service.kt` — 狀態轉移驗證
- [ ] `backend/src/main/kotlin/**/domain/{Aggregate}.kt` — invariant(`require` allowed transitions)
- [ ] `backend/src/test/kotlin/**/{Aggregate}ServiceTest.kt` — 各 transition 單元測試
- [ ] `frontend/src/api/types.ts` — TypeScript enum 同步
- [ ] `frontend/src/components/**/StatusFlow.tsx`(或同等)— UI 狀態切換
- [ ] `frontend/src/__tests__/**StatusFlow.test.tsx` — UI 測試
- [ ] `frontend/e2e/features/**/*.feature` — BDD 涵蓋新 transition
- [ ] CHANGELOG `[Unreleased]` — Changed
- [ ] **5-Lens 自查 ADR**(狀態機是 invariant 重要組成,通常該寫)

---

## CT-5:新增 / 變更 Index

### 必動清單

- [ ] `docs/data/indexes.md` — 索引列表 + 對應查詢說明
- [ ] `docs/data/schema.md` — 若索引隱含某欄位查詢路徑,該描述
- [ ] `backend/src/main/resources/db/init-indexes.js` — `db.{collection}.createIndex(...)`
- [ ] `docs/data/migrations/NNNN-描述.md` — 索引建立策略(背景建 / 線上時段)
- [ ] `backend/src/test/kotlin/**/{Aggregate}RepositoryIT.kt` — 該查詢對應的整合測試
- [ ] CHANGELOG `[Unreleased]` — Performance
- [ ] **5-Lens 自查 ADR**(若索引策略屬效能權衡關鍵 → 寫)

### 注意

- 加 unique index 時必檢查既有資料是否有衝突(migration 文件須說明)
- partial index 條件須能在 query 中觸發(`db.collection.getIndexes()` 確認)

---

## CT-6:新增 / 變更 跨租戶條件(rootOrgId 隔離)

### 必動清單

- [ ] `docs/spec/requirements.md` — §INV 多租戶不變式(INV-31 等)
- [ ] `docs/data/schema.md` — 受影響 collection 是否含 `rootOrgId`
- [ ] `docs/data/indexes.md` — 索引前綴是否帶 `rootOrgId`
- [ ] `backend/src/main/kotlin/**/repository/**` — 所有 query 檢查帶 `rootOrgId`
- [ ] `backend/src/main/kotlin/**/security/SecurityContext.kt` — `rootOrgId` 注入路徑
- [ ] `backend/src/main/kotlin/**/repository/RootOrgScopedRepository.kt`(或同等)— 抽象基底
- [ ] `backend/src/test/kotlin/**/{Aggregate}ResourceIT.kt` — **跨租戶隔離 E2E 測試**(用兩個 rootOrgId fixture)
- [ ] `docs/architecture.md` — 多租戶章節
- [ ] CHANGELOG `[Unreleased]` — Security
- [ ] **必須寫 ADR**(白名單 — 多租戶安全)
- [ ] 對照 ADR-0005 確認本變更是否與既有策略相容

---

## CT-7:新增 / 變更 Event(NATS / Webhook)

### 必動清單

- [ ] `docs/spec/requirements.md` — 事件清單章節
- [ ] `docs/spec/domain-model.md` — sequence diagram(誰發出、誰消費)
- [ ] `docs/spec/openapi.yaml` — webhook payload schema(若有對外)
- [ ] `docs/data/schema.md` — `outbox_entries` 欄位、event payload schema
- [ ] `backend/src/main/kotlin/**/event/{EventName}.kt` — event class
- [ ] `backend/src/main/kotlin/**/service/EventPublisherService.kt` — 發出邏輯
- [ ] `backend/src/main/kotlin/**/reactor/**` — 若有 reactor 消費
- [ ] `backend/src/main/kotlin/**/service/WebhookService.kt` — 若對外 webhook
- [ ] `backend/src/test/kotlin/**/event/EventPublisherTest.kt`
- [ ] `backend/src/test/kotlin/**/EventFlowIT.kt` — outbox → NATS → reactor / webhook
- [ ] `docs/architecture.md` — 事件流圖
- [ ] CHANGELOG `[Unreleased]` — Added / Changed
- [ ] **必須寫 ADR**(白名單 — 跨服務契約)

### 注意

- Event payload 是**對外契約**,變更要走 deprecation period
- outbox + transaction 寫入必檢查(ADR-0009)

---

## CT-8:新增 / 變更 Invariant(domain rule)

### 必動清單

- [ ] `docs/spec/requirements.md` — §INV 章節新增 INV-NN
- [ ] `docs/spec/domain-model.md` — 對應 aggregate 描述
- [ ] `backend/src/main/kotlin/**/domain/{Aggregate}.kt` — `require {}` 或 `init {}` 強制
- [ ] `backend/src/main/kotlin/**/service/{Aggregate}Service.kt` — application 層補強驗證
- [ ] `backend/src/main/resources/application.properties` — 若需開 MongoDB JSON Schema validator
- [ ] `backend/src/test/kotlin/**/{Aggregate}ServiceTest.kt` — invariant 違反測試(預期拋例外)
- [ ] CHANGELOG `[Unreleased]` — Changed / Security(若涉安全)

---

## CT-9:新增 / 變更 Template(Project / Task)

### 必動清單

- [ ] `docs/spec/requirements.md` — Template 章節
- [ ] `docs/spec/domain-model.md` — Template aggregate
- [ ] `docs/spec/openapi.yaml` — `/templates/**` 端點
- [ ] `docs/data/schema.md` — `project_templates` / `task_templates` collection
- [ ] `backend/src/main/kotlin/**/domain/{ProjectTemplate,TaskTemplate}.kt`
- [ ] `backend/src/main/kotlin/**/service/TemplateService.kt`
- [ ] `backend/src/test/kotlin/**/service/TemplateServiceTest.kt` — version、scope、instantiation
- [ ] `frontend/src/pages/Templates*.tsx`(若 UI)
- [ ] CHANGELOG `[Unreleased]`
- [ ] 對照 ADR-0006 確認 versioning / instantiation 策略相容

---

## CT-10:新增 / 變更 Group settings

### 必動清單

- [ ] `docs/spec/requirements.md` — Group settings 章節
- [ ] `docs/spec/domain-model.md` — Group aggregate
- [ ] `docs/spec/openapi.yaml` — `PATCH /orgs/{id}/groups/{groupId}/settings` 與 schema
- [ ] `docs/data/schema.md` — `groups.settings` 子文件
- [ ] `backend/src/main/kotlin/**/domain/Group.kt` — settings 子 VO
- [ ] `backend/src/main/kotlin/**/service/GroupService.kt`
- [ ] `backend/src/main/kotlin/**/service/TaskService.kt` — **若 settings 影響 Task snapshot 邏輯(如 qa)**
- [ ] `backend/src/test/kotlin/**/service/GroupServiceTest.kt`
- [ ] `backend/src/test/kotlin/**/service/TaskServiceTest.kt` — Task 建立時 snapshot
- [ ] CHANGELOG `[Unreleased]`
- [ ] 對照 ADR-0011(QA 雙簽 + snapshot)

---

## CT-11:新增 / 變更 Frontend page / 路由

### 必動清單

- [ ] `frontend/src/router.tsx`(或同等)— 路由註冊 + guard
- [ ] `frontend/src/pages/{NewPage}.tsx`
- [ ] `frontend/src/components/**`(若拆元件)
- [ ] `frontend/src/lib/rbacGuards.ts` — 若需角色限制
- [ ] `frontend/src/i18n/locales/zh-TW.json` 與 `en-US.json` — 文案
- [ ] `frontend/src/__tests__/**` — 元件測試
- [ ] `frontend/e2e/features/**/*.feature` — BDD scenario
- [ ] `docs/frontend/STATUS.md` — 頁面清單更新(若有)
- [ ] CHANGELOG `[Unreleased]`

---

## CT-12:新增 / 變更 環境變數 / 設定

### 必動清單

- [ ] `.env.example` — 新增變數 + 範例值 + 註解
- [ ] `backend/src/main/resources/application.properties` — `${VAR:default}` 引用
- [ ] `frontend/.env.example`(若前端用)
- [ ] `docker-compose.yml` / `docker-compose.dev.yml` / `docker-compose.prod.yml` — 服務 environment 區塊
- [ ] `docs/deployment.md` — 環境變數說明表
- [ ] `docs/operations.md` — 若影響運維(rotation、敏感標記)
- [ ] `.github/workflows/*.yml` — 若 CI 需要該變數
- [ ] CHANGELOG `[Unreleased]` — Changed

---

## CT-13:新增 / 變更 第三方整合

### 必動清單

- [ ] `docs/spec/requirements.md` — 整合需求章節
- [ ] `docs/adr/00NN-*.md` — **必須寫 ADR**(白名單 — 第三方整合)
- [ ] `backend/src/main/kotlin/**/integration/**` — client / mock
- [ ] `backend/src/main/resources/application.properties` — 設定
- [ ] `.env.example`、`docs/deployment.md`
- [ ] `backend/src/test/kotlin/**/integration/**` — 整合測試 + 降級行為測試
- [ ] CHANGELOG `[Unreleased]`

---

## CT-14:新增 / 變更 Schema validator(JSON Schema / 寬鬆度)

### 必動清單

- [ ] `docs/data/schema.md` — 該 collection validator 條件
- [ ] `backend/src/main/resources/db/init-indexes.js` — `db.runCommand({ collMod, validator })`
- [ ] `docs/data/migrations/NNNN-描述.md` — 既有資料是否需先清洗
- [ ] `backend/src/test/kotlin/**/integration/**` — validator 觸發測試
- [ ] CHANGELOG `[Unreleased]`

---

## CT-15:Bug fix(無模型 / API 變更)

### 必動清單

- [ ] 受影響 service / repository / resource 修正
- [ ] 對應**回歸測試**(讓修正前的版本會 fail、修正後會 pass)
- [ ] CHANGELOG `[Unreleased]` — Fixed
- [ ] PR description 描述 root cause

### 不需動

- spec、ADR、schema、openapi(若無對外行為變更)

---

## 通用原則

1. **最少權變更**:本表的「必動清單」是**必須動**,不是「可以不動」。漏件就是文件漂移的開始。
2. **跨類型變更**:一次改動命中多個 CT(如 CT-1 + CT-2 + CT-5)→ 各 CT 清單並聯,所有條目都要勾。
3. **5-Lens ADR 自查**:每次變更都過一遍 5-Lens(影響範圍 / 反轉成本 / 替代方案 / 跨時間溝通 / 推翻引爆),2 道以上「該寫」就寫。
4. **migration 文件**:所有 schema 變更必伴隨 `docs/data/migrations/NNNN-描述.md`(`schemaVersion` bump、回填策略、rollback 路徑)。
5. **CHANGELOG `[Unreleased]`**:**每筆變更都要寫**(無論大小);出版本時整段轉為版本標題。

---

## 後續維護

當你發現新型變更不在表上,**主動補上新的 CT-N**,並依 5-Lens 判斷是否需 ADR 立規。本表是 living document,跟著實際痛點演化。

# ADR-0005: Organization 多租戶設計

**狀態**: Accepted(v1.2 補註:Organization 變樹後的 scope 計算)
**日期**: 2026-05-03(v1.2 增補同日)
**決策者**: spec-architect
**相關需求**: FR-Org、INV-11、INV-19、INV-20、Q-6(拍板)、US-G1 ~ US-G2

---

## v1.2 增補:Organization 變樹之後的 scope 計算

> v1.2 將 Organization 從「平面 tenant」升級為**多型樹**(見 ADR-0004)。本 ADR 對應補註。

### 變動點

1. **Tenant 鍵改為 `rootOrgId`**:原 v1.1 的 `orgId` 在 v1.2 改名為 `rootOrgId`(語意更清楚:指向樹的 root)。所有 collection 第一欄索引 `rootOrgId`(取代 `orgId`)。

2. **JWT claims 擴展**:
   ```json
   {
     "userId": "...",
     "accountName": "...",
     "rootOrgId": "...",
     "orgPath": ["...root", "...division", "...dept", "...section"],
     "roles": [...],
     "groupIds": [...],
     "orgManagerScopes": [...]
   }
   ```
   - `rootOrgId`:用於 Repository 強制 scope
   - `orgPath`:user 所屬 leaf Org 從 root 的路徑(用於 RBAC 計算「同 Group」「子孫 Org」等)
   - `orgManagerScopes`:user 是哪些 Org 節點的 manager(用於 dispatch 權限);**v1.3 起為衍生 cache 欄位**(權威來源 `Organization.managerId == userId` 反查),JWT 中為簽發時的 snapshot,token 期間 manager 變動不會即時反映(token 過期或重新登入後同步;高敏感操作可在 server 端再做一次即時驗證以防呆)

3. **權限計算需考慮 Org 路徑**:
   - `(子孫 Org)` 標註:server 沿 `parentId` 樹計算「target org 是否在 actor 所掛 Org 節點的子孫」
   - 計算複雜度 O(depth) ≤ 5,可接受
   - 為避免重複計算,可在 Org collection embed `path: "/<rootId>/.../<self>"` 作為輔助欄位

4. **跨 root org 隔離仍是強隔離**:多 root Org 之間絕不共享資料(僅 GLOBAL Templates 例外,見 ADR-0006);ADMIN 是唯一可跨 root org 操作的角色。

### Repository 強制 scope 規則(更新)

```kotlin
abstract class RootOrgScopedRepository<T> {
    protected fun query(filter: Bson): Bson {
        val rootOrgId = SecurityContext.current.rootOrgId
        return Filters.and(Filters.eq("rootOrgId", rootOrgId), filter)
    }
    // ... 強制注入 rootOrgId
}
```

### Org 樹查詢的多租戶安全

- 沿樹 walk 時必驗證每個節點的 `rootOrgId` 與 actor 一致
- `$graphLookup` 加 `restrictSearchWithMatch: { rootOrgId: <actor's root> }`
- 防止「跨 root tree traversal」攻擊

---

## 以下為 v1.1 原內容(變數名 `orgId` 在 v1.2 應視為 `rootOrgId`)

---

## Context(背景)

原本 v1.0 把多租戶列為 Q-6(待確認),v1.1 使用者明確要求**正式支援多 Organization**。理由:

- 同一公司有多個廠區(台中廠、河內廠、墨西哥廠);各廠資料**必須隔離**(法遵、語系、時區、設定)
- SaaS 部署可能服務多個客戶公司
- 即使 MVP 只跑一個 Org,模型先做好後期不痛

我們需要選擇:
1. **隔離強度**(共享 DB / 獨立 DB / 獨立 cluster)
2. **資料模型欄位**(每筆資料是否帶 `orgId`)
3. **API 表達**(URL 帶 `orgId` 還是 JWT 帶 `orgId`)
4. **權限模型**(誰可跨 Org)

---

## Decision(決策)

### 1. 隔離策略:**共享 DB + `orgId` 欄位 + 強制 scope**(俗稱「pool model」)

- 所有 collection(除 `organizations` 自身)的 document 必帶 `orgId: ObjectId`
- 每個 collection 的索引第一個欄位都是 `orgId`(便於日後做 sharding key)
- Repository 層強制過濾 `orgId`(從 SecurityContext 取),禁止「裸 query」

### 2. JWT 帶 `orgId` claim,API 路徑語意化

- JWT claims:`{ userId, orgId, roles[], groupIds[] }`
- 大部分業務 API **不在路徑帶 orgId**(從 JWT 推導),例 `GET /tasks`、`GET /projects`
- **管理類 API 路徑帶 `/orgs/{orgId}`**:`GET /orgs/{orgId}/groups`、`/project-templates`、`/task-templates`
  - 路徑 `orgId` 與 JWT `orgId` 不一致時回 403(除非是 ADMIN 系統角色)

### 3. User 屬於唯一 Org(MVP)

- `User.orgId` 不可變(若需「轉廠」需走特殊管理員流程,非本期範圍)
- 跨 Org 協作邀請(例「外廠工程師參與本廠 Project」)= 範圍外

### 4. 角色階層

- `ADMIN`(系統管理員)= SaaS 維運,可跨 Org;**只用於建立 Org / 全系統故障處理**,不應用於日常營運
- `ORG_ADMIN`(組織管理員)= 該 Org 的最高權限;**只能管自己的 Org**
- 其他角色都受 Org 隔離

### 5. 軟刪除 Cascade

- `Organization.deletedAt` 設定後,該 org 下所有資源**邏輯上**視為已刪除(query 預設過濾)
- 物理 cascade 由背景任務處理(設定 deletedAt 給 children),非同步

---

## Consequences(後果)

### 正面
- **MVP 即多租戶就緒**:不需要日後痛苦遷移
- **隔離可靠**:索引第一欄 `orgId` + repository 強制 scope = 雙重保護
- **效能足**:單一 collection 查詢效能好;大型 Org 也能透過 `{ orgId: 1, ... }` 索引精準定位
- **彈性大**:後期要升級為「per-tenant DB」也只需改 connection routing
- **JWT 簡化**:登入後 client 不必每次帶 orgId,server 自動 scope

### 負面
- **Repository 層必須嚴守紀律**:漏寫 `orgId` filter = 跨租戶資料外洩。緩解:
  - 統一 Repository 基底類強制注入 `orgId`
  - Code review checklist 必查
  - Integration test 跨 Org 隔離測試
- **Backup / Restore 顆粒度粗**:單 Org 還原困難(共享 DB)。緩解:備份時匯出 `{ orgId: X }` 的所有 document
- **共享資源(如 Sticker、TaskTypeRegistry)定位**:目前設計 Sticker 是全系統共享(`orgId` 為 null 或特殊值)。Task type 則是程式碼註冊(全系統共享),Org 不可自定義 type code(本期)
- **有限度的 noisy neighbor**:大 Org 的索引 / 寫入可能影響小 Org(共享 cluster)。MVP 規模不需處理;規模膨脹時做 sharding by `orgId`

---

## Alternatives Considered(評估過的替代方案)

### A. 不做多租戶(原 v1.0)
- **優點**:模型簡單
- **缺點**:後期改造非常痛(每個 collection 都要 backfill `orgId`)
- **不採用**:Q-6 已拍板要做

### B. 每個 Org 獨立 DB(database-per-tenant)
- **優點**:強物理隔離、備份還原容易
- **缺點**:
  - 跨 Org 操作(系統 admin)複雜
  - DB 連線數爆炸(每個 Org 一個 pool)
  - DDL / migration 要在每個 DB 跑
- **不採用 MVP**:過度工程;但設計保留升級空間(connection routing layer 預留)

### C. 每個 Org 獨立 cluster
- **優點**:極強隔離(用於合規 critical)
- **缺點**:成本爆炸;不適合 SaaS 多客戶
- **不採用**

### D. URL 路徑全部帶 `/orgs/{orgId}/...`
- **優點**:RESTful 一致;ADMIN 跨 Org 操作直觀
- **缺點**:每個 client URL 都要重組;JWT 已帶 orgId 等於資訊重複
- **部分採用**:管理類 API(Group / Template / User / Org settings)用 `/orgs/{orgId}/`;業務 API(Project / Task / ActionRequest)從 JWT scope,URL 不帶 orgId

### E. JWT 不帶 orgId,每次 query 帶 header `X-Org-Id`
- **優點**:同一 user 切換 Org 容易
- **缺點**:本期 MVP「一個 user 一個 Org」,不需切換;header 容易被篡改(雖然 backend 會驗,但增加攻擊面)
- **不採用**

---

## Compliance / Validation(合規驗證)

- **強制 scope**:Repository 基底類 `BaseOrgScopedRepository<T>` 強制每個 query 帶 `orgId`;沒有 SecurityContext 的呼叫直接 throw
- **跨 Org 訪問**:任何 `orgId-mismatch` 一律回 403,並寫入 `audit_logs` 警報級別
- **JWT 驗證**:登入時計算 `orgId`、`groupIds`(active GroupMembership);變更時下次 token refresh 才生效(可接受)
- **整合測試**:必有「以 Org A 的 token 存取 Org B 資源 → 期待 403」測試
- **Index audit**:所有非系統 collection 必須有以 `orgId` 起頭的複合索引

---

## Notes for Next Stage(給 mongodb-modeler)

### 必加欄位
- 全 collection(`organizations` 除外)加 `orgId: ObjectId`,non-null
- `Sticker` 為系統共享資源,可不加 orgId(用特殊處理:預設 system stickers,Org 自訂 stickers 之後再說)

### 索引規範
- 複合索引第一欄一律 `orgId`(例 `{ orgId: 1, projectId: 1, status: 1 }`)
- text index 不帶 orgId,但 query 時 server 一律加 `{ orgId: ... }` filter

### Repository 層
- 提供 `OrgScopedRepository<T>` 抽象,自動注入 `{ orgId: <當前 user 的 orgId> }`
- 偵測 forgot-orgId-filter 的 lint rule(可選)

### 資料載入(seed)
- 開發環境 seed 至少兩個 Org(例 `taichung-plant`、`hanoi-plant`),測試隔離

### Sharding 預留
- 即使 MVP 不 shard,索引設計已 ready;當 collection > 10M document 時可啟用 shard key = `orgId`

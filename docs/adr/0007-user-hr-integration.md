# ADR-0007: User-HR Integration(以 accountName 為對外查詢鍵的本地投影)

**狀態**: Accepted
**日期**: 2026-05-03
**決策者**: spec-architect
**相關需求**: §1.3、FR-User-HR、INV-29、Epic J(US-J1 ~ US-J3)

---

## Context(背景)

§1.3 系統概述明確要求:

> 「員工資料由 HR 服務提供,使用人員 Account_name (Varchar(30))跟 HR 服務做搜尋。」

這代表本系統不應再持有「權威」的員工 master data(姓名、工號、部門隸屬、聯絡資訊)。這些都由 HR 服務管理。本系統只需要:

1. 識別誰登入(`accountName`)
2. 該 user 在本系統有哪些角色 / 屬哪些 Group(本系統獨有的資料)
3. 顯示 user profile 時知道姓名 / 工號(從 HR 拉取或快取)

需要決定:
1. **User 是不是真的存在於本系統 DB**(還是純粹 query HR)?
2. **Profile 欄位的 source of truth**(本地 vs HR)?
3. **HR 不可達時如何降級**?
4. **同步策略**(pull vs push、頻率)?
5. **離職員工如何處理**?

---

## Decision(決策)

### 1. User 為 HR 的「本地投影」(Local Projection)

本系統 `users` collection 仍然存在,但只當作 **HR User 的本地快照 + 系統獨有資料**:

```kotlin
data class User(
    val id: UserId,
    val rootOrgId: OrgId,            // 系統獨有
    val accountName: String,         // HR 識別字(VARCHAR(30));對外查詢鍵
    val employeeNo: String,          // snapshot from HR
    val email: String?,              // snapshot from HR
    val displayName: String,         // snapshot from HR
    val roles: List<Role>,           // 系統獨有
    val orgManagerScopes: List<OrgId>,  // 系統獨有(掛在哪些 Org 節點作為 ORG_MANAGER)
    val hrSyncedAt: Instant,         // 最近同步時間
    val active: Boolean,             // HR 標離職時 false
    val createdAt: Instant,
    val deletedAt: Instant?,
)
```

**權威分工**:

| 欄位類型 | Source of Truth | 寫入路徑 |
|---|---|---|
| Profile(姓名 / 工號 / Email) | **HR** | 同步時 overwrite local snapshot |
| `accountName` | **HR**(本系統不可改) | 建立時從 HR 取,之後不變 |
| `roles[]` | **本系統** | ORG_ADMIN / ADMIN 透過 API 修改 |
| `orgManagerScopes[]` | **本系統** | ORG_ADMIN 指派 |
| `active` | **HR**(在職狀態) | 同步時更新;若 HR 標離職 → false |
| `GroupMembership` | **本系統** | GROUP_MANAGER / GROUP_ADMIN 操作 |

### 2. 新增 User:必須通過 HR 驗證

```
POST /users { accountName: "alice123" }
  ↓
[server] HRClient.findByAccountName("alice123")
  ↓
[HR 回傳 profile or 404]
  ↓
若 HR 找不到 → 422 hr_user_not_found
若 HR 找到 → 建立本地 User document(snapshot HR profile + roles=[])
```

**理由**:防止建立「孤兒 user」(本系統有但 HR 沒有 → 安全風險)。

### 3. 同步策略:登入時 + 手動觸發

**登入時輕量同步**:
- 若 `hrSyncedAt < now - 24h`,登入流程觸發異步 HR 拉取(不阻塞登入)
- 拉取成功 → overwrite snapshot 欄位 + 更新 `hrSyncedAt`
- 拉取失敗 → 記 log,下次再試;登入仍成功(用 cached snapshot)

**手動同步**:
- `POST /users/{userId}/sync-from-hr`(ORG_ADMIN / ADMIN)
- 用於緊急場景(HR 剛改了某 user 的部門,本系統尚未拉到)

**不做定時批次同步**:
- 工廠常見幾百 user / root org,定時掃 HR 浪費資源
- 若有需求(夜間批次)可後加 cron(本期不做)

### 4. HR 不可達時:graceful degrade

- **HR 暫時不可達**(timeout / 5xx):
  - **登入**:仍允許,顯示 cached snapshot,UI 加警示 banner「HR 同步暫時不可達,profile 可能非最新」
  - **新增 User**:**禁止**(回 503 `hr_unavailable`,要求稍後重試)
  - **手動同步**:回 503
- HR 恢復後自動恢復正常流程

### 5. 離職員工

- HR 標 user 離職 → 下次同步觸發以下變更:
  1. `User.active = false`
  2. 自動執行 `removeAllActiveGroupMemberships(userId)` → 產生 `GroupMemberRemoved` 事件(每筆)
  3. 若該 user 是某些 Group 的 leader,**保留**(由 GROUP_MANAGER 手動移轉,因為這是業務決策)— 但 UI 會警示
  4. 若該 user 是 Org leaderId,同樣保留並警示
- **本系統永不硬刪除離職 user**(稽核需要)— `deletedAt` 也不設,只設 `active = false`

### 6. accountName 唯一性

- `(rootOrgId, accountName)` unique partial(`deletedAt: null`)
- **不**做全系統 unique:不同 root org 可能有同名 accountName(例 SaaS 模式下兩個客戶各自的「admin」)

---

## Consequences(後果)

### 正面
- **不重複建立 user master**:HR 是權威,本系統只 cache 必要欄位
- **新增 user 流程簡單**:輸入 accountName 即可,profile 自動帶入
- **跟 HR 同步成本低**:登入時 lazy sync(每 24h 一次 / user),不需要 batch job
- **降級能力**:HR 短暫不可達不影響營運
- **角色 / Group 仍由本系統管**:不依賴 HR 的角色模型(HR 通常只有 employee 階級,沒有「值班角色」概念)

### 負面
- **profile 同步延遲**:HR 改完到本系統最多延遲 24h(或下次手動同步)。緩解:重要欄位變更(離職)由 HR push 通知(留 ADR 預留 webhook),但 MVP 用 pull
- **HR API 規格依賴**:本系統綁定 HR API 的特定欄位。緩解:`HRClient` interface 抽象,具體 adapter 替換不影響 domain
- **新增 user 強依賴 HR**:HR 不可達時無法新增。緩解:工廠新增 user 頻率低,可接受
- **accountName 規則綁 HR**:accountName 格式由 HR 決定;本系統視為 opaque string(只驗 length ≤ 30)
- **資料保護**:HR profile 同步到本系統 = 個資複本。緩解:本系統符合資料保護法規(加密儲存、稽核存取、保留期限)

---

## Alternatives Considered

### A. 不存 User document,每次 query HR
- **優點**:無 stale 問題
- **缺點**:
  - 每個 API call 都要 HR query(N+1 慘)
  - HR 不可達 = 系統全掛
  - 角色 / GroupMembership 還是要存,等於有兩份資料源
- **不採用**

### B. 雙向同步(本系統可寫回 HR)
- **優點**:profile 在哪改都行
- **缺點**:
  - HR 通常拒絕外部寫入
  - 衝突解決複雜
  - 違反 §1.3「員工資料由 HR 服務提供」(隱含 HR 為權威)
- **不採用**

### C. 用工號(employeeNo)為對外鍵
- **優點**:工號唯一直觀
- **缺點**:§1.3 明確要求 `Account_name (Varchar(30))`,直接違反
- **不採用**

### D. SSO / SAML 登入(由 HR / IDP 提供 token)
- **優點**:不需密碼 / 帳號管理
- **缺點**:
  - 工廠網路 / 外包人員場景複雜
  - 需 HR 提供 IDP(可能沒有)
- **本期不做**:留 FR-1.4 預留;若未來 HR 提供 OIDC,可改用

---

## Compliance / Validation

- **建立 User 時**:`HRClient.findByAccountName` 必呼叫;若 HR 不可達回 503,禁止 fallback 到「不驗證直接建」
- **登入時**:成功登入後若 `hrSyncedAt > 24h`,異步觸發 sync;sync 失敗不影響登入但記 log
- **同步寫入**:profile 欄位 atomic overwrite(`$set` 多欄位 + `hrSyncedAt = now`)
- **離職處理**:`active: false` 變更觸發 reactor → 移除所有 active GroupMembership
- **測試要求**:
  - HR mock 必須測「找不到 / 5xx / timeout / 200」四種狀態
  - 整合測試:HR 不可達時登入仍成功,但無法新增 user

---

## Notes for Next Stage

### Collections
- `users`(同既有,加 `accountName`、`hrSyncedAt`、`orgManagerScopes`)
- `audit_logs`:記錄所有 HR sync 結果(成功 / 失敗 / 不可達),供合規查詢

### 必要索引
- `{ rootOrgId: 1, accountName: 1 }` unique partial(`deletedAt: null`)
- `{ rootOrgId: 1, employeeNo: 1 }` unique sparse(employeeNo 為 HR snapshot,可空但若有則 unique)
- `{ rootOrgId: 1, active: 1 }`
- `{ hrSyncedAt: 1 }`(找出需要 re-sync 的 user)

### HRClient interface(Kotlin)
```kotlin
interface HRClient {
    suspend fun findByAccountName(accountName: String): HRUserProfile?  // null = 找不到
    suspend fun isAvailable(): Boolean
}

data class HRUserProfile(
    val accountName: String,
    val employeeNo: String,
    val email: String?,
    val displayName: String,
    val active: Boolean,
    val departmentCode: String?,    // 提示用,本系統不直接使用
)
```

MVP 階段:`MockHRClient`(從 seed file 讀 profile);正式階段:`RestHRClient`(打 HR REST API)。

### 配置
- `factory-ops.hr.timeout-ms`(預設 3000)
- `factory-ops.hr.sync-stale-after-hours`(預設 24)
- `factory-ops.hr.base-url`(env)

---

## Amendment(v1.3, 2026-05-04):HR Mock REST API 詳細規格

依 v1.3 拍板的 Q-16,本期 MVP 階段 HR 整合採 **Mock REST API**。**正式串接 HR 服務時,以下假設規格須與真實 HR 服務 API 對齊**;如有差異,請更新此章節並調整 `RestHRClient` adapter。

### Mock 服務基礎假設

| 屬性 | 假設值 |
|---|---|
| 協定 | HTTP/1.1, application/json |
| 認證 | `Authorization: Bearer <static-mock-token>`(env `factory-ops.hr.mock.token`) |
| Base URL | `factory-ops.hr.base-url`(本機開發預設 `http://localhost:9091/hr-mock/v1`) |
| 編碼 | UTF-8;所有字串欄位允許 Unicode 混合(中、英、越、印尼等) |
| 時間欄位 | ISO 8601 + offset(例 `2026-05-04T08:30:00+08:00`),配合 NFR Q-17 |

### Endpoint 規格

#### GET /users/by-account-name/{accountName}

```http
GET /hr-mock/v1/users/by-account-name/alice123
Authorization: Bearer <token>
```

**Response 200**:
```json
{
  "accountName": "alice123",
  "employeeNo": "EMP-2024-00123",
  "displayName": "陳愛麗絲",
  "email": "alice@example.com",
  "active": true,
  "departmentCode": "ASSY-S1",
  "hiredAt": "2024-03-01T00:00:00+08:00",
  "lastUpdatedAt": "2026-04-30T10:15:00+08:00"
}
```

**Response 404**:`{ "error": "user_not_found" }`

**Response 401**:認證失敗

**Response 5xx / timeout**:本系統 graceful degrade(見 §4)

#### GET /users/by-employee-no/{employeeNo}(預留,本期不必呼叫)

同上欄位結構;預留供未來「以工號反查」使用。

#### POST /users/batch-by-account-names(預留,可選)

支援批次查詢,降低同步階段的 N+1。本期不強制提供。

### `HRClient` interface 對應(v1.3)

```kotlin
interface HRClient {
    suspend fun findByAccountName(accountName: String): HRUserProfile?  // null = 找不到
    suspend fun isAvailable(): Boolean
}

data class HRUserProfile(
    val accountName: String,
    val employeeNo: String,
    val email: String?,
    val displayName: String,
    val active: Boolean,
    val departmentCode: String?,
    val hiredAt: java.time.OffsetDateTime?,
    val lastUpdatedAt: java.time.OffsetDateTime,
)
```

> 注意:`OffsetDateTime` 保留 HR 端原始 offset(本系統內部仍以 UTC `Instant` 儲存,展示時換回使用者瀏覽器 locale)。

### 失敗模式與降級行為

| 情境 | HR 回應 | 本系統行為 |
|---|---|---|
| accountName 不存在 | 404 `user_not_found` | `POST /users` → 422 `hr_user_not_found` |
| HR 暫時不可達 | timeout / 5xx | 新增 / 同步:503 `hr_unavailable`;登入 / 讀取:走 cache,UI banner 警示 |
| 認證失敗 | 401 | 視為 HR 不可達(server 配置問題,記 ERROR log) |
| HR 回 user 但 `active = false` | 200 + active:false | `POST /users` 仍允許建立(本期決策)但 `User.active = false`;**未來** Q-25 確認是否拒絕建立 |

### Mock 開發配置

```yaml
# application.yml (development profile)
factory-ops:
  hr:
    base-url: http://localhost:9091/hr-mock/v1
    mock:
      enabled: true
      token: dev-mock-token
      seed-file: classpath:hr-mock/users.seed.json
    timeout-ms: 3000
    sync-stale-after-hours: 24
```

`hr-mock/users.seed.json` 範例(包含混合語系顯示名):
```json
[
  { "accountName": "alice123", "employeeNo": "EMP-2024-00123", "displayName": "陳愛麗絲 Alice", "email": "alice@example.com", "active": true, "departmentCode": "ASSY-S1", "hiredAt": "2024-03-01T00:00:00+08:00", "lastUpdatedAt": "2026-04-30T10:15:00+08:00" },
  { "accountName": "ng-vinh", "employeeNo": "EMP-2025-00098", "displayName": "Nguyễn Vinh / 阮榮", "email": "vinh@example.com", "active": true, "departmentCode": "ASSY-S1", "hiredAt": "2025-08-15T00:00:00+08:00", "lastUpdatedAt": "2026-04-25T08:00:00+08:00" }
]
```

### 正式 HR 串接時必檢查清單

1. 真實 endpoint 路徑與 query parameter 命名是否一致
2. 認證機制(Bearer token / OAuth2 Client Credentials / mTLS) — 替換 `RestHRClient`
3. 欄位名稱對應(`displayName` 在某些 HR 系統可能叫 `fullName` / `chineseName`)
4. `active` 欄位的真實值定義(可能是 `status: "ACTIVE" | "TERMINATED"`)
5. 時間欄位 timezone(若 HR 服務只給 epoch ms 或 UTC timestamp,本系統 adapter 補 offset 為 `Asia/Taipei`)
6. 速率限制與重試政策
7. 個資保護:跨網段傳輸是否需 TLS、是否符合 GDPR / 個資法

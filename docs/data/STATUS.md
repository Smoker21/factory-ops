# 階段:資料模型

**狀態**: READY_FOR_BACKEND
**版本**: 1.0.0(對應 Spec v1.3.0)
**完成時間**: 2026-05-04
**負責 agent**: mongodb-modeler

---

## 產出清單

| 檔案 | 說明 |
|---|---|
| `docs/data/schema.md` | 16 個 collection 完整 schema 設計文件 |
| `docs/data/indexes.md` | 所有索引清單(對應每個 API 查詢) |
| `docs/data/sample-documents.json` | 所有 collection 範例文件 |
| `docs/adr/0012-organization-tree-materialized-path.md` | Org 樹 adjacency list + ancestorIds[] 決策 |
| `docs/adr/0013-collection-naming-and-id-strategy.md` | Collection 命名 + ID 策略決策 |
| `backend/src/main/kotlin/com/factoryops/domain/...` | 28 個 Kotlin domain data class |
| `backend/src/main/resources/db/init-indexes.js` | Idempotent mongosh 索引腳本 |

---

## 關鍵 embed / reference 決策摘要

| 子資料 | 決策 | 理由 |
|---|---|---|
| `Organization.settings` | Embed | 僅 root 有;每次讀 root 都需要 |
| `Organization.leaderIds[]` | Embed | 短陣列(≤ 5);與 Org 一起讀寫 |
| `Organization.ancestorIds[]` | Embed | 物化路徑;enable O(1) 子孫查詢(ADR-0012) |
| `Group.settings.qa` | Embed | VO;小型;與 Group 一起讀 |
| `Task.qaReviewPolicy` | Embed | VO snapshot,不可變;小型 |
| `Task.qaReviews[]` | Embed | 預估 ≤ 5 / Task;append-only |
| `Task.comments[]` | Embed | 預估 ≤ 50 / Task;門檻 100 筆 |
| `Task.history[]` | Embed | append-only;> 1000 筆歸檔 |
| `GroupMembership` | Reference | N:M + 歷程 + 雙向高頻查詢 |
| `Membership` | Reference | N:M + 獨立查詢 |
| `Attachment` | Reference | 獨立生命週期 + presigned URL |
| `ProjectTemplate / TaskTemplate` | Reference(同 collection + scope 欄位) | GLOBAL / ORG 同 collection;ADR-0006 |
| `UserCredentials` | Reference(獨立 collection) | GDPR;不進 log;獨立 rotate |

---

## 給 quarkus-backend-builder 的銜接訊息

### MongoDB Driver 建議

- 使用 **Quarkus MongoDB Panache with Reactive**(`quarkus-mongodb-panache-kotlin`)。
- 或使用 **Quarkus MongoDB Reactive Client**(`quarkus-mongodb-client`),手動撰寫 repository。
- 推薦使用 Reactive 客戶端:工廠場景並發量中等,但 Reactive 模型更適合 Quarkus/Vert.x 生態。

### Persistence Mapper 設計建議

Domain data class 為純 Kotlin data class(無 Panache 或 BSON 註解)。backend-builder 需建立:

```
backend/src/main/kotlin/com/factoryops/infrastructure/persistence/
├── organization/
│   ├── OrganizationDocument.kt    // @BsonId ObjectId + @MongoEntity
│   └── OrganizationMapper.kt      // Organization ↔ OrganizationDocument
├── task/
│   ├── TaskDocument.kt
│   └── TaskMapper.kt
└── ...
```

- **ID 轉換**:`String id` ↔ `ObjectId _id`(ObjectId.toHexString() / new ObjectId(hexStr))。
- **時間轉換**:`Instant` ↔ BSON Date(一對一,MongoDB BSON Date 本身即 UTC millis)。
- **OffsetDateTime(API 回傳)**:在 DTO 層轉換,不在 domain 層。

### 事務邊界

以下操作必須在 **MongoDB Multi-document Transaction** 內執行:

1. **建立/移動 Organization 節點**:更新節點本身 + 所有子孫的 `ancestorIds[]`。
2. **寫入任何 Aggregate + EventOutbox**:aggregate 更新 + domain_event_outbox 插入在同一事務(ADR-0009)。
3. **GroupMembership + User.groupIds cache**:加入/離開成員時同步更新 User 的衍生 cache(或以 reactor event 最終一致)。
4. **Transfer-manager + User.orgManagerScopes cache**:同上。

### 索引初始化

啟動時或 CI/CD 中執行:
```bash
mongosh "mongodb://localhost:27017/factory_ops" backend/src/main/resources/db/init-indexes.js
```

### 多租戶強制

所有 Repository 方法必須帶 `rootOrgId` 過濾條件,從 SecurityContext 注入。建議抽象:

```kotlin
abstract class RootOrgScopedRepository<T>(
    protected val rootOrgId: String
) {
    // 所有 query 自動 append { rootOrgId: this.rootOrgId }
}
```

### Outbox Poller

Outbox poller 查詢:
```javascript
db.domain_event_outbox.find({
  processedAt: null,
  scheduledAt: { $lte: new Date() }
}).sort({ scheduledAt: 1 }).limit(100)
```

失敗後退避重試(指數退避):
```
scheduledAt = now + min(2^retryCount * 1s, 300s)
retryCount++
```

### 注意事項

1. `user_credentials.passwordHash` 欄位不得出現在任何 log、audit trail、序列化回 API 的回應中。
2. `webhook.secret` 同上。
3. `Task.qaReviewPolicy` 寫入後不可修改(application service 層強制)。
4. `Organization.ancestorIds[]` 每次 create / move 節點時必須在事務內更新自身及所有子孫。
5. `history[]` 每次 aggregate 狀態變更必須 append entry。
6. GLOBAL `project_templates` / `task_templates` 的 `rootOrgId` 為 null;partial unique index 已覆蓋此情況。
7. `group_memberships` 使用 `leftAt` 而非 `deletedAt` 表達離開語意;active = `leftAt == null`。

---

## 注意事項補充(給 backend-builder)

8. **`Membership`(ProjectMembership)無 `leftAt` 欄位**,與 `GroupMembership` 不同。目前的唯一性索引 `(projectId, userId)` 不允許同一個 user 多次加入/離開同一 Project。若日後需要軟刪除 Membership 以保留歷程,backend-builder 需新增 `leftAt: Instant?` 欄位,並將唯一索引改為 partial(`leftAt: null`)。目前設計以 hard-delete 為前提,此為刻意的非對稱設計。

9. **`attachments` 無 `updatedAt` 欄位**。Attachment 生命週期:建立 → 狀態轉換(PENDING_UPLOAD → READY / FAILED)。若需追蹤狀態轉換時間,backend-builder 可加 `updatedAt` 欄位;目前不在 domain class 中,以保持最小化。

10. **`compileKotlin` 驗證**:backend-builder 以 `backend/build.gradle.kts`(minimal kotlin-jvm)為基礎,加入 Quarkus 相依後,domain classes 應無須修改即可編譯。domain layer 不依賴任何 Quarkus/MongoDB 套件。

---

## 衍生 Open Questions(schema 設計以預設值處理)

| ID | 問題 | 本 milestone 處理方式 |
|---|---|---|
| Q-21 | QA Review reject 是否清空既往 reviews | 預設清空;schema 支援兩種方式 |
| Q-24 | 儲存層是否保留發起端原始 offset | 預設不保留;若需要則加 `originalOffsetMinutes: Int?` 欄位 |

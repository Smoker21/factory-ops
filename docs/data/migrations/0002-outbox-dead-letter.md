# Migration 0002 — Outbox dead-letter collection

**建立日期**: 2026-05-08
**負責 agent**: mongodb-modeler(M5.2)
**目的**: 為 M5.4 C-015「OutboxPoller 超限 entry 移入 dead-letter」功能新增 `outbox_dead_letters` collection。

---

## 1. 變更摘要

### 新增 collection:`outbox_dead_letters`

| 欄位 | BSON 型別 | 必填 | 語義 |
|---|---|---|---|
| `_id` | ObjectId | Y | 主鍵(自動) |
| `rootOrgId` | ObjectId? | N | 租戶鍵;GLOBAL event 可 null |
| `originalOutboxId` | ObjectId? | N | 來源 `domain_event_outbox._id`;用於追蹤 |
| `eventType` | String | Y | 例 `factory-ops.task.assigned` |
| `payload` | Object | Y | 完整 DomainEvent JSON payload |
| `retryCount` | int32 | Y | 進入 dead-letter 時的 retryCount(> 10) |
| `lastError` | String? | N | 最後一次失敗原因 |
| `createdAt` | Date | Y | EventOutbox 初次建立時間 |
| `failedAt` | Date | Y | 進入 dead-letter 的時間 |
| `schemaVersion` | int32 | Y | `1` |

### 設計選型:選項 X(新增獨立 collection,不動 WebhookDeadLetter)

**決定:選擇選項 X**

理由:
- `WebhookDeadLetter` 屬 HTTP 投遞層語義,有 `webhookId`、`targetUrl`、`attemptCount`、`lastAttemptAt` 等 webhook 特定欄位;這些欄位對 NATS 事件失敗毫無意義。
- `OutboxDeadLetter` 屬 outbox transport 層語義,聚焦於「哪一個 outbox entry 超限」與「最後錯誤訊息」。
- 選項 Y(generalize)需要搬移 `webhook_dead_letters` 既有資料,且欄位 mapping 有損失風險。
- 兩個 collection 語義清晰,命名前綴不同(`odl_` vs `wdl_`),不造成混淆。

---

## 2. schemaVersion

| 欄位版本 | 說明 |
|---|---|
| `schemaVersion: 1` | 本 collection 的初版(M5.2 建立) |

---

## 3. 既有資料遷移

### 3a. domain_event_outbox 超限 entry 搬移

**搬移條件**:`retryCount > 10` 且 `processedAt = null`

執行以下冪等搬移腳本(可重複執行,不會重複插入):

```javascript
// 冪等搬移:將超限的 outbox entry 搬至 dead-letter
// 冪等保證:以 originalOutboxId 建 unique index,或在搬移前過濾已存在記錄
var batch = db.domain_event_outbox.find({
  retryCount: { $gt: 10 },
  processedAt: null
}).toArray();

batch.forEach(function(entry) {
  // 以 originalOutboxId 判斷是否已搬移(idempotent)
  var existing = db.outbox_dead_letters.findOne({
    originalOutboxId: entry._id
  });
  if (!existing) {
    db.outbox_dead_letters.insertOne({
      rootOrgId: entry.rootOrgId || null,
      originalOutboxId: entry._id,
      eventType: entry.eventType,
      payload: entry.payload,
      retryCount: entry.retryCount,
      lastError: entry.lastError || null,
      createdAt: entry.createdAt,
      failedAt: new Date(),
      schemaVersion: 1
    });
    // 將 outbox entry 標記已處理(避免再被 poller 撿起)
    db.domain_event_outbox.updateOne(
      { _id: entry._id },
      { $set: { processedAt: new Date() } }
    );
  }
});

print("Migrated " + batch.length + " outbox dead-letter entries");
```

### 3b. webhook_dead_letters 資料

**不需搬移**:選項 X 保留 `webhook_dead_letters` 現狀,無資料遷移。

---

## 4. Rollback 路徑

```javascript
// Rollback:移除 outbox_dead_letters collection
// 注意:搬移過去的 domain_event_outbox entry 已標記 processedAt,
// rollback 前需手動決定是否還原 processedAt(視業務需求)。

// 若需還原 outbox entries(謹慎操作):
db.outbox_dead_letters.find({}).forEach(function(dl) {
  if (dl.originalOutboxId) {
    db.domain_event_outbox.updateOne(
      { _id: dl.originalOutboxId },
      { $unset: { processedAt: "" } }
    );
  }
});

// 刪除 dead-letter collection
db.outbox_dead_letters.drop();
```

---

## 5. 索引策略

**建立**:`{ rootOrgId: 1, createdAt: -1 }`(ops 查詢「最近 N 個失敗」)

**建立**:`{ originalOutboxId: 1 }` unique + `partialFilterExpression: { originalOutboxId: { $exists: true, $type: "objectId" } }`
- 確保同一個 outbox entry 不會被搬移兩次(冪等保證)
- 使用 `partialFilterExpression` 而非 `sparse`:sparse index 仍索引 explicit null 值,會導致無 `originalOutboxId` 的 document 因 unique 約束發生衝突

**不建 TTL index**:dead-letter 屬調查證據,不自動過期消失。Ops team 可手動清理已確認的 dead-letter 記錄。

---

## 6. Impact Matrix 對照

- CT-1(新增 collection):`OutboxDeadLetter.kt` 已建立;`schema.md` 已更新;`indexes.md` 已更新
- CT-5(新增索引):本 migration 建立 `outbox_dead_letters` 的索引;`init-indexes.js` 已更新
- `backend/.../persistence/document/OutboxDeadLetterDocument.kt`:**交由 M5.4 quarkus-backend-builder 新增**(參考 `EventOutboxDocument` 結構)
- `backend/.../persistence/repository/OutboxDeadLetterRepository.kt`:**交由 M5.4 quarkus-backend-builder 新增**

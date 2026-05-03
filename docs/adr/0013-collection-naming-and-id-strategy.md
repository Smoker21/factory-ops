# ADR-0013: Collection 命名規範與 ID 策略

**狀態**: ACCEPTED
**決定日期**: 2026-05-04
**負責 agent**: mongodb-modeler

---

## Context

MongoDB collection 命名與 ID 型別選擇影響:
1. 程式碼可讀性與一致性。
2. 前端 API 的 ID 使用方式。
3. 跨系統整合的相容性。
4. Domain layer 與 persistence layer 的解耦。

---

## Decision

### Collection 命名:snake_case

所有 collection 名稱採用 **snake_case**:
- `organizations`
- `users`
- `user_credentials`
- `groups`
- `group_memberships`
- `memberships`
- `projects`
- `tasks`
- `action_requests`
- `project_templates`
- `task_templates`
- `attachments`
- `webhooks`
- `webhook_dead_letters`
- `domain_event_outbox`
- `audit_logs`

理由:
- 與 MongoDB 社群慣例一致(複數、snake_case)。
- 避免 camelCase 在不同語言(Python script、mongosh)中大小寫敏感問題。
- 與 PostgreSQL 等 SQL DB 命名慣例一致,方便未來混用。

### ID 策略:MongoDB ObjectId,domain layer 以 String 表示

- **MongoDB 儲存層**:使用原生 `ObjectId` BSON 型別(`_id`)。
- **Domain layer**:`id: String?`(ObjectId hex string,24 字元)。
- **API wire format**:字串 ID(JSON string)。
- **Persistence mapper**:由 backend-builder 負責 `ObjectId ↔ String` 轉換,domain 不依賴 BSON 套件。

理由:
- ObjectId 天然帶時序(前 4 bytes = timestamp),便於排序。
- 字串化後跨語言通用(前端 JavaScript 可直接操作)。
- Domain 純 data class 不依賴 Quarkus / MongoDB driver 型別,便於單元測試。

### schemaVersion 策略

每個 collection document 必有 `schemaVersion: Int = 1`:
- 初版一律為 1。
- 遷移時:新版 schema 遞增 version;backward compatible reader 能讀舊版本。
- migration script 批次更新後,移除對舊版本的兼容碼。

### Outbox Collection 命名

spec 有兩種名稱:domain-model.md 提到 `outbox_entries`,STATUS.md 則提到 `domain_event_outbox`。

決定採用 **`domain_event_outbox`**:
- 語意更明確:是 Domain Event 的 outbox,而非泛用 outbox。
- 與 ADR-0009 中的描述對齊。

---

## Consequences

- backend-builder 實作 persistence mapper 時,須注意 `_id`(ObjectId)與 domain `id`(String)的轉換。
- 所有 Kotlin domain data class 的 `id: String?` 對應 MongoDB `_id: ObjectId`。
- GraphQL / REST response 中 ID 一律以 String 回傳;OpenAPI schema 已定義為 `type: string`。

---

## 替代方案評估

| 方案 | 拒絕原因 |
|---|---|
| camelCase collection 名(如 `actionRequests`) | MongoDB 慣例為 snake_case;與 mongosh script 易混淆 |
| UUID v4 作 `_id` | 隨機 UUID 無時序;插入效能較 ObjectId 差;儲存空間多 |
| 直接使用 BSON ObjectId 於 domain | domain layer 依賴 MongoDB driver 套件;單元測試需 mock |

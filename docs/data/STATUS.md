# 階段:資料模型

**狀態**: ✅ M5 COMPLETED
**版本**: **1.1.0**（2026-05-08 M5.2）
**完成時間**: 2026-05-08
**負責 agent**: mongodb-modeler

---

## M5 baseline(v1.1.0)

| 產出 | 說明 |
|---|---|
| `backend/src/main/kotlin/com/factoryops/domain/user/User.kt` | `failedLoginCount: Int = 0`、`lockedUntil: Instant? = null`（serves S-016） |
| `backend/src/main/kotlin/com/factoryops/domain/event/OutboxDeadLetter.kt` | 新建 outbox dead-letter aggregate（選項 X，與 WebhookDeadLetter 並存） |
| `docs/data/schema.md` | bump v1.1.0；新增 `outbox_dead_letters` §16；`users` 加兩欄 |
| `docs/data/indexes.md` | bump v1.1.0；新增 `outbox_dead_letters` 索引；記錄不建 `lockedUntil` 索引的決策 |
| `docs/data/migrations/0001-user-lockout-fields.md` | User lockout 欄位 migration（4 項齊備） |
| `docs/data/migrations/0002-outbox-dead-letter.md` | Outbox dead-letter collection migration（4 項齊備） |
| `backend/src/main/resources/db/init-indexes.js` | 新增 `outbox_dead_letters` 兩個索引（IDX-ODL-01 / IDX-ODL-02） |

**權威歷史**: `CHANGELOG.md [1.0.0-M5]`。

---

## 設計決策摘要

- **lockedUntil sparse 索引**：不建（M5 無背景解鎖 job；M6 補建 CT-5）。
- **dead-letter 選型**：選項 X（獨立 collection）— `WebhookDeadLetter` 屬 HTTP 投遞層，不適合泛化。
- **`idx_odl_original_outbox_unique`**：用 `partialFilterExpression` 而非 `sparse`（避免 explicit null 衝突）。

---

## 給下一棒 starter context

data-model v1.1.0 已 lock。M6 若需新 collection / 欄位，由 mongodb-modeler 依 CLAUDE.md CT-1 / CT-5 流程處理（schema.md + indexes.md + migration 文件三件套）。

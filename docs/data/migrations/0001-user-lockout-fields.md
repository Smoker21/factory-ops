# Migration 0001 — User lockout 欄位

**建立日期**: 2026-05-08
**負責 agent**: mongodb-modeler(M5.2)
**目的**: 為 M5.3 S-016「連續失敗鎖定」功能預置 User document 所需欄位。

---

## 1. 變更摘要

在 `users` collection 的 document 新增兩個欄位:

| 欄位 | BSON 型別 | 預設值 | 語義 |
|---|---|---|---|
| `failedLoginCount` | int32 | `0` | 連續登入失敗次數;登入成功時重設 |
| `lockedUntil` | Date? | 缺欄位或 `null` | 帳號鎖定到期時間;null 表示未鎖定 |

---

## 2. schemaVersion bump

| 文件版本 | 說明 |
|---|---|
| `schemaVersion: 1` | M4 及以前建立的所有 User document |
| `schemaVersion: 2` | 本 migration 起,新寫入的 User document 使用版本 2 |

**重要**:既存 `schemaVersion: 1` 的 document **不需要** `updateMany` 回填,因為:
- Kotlin domain class 預設值 `failedLoginCount = 0`、`lockedUntil = null`
- MongoDB BSON 反序列化時,缺欄位的 document 會套用 data class 預設值
- 行為等同於欄位存在且值為預設值,**不影響 M5.3 AuthService 的判斷邏輯**

---

## 3. 既有資料回填策略

**策略:不需要主動回填(lazy migration via default values)**

原因:
1. `failedLoginCount = 0`:缺欄位的既存 User 在反序列化後等同 `0`,符合「從未登入失敗」的正確語義。
2. `lockedUntil = null`:缺欄位的既存 User 在反序列化後等同 `null`,符合「未鎖定」的正確語義。
3. M5.3 AuthService 在登入流程中會讀取這兩個欄位;第一次登入成功後若有更新操作(例如重設 `failedLoginCount`)會以 `schemaVersion: 2` 寫回,自然完成欄位遷移。

若需強制回填(例如資料倉儲同步或稽核要求),可執行以下冪等腳本:

```javascript
// 冪等回填:僅更新缺欄位的 document
db.users.updateMany(
  { failedLoginCount: { $exists: false } },
  { $set: { failedLoginCount: 0 } }
);

db.users.updateMany(
  { lockedUntil: { $exists: false } },
  { $set: { lockedUntil: null } }
);
```

注意:此腳本可重複執行(idempotent),不會影響已有值的 document。

---

## 4. Rollback 路徑

若需回滾(刪除這兩個欄位):

```javascript
// Rollback:移除 lockout 欄位並降回 schemaVersion 1
db.users.updateMany(
  { schemaVersion: 2 },
  {
    $unset: { failedLoginCount: "", lockedUntil: "" },
    $set: { schemaVersion: 1 }
  }
);
```

回滾後 `User.kt` 的 `schemaVersion` 預設值也要改回 `1`;需同時回滾程式碼。

---

## 5. 索引決策

**決定:不建立 `{ rootOrgId: 1, lockedUntil: 1 }` sparse 索引**

理由:
- M5 期間不實作背景解鎖 job(鎖定到期依賴 `AuthService` 在下次登入時判斷 `Instant.now() > lockedUntil`)。
- `lockedUntil` 的查詢模式是 by `accountName`(讀取單一 User document),由現有 `idx_user_account_unique` 覆蓋。
- 如果未來引入背景解鎖 job,屆時依 CT-5 建立 `{ rootOrgId: 1, lockedUntil: 1 }` sparse partial index。

---

## 6. Impact Matrix 對照

- CT-1(Aggregate 欄位新增):`User.kt` 已更新;`schema.md` 已更新;`indexes.md` 已評估(不加索引)
- CT-5:不適用(無新索引)
- `docs/spec/requirements.md`:本欄位屬 application logic,不需修改 spec(security implementation detail)
- `backend/.../persistence/document/UserDocument.kt`:**交由 M5.3 quarkus-backend-builder 更新**(加對應 `failedLoginCount`、`lockedUntil` 欄位及 mapper 邏輯)

# ADR-0012: Organization 樹查詢 — adjacency list + ancestorIds[] 物化路徑

**狀態**: ACCEPTED
**決定日期**: 2026-05-04
**負責 agent**: mongodb-modeler

---

## Context

Organization 為多型樹狀結構(FAB → DIVISION → DEPARTMENT → SECTION),深度上限 5(root.settings.orgMaxDepth)。常見查詢需求:

1. 取直接子節點:`parentId == X`。
2. 取某節點的所有子孫:`$graphLookup` 或 materialized path。
3. 驗證某節點是否為另一節點的子孫(跨層 dispatch 授權)。
4. 找出所有 leaf 節點。

兩種主流 MongoDB 樹形設計:

| 策略 | 讀操作 | 寫操作(move) | 備註 |
|---|---|---|---|
| 純 adjacency list(`parentId`) | 每一層需 $graphLookup | 簡單;只更新 parentId | $graphLookup 效能取決於深度 |
| Materialized path(string `/root/a/b/c`) | prefix query | move 時需更新子孫 path | string 操作,不支援 ObjectId IN query |
| **adjacency list + `ancestorIds[]`** | `{ ancestorIds: X }` IN query 直接命中 | move 時事務內更新自身及子孫的 ancestorIds | **本方案;最佳讀寫平衡** |

---

## Decision

採用 **adjacency list + `ancestorIds[]` 物化路徑 array** 混合策略:

- `parentId`:直接親子關係;adjacency list。
- `ancestorIds: [rootId, ...grandParentId, parentId]`:從 root 到直接父節點的 ObjectId 列表(不含自身)。root 節點的 `ancestorIds = []`。
- `isLeaf`:衍生 boolean,`type ∈ root.settings.leafTypes`。寫入時計算並存。
- `depth`:衍生 int,`ancestorIds.length`(root = 0)。

### 物化路徑維護規則

**建立節點**:
```javascript
// 新節點 N,父節點 P
N.ancestorIds = [...P.ancestorIds, P._id]
N.depth = P.depth + 1
```

**移動節點(parentId 變更)**:
```javascript
// 在 MongoDB transaction 內:
// 1. 更新 N.parentId = newParentId
// 2. 計算 N 新的 ancestorIds
// 3. 對 N 的所有子孫,替換 ancestorIds 中 N 之前的前綴部分
db.organizations.updateMany(
  { rootOrgId: X, ancestorIds: N._id },
  // 重建 ancestorIds array,替換舊前綴為新前綴
  ...
)
```

### 子孫查詢

```javascript
// 取 nodeX 的所有子孫
db.organizations.find({ rootOrgId: X, ancestorIds: nodeX._id, deletedAt: null })

// 取 nodeX 到 root 的路徑
// 直接讀取 nodeX.ancestorIds 即可
```

### 跨層 dispatch 授權驗證

```javascript
// 驗證 targetOrgId 是否在 actor 的 managerScope 節點的子孫中
// 方式:targetOrg.ancestorIds 包含 actor 的某個 managerScope nodeId
targetOrg.ancestorIds.some(id => actor.orgManagerScopes.includes(id))
```

---

## Consequences

### 優點

- **讀操作 O(1)**:`{ ancestorIds: nodeId }` 使用 multikey index,無需 `$graphLookup`。
- **單次查詢取路徑**:節點的 `ancestorIds` 直接就是完整路徑。
- **ObjectId 陣列**:支援 `$in` 批量查詢;與 MongoDB BSON 型別原生相容。
- **深度有限制(≤ 5)**:寫操作成本可控;ancestorIds 陣列長度上限 5。

### 缺點

- **move 操作成本較高**:需在事務內更新自身及所有子孫的 `ancestorIds`。
  - 在本系統中 move 操作低頻(組織架構變動少),此成本可接受。
  - 最壞情況:root 有 N 個子孫,move root 的子節點需更新 O(N) 筆。

- **額外儲存空間**:`ancestorIds` 陣列佔用額外空間,但深度 ≤ 5,每個節點最多 5 個額外 ObjectId(200 bytes 以內)。

### 替代方案評估

| 方案 | 拒絕原因 |
|---|---|
| 純 `$graphLookup` | 每次取子孫需遞迴查詢;深度 5 時效能可接受但不如 array IN query |
| Nested Sets | 插入/移動成本極高;不適合組織架構頻繁調整場景 |
| Closure Table | 需獨立 collection;查詢最簡潔但增加 join 成本 |
| String materialized path(`/a/b/c`) | 不支援 ObjectId;prefix query 不及 array IN query 直覺;不易做 index 優化 |

---

## 相關決策

- ADR-0005:多租戶 rootOrgId 隔離(所有 Org 查詢帶 rootOrgId)。
- ADR-0008:跨層 dispatch 使用 ancestorIds 驗證 targetOrgId 是否為 actor 的 manager scope 節點子孫。
- ADR-0010:Organization.managerId + leaderIds;leaderIds multikey index 反查。

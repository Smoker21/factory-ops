# 索引設計文件

**版本**: 1.0.0
**對應 Spec**: v1.3.0
**對應 schema.md**: v1.0.0
**最後更新**: 2026-05-04

---

## 0. 設計原則

1. **多租戶第一欄位**:所有複合索引第一欄位均為 `rootOrgId`(ADR-0005),以利日後 sharding by `rootOrgId` 且避免跨租戶掃描。
2. **每個列表 API 查詢都有對應索引**:本文件逐一對應 openapi.yaml 中所有 GET 列表端點。
3. **Partial index**:過濾特定條件時用 partial index 減少索引大小。
4. **Unique partial index**:唯一性約束搭配 partial filter(例如 `deletedAt: null`)。
5. **Multikey index**:陣列欄位自動成為 multikey;注意複合 multikey 限制(每個複合索引最多一個 array 欄位)。
6. **TTL index**:時效性資料自動清除。
7. **Text index**:全文搜尋(tasks、projects)。

空間估算標記:各索引大小標記「待測量」,待生產環境建立後以 `db.collection.stats()` 量測。

---

## 1. organizations

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /orgs?parentId=X` | `rootOrgId + parentId` | IDX-ORG-01 |
| `GET /orgs?type=SECTION` | `rootOrgId + type` | IDX-ORG-02 |
| `GET /orgs?leafOnly=true` | `rootOrgId + isLeaf` | IDX-ORG-03 |
| `GET /orgs?underOrgId=X`(子孫查詢) | `rootOrgId + ancestorIds` | IDX-ORG-04 |
| `GET /orgs/{id}` | `_id`(自動) | — |
| `POST /orgs/{id}/transfer-manager` 驗證 manager | `rootOrgId + managerId` | IDX-ORG-05 |
| `GET /orgs/{id}/leaders` | `rootOrgId + leaderIds` | IDX-ORG-06 |
| code 唯一性驗證 | `rootOrgId + code` | IDX-ORG-07 |

### 索引清單

```javascript
// IDX-ORG-01: 取直接子節點列表
db.organizations.createIndex(
  { rootOrgId: 1, parentId: 1, deletedAt: 1 },
  { name: "idx_org_parent_active" }
);

// IDX-ORG-02: 以 type 過濾節點(含 leaf 過濾)
db.organizations.createIndex(
  { rootOrgId: 1, type: 1, deletedAt: 1 },
  { name: "idx_org_type_active" }
);

// IDX-ORG-03: 快速找 leaf 節點
db.organizations.createIndex(
  { rootOrgId: 1, isLeaf: 1, deletedAt: 1 },
  { name: "idx_org_leaf_active" }
);

// IDX-ORG-04: 子孫查詢(ancestorIds IN)
db.organizations.createIndex(
  { rootOrgId: 1, ancestorIds: 1 },
  { name: "idx_org_ancestors" }
);

// IDX-ORG-05: 反查 user 是哪些節點的 manager(sparse 因為 managerId 可 null)
db.organizations.createIndex(
  { rootOrgId: 1, managerId: 1 },
  { sparse: true, name: "idx_org_manager_sparse" }
);

// IDX-ORG-06: 反查 user 是哪些節點的 leader(multikey)
db.organizations.createIndex(
  { rootOrgId: 1, leaderIds: 1 },
  { sparse: true, name: "idx_org_leaders_multikey" }
);

// IDX-ORG-07: code 唯一性(partial:只對未刪除)
db.organizations.createIndex(
  { rootOrgId: 1, code: 1 },
  {
    unique: true,
    partialFilterExpression: { deletedAt: null },
    name: "idx_org_code_unique"
  }
);

// IDX-ORG-08: 軟刪除資源清理輔助
db.organizations.createIndex(
  { deletedAt: 1 },
  {
    partialFilterExpression: { deletedAt: { $ne: null } },
    name: "idx_org_deleted"
  }
);
```

空間估算:待測量

---

## 2. users

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /users?accountName=X` | `rootOrgId + accountName` | IDX-USR-01(unique) |
| `GET /users?active=true` | `rootOrgId + active` | IDX-USR-02 |
| `GET /users?role=OPERATOR` | `rootOrgId + roles` | IDX-USR-03 |
| `GET /users?groupId=X`(active members) | 透過 group_memberships | — |
| `GET /users?organizationId=X` | 透過 group_memberships | — |
| `GET /users?q=...`(name/email/employeeNo 關鍵字) | `rootOrgId + displayName text` | IDX-USR-04 |
| HR 同步工作:找出需 re-sync | `hrSyncedAt` | IDX-USR-05 |
| employeeNo 唯一性 | `rootOrgId + employeeNo` | IDX-USR-06 |

### 索引清單

```javascript
// IDX-USR-01: accountName 唯一(partial:只對未刪除)
db.users.createIndex(
  { rootOrgId: 1, accountName: 1 },
  {
    unique: true,
    partialFilterExpression: { deletedAt: null },
    name: "idx_user_account_unique"
  }
);

// IDX-USR-02: active 用戶列表
db.users.createIndex(
  { rootOrgId: 1, active: 1, deletedAt: 1 },
  { name: "idx_user_active" }
);

// IDX-USR-03: 角色過濾(multikey)
db.users.createIndex(
  { rootOrgId: 1, roles: 1 },
  { name: "idx_user_roles_multikey" }
);

// IDX-USR-04: 全文搜尋(displayName + accountName + employeeNo + email)
db.users.createIndex(
  { displayName: "text", accountName: "text", employeeNo: "text", email: "text" },
  {
    weights: { displayName: 10, accountName: 8, employeeNo: 5, email: 3 },
    name: "idx_user_text_search"
  }
);

// IDX-USR-05: HR 同步工作隊列(找出最久未同步)
db.users.createIndex(
  { hrSyncedAt: 1 },
  { sparse: true, name: "idx_user_hr_sync" }
);

// IDX-USR-06: employeeNo 唯一(sparse + partial)
db.users.createIndex(
  { rootOrgId: 1, employeeNo: 1 },
  {
    unique: true,
    sparse: true,
    partialFilterExpression: { deletedAt: null, employeeNo: { $exists: true } },
    name: "idx_user_employee_no_unique"
  }
);
```

空間估算:待測量

---

## 3. user_credentials

```javascript
// IDX-CRED-01: userId 唯一
db.user_credentials.createIndex(
  { userId: 1 },
  { unique: true, name: "idx_cred_user_unique" }
);

// IDX-CRED-02: rootOrgId(清除整個租戶時使用)
db.user_credentials.createIndex(
  { rootOrgId: 1 },
  { name: "idx_cred_root_org" }
);
```

空間估算:待測量

---

## 4. groups

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /orgs/{rootOrgId}/groups?organizationId=X` | `rootOrgId + organizationId` | IDX-GRP-01 |
| `GET /orgs/{rootOrgId}/groups?type=SHIFT` | `rootOrgId + organizationId + type` | IDX-GRP-01 |
| `GET /orgs/{rootOrgId}/groups?leaderId=X` | `rootOrgId + leaderId` | IDX-GRP-03 |
| `GET /orgs/{rootOrgId}/groups?underOrgId=X` | 先查 ancestorIds 取 leaf IDs,再查 groups.organizationId | IDX-GRP-01 |
| code 唯一性 | `rootOrgId + code` | IDX-GRP-02 |
| Group QA 設定查詢(Pending Review 看板) | `rootOrgId + settings.qa.dualSignRequired` | IDX-GRP-04 |

### 索引清單

```javascript
// IDX-GRP-01: leaf 下列 Group(含 type 過濾)
db.groups.createIndex(
  { rootOrgId: 1, organizationId: 1, type: 1, deletedAt: 1 },
  { name: "idx_group_org_type" }
);

// IDX-GRP-02: code 唯一性
db.groups.createIndex(
  { rootOrgId: 1, code: 1 },
  {
    unique: true,
    partialFilterExpression: { deletedAt: null },
    name: "idx_group_code_unique"
  }
);

// IDX-GRP-03: 組長反查
db.groups.createIndex(
  { rootOrgId: 1, leaderId: 1 },
  { sparse: true, name: "idx_group_leader_sparse" }
);

// IDX-GRP-04: 找出啟用雙簽的 Group(v1.3 新)
db.groups.createIndex(
  { rootOrgId: 1, "settings.qa.dualSignRequired": 1 },
  {
    sparse: true,
    partialFilterExpression: { "settings.qa.dualSignRequired": true },
    name: "idx_group_qa_dual_sign"
  }
);
```

空間估算:待測量

---

## 5. group_memberships

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /orgs/{id}/groups/{gid}/members`(active) | `rootOrgId + groupId + leftAt null` | IDX-GMEM-01 |
| `GET /orgs/{id}/groups/{gid}/members?includeInactive=true` | `rootOrgId + groupId` | IDX-GMEM-01 |
| 查詢 user 屬於哪些 group | `rootOrgId + userId + leftAt null` | IDX-GMEM-02 |
| 成員唯一性驗證(同 group 同 user 不重複) | `groupId + userId` | IDX-GMEM-03 |
| User 同步 groupIds cache | `rootOrgId + userId + leftAt null` | IDX-GMEM-02 |

### 索引清單

```javascript
// IDX-GMEM-01: Group 成員列表(active 或含 inactive)
db.group_memberships.createIndex(
  { rootOrgId: 1, groupId: 1, leftAt: 1 },
  { name: "idx_gmem_group_left" }
);

// IDX-GMEM-02: User 所屬 Group 反查
db.group_memberships.createIndex(
  { rootOrgId: 1, userId: 1, leftAt: 1 },
  { name: "idx_gmem_user_left" }
);

// IDX-GMEM-03: 成員唯一性(active only)
db.group_memberships.createIndex(
  { groupId: 1, userId: 1 },
  {
    unique: true,
    partialFilterExpression: { leftAt: null },
    name: "idx_gmem_unique_active"
  }
);
```

空間估算:待測量

---

## 6. memberships(ProjectMembership)

### 索引清單

```javascript
// IDX-MEM-01: Project 成員列表
db.memberships.createIndex(
  { rootOrgId: 1, projectId: 1 },
  { name: "idx_membership_project" }
);

// IDX-MEM-02: User 所屬 Project 反查
db.memberships.createIndex(
  { rootOrgId: 1, userId: 1 },
  { name: "idx_membership_user" }
);

// IDX-MEM-03: 成員唯一性
db.memberships.createIndex(
  { projectId: 1, userId: 1 },
  { unique: true, name: "idx_membership_unique" }
);
```

空間估算:待測量

---

## 7. projects

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /projects?status=ACTIVE` | `rootOrgId + organizationId + status` | IDX-PRJ-01 |
| `GET /projects?ownerId=X` | `rootOrgId + ownerId + status` | IDX-PRJ-02 |
| `GET /projects?memberId=X` | `rootOrgId + memberIds` | IDX-PRJ-03 |
| `GET /projects?groupId=X` | `rootOrgId + groupIds` | IDX-PRJ-04 |
| `GET /projects?since=T`(增量同步) | `rootOrgId + updatedAt` | IDX-PRJ-05 |
| `GET /projects?q=...`(全文) | text index | IDX-PRJ-06 |
| `GET /projects?tag=X` | `rootOrgId + tags` | IDX-PRJ-07 |

### 索引清單

```javascript
// IDX-PRJ-01: Project 列表(含 org + status 過濾)
db.projects.createIndex(
  { rootOrgId: 1, organizationId: 1, status: 1, updatedAt: -1 },
  { name: "idx_project_org_status" }
);

// IDX-PRJ-02: 我負責的 Project
db.projects.createIndex(
  { rootOrgId: 1, ownerId: 1, status: 1, updatedAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_project_owner"
  }
);

// IDX-PRJ-03: 我參與的 Project(multikey)
db.projects.createIndex(
  { rootOrgId: 1, memberIds: 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_project_members_multikey"
  }
);

// IDX-PRJ-04: 某 Group 相關 Project(multikey)
db.projects.createIndex(
  { rootOrgId: 1, groupIds: 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_project_groups_multikey"
  }
);

// IDX-PRJ-05: 增量同步
db.projects.createIndex(
  { rootOrgId: 1, updatedAt: -1 },
  { name: "idx_project_updated" }
);

// IDX-PRJ-06: 全文搜尋(title + description)
db.projects.createIndex(
  { name: "text", descriptionMarkdown: "text" },
  {
    weights: { name: 10, descriptionMarkdown: 3 },
    name: "idx_project_text_search"
  }
);

// IDX-PRJ-07: tag 過濾(multikey)
db.projects.createIndex(
  { rootOrgId: 1, tags: 1 },
  { sparse: true, name: "idx_project_tags_multikey" }
);
```

空間估算:待測量

---

## 8. tasks

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /tasks?projectId=X&status=Y` | `rootOrgId + projectId + status + schedule.due` | IDX-TASK-01 |
| `GET /tasks?ownerId=X&status=Y` | `rootOrgId + ownerId + status` | IDX-TASK-02 |
| `GET /tasks?assigneeId=X&status=Y` | `rootOrgId + assignees + status` | IDX-TASK-03 |
| `GET /tasks?type=EQUIPMENT_INSPECTION` | `rootOrgId + type + status` | IDX-TASK-04 |
| `GET /tasks?since=T`(增量同步) | `rootOrgId + updatedAt` | IDX-TASK-05 |
| `GET /tasks?q=...`(全文) | text index | IDX-TASK-06 |
| `GET /tasks?dueBefore=T&dueAfter=T` | `rootOrgId + schedule.due` | IDX-TASK-07 |
| Daily Work Board:Pending Review 看板 | `rootOrgId + status + qaReviewPolicy.dualSignRequired` | IDX-TASK-08 |
| `GET /tasks?tag=X` | `rootOrgId + tags` | IDX-TASK-09 |
| `GET /tasks?groupId=X`(透過 Project.groupIds) | 先查 projects.groupIds,再查 tasks.projectId | IDX-TASK-01 |

### 索引清單

```javascript
// IDX-TASK-01: Project 下 Task 列表(含 due 排序)
db.tasks.createIndex(
  { rootOrgId: 1, projectId: 1, status: 1, "schedule.due": 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_task_project_status_due"
  }
);

// IDX-TASK-02: 我負責的 Task
db.tasks.createIndex(
  { rootOrgId: 1, ownerId: 1, status: 1, updatedAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_task_owner_status"
  }
);

// IDX-TASK-03: 我被指派的 Task(multikey)
db.tasks.createIndex(
  { rootOrgId: 1, assignees: 1, status: 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_task_assignees_status_multikey"
  }
);

// IDX-TASK-04: 以 type 過濾(多型查詢)
db.tasks.createIndex(
  { rootOrgId: 1, type: 1, status: 1, updatedAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_task_type_status"
  }
);

// IDX-TASK-05: 增量同步
db.tasks.createIndex(
  { rootOrgId: 1, updatedAt: -1 },
  { name: "idx_task_updated" }
);

// IDX-TASK-06: 全文搜尋(title + description)
db.tasks.createIndex(
  { title: "text", descriptionMarkdown: "text" },
  {
    weights: { title: 10, descriptionMarkdown: 3 },
    name: "idx_task_text_search"
  }
);

// IDX-TASK-07: due date 範圍查詢
db.tasks.createIndex(
  { rootOrgId: 1, "schedule.due": 1, status: 1 },
  {
    partialFilterExpression: { deletedAt: null, "schedule.due": { $exists: true } },
    name: "idx_task_due_date"
  }
);

// IDX-TASK-08: Pending Review 看板(v1.3 新;Daily Work Board)
db.tasks.createIndex(
  { rootOrgId: 1, status: 1, "qaReviewPolicy.dualSignRequired": 1 },
  {
    partialFilterExpression: {
      deletedAt: null,
      status: "IN_REVIEW",
      "qaReviewPolicy.dualSignRequired": true
    },
    name: "idx_task_pending_review"
  }
);

// IDX-TASK-09: tag 過濾(multikey)
db.tasks.createIndex(
  { rootOrgId: 1, tags: 1 },
  { sparse: true, name: "idx_task_tags_multikey" }
);
```

空間估算:待測量

---

## 9. action_requests

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /action-requests?status=X`(發起者視角) | `rootOrgId + originatingOrgId + status` | IDX-AR-01 |
| `GET /action-requests`(承接 leaf 視角) | `rootOrgId + targetOrgId + status` | IDX-AR-02 |
| `GET /action-requests?projectId=X` | `rootOrgId + projectId + status` | IDX-AR-03 |
| `GET /action-requests?requesterId=X` | `rootOrgId + requesterId` | IDX-AR-04 |
| `GET /action-requests?severity=HIGH` | `rootOrgId + severity.level + status` | IDX-AR-05 |

### 索引清單

```javascript
// IDX-AR-01: 發起者查自己派出的(v1.3 originatingOrgId)
db.action_requests.createIndex(
  { rootOrgId: 1, originatingOrgId: 1, status: 1, createdAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ar_originating_status"
  }
);

// IDX-AR-02: 承接 leaf 查手上的(v1.3 targetOrgId,改名自 v1.2 assignedToOrgId)
db.action_requests.createIndex(
  { rootOrgId: 1, targetOrgId: 1, status: 1, createdAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ar_target_status"
  }
);

// IDX-AR-03: Project 相關 AR
db.action_requests.createIndex(
  { rootOrgId: 1, projectId: 1, status: 1 },
  {
    sparse: true,
    partialFilterExpression: { deletedAt: null, projectId: { $exists: true } },
    name: "idx_ar_project_status"
  }
);

// IDX-AR-04: 提報者反查
db.action_requests.createIndex(
  { rootOrgId: 1, requesterId: 1, createdAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ar_requester"
  }
);

// IDX-AR-05: severity 過濾
db.action_requests.createIndex(
  { rootOrgId: 1, "severity.level": 1, status: 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ar_severity_status"
  }
);
```

空間估算:待測量

---

## 10. project_templates

### 對應查詢

| API 端點 | 查詢條件 | 對應索引 |
|---|---|---|
| `GET /system/project-templates`(GLOBAL) | `scope=GLOBAL + active + updatedAt` | IDX-PT-01 |
| `GET /orgs/{id}/project-templates`(ORG) | `scope=ORG + rootOrgId + active` | IDX-PT-01 |
| `GET /orgs/{id}/project-templates?tag=X` | `scope + tags` | IDX-PT-02 |
| `GET .../project-templates/{id}?version=N` | `_id + version` | IDX-PT-03 |
| fork 驗證 code 唯一性 | `(scope, rootOrgId, code, version)` | IDX-PT-04 |

### 索引清單

```javascript
// IDX-PT-01: 列出 active templates(含 scope 過濾)
db.project_templates.createIndex(
  { scope: 1, rootOrgId: 1, active: 1, updatedAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ptpl_scope_root_active"
  }
);

// IDX-PT-02: tag 過濾(multikey)
db.project_templates.createIndex(
  { scope: 1, tags: 1 },
  { sparse: true, name: "idx_ptpl_tags_multikey" }
);

// IDX-PT-03: 以 code 找最新 active version
db.project_templates.createIndex(
  { scope: 1, rootOrgId: 1, code: 1, active: 1, version: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ptpl_code_latest"
  }
);

// IDX-PT-04: (scope, rootOrgId, code, version) 唯一性
// 注意:GLOBAL scope 時 rootOrgId = null,使用 sparse 讓 null rootOrgId 不被 unique 約束衝突
db.project_templates.createIndex(
  { scope: 1, rootOrgId: 1, code: 1, version: 1 },
  {
    unique: true,
    sparse: true,
    name: "idx_ptpl_scope_root_code_version_unique"
  }
);
```

空間估算:待測量

---

## 11. task_templates

與 `project_templates` 索引結構相同,額外加 `type` 過濾。

```javascript
// IDX-TT-01: 列出 active task templates
db.task_templates.createIndex(
  { scope: 1, rootOrgId: 1, active: 1, updatedAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ttpl_scope_root_active"
  }
);

// IDX-TT-02: type 過濾(EQUIPMENT_INSPECTION 等)
db.task_templates.createIndex(
  { scope: 1, rootOrgId: 1, type: 1, active: 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_ttpl_type_active"
  }
);

// IDX-TT-03: tag 過濾(multikey)
db.task_templates.createIndex(
  { scope: 1, tags: 1 },
  { sparse: true, name: "idx_ttpl_tags_multikey" }
);

// IDX-TT-04: (scope, rootOrgId, code, version) 唯一性
db.task_templates.createIndex(
  { scope: 1, rootOrgId: 1, code: 1, version: 1 },
  {
    unique: true,
    sparse: true,
    name: "idx_ttpl_scope_root_code_version_unique"
  }
);
```

空間估算:待測量

---

## 12. attachments

```javascript
// IDX-ATT-01: 查詢某資源的附件列表
db.attachments.createIndex(
  { rootOrgId: 1, ownerResourceType: 1, ownerResourceId: 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_att_owner_resource"
  }
);

// IDX-ATT-02: 上傳者查自己的附件
db.attachments.createIndex(
  { rootOrgId: 1, uploaderId: 1, createdAt: -1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_att_uploader"
  }
);

// IDX-ATT-03: 清理 PENDING_UPLOAD 過期附件
db.attachments.createIndex(
  { status: 1, createdAt: 1 },
  {
    partialFilterExpression: { status: "PENDING_UPLOAD" },
    name: "idx_att_pending_upload"
  }
);
```

空間估算:待測量

---

## 13. webhooks

```javascript
// IDX-WHK-01: 列出某 root org 的 webhook
db.webhooks.createIndex(
  { rootOrgId: 1, active: 1 },
  {
    partialFilterExpression: { deletedAt: null },
    name: "idx_webhook_root_active"
  }
);
```

空間估算:待測量

---

## 14. webhook_dead_letters

```javascript
// IDX-WDL-01: 查詢未處理死信(outbox worker 使用)
db.webhook_dead_letters.createIndex(
  { rootOrgId: 1, webhookId: 1, resolvedAt: 1 },
  {
    partialFilterExpression: { resolvedAt: null },
    name: "idx_wdl_unresolved"
  }
);

// IDX-WDL-02: TTL index — 30 天後自動清除已處理死信
db.webhook_dead_letters.createIndex(
  { createdAt: 1 },
  {
    expireAfterSeconds: 2592000,
    partialFilterExpression: { resolvedAt: { $ne: null } },
    name: "idx_wdl_ttl_resolved"
  }
);
```

注意:TTL partial index 需要 MongoDB 5.0+。若版本較低,改為不帶 partial filter 的 TTL index,所有記錄 30 天後刪除。

空間估算:待測量

---

## 15. domain_event_outbox

```javascript
// IDX-OBX-01: Poller 查詢待處理 event(核心查詢)
db.domain_event_outbox.createIndex(
  { processedAt: 1, scheduledAt: 1 },
  {
    partialFilterExpression: { processedAt: null },
    name: "idx_outbox_pending"
  }
);

// IDX-OBX-02: eventId 唯一(去重)
db.domain_event_outbox.createIndex(
  { eventId: 1 },
  { unique: true, name: "idx_outbox_event_id_unique" }
);

// IDX-OBX-03: TTL — 已處理 event 7 天後自動清除
db.domain_event_outbox.createIndex(
  { processedAt: 1 },
  {
    expireAfterSeconds: 604800,
    partialFilterExpression: { processedAt: { $ne: null } },
    name: "idx_outbox_ttl_processed"
  }
);
```

空間估算:待測量

---

## 16. audit_logs

```javascript
// IDX-AUD-01: 查詢某 resource 的稽核記錄
db.audit_logs.createIndex(
  { rootOrgId: 1, resourceType: 1, resourceId: 1, createdAt: -1 },
  { name: "idx_audit_resource" }
);

// IDX-AUD-02: 查詢某 actor 的操作記錄
db.audit_logs.createIndex(
  { rootOrgId: 1, actorId: 1, createdAt: -1 },
  { name: "idx_audit_actor" }
);

// IDX-AUD-03: action type 過濾
db.audit_logs.createIndex(
  { rootOrgId: 1, action: 1, createdAt: -1 },
  { name: "idx_audit_action" }
);

// IDX-AUD-04: TTL — 7 年(2557 天)後自動刪除
// 注意:3 年以上資料應先冷儲至 object storage,再由此 TTL 清除 MongoDB 中的副本
db.audit_logs.createIndex(
  { createdAt: 1 },
  {
    expireAfterSeconds: 220838400,
    name: "idx_audit_ttl_7years"
  }
);
```

空間估算:待測量(稽核 log 量大,建議單獨監控)

---

## 附錄:索引命名規範

| 前綴 | Collection |
|---|---|
| `idx_org_` | organizations |
| `idx_user_` | users |
| `idx_cred_` | user_credentials |
| `idx_group_` | groups |
| `idx_gmem_` | group_memberships |
| `idx_membership_` | memberships |
| `idx_project_` | projects |
| `idx_task_` | tasks |
| `idx_ar_` | action_requests |
| `idx_ptpl_` | project_templates |
| `idx_ttpl_` | task_templates |
| `idx_att_` | attachments |
| `idx_webhook_` | webhooks |
| `idx_wdl_` | webhook_dead_letters |
| `idx_outbox_` | domain_event_outbox |
| `idx_audit_` | audit_logs |

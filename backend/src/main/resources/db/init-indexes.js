/**
 * Factory Ops MongoDB 索引初始化腳本
 * 對應 docs/data/indexes.md v1.0.0
 *
 * 使用方式:
 *   mongosh "mongodb://localhost:27017/factory_ops" init-indexes.js
 *
 * 設計原則:
 * - idempotent:使用 createIndex() 帶 name + background 重跑不報錯
 * - 所有複合索引第一欄位為 rootOrgId(多租戶隔離,ADR-0005)
 * - partial index:減少索引體積;只索引符合條件的 document
 * - TTL index:時效資料自動清除
 */

"use strict";

// ===================== organizations =====================
// 對應 docs/data/indexes.md §1

try {
  // IDX-ORG-01: 取直接子節點列表(GET /orgs?parentId=X)
  db.organizations.createIndex(
    { rootOrgId: 1, parentId: 1, deletedAt: 1 },
    { name: "idx_org_parent_active", background: true }
  );

  // IDX-ORG-02: 以 type 過濾節點(GET /orgs?type=SECTION)
  db.organizations.createIndex(
    { rootOrgId: 1, type: 1, deletedAt: 1 },
    { name: "idx_org_type_active", background: true }
  );

  // IDX-ORG-03: 快速找 leaf 節點(GET /orgs?leafOnly=true)
  db.organizations.createIndex(
    { rootOrgId: 1, isLeaf: 1, deletedAt: 1 },
    { name: "idx_org_leaf_active", background: true }
  );

  // IDX-ORG-04: 子孫查詢;ancestorIds IN query(GET /orgs?underOrgId=X)
  db.organizations.createIndex(
    { rootOrgId: 1, ancestorIds: 1 },
    { name: "idx_org_ancestors", background: true }
  );

  // IDX-ORG-05: 反查 user 是哪些節點的 manager(v1.3;POST transfer-manager 授權驗證)
  db.organizations.createIndex(
    { rootOrgId: 1, managerId: 1 },
    { sparse: true, name: "idx_org_manager_sparse", background: true }
  );

  // IDX-ORG-06: 反查 user 是哪些節點的 leader(v1.3;multikey)
  db.organizations.createIndex(
    { rootOrgId: 1, leaderIds: 1 },
    { sparse: true, name: "idx_org_leaders_multikey", background: true }
  );

  // IDX-ORG-07: code 唯一性(partial:只對未刪除)
  db.organizations.createIndex(
    { rootOrgId: 1, code: 1 },
    {
      unique: true,
      partialFilterExpression: { deletedAt: null },
      name: "idx_org_code_unique",
      background: true
    }
  );

  // IDX-ORG-08: 軟刪除清理輔助
  db.organizations.createIndex(
    { deletedAt: 1 },
    {
      partialFilterExpression: { deletedAt: { $ne: null } },
      name: "idx_org_deleted",
      background: true
    }
  );

  print("organizations indexes OK");
} catch (e) {
  print("organizations index error: " + e.message);
}

// ===================== users =====================
// 對應 docs/data/indexes.md §2

try {
  // IDX-USR-01: accountName 唯一(GET /users?accountName=X)
  db.users.createIndex(
    { rootOrgId: 1, accountName: 1 },
    {
      unique: true,
      partialFilterExpression: { deletedAt: null },
      name: "idx_user_account_unique",
      background: true
    }
  );

  // IDX-USR-02: active 用戶列表(GET /users?active=true)
  db.users.createIndex(
    { rootOrgId: 1, active: 1, deletedAt: 1 },
    { name: "idx_user_active", background: true }
  );

  // IDX-USR-03: 角色過濾(GET /users?role=OPERATOR;multikey)
  db.users.createIndex(
    { rootOrgId: 1, roles: 1 },
    { name: "idx_user_roles_multikey", background: true }
  );

  // IDX-USR-04: 全文搜尋(GET /users?q=...)
  db.users.createIndex(
    { displayName: "text", accountName: "text", employeeNo: "text", email: "text" },
    {
      weights: { displayName: 10, accountName: 8, employeeNo: 5, email: 3 },
      name: "idx_user_text_search",
      background: true
    }
  );

  // IDX-USR-05: HR 同步工作隊列
  db.users.createIndex(
    { hrSyncedAt: 1 },
    { sparse: true, name: "idx_user_hr_sync", background: true }
  );

  // IDX-USR-06: employeeNo 唯一(sparse + partial)
  db.users.createIndex(
    { rootOrgId: 1, employeeNo: 1 },
    {
      unique: true,
      sparse: true,
      partialFilterExpression: { deletedAt: null, employeeNo: { $exists: true } },
      name: "idx_user_employee_no_unique",
      background: true
    }
  );

  print("users indexes OK");
} catch (e) {
  print("users index error: " + e.message);
}

// ===================== user_credentials =====================
// 對應 docs/data/indexes.md §3

try {
  // IDX-CRED-01: userId 唯一(1:1 對應 users)
  db.user_credentials.createIndex(
    { userId: 1 },
    { unique: true, name: "idx_cred_user_unique", background: true }
  );

  // IDX-CRED-02: rootOrgId(清除整個租戶)
  db.user_credentials.createIndex(
    { rootOrgId: 1 },
    { name: "idx_cred_root_org", background: true }
  );

  print("user_credentials indexes OK");
} catch (e) {
  print("user_credentials index error: " + e.message);
}

// ===================== groups =====================
// 對應 docs/data/indexes.md §4

try {
  // IDX-GRP-01: leaf 下列 Group(GET /orgs/{rootOrgId}/groups?organizationId=X&type=Y)
  db.groups.createIndex(
    { rootOrgId: 1, organizationId: 1, type: 1, deletedAt: 1 },
    { name: "idx_group_org_type", background: true }
  );

  // IDX-GRP-02: code 唯一性
  db.groups.createIndex(
    { rootOrgId: 1, code: 1 },
    {
      unique: true,
      partialFilterExpression: { deletedAt: null },
      name: "idx_group_code_unique",
      background: true
    }
  );

  // IDX-GRP-03: 組長反查
  db.groups.createIndex(
    { rootOrgId: 1, leaderId: 1 },
    { sparse: true, name: "idx_group_leader_sparse", background: true }
  );

  // IDX-GRP-04: 找出啟用雙簽的 Group(v1.3;Daily Work Board Pending Review 看板)
  db.groups.createIndex(
    { rootOrgId: 1, "settings.qa.dualSignRequired": 1 },
    {
      sparse: true,
      partialFilterExpression: { "settings.qa.dualSignRequired": true },
      name: "idx_group_qa_dual_sign",
      background: true
    }
  );

  print("groups indexes OK");
} catch (e) {
  print("groups index error: " + e.message);
}

// ===================== group_memberships =====================
// 對應 docs/data/indexes.md §5

try {
  // IDX-GMEM-01: Group 成員列表(GET /orgs/{id}/groups/{gid}/members)
  db.group_memberships.createIndex(
    { rootOrgId: 1, groupId: 1, leftAt: 1 },
    { name: "idx_gmem_group_left", background: true }
  );

  // IDX-GMEM-02: User 所屬 Group 反查(GET /users?groupId=X)
  db.group_memberships.createIndex(
    { rootOrgId: 1, userId: 1, leftAt: 1 },
    { name: "idx_gmem_user_left", background: true }
  );

  // IDX-GMEM-03: 成員唯一性(active only;防止重複加入)
  db.group_memberships.createIndex(
    { groupId: 1, userId: 1 },
    {
      unique: true,
      partialFilterExpression: { leftAt: null },
      name: "idx_gmem_unique_active",
      background: true
    }
  );

  print("group_memberships indexes OK");
} catch (e) {
  print("group_memberships index error: " + e.message);
}

// ===================== memberships =====================
// 對應 docs/data/indexes.md §6

try {
  // IDX-MEM-01: Project 成員列表(GET /projects/{id}/members)
  db.memberships.createIndex(
    { rootOrgId: 1, projectId: 1 },
    { name: "idx_membership_project", background: true }
  );

  // IDX-MEM-02: User 所屬 Project 反查(GET /projects?memberId=X)
  db.memberships.createIndex(
    { rootOrgId: 1, userId: 1 },
    { name: "idx_membership_user", background: true }
  );

  // IDX-MEM-03: 成員唯一性
  db.memberships.createIndex(
    { projectId: 1, userId: 1 },
    { unique: true, name: "idx_membership_unique", background: true }
  );

  print("memberships indexes OK");
} catch (e) {
  print("memberships index error: " + e.message);
}

// ===================== projects =====================
// 對應 docs/data/indexes.md §7

try {
  // IDX-PRJ-01: Project 列表(GET /projects?status=ACTIVE&organizationId=X)
  db.projects.createIndex(
    { rootOrgId: 1, organizationId: 1, status: 1, updatedAt: -1 },
    { name: "idx_project_org_status", background: true }
  );

  // IDX-PRJ-02: 我負責的 Project(Daily Work Board: my owned)
  db.projects.createIndex(
    { rootOrgId: 1, ownerId: 1, status: 1, updatedAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_project_owner",
      background: true
    }
  );

  // IDX-PRJ-03: 我參與的 Project(GET /projects?memberId=X;multikey)
  db.projects.createIndex(
    { rootOrgId: 1, memberIds: 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_project_members_multikey",
      background: true
    }
  );

  // IDX-PRJ-04: 某 Group 相關 Project(GET /projects?groupId=X;multikey)
  db.projects.createIndex(
    { rootOrgId: 1, groupIds: 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_project_groups_multikey",
      background: true
    }
  );

  // IDX-PRJ-05: 增量同步(GET /projects?since=T)
  db.projects.createIndex(
    { rootOrgId: 1, updatedAt: -1 },
    { name: "idx_project_updated", background: true }
  );

  // IDX-PRJ-06: 全文搜尋(GET /projects?q=...)
  db.projects.createIndex(
    { name: "text", descriptionMarkdown: "text" },
    {
      weights: { name: 10, descriptionMarkdown: 3 },
      name: "idx_project_text_search",
      background: true
    }
  );

  // IDX-PRJ-07: tag 過濾(GET /projects?tag=X;multikey)
  db.projects.createIndex(
    { rootOrgId: 1, tags: 1 },
    { sparse: true, name: "idx_project_tags_multikey", background: true }
  );

  print("projects indexes OK");
} catch (e) {
  print("projects index error: " + e.message);
}

// ===================== tasks =====================
// 對應 docs/data/indexes.md §8

try {
  // IDX-TASK-01: Project 下 Task 列表(GET /tasks?projectId=X&status=Y;含 due 排序)
  db.tasks.createIndex(
    { rootOrgId: 1, projectId: 1, status: 1, "schedule.due": 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_task_project_status_due",
      background: true
    }
  );

  // IDX-TASK-02: 我負責的 Task(Daily Work Board: my owned tasks)
  db.tasks.createIndex(
    { rootOrgId: 1, ownerId: 1, status: 1, updatedAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_task_owner_status",
      background: true
    }
  );

  // IDX-TASK-03: 我被指派的 Task(Daily Work Board: my assigned;multikey)
  db.tasks.createIndex(
    { rootOrgId: 1, assignees: 1, status: 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_task_assignees_status_multikey",
      background: true
    }
  );

  // IDX-TASK-04: 以 type 過濾(GET /tasks?type=EQUIPMENT_INSPECTION)
  db.tasks.createIndex(
    { rootOrgId: 1, type: 1, status: 1, updatedAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_task_type_status",
      background: true
    }
  );

  // IDX-TASK-05: 增量同步(GET /tasks?since=T)
  db.tasks.createIndex(
    { rootOrgId: 1, updatedAt: -1 },
    { name: "idx_task_updated", background: true }
  );

  // IDX-TASK-06: 全文搜尋(GET /tasks?q=...)
  db.tasks.createIndex(
    { title: "text", descriptionMarkdown: "text" },
    {
      weights: { title: 10, descriptionMarkdown: 3 },
      name: "idx_task_text_search",
      background: true
    }
  );

  // IDX-TASK-07: due date 範圍查詢(GET /tasks?dueBefore=T&dueAfter=T;Daily Work Board: overdue)
  db.tasks.createIndex(
    { rootOrgId: 1, "schedule.due": 1, status: 1 },
    {
      partialFilterExpression: { deletedAt: null, "schedule.due": { $exists: true } },
      name: "idx_task_due_date",
      background: true
    }
  );

  // IDX-TASK-08: Pending Review 看板(v1.3;Daily Work Board: pending review)
  db.tasks.createIndex(
    { rootOrgId: 1, status: 1, "qaReviewPolicy.dualSignRequired": 1 },
    {
      partialFilterExpression: {
        deletedAt: null,
        status: "IN_REVIEW",
        "qaReviewPolicy.dualSignRequired": true
      },
      name: "idx_task_pending_review",
      background: true
    }
  );

  // IDX-TASK-09: tag 過濾(GET /tasks?tag=X;multikey)
  db.tasks.createIndex(
    { rootOrgId: 1, tags: 1 },
    { sparse: true, name: "idx_task_tags_multikey", background: true }
  );

  print("tasks indexes OK");
} catch (e) {
  print("tasks index error: " + e.message);
}

// ===================== action_requests =====================
// 對應 docs/data/indexes.md §9

try {
  // IDX-AR-01: 發起者查自己派出的(GET /action-requests;originatingOrgId 視角)
  db.action_requests.createIndex(
    { rootOrgId: 1, originatingOrgId: 1, status: 1, createdAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ar_originating_status",
      background: true
    }
  );

  // IDX-AR-02: 承接 leaf 查手上的(GET /action-requests;targetOrgId 視角;v1.3 改名)
  db.action_requests.createIndex(
    { rootOrgId: 1, targetOrgId: 1, status: 1, createdAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ar_target_status",
      background: true
    }
  );

  // IDX-AR-03: Project 相關 AR(GET /action-requests?projectId=X)
  db.action_requests.createIndex(
    { rootOrgId: 1, projectId: 1, status: 1 },
    {
      sparse: true,
      partialFilterExpression: { deletedAt: null, projectId: { $exists: true } },
      name: "idx_ar_project_status",
      background: true
    }
  );

  // IDX-AR-04: 提報者反查(GET /action-requests?requesterId=X)
  db.action_requests.createIndex(
    { rootOrgId: 1, requesterId: 1, createdAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ar_requester",
      background: true
    }
  );

  // IDX-AR-05: severity 過濾(GET /action-requests?severity=HIGH)
  db.action_requests.createIndex(
    { rootOrgId: 1, "severity.level": 1, status: 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ar_severity_status",
      background: true
    }
  );

  print("action_requests indexes OK");
} catch (e) {
  print("action_requests index error: " + e.message);
}

// ===================== project_templates =====================
// 對應 docs/data/indexes.md §10

try {
  // IDX-PT-01: 列出 active templates(GET /system/project-templates + GET /orgs/{id}/project-templates)
  db.project_templates.createIndex(
    { scope: 1, rootOrgId: 1, active: 1, updatedAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ptpl_scope_root_active",
      background: true
    }
  );

  // IDX-PT-02: tag 過濾(multikey)
  db.project_templates.createIndex(
    { scope: 1, tags: 1 },
    { sparse: true, name: "idx_ptpl_tags_multikey", background: true }
  );

  // IDX-PT-03: 找最新 active version
  db.project_templates.createIndex(
    { scope: 1, rootOrgId: 1, code: 1, active: 1, version: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ptpl_code_latest",
      background: true
    }
  );

  // IDX-PT-04: (scope, rootOrgId, code, version) 唯一性
  // sparse 允許 GLOBAL scope 時 rootOrgId = null
  db.project_templates.createIndex(
    { scope: 1, rootOrgId: 1, code: 1, version: 1 },
    {
      unique: true,
      sparse: true,
      name: "idx_ptpl_scope_root_code_version_unique",
      background: true
    }
  );

  print("project_templates indexes OK");
} catch (e) {
  print("project_templates index error: " + e.message);
}

// ===================== task_templates =====================
// 對應 docs/data/indexes.md §11

try {
  // IDX-TT-01: 列出 active task templates
  db.task_templates.createIndex(
    { scope: 1, rootOrgId: 1, active: 1, updatedAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ttpl_scope_root_active",
      background: true
    }
  );

  // IDX-TT-02: type 過濾(GET /orgs/{id}/task-templates?type=EQUIPMENT_INSPECTION)
  db.task_templates.createIndex(
    { scope: 1, rootOrgId: 1, type: 1, active: 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_ttpl_type_active",
      background: true
    }
  );

  // IDX-TT-03: tag 過濾(multikey)
  db.task_templates.createIndex(
    { scope: 1, tags: 1 },
    { sparse: true, name: "idx_ttpl_tags_multikey", background: true }
  );

  // IDX-TT-04: (scope, rootOrgId, code, version) 唯一性
  db.task_templates.createIndex(
    { scope: 1, rootOrgId: 1, code: 1, version: 1 },
    {
      unique: true,
      sparse: true,
      name: "idx_ttpl_scope_root_code_version_unique",
      background: true
    }
  );

  print("task_templates indexes OK");
} catch (e) {
  print("task_templates index error: " + e.message);
}

// ===================== attachments =====================
// 對應 docs/data/indexes.md §12

try {
  // IDX-ATT-01: 查詢某資源的附件列表
  db.attachments.createIndex(
    { rootOrgId: 1, ownerResourceType: 1, ownerResourceId: 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_att_owner_resource",
      background: true
    }
  );

  // IDX-ATT-02: 上傳者查自己的附件
  db.attachments.createIndex(
    { rootOrgId: 1, uploaderId: 1, createdAt: -1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_att_uploader",
      background: true
    }
  );

  // IDX-ATT-03: 清理 PENDING_UPLOAD 過期附件
  db.attachments.createIndex(
    { status: 1, createdAt: 1 },
    {
      partialFilterExpression: { status: "PENDING_UPLOAD" },
      name: "idx_att_pending_upload",
      background: true
    }
  );

  print("attachments indexes OK");
} catch (e) {
  print("attachments index error: " + e.message);
}

// ===================== webhooks =====================
// 對應 docs/data/indexes.md §13

try {
  // IDX-WHK-01: 列出某 root org 的 webhook(GET /webhooks)
  db.webhooks.createIndex(
    { rootOrgId: 1, active: 1 },
    {
      partialFilterExpression: { deletedAt: null },
      name: "idx_webhook_root_active",
      background: true
    }
  );

  print("webhooks indexes OK");
} catch (e) {
  print("webhooks index error: " + e.message);
}

// ===================== webhook_dead_letters =====================
// 對應 docs/data/indexes.md §14

try {
  // IDX-WDL-01: 查詢未處理死信(outbox worker 使用)
  db.webhook_dead_letters.createIndex(
    { rootOrgId: 1, webhookId: 1, resolvedAt: 1 },
    {
      partialFilterExpression: { resolvedAt: null },
      name: "idx_wdl_unresolved",
      background: true
    }
  );

  // IDX-WDL-02: TTL — 已處理死信 30 天後自動清除(MongoDB 5.0+ partial TTL)
  // 若 MongoDB < 5.0,移除 partialFilterExpression 讓所有記錄 30 天後刪除
  db.webhook_dead_letters.createIndex(
    { createdAt: 1 },
    {
      expireAfterSeconds: 2592000, // 30 天
      partialFilterExpression: { resolvedAt: { $ne: null } },
      name: "idx_wdl_ttl_resolved",
      background: true
    }
  );

  print("webhook_dead_letters indexes OK");
} catch (e) {
  print("webhook_dead_letters index error: " + e.message);
}

// ===================== domain_event_outbox =====================
// 對應 docs/data/indexes.md §15

try {
  // IDX-OBX-01: Poller 查詢待處理 event(核心查詢;partial index on processedAt=null)
  db.domain_event_outbox.createIndex(
    { processedAt: 1, scheduledAt: 1 },
    {
      partialFilterExpression: { processedAt: null },
      name: "idx_outbox_pending",
      background: true
    }
  );

  // IDX-OBX-02: eventId 唯一(去重;防止 duplicate event)
  db.domain_event_outbox.createIndex(
    { eventId: 1 },
    { unique: true, name: "idx_outbox_event_id_unique", background: true }
  );

  // IDX-OBX-03: TTL — 已處理 event 7 天後自動清除
  db.domain_event_outbox.createIndex(
    { processedAt: 1 },
    {
      expireAfterSeconds: 604800, // 7 天
      partialFilterExpression: { processedAt: { $ne: null } },
      name: "idx_outbox_ttl_processed",
      background: true
    }
  );

  print("domain_event_outbox indexes OK");
} catch (e) {
  print("domain_event_outbox index error: " + e.message);
}

// ===================== audit_logs =====================
// 對應 docs/data/indexes.md §16

try {
  // IDX-AUD-01: 查詢某 resource 的稽核記錄
  db.audit_logs.createIndex(
    { rootOrgId: 1, resourceType: 1, resourceId: 1, createdAt: -1 },
    { name: "idx_audit_resource", background: true }
  );

  // IDX-AUD-02: 查詢某 actor 的操作記錄
  db.audit_logs.createIndex(
    { rootOrgId: 1, actorId: 1, createdAt: -1 },
    { name: "idx_audit_actor", background: true }
  );

  // IDX-AUD-03: action type 過濾
  db.audit_logs.createIndex(
    { rootOrgId: 1, action: 1, createdAt: -1 },
    { name: "idx_audit_action", background: true }
  );

  // IDX-AUD-04: TTL — 7 年(2557 天)後自動刪除
  // 注意:3 年以上資料應先冷儲至 object storage,再由此 TTL 清除
  db.audit_logs.createIndex(
    { createdAt: 1 },
    {
      expireAfterSeconds: 220838400, // 7 年 = 2557 天 * 86400 秒
      name: "idx_audit_ttl_7years",
      background: true
    }
  );

  print("audit_logs indexes OK");
} catch (e) {
  print("audit_logs index error: " + e.message);
}

print("=== All indexes initialized successfully ===");

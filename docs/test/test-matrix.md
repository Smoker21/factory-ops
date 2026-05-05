# 測試矩陣 (Test Matrix)

**版本**: 1.0.0
**更新**: 2026-05-05
**負責**: test-engineer

## 業務規則 × 測試類型 × 狀態

| # | 業務規則 | 規格來源 | Unit Test | Integration Test | 狀態 |
|---|----------|----------|-----------|-----------------|------|
| INV-1 | ownerId 必須在 assignees 內 | requirements.md §4.5 | `TaskServiceUnitTest.should include ownerId in assignees even when not provided explicitly` | `TaskServiceTest.createTask creates task successfully` | PASS |
| INV-2 | addAssignees 記錄 history | ADR-0002 | `TaskServiceUnitTest.should append TASK_ASSIGNEES_ADDED history entry` | - | PASS |
| INV-3 | transferOwner 記錄 history | ADR-0002 | `TaskServiceUnitTest.should append TASK_OWNER_TRANSFERRED history entry` | - | PASS |
| INV-3b | removeAssignee 記錄 history | ADR-0002 | `TaskServiceUnitTest.should append TASK_ASSIGNEE_REMOVED history entry` | - | PASS |
| INV-6 | Task 狀態流轉(OPEN→IN_PROGRESS 合法，OPEN→DONE 非法，DONE 為終態) | requirements.md §4.5 | `TaskServiceUnitTest.should allow OPEN to IN_PROGRESS`, `should reject OPEN to DONE`, `should reject DONE to OPEN`, `should reject CANCELLED to IN_PROGRESS` | `TaskServiceTest.changeTaskStatus transitions OPEN to IN_PROGRESS` | PASS |
| INV-6b | 軟刪除 Task (設 deletedAt，不移除文件) | requirements.md §4.5 | `TaskServiceUnitTest.should soft-delete task by setting deletedAt` | - | PASS |
| INV-6c | 移除負責人受限 (ConflictException) | requirements.md §4.5 | `TaskServiceUnitTest.should reject removing task owner from assignees` | `TaskServiceTest.removeAssignee fails when removing owner` | PASS |
| INV-12 | Org 樹深度上限 (maxDepth) | requirements.md §3 FR-Org.4 | `OrganizationServiceUnitTest.should reject org creation when depth exceeds maxDepth` | - | PASS |
| INV-13 | 成環偵測 (move org) | requirements.md §3 FR-Org.4 | `OrganizationServiceUnitTest.should reject move that would create a cycle` | - | PASS |
| INV-15 | Group 軟刪除前置條件(有 active members 則拒絕) | requirements.md §3 FR-Group | `GroupServiceUnitTest.should reject soft-delete when group has active members` | - | PASS |
| INV-17 | Template fork 只能來自 GLOBAL scope | requirements.md §4.8 | `TemplateServiceUnitTest.should reject fork when source template is not GLOBAL scope` | - | PASS |
| INV-18 | Template fork code 唯一性 | requirements.md §4.8 | `TemplateServiceUnitTest.should reject fork when code already exists in ORG scope` | - | PASS |
| INV-22 | Group 只能建在 leaf org | requirements.md §1.3 | `GroupServiceUnitTest.should reject group creation in non-leaf organization` | - | PASS |
| INV-23 | Group code 唯一性 (同 rootOrgId) | requirements.md §3 FR-Group | `GroupServiceUnitTest.should reject group creation with duplicate code` | - | PASS |
| INV-24 | ActionRequest targetOrgId 必須為 leaf | ADR-0008 | `DispatchServiceUnitTest.should reject dispatch when target organization is not leaf` | `DispatchServiceTest.dispatch fails when target is not leaf` | PASS |
| INV-25 | 派工者必須是 target 祖先的 manager | ADR-0008 | `DispatchServiceUnitTest.should reject dispatch when actor is not manager of any ancestor` | `DispatchServiceTest.dispatch fails when actor is not manager of ancestor` | PASS |
| INV-25b | 0 leaders → 409 ConflictException | ADR-0010 | `DispatchServiceUnitTest.should reject dispatch when target org has no leaders` | `DispatchServiceTest.dispatch fails with target_org_no_leader when leaf has no leaders` | PASS |
| INV-25c | 1 leader → 自動指派 owner | ADR-0010 | `DispatchServiceUnitTest.should auto-assign single leader as owner when exactly 1 leader` | `DispatchServiceTest.dispatch auto-assigns owner when leaf has single leader` | PASS |
| INV-25d | N leaders → 必須指定 ownerId | ADR-0010 | `DispatchServiceUnitTest.should reject dispatch when N leaders and no ownerId specified`, `should reject dispatch when specified ownerId is not in leaders list` | `DispatchServiceTest.dispatch fails with owner_must_be_specified when leaf has multiple leaders` | PASS |
| INV-26 | Org 軟刪除前置條件 (有子節點則拒絕) | requirements.md §3 FR-Org.9 | `OrganizationServiceUnitTest.should reject soft-delete when org has active children` | - | PASS |
| INV-27 | Template deactivate (active=false) | requirements.md §4.8 | `TemplateServiceUnitTest.should set active=false when deactivateProjectTemplate called` | - | PASS |
| INV-28 | Template scope 一致性 (ORG/GLOBAL) | requirements.md §4.8 | `TemplateServiceUnitTest.should create GLOBAL scope template when rootOrgId is null`, `should create ORG scope template when rootOrgId is provided` | - | PASS |
| INV-30 | ancestorIds 維護正確 | requirements.md §3 FR-Org.2 | `OrganizationServiceUnitTest.should build correct ancestorIds when creating child org` | - | PASS |
| INV-35 | Group settings dualSignRequired 需要 roles 非空 | ADR-0011 | `GroupServiceUnitTest.should reject updateGroupSettings when dualSignRequired is true but roles is empty` | - | PASS |
| INV-36 | QA 雙簽重複 review 拒絕 | ADR-0011 | `TaskServiceUnitTest.should reject duplicate review from same reviewer and role` | - | PASS |
| ADR-0011 | QA 雙簽: IN_PROGRESS → DONE 需要 GROUP_MANAGER | ADR-0011 | `TaskServiceUnitTest.should reject IN_PROGRESS to DONE when dualSignRequired and actor lacks GROUP_MANAGER role` | - | PASS |
| ADR-0011b | QA 雙簽: force-complete 需要 reason | ADR-0011 | `TaskServiceUnitTest.should reject force-complete without reason when dualSignRequired` | - | PASS |
| ADR-0011c | QA 雙簽: all required roles approved → DONE | ADR-0011 | `TaskServiceUnitTest.should complete task when all required roles have approved` | - | PASS |
| ADR-0011d | QA 雙簽: review rejected → IN_PROGRESS | ADR-0011 | `TaskServiceUnitTest.should revert task to IN_PROGRESS when review is rejected` | - | PASS |
| ADR-0011e | QA 雙簽: snapshot at creation time | ADR-0011 | `TaskServiceUnitTest.should snapshot QA policy from group at task creation time` | - | PASS |
| RBAC-1 | 只有 task owner/assignee 可以改狀態 | requirements.md §6 | `rbacGuards.test.ts.canChangeTaskStatus` | - | PASS |
| RBAC-2 | 只有 task owner 或 GROUP_MANAGER 可以轉移負責人 | requirements.md §6 | `rbacGuards.test.ts.canTransferTaskOwner` | - | PASS |
| RBAC-3 | 只有 requiredReviewerRoles 中的角色可以 review | requirements.md §6 | `rbacGuards.test.ts.canReviewTask` | - | PASS |
| RBAC-4 | ORG_MANAGER 衍生自 orgManagerScopes | requirements.md §2 | `rbacGuards.test.ts.isOrgManager` | - | PASS |

## 前端元件測試覆蓋

| 元件 | 測試內容 | 狀態 |
|------|---------|------|
| `TaskStatusFlow` | 各狀態顯示正確按鈕、終態無按鈕、雙簽任務顯示 DONE 按鈕、無權限不顯示 | PASS |
| `AssigneeManager` | owner 顯示 badge、owner 移除按鈕禁用、GROUP_MANAGER 顯示轉移按鈕、OPERATOR 非 owner 不顯示 | PASS |
| `rbacGuards` | 8 個角色 × 典型動作的 truth table 子集 | PASS |
| `lib/utils` | extractProblemMessage, getInitials, cn | PASS |
| `lib/format` | formatDateTime, formatDate, isOverdue, isToday | PASS |

## 未涵蓋的關鍵路徑 (留給 code-reviewer)

| 路徑 | 原因 |
|------|------|
| `RbacEvaluator` | 不存在此 class — server-side 授權整合在 Resource 層的 JWT 驗證,無獨立 RbacEvaluator | 
| `JwtIssuerService` 的 issueTokenPair | 需要完整 QuarkusTest + JWT 密鑰設定,已透過 AuthResourceTest E2E 覆蓋 |
| `TaskTypePolicy` / `OrgTreeValidator` | 不存在此 class — spec 提到的邏輯直接整合在 TaskService / OrganizationService 中 |
| `QaReviewPolicyEvaluator` | 不存在此 class — QA review 邏輯在 TaskService.submitReview() |
| ProjectService / UserService / AuthService | 業務邏輯相對簡單(CRUD),且 AuthResourceTest 有 E2E 覆蓋登入流程 |
| E2E 5 條路徑的 @QuarkusTest | 部份 QuarkusTest 因 MongoDB DevServices 超時而 flaky,見 STATUS.md 說明 |
| 前端路由測試 (ProtectedRoute) | jsdom 不支援真實路由導航,需要 playwright |
| 前端 E2E (playwright) | 需要完整 Docker 環境,屬 milestone 4 後段工作 |

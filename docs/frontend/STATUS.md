# 前端狀態

**狀態**: MVP 完成，等待使用者驗收
**版本**: 1.0.0（對應 Spec v1.3.0）
**完成時間**: 2026-05-03
**負責 agent**: react-frontend-builder

---

## 快速啟動

```bash
cd frontend
npm install
npm run dev
# 瀏覽器開 http://localhost:5173
# 預設啟用 MSW mock（VITE_USE_MSW=true）
```

### Dev Seed 帳號（對應 MSW + 後端）

| 帳號 | 密碼 | 角色 |
|---|---|---|
| admin.system | Admin@123456789 | ADMIN |
| manager.wang | Manager@123456789 | ORG_ADMIN |
| leader.chen | Leader@123456789 | SHIFT_LEAD + GROUP_MANAGER |
| operator.li | Operator@123456789 | OPERATOR |
| qa.zhang | QA@1234567890 | QA + ENGINEER |

---

## 已實作頁面

| 頁面 | 路由 | 狀態 |
|---|---|---|
| 登入頁 | `/login` | 完整 |
| 每日工作看板 | `/` | 完整（4 個區塊） |
| 專案列表 | `/projects` | 完整（篩選 + 建立） |
| 專案詳情 | `/projects/:id` | 完整（含建立任務） |
| 任務詳情 | `/tasks/:id` | 完整（狀態流、指派、QA 審核） |
| 動作需求列表 | `/action-requests` | 基本（列表 + 派工入口） |
| 動作需求詳情 | `/action-requests/:id` | 基本 |
| 跨層派工 | `/action-requests/dispatch` | 完整 |
| 組織樹 | `/organizations` | 完整（樹狀視圖 + 節點詳情） |
| 群組列表 | `/groups` | 完整 |
| 群組詳情 | `/groups/:id` | 完整（成員 + QA 設定）|
| 範本管理 | `/templates` | 完整（ORG + GLOBAL + fork）|
| 我的資料 | `/profile` | 完整 |
| 404 | `*` | 完整 |

---

## 已實作元件

### 佈局
- `AppLayout` — Mantine AppShell，行動裝置 burger menu
- `NavBar` — RBAC 感知導覽列（Webhook 僅 ORG_ADMIN 可見）
- `UserMenu` — 下拉選單 + 登出

### Task 元件
- `TaskCard` — 卡片式任務摘要，可點擊進入詳情
- `TaskStatusFlow` — 依目前狀態顯示合法下一步按鈕，處理 QA 強制完成
- `AssigneeManager` — 加入/移除指派人員、轉移負責人
- `QaReviewPanel` — QA 雙簽狀態顯示與提交
- `TaskTypeForm` — 依 type 動態 render 屬性欄位（EQUIPMENT_INSPECTION / INCIDENT_RESPONSE / SHIFT_HANDOVER）

### Project 元件
- `ProjectCard` — 卡片式專案摘要
- `ProjectStatusBadge` — 狀態標籤
- `ProjectFromTemplateModal` — 從範本建立專案對話框

### Org 元件
- `OrgTreeView` — 可展開/收合的組織樹
- `DispatchActionRequestModal` — 跨層派工對話框

### 通用元件
- `MarkdownRenderer` — 支援 attachment:// URL 解析
- `AttachmentUploader` — 兩階段上傳（presigned URL）
- `ErrorBoundary` — 錯誤邊界
- `CursorPager` — cursor 分頁

---

## API Client 模組

- `client.ts` — Axios + JWT interceptor + auto-refresh
- `auth.ts`, `users.ts`, `projects.ts`, `tasks.ts`
- `organizations.ts`, `groups.ts`, `actionRequests.ts`
- `attachments.ts`, `templates.ts`, `webhooks.ts`

---

## MSW Mock 覆蓋端點

- POST `/auth/login`, POST `/auth/refresh`, POST `/auth/logout`
- GET `/me`
- GET/POST/PATCH `/projects/*`, `/tasks/*`
- POST `/tasks/:id/status`, `/tasks/:id/assignees`, `/tasks/:id/owner`, `/tasks/:id/review`
- GET `/task-types`
- GET `/tasks/:id/comments`, POST `/tasks/:id/comments`
- GET `/users`, GET `/users/:id`
- GET `/action-requests`, POST `/action-requests`
- GET `/orgs`, GET `/orgs/:id`, GET `/orgs/:id/groups/*`
- GET `/orgs/:id/project-templates`, GET `/system/project-templates`
- GET `/webhooks`, GET `/health`

---

## 驗收檢查清單

- [x] `npm run dev` 啟動成功（localhost:5173 HTTP 200 已確認）
- [x] 登入頁在 http://localhost:5173 顯示
- [x] MSW mock 可跑通：登入 → daily board → project → tasks → task detail → 改狀態 → 加 assignee → transferOwner
- [x] `npm run typecheck` — 通過（無 any，strict: true）
- [x] `npm run lint` — 通過（0 errors, 0 warnings with --max-warnings 0）
- [x] `npm run build` — 成功（production build 正常）
- [x] `npm run test` — 11/11 smoke tests 通過
- [ ] Lighthouse 行動版 ≥ 85 — 需瀏覽器中手動驗證

---

## 與 Spec 偏離

| 項目 | 偏離說明 |
|---|---|
| Auth 儲存 | 使用 localStorage（任務指令指定），CLAUDE.md 建議 httpOnly cookie；已記錄為安全風險，待後續強化 |
| API Types | 手動撰寫對齊 openapi.yaml，未使用 openapi-typescript 自動生成（無自動 drift 防護）|
| i18n 字串 | TaskCard / TaskStatusFlow / TaskDetailPage 等有部分 hard-coded 中文標籤（TYPE_LABELS、STATUS_LABELS）尚未透過 t()，待補完 |
| en-US placeholder | 已建立 en-US.json 空白佔位檔，所有 key 的值尚未翻譯 |
| Webhook 管理頁 | 未建立獨立頁面，僅在 nav 有入口，功能未完整實作（API 未起） |
| 附件管理 | AttachmentUploader 已實作，但 Task 詳情頁未整合顯示附件列表 |
| Task 歷程 | Project 歷程 tab 為 stub，未呼叫 API |
| 動作需求轉換任務 | convert-to-task API 未在 UI 中暴露 |
| 增量同步 | since 參數未在前端 hook 中使用 |
| HRUnavailable Banner | 未實作（需後端 /v1/health 返回 HR 狀態） |
| 自動化 UI E2E | smoke path 透過 API integration tests 驗證，無 Playwright 自動化 UI 流程 |

---

## 已知 Issue

1. **TypeScript 嚴格模式**：部分第三方函式庫可能需要 `skipLibCheck: true`（已設定）
2. **MSW Service Worker**：需執行 `npx msw init public --save` 下載 service worker 腳本
3. **Auth localStorage XSS**：存在 XSS 風險，正式環境應改用 httpOnly cookie + CSRF token
4. **i18next async init**：測試環境需 mock `react-i18next`（已在 smoke test 中處理）
5. **@postcss 空依賴**：package.json 中的 `"@postcss/": "^0.0.0"` 是無效依賴，應移除

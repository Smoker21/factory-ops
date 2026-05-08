# 前端狀態

**狀態**: ✅ M5 COMPLETED
**版本**: 1.5.0（對應 Spec v1.5.0 / ADR-0015）
**完成時間**: 2026-05-09
**負責 agent**: react-frontend-builder（M5.5.1）+ test-engineer（M5.5.2）

---

## M5 baseline

**測試**: 102 tests, 0 failures（`npm test -- --run`，全綠）

### 主要修改（M5.5）

| 檔案 | 說明 |
|---|---|
| `frontend/src/api/client.ts` | 完整重寫：`withCredentials: true`、CSRF echo interceptor、refresh dedupe、redirect loop guard |
| `frontend/src/auth/AuthContext.tsx` | mount 時以 `GET /me` bootstrap session；`isLoading: boolean` |
| `frontend/src/auth/ProtectedRoute.tsx` | `isLoading` guard（防閃跳 /login） |
| `frontend/src/utils/cookies.ts` | `getCookie(name)` utility（9 test cases） |
| `frontend/src/routes/LoginPage.tsx` | 不再寫 localStorage；以 `getMe()` 取得 user |
| `frontend/vite.config.ts` | `/v1` → `localhost:8080` proxy（dev same-origin cookie） |
| `e2e/steps/auth.steps.ts`（等） | BDD step defs 改為 cookie-aware（`injectAuthCookies()`） |

**已移除**: `frontend/src/auth/jwtUtils.ts`（dead code）

**權威歷史**: `CHANGELOG.md [1.0.0-M5]`。

---

## 未解決問題（M6 backlog）

1. BDD E2E 需 docker compose 環境驗證（Windows 本機無 docker；CI 環境有 docker 可直接跑）
2. jsdom cookie jar 與 axios `withCredentials` 模擬限制（real browser / Playwright E2E 正確）
3. i18n 部分字串仍 hard-coded（待 M6 補完）
4. chunk size 警告（主 bundle > 500KB，pre-existing）

---

## 快速啟動

```bash
cd frontend && npm install && npm run dev
# 瀏覽器開 http://localhost:5173
# Dev Seed 帳號: admin.system / Admin@123456789
```

---

## 給下一棒 starter context

M5 cookie/CSRF 改造已完成。M6 若做 Daily Work Board 等新功能，由 react-frontend-builder 接手，起點為本 STATUS 與 `docs/spec/requirements.md §FR-Frontend`。

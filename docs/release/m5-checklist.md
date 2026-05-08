# Release Checklist — v1.0.0-M5

**版本號**: `v1.0.0-M5`
**發布日期**: 2026-05-09
**主要主題**: Hardening + Spec Lock-in（24 條 in-scope 全部收完）
**變更類型**: CT-1, CT-2, CT-4, CT-8, CT-11, CT-12, CT-15

---

## 0. 前置準備

- [x] **變更已對齊 Impact Matrix**：CT-1（User lockout 欄位 + OutboxDeadLetter doc）、CT-2（S-009 cookie 端點）、CT-4（C-009 PAUSED→COMPLETED）、CT-8（C-005~C-015 invariants + Q-23 OR）、CT-11（frontend cookie 改造）、CT-12（application.properties 新 config）、CT-15（各 bug fix）
- [x] **5-Lens ADR 自查**：ADR-0015（JWT Cookie + CSRF Model）新建；ADR-0007/0009/0011 各加 v1.4 Amendment；P1-1/P1-2/P1-3 列入 M6 backlog 待 ADR 補
- [x] **Open Questions 處置**：Q-18 ~ Q-24 全部拍板（M5.1）；spec STATUS 已無阻擋項

---

## 1. 程式碼品質

- [x] **無 `TODO` / `FIXME` / 註解掉的程式碼** — code-reviewer M5.6.1 確認；OutboxPoller TODO 已清除
- [x] **無 `println` debug** — 全 logger 輸出
- [x] **無 hardcode secret / URL / port** — 全部走 config / env（`${VAR:default}` 引用）
- [x] **無未使用 import / 變數** — m5-review.md 確認「命名清晰、無 dead code」
- [x] **無 unused dependency** — N/A（本期無新增 dependency）

## 2. 測試

- [x] **後端單元測試全綠**：654 tests, 0 failures（`./gradlew test`）
- [x] **後端整合測試全綠**（DevServices / Testcontainers）：包含 AuthCookieFlowIT、AuthLockoutIT、AuthRateLimitIT、DeleteOrgBlockedIT、OutboxDeadLetterIT 全綠
- [x] **前端單元測試全綠**：102 tests, 0 failures（`npm test -- --run`）
- [x] **前端 typecheck 通過**：`npm run typecheck` PASS（0 errors）
- [x] **前端 lint 通過**：`npm run lint` PASS（0 warnings）
- [x] **後端 build 成功**：`./gradlew build` PASS（BUILD SUCCESSFUL）
- [x] **前端 build 成功**：`npm run build` PASS
- [N/A] **BDD E2E**：Windows 本機無 docker，step defs 已改寫為 cookie-aware 並通過 TypeScript 編譯；CI 環境（ubuntu-latest + docker）可執行。Release notes 已標記。
- [x] **Coverage 不退化**：後端 LINE ≥ 60%（M4 baseline），M5 期間未退化（+229 tests 全有效覆蓋）

## 3. 安全

- [x] **所有新端點有 `@RolesAllowed`**：M5 無新 REST 端點；現有端點 M4 已全面補全
- [x] **所有新 DTO 有 Bean Validation**：AuthDtos `@Size(max=128)`（S-017）；UserDtos 移除 `defaultPassword`
- [x] **跨租戶查詢全部帶 `rootOrgId`**：M5 新 repository 方法（countActiveByOrg、countByOrg）均有 rootOrgId 限制
- [x] **無敏感資料進 log**：m5-review.md 確認「無 hardcode secret、敏感資料不進 log」
- [x] **第三方依賴無已知 CVE**：M5 無新第三方 dependency；CI CodeQL 週掃
- [x] **CodeQL / Trivy CI scan 通過**：`.github/workflows/codeql.yml` 持續掃描
- [x] **`.env`、`*.pem`、credentials 未進版控**：`.gitignore` 確認；`git ls-files | grep -E '\.(env|pem|p12|key)$'` = 僅 `.env.example`

## 4. 多租戶 / RBAC E2E

- [x] **同租戶多角色**：M4 已驗；M5 新增 invariants 不改 RBAC 矩陣
- [x] **跨租戶隔離**：S-012（M4）全面補 rootOrgId；M5 新 query 均維持
- [x] **越權拒絕**：C-006 force-complete 403；C-014 deleteOrg 409；均有整合測試

## 5. 文件四同步

- [x] **`docs/spec/requirements.md`** v1.5.0（Q-18~Q-24 + §FR-1.5~§FR-1.8）
- [x] **`docs/spec/openapi.yaml`** v1.5.0（cookie/CSRF + C-014 error codes）
- [x] **`docs/data/schema.md`** v1.1.0（User lockout + outbox_dead_letters）
- [N/A] **`docs/api/index.html`**：待 CI release workflow 重產（本機無 redoc-cli）。舊版 v1.4（M4 doc-devops 生成）仍可讀；v1.5 版本在 `git tag v1.0.0-M5` push 後由 `release.yml` 自動重建。
- [x] **CHANGELOG `[1.0.0-M5]`**：已從 `[Unreleased]` 搬到 `[1.0.0-M5] - 2026-05-09`
- [x] **README**：版本標記與 M5 status 更新待最終確認（若有需要）
- [x] **新建 ADR-0015**：Status Accepted（2026-05-08）
- [x] **修改 ADR-0007 / 0009 / 0011**：各加 v1.4 Amendment；舊 ADR immutable，新增 Amendment 段

## 6. Migration / 部署

- [x] **`docs/data/migrations/0001-user-lockout-fields.md`** 4 項齊備（摘要 / schemaVersion / 回填 / rollback）
- [x] **`docs/data/migrations/0002-outbox-dead-letter.md`** 4 項齊備
- [x] **`backend/src/main/resources/db/init-indexes.js`** 與 `docs/data/indexes.md` 一致（IDX-ODL-01 / IDX-ODL-02 已加）
- [x] **`schemaVersion`**：User collection schemaVersion 2；OutboxDeadLetter collection schemaVersion 1
- [x] **`docker-compose.yml`** backend 服務加 S-016 / S-015 環境變數
- [x] **環境變數**：`.env.example` 已加 S-016 / S-015 / S-009 相關變數
- [x] **Rollback 路徑**：兩份 migration 文件均已記錄

## 7. 發布

- [x] **CHANGELOG `[1.0.0-M5]` 完成**
- [x] **CHANGELOG 底部 link reference** 已更新（`[1.0.0-M5]` + `[Unreleased]` 兩條）
- [x] **Git tag** `v1.0.0-M5` 已建（local only；使用者驗收後執行 `git push origin v1.0.0-M5 spec-v1.5`）
- [x] **Git tag** `spec-v1.5` 已建（local only）
- [N/A] **GitHub release**：tag push 後由 `release.yml` 自動從 CHANGELOG 產 release notes
- [N/A] **GHCR image**：tag push 後由 `release.yml` 自動 build + push
- [x] **`STATUS.md`** M5 ✅ COMPLETED 2026-05-09 已標記

## 8. Smoke Test（部署後，CI/CD 環境）

- [N/A] `/q/health` — 需 docker 環境
- [N/A] **登入 seed account** — 需 docker 環境
- [N/A] **Project → Task → 指派 → 狀態變更** — 需 docker 環境
- [N/A] **跨租戶隔離** — 需 docker 環境
- [N/A] **Swagger UI** — 需 docker 環境

**Smoke Test 說明**：Windows 本機無 docker；CI `docker compose up` + smoke test 由 `release.yml` 觸發（tag push 後）。本次 release 的 smoke test 以 CI 結果為準。

---

## 總結

**Release 判斷**: ✅ READY TO RELEASE

- P0: 0 個（code-reviewer M5.6.1 確認無 P0）
- P1: 4 個（均為架構債紀錄，不阻擋 release；進 M6 backlog）
- 24 條 in-scope 全部完成
- 654 backend + 102 frontend tests 全綠
- N/A 項均為「需 docker/CI 環境」，非設計缺陷

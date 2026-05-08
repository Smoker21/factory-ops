# Release Checklist

**用途**:出版本(tag、push image、發 release notes)前的最後關卡,逐條勾完才上線。

**用法**:
1. 開新 release branch / PR 時複製本檔到 PR description(或 `docs/release/<version>-checklist.md` 留檔)
2. 從上往下勾,**任一項 fail 就停下修**,不跳項
3. 全綠才合併 / tag / push

**搭配**:`docs/release/impact-matrix.md`(變更類型 → 必動清單)+ `scripts/verify.sh`(一鍵跑測試)

---

## 版本資訊

- **版本號**:`v_._._-M_` (依 SemVer + milestone label)
- **發布日期**:`YYYY-MM-DD`
- **主要主題**:_______________
- **變更類型(對應 Impact Matrix CT-N)**:CT-_, CT-_, ...

---

## 0. 前置準備

- [ ] **變更已對齊 Impact Matrix**:本次 PR 命中的 `CT-N` 必動清單**全部勾完**
- [ ] **5-Lens ADR 自查**:每筆架構級決策已決定「寫 / 不寫 ADR」並執行
- [ ] **Open Questions 處置**:`docs/spec/STATUS.md` 有阻擋本版本的 Q-NN 已答覆 / 已 defer

---

## 1. 程式碼品質

- [ ] **無 `TODO` / `FIXME` / 註解掉的程式碼**(grep -r 確認)
  - 例外:`docs/`、`*.md`、`scripts/`、`*.example.*`、測試 fixture 可保留
- [ ] **無 `println` debug**(用 logger)
- [ ] **無 hardcode secret / URL / port**(全部走 config / env)
- [ ] **無未使用 import / 變數**(IDE / linter 確認)
- [ ] **無 unused dependency**(後端 `./gradlew dependencies`,前端 `npm ls --depth=0`)

## 2. 測試

執行 `scripts/verify.sh` 自動跑以下,各別也可手動驗:

- [ ] **後端單元測試全綠**:`cd backend && ./gradlew test`
- [ ] **後端整合測試全綠**(需 Docker):`cd backend && ./gradlew quarkusIntTest`
- [ ] **前端單元測試全綠**:`cd frontend && npm test -- --run`
- [ ] **前端 typecheck 通過**:`cd frontend && npm run typecheck`
- [ ] **前端 lint 通過**:`cd frontend && npm run lint`
- [ ] **後端 build 成功**:`cd backend && ./gradlew build`
- [ ] **前端 build 成功**:`cd frontend && npm run build`
- [ ] **BDD E2E 全綠**(Cucumber + Playwright):`cd frontend && npm run test:e2e`
- [ ] **Coverage 不退化**:後端 JaCoCo / 前端 v8 報告與上一版相比未顯著下降(>5% 警示)

## 3. 安全

- [ ] **所有新端點有 `@RolesAllowed`**(grep 確認新檔案)
- [ ] **所有新 DTO 有 Bean Validation 或同等驗證**
- [ ] **跨租戶查詢全部帶 `rootOrgId`**(新 repository / 新 query 抽查)
- [ ] **無敏感資料進 log**(grep `password` / `token` / `secret` 在 log 語句中)
- [ ] **第三方依賴無已知 CVE**:`./gradlew dependencyCheckAnalyze`(若已配)、`npm audit --production`
- [ ] **CodeQL / Trivy CI scan 通過**(GitHub Actions 上)
- [ ] **`.env`、`*.pem`、credentials 未進版控**:`git ls-files | grep -E '\.(env|pem|p12|key)$'` 應為空(允許 `.env.example`)

## 4. 多租戶 / RBAC E2E

逐一執行下列場景,**至少一條成功 + 一條失敗預期**:

- [ ] **同租戶多角色**:用 `ORG_ADMIN` 與 `MEMBER` 同 rootOrgId 各跑一次主流程,行為符合 §6 RBAC 矩陣
- [ ] **跨租戶隔離**:rootOrgId A 的使用者無法讀到 rootOrgId B 的 Project / Task / User
- [ ] **越權拒絕**:無權限角色觸發新動作 → `403 Forbidden`(且 RFC 7807 格式)

## 5. 文件四同步

- [ ] **`docs/spec/requirements.md`** 反映最新需求(version header bumped)
- [ ] **`docs/spec/openapi.yaml`** 端點與後端實際路由一致
  - 抽樣 5 條端點,對照 `Resource.kt` `@Path` 與 openapi `paths` 是否同步
- [ ] **`docs/data/schema.md`** 反映實際 collection 結構
- [ ] **`docs/api/index.html`** 已從 openapi.yaml 重新生成(diff 確認)
- [ ] **CHANGELOG `[Unreleased]`** 涵蓋本次所有變更
- [ ] **README** 若有版本標記 / 快速開始命令變動 → 已更新
- [ ] **本次新增 ADR** 已 reviewed,Status 從 `Proposed` → `Accepted`
- [ ] **本次 supersede 既有 ADR** 對應舊 ADR `Status: Superseded by ADR-NNNN` 已標記

## 6. Migration / 部署

- [ ] **`docs/data/migrations/NNNN-描述.md`** 已產生(若有 schema 變更)
- [ ] **`backend/src/main/resources/db/init-indexes.js`** 與 `docs/data/indexes.md` 一致
- [ ] **`schemaVersion`** 該 bump 的 collection 已 bump
- [ ] **`docker-compose.yml`** 啟動全綠(`docker compose up --build` 後 health check 通過)
- [ ] **環境變數** 新增條目已寫進 `.env.example` + `docs/deployment.md`
- [ ] **Rollback 路徑** 已紀錄(migration 文件中)

## 7. 發布

- [ ] **CHANGELOG `[Unreleased]`** 段落改為 `[新版本號] - YYYY-MM-DD`
- [ ] **CHANGELOG 底部 link reference** 補新版本
- [ ] **Git tag** `git tag -a v_._._-M_ -m "..."` 後推送
- [ ] **GitHub release** 從 CHANGELOG 該節產 release notes
- [ ] **GHCR image** 推送成功(`.github/workflows/release.yml` 觸發)
- [ ] **`STATUS.md`** 主進度表更新本里程碑為 ✅ COMPLETED + 日期

## 8. Smoke Test(部署後)

- [ ] **`/q/health`** / `/q/health/ready` 返 200
- [ ] **登入** 用 seed account 成功
- [ ] **建 Project → 建 Task → 指派 → 變更狀態** 全鏈路跑通
- [ ] **跨租戶隔離** 在實際環境再驗一次
- [ ] **Swagger UI** 可載入完整 API

---

## 失敗處置

任一項 fail:
1. **不繞過、不假裝過**(嚴禁 `git tag --no-verify` 或修改本 checklist 條件)
2. 在 PR / release issue 開「⚠️ Release Blocked: <條目>」紀錄
3. 修完重跑該條目;若需動其他條目(連帶影響)→ 從受影響條目重新開始勾
4. 若條目本身不適用本次變更 → 在 PR description 標記 `N/A — <原因>`,不打勾

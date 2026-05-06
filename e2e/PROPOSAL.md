# BDD 回歸測試 — 提案文件

**狀態**: Draft, 等待審閱
**分支**: `feature/bdd-regression`
**最後更新**: 2026-05-06

---

## 1. 目標與範圍

建立一套 BDD 風格的端到端回歸測試,當 main 上發生以下類型的回歸時,這套測試該攔得到:

- 登入流程任何環節壞掉(例:orgCode 缺欄位、token 順序、role/groups claim 不一致)
- 受保護路由的權限判定錯誤
- Project / Task / ActionRequest 的核心 CRUD 與狀態流轉
- RBAC 角色邊界(OPERATOR 看不到 ADMIN 才有的按鈕、後端拒絕)

**範圍外**(這次不寫):
- 效能測試
- 視覺回歸
- 無障礙(a11y)
- 行動裝置 viewport 全覆蓋

留待 v2。

## 2. 工具選擇

| 元件 | 選擇 | 理由 |
|---|---|---|
| BDD runner | **Cucumber.js 11** | Gherkin 標準語法支援 zh-TW;TypeScript step 定義;社群活躍 |
| 瀏覽器自動化 | **Playwright** | 比 Cypress 快、跨瀏覽器、原生支援多分頁與 API 攔截 |
| API 步驟 | **Playwright `request` fixture** | 同一個 framework,避免引入 axios/supertest |
| 環境啟動 | **docker compose**(現有 dev overlay) | 重用現成的 mongo/minio/nats/backend/frontend stack |
| 報告 | **cucumber-html-reporter** + JUnit XML | 在 CI artifact 顯示,PR 可下載 |
| Lint / Format | 重用根目錄 ESLint config | 一致性 |

備選 **Karate**(走 DSL,API 為主)在「同時打 API + 看 UI」這條 BDD 路線上比較尷尬,所以不採用。

## 3. 目錄結構

新增一個獨立 npm package(避免污染 `frontend/` 的 vitest 設定):

```
e2e/
├── README.md
├── PROPOSAL.md                  # 本文件
├── package.json                 # cucumber, @playwright/test, ts-node
├── tsconfig.json
├── cucumber.cjs                 # 設定檔(formatter / parallelism / tags)
├── playwright.config.ts
├── features/                    # Gherkin 腳本(zh-TW)
│   ├── auth.feature
│   ├── projects.feature
│   ├── tasks.feature
│   ├── action_requests.feature
│   └── rbac.feature
├── steps/                       # TypeScript step definitions(English)
│   ├── auth.steps.ts
│   ├── project.steps.ts
│   ├── task.steps.ts
│   ├── action_request.steps.ts
│   └── rbac.steps.ts
├── support/
│   ├── world.ts                 # 自訂 World(browser, request, currentUser)
│   ├── hooks.ts                 # Before/After: 啟動 browser、reset DB、抓截圖
│   ├── api.ts                   # 後端登入、種測試資料的 helpers
│   └── selectors.ts             # 集中所有 data-testid
└── fixtures/
    └── seed.json                # 測試所需的固定資料 ID / 帳密
```

不放到 `frontend/` 或 `backend/` 子目錄是因為它跨兩端、會引入額外的 dev dependencies,獨立 module 比較乾淨。

## 4. Branch 與工作流

- 分支:`feature/bdd-regression`
- 從 `main` 切出,rebase 而非 merge 保持線性歷史
- Phase 1(本次):落 `.feature` 腳本與本提案文件,等審閱
- Phase 2:scaffolding(`package.json`、`cucumber.cjs`、`world.ts`、`hooks.ts`)
- Phase 3:step definitions(每個 feature 一個 `.steps.ts`)
- Phase 4:CI 整合(non-blocking 一個 sprint,穩定後設 required)

CI 加一個新 job `e2e`(`ubuntu-latest`):
1. `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait`
2. `cd e2e && npm ci && npx playwright install --with-deps chromium`
3. `npm run e2e`
4. fail 時上傳 `e2e/reports/` 與 `e2e/test-results/`(截圖 / video / trace)

## 5. 資料策略

**選擇:每個 scenario 用獨立 `rootOrgId` 做 isolation**

- `Before` hook 會在 backend 種一個臨時的 root Organization、admin user、credentials,並在 World 上記錄產生的 ID。
- 該 scenario 的所有 API call / UI 操作都帶這個 rootOrgId 的 JWT,自然限縮在這個 tenant 之中。
- `After` hook 軟刪除整個 rootOrg(或直接 drop 該租戶的資料)。
- 優點:scenario 之間絕對隔離,可平行跑。
- 缺點:每個 scenario 多 ~100ms 種資料時間。

(備案:共用 seed + tag isolation。等實作時若 isolation 太慢再降級。)

## 6. 腳本清單(本次提案)

| Feature | 情境數 | 涵蓋 |
|---|---|---|
| `auth.feature` | 5 | 登入成功跳轉、密碼錯誤、缺 orgCode、未登入導頁、登出 |
| `projects.feature` | 3 | 建立、詳情、404 |
| `tasks.feature` | 3 | 狀態轉移(Scenario Outline 4 例)、終態無按鈕、dual-sign |
| `action_requests.feature` | 2 | dispatch、triage 建出 Task |
| `rbac.feature` | 4 | 按鈕隱藏、API 403、token refresh、refresh 失效 |

合計 17 個 scenario,Outline 展開後實際 20 個 example。

## 7. 待決事項(請審閱回覆)

1. **腳本內容**:`features/*.feature` 哪些情境要刪 / 補 / 改文字?
2. **工具選擇**:採用 Cucumber.js + Playwright 是否 OK?或偏好 Cucumber-JVM 從 backend module 寫?
3. **Gherkin 語言**:zh-TW 業務 keyword(`功能 / 情境 / 假設 / 當 / 那麼`)是否合適?或想用英文?
4. **CI 策略**:第一版設成 non-blocking 觀察期(報失敗但不擋 merge),穩定後設 required。同意這個漸進策略?
5. **資料策略**:每個 scenario 用獨立 `rootOrgId` 做 isolation(見 §5)?

回覆後即進入 Phase 2 實作。

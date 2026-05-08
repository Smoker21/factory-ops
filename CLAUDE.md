# 專案:工廠值班工作管理系統

## 專案簡介

一個給工廠值班團隊使用的工作管理系統:
- 建立 **Project**(專案),設定負責人與時程
- 在 Project 下建立 **Task** 或 **ActionRequest**(動作需求)
- Task 有**多種型態**(可擴展),用 polymorphic 設計
- Task 可指派給**多人**,但**必須有單一負責人**(`ownerId`)
- Task 內容是多媒體文字內容,使用 markdown 格式儲存
- 部署:Web (React, 行動裝置友善) + 未來 Native App
- 後端:Kotlin + Quarkus + MongoDB

**目前狀態**:M1-M4 已完成(2026-05-04);**M5(Hardening + Spec Lock-in)計畫已就位**(2026-05-07,`docs/release/m5-plan.md`),待使用者 review 後啟動。後續主題見 `STATUS.md`、`CHANGELOG.md`。

## 開發流程(里程碑模式)

本專案採用**里程碑式自動開發** — agent 完成一個里程碑後**停下來**讓使用者驗收,確認後再啟動下一棒。

### 完成里程碑(M1-M4)

| # | 里程碑 | 主負責 agent | 完成日 | 詳細紀錄 |
|---|---|---|---|---|
| 1 | 規格 + 領域設計 | spec-architect | 2026-05-04 | `docs/spec/STATUS.md`、CHANGELOG `[1.0.0-M1]` |
| 2 | 資料模型 | mongodb-modeler | 2026-05-04 | `docs/data/STATUS.md`、CHANGELOG `[1.0.0-M2]` |
| 3 | 後端 + 前端骨架 | quarkus-backend-builder → react-frontend-builder | 2026-05-04 | `docs/backend/STATUS.md`、`docs/frontend/STATUS.md`、CHANGELOG `[1.0.0-M3]` |
| 4 | 測試 + 審查 + 文件 + 部署 | test-engineer → code-reviewer → doc-devops | 2026-05-04 | `docs/test/STATUS.md`、`docs/review/STATUS.md`、`docs/devops/STATUS.md`、CHANGELOG `[1.0.0-M4]` |

### 里程碑 5:Hardening + Spec Lock-in(計畫中,2026-05-07)

**主題**:收 P1 Backlog 安全 / 一致性債 + 拍板衍生 Open Questions Q-18 ~ Q-24
**完整計畫書**:`docs/release/m5-plan.md`(主 agent 與所有 sub-agent **必讀**;啟動指令、scope freeze、DOD、回退策略全在裡面)
**Sub-phase**(序列,6 棒;data 層變動集中於 M5.2 由 mongodb-modeler 一棒收完 — 因 quarkus-backend-builder 嚴禁改 `domain/` 與 `docs/data/`):

| 階段 | 主題 | 負責 agent | 條目數 |
|---|---|---|---|
| M5.1 | Spec v1.4 lock-in(Q-18 ~ Q-24 拍板) | spec-architect | 7 |
| M5.2 | Data model preparation(User lockout 欄位 + OutboxDeadLetter document) | mongodb-modeler | — |
| M5.3 | Backend Security Hardening(S-009 ~ S-017) | quarkus-backend-builder → test-engineer | 7 |
| M5.4 | Domain Invariants + Q-23 OR(C-005 ~ C-015 + 雙簽語意切換) | quarkus-backend-builder → test-engineer | 11 |
| M5.5 | Frontend Hardening(配合 S-009 cookie 改造) | react-frontend-builder → test-engineer | 1+ |
| M5.6 | Compact + Release(複審 / tag v1.0.0-M5) | code-reviewer → doc-devops | — |

**驗收重點**:見 `docs/release/m5-plan.md` §4 Definition of Done。

### M6+:候選主題(暫存,待 M5 驗收後再選)

- Notification / Webhook 實作(spec 預留 Notification context)
- Daily Work Board UI(Q-8 拍板的四區塊儀表板)
- P-002 增量同步(`?since=` / ETag / If-Modified-Since)
- 行動裝置 native app skeleton

格式骨架(待使用者選定主題後填入):

```
### 里程碑 N:<主題>
**負責 agent**:<agent-name>
**接手起點**:<sub-STATUS.md path>
**產出**:
- ...
**驗收重點**:
- ...
```

啟動新里程碑前,**主 agent 必須先讀** `docs/release/impact-matrix.md` 與本檔通用規則,並確認 `STATUS.md` Backlog / Open Questions 沒有阻擋此主題的項目。

## Agent 啟動方式

### M5+ 通用範本(單一 agent)

```
> 啟動里程碑 M<N>(<主題名>)。
> 負責 agent:<agent-name>。
> 接手起點:<sub-STATUS.md path>。
> 產出:<列出檔案 / 文件清單>。
> 完成後依「Agent 協作協定」交付 handoff 四件套,並停下等驗收。
```

### 連續多 agent 啟動(序列)

```
> 啟動里程碑 M<N>:
> 1. <agent-1>:<目標>
> 2. <agent-2>:<目標>(依賴 1 完成)
> 3. <agent-3>:<目標>(依賴 2 完成)
> 各 agent 完成後停下回報,確認再啟動下一棒。
```

### 並行啟動(僅當讀寫邊界互不交集)

```
> 並行啟動:
> - <agent-A>:<目標 A>(改 path-A/)
> - <agent-B>:<目標 B>(改 path-B/)
> 兩者完成後一起回報,我會合併驗收。
```

## 通用規則(所有 agent 必守)

### 語言
- **文件用繁體中文**書寫
- **程式碼識別字、註解、commit message 用英文**
- 變數名稱要有意義,不用縮寫

### 程式碼品質
- 不留 `TODO`、`FIXME`、註解掉的程式碼
- 不寫 `println` debug,用 logger
- 不 hardcode secret、URL、port — 用 config / env

### 安全
- 所有 API 端點預設要認證(明確 public 才開放)
- 輸入驗證每個 DTO 都要做
- 敏感資料不進 log

### 文件
- 每個 agent 完成階段依 § STATUS.md Compact 原則 與 § Agent 協作協定 留訊息
- 重要決策依 § ADR 門檻 判斷是否要寫 ADR

### 自主程度設定
- **完成大功能才回報**(level 2)
- 階段內可自主跑,完成後**必須停下來**等使用者驗收
- 卡住的時候依 § Agent 協作協定 § Stuck Protocol 回報,**不假設、不繞過**

---

### Spec / ADR 變更原則(混合制)

**ADR(Architecture Decision Record)**

- ADR 一旦 `Status: Accepted` 之後**內容 immutable**(歷史可追)
- **Decision 仍成立但需補細節 / 補語境** → 在原 ADR 末尾加 `## vX.Y Amendment` 區段;不刪原文
- **Decision 被推翻** → **新建 ADR-NNNN**,將舊 ADR 標記 `Status: Superseded by ADR-NNNN`,新 ADR 開頭引用舊 ADR 編號
- ADR Status Lifecycle:`Proposed`(可改)→ `Accepted`(immutable)→ `Deprecated` / `Superseded by ADR-NNNN`

**Spec(`requirements.md` / `domain-model.md` / `openapi.yaml`)**

- 視為 **living document**,**in-place** 修改
- 每次修改 bump header 的 `version: X.Y.Z`(SemVer:大主題 → major、新增條目 → minor、字句調整 → patch)
- 完整歷史靠 `git tag spec-vX.Y` + git history,**不要在文件內保留歷史片段**

**Schema(`docs/data/schema.md`、`docs/data/indexes.md`)**

- `schema.md` / `indexes.md` 永遠反映**目前線上**狀態
- 每次變更**必新增** `docs/data/migrations/NNNN-描述.md`,內容包含:
  - 變更摘要
  - `schemaVersion` bump 對照
  - 既有資料回填策略(必填欄位預設值、enum 對應)
  - rollback 路徑
- Document 內 `schemaVersion: int` 對應 bump

---

### STATUS.md Compact 原則

**主 `STATUS.md` 是儀表板,不是日誌**。內容只應包含:

1. 里程碑進度表(指向 sub-STATUS / CHANGELOG 看細節)
2. **未解決** Backlog(P1 / P2)
3. **跨 domain** 阻擋下一棒的 Open Questions(其餘留 sub-STATUS)
4. 下一步啟動指令(M5+ placeholder)

**寫進 sub-STATUS.md(`docs/<area>/STATUS.md`)**:

- 域特定的歷史敘事
- agent 對下一棒的銜接訊息(handoff 四件套的前三項)

**必須移除**(避免 STATUS.md 膨脹):

- 已驗收的里程碑摘要 → 進 `CHANGELOG.md`
- 已驗收的驗收檢核清單 → 立即刪除
- 已被下一棒讀過的 handoff message → 立即刪除
- 已答覆 / 已 defer 的 Q-NN → 從 Open Questions 移除(必要時搬到對應 ADR)

**Compact 時機**:**每完成一個里程碑強制 compact**(由主 agent 在驗收通過後立即執行)。

---

### Release Discipline(中級 QA 規範)

每次變更跑下列三道關卡:

**1. 變更前:對照 Impact Matrix**

開工前查 `docs/release/impact-matrix.md`,找出本次變更命中的 `CT-N` 類型,**逐欄勾完所有「必動清單」**。漏件 = 文件 / 程式碼漂移。

**2. 變更後:跑一鍵驗證**

```
scripts/verify.sh           # 快速:test + typecheck + lint
scripts/verify.sh --full    # 完整:加 integration test + build
```

任一步 fail → 修到綠才能往下,**不繞過**。

**3. 出版本前:勾 Release Checklist**

複製 `docs/release/checklist.md` 到 PR description,逐條勾完才能合併 / tag / 發 release。

**Definition of Done**(任一變更 / 里程碑完成的判準):

- [ ] `scripts/verify.sh` 全綠(若涉 integration → `--full` 全綠)
- [ ] Impact Matrix 對應 `CT-N` 必動清單**全部勾完**
- [ ] 文件四同步:spec / openapi / schema / CHANGELOG
- [ ] Release Checklist 全綠(若是出版本)

**註**:本層級**不依賴 CI 自動把關**,純靠紀律 + 一鍵腳本。CI 仍會跑(`.github/workflows/ci.yml`),但**信任源頭是本地驗證**。

---

### Agent 協作協定

#### Handoff 四件套(每個 agent 完成階段必交付)

| 交付物 | 寫到哪 |
|---|---|
| 產出檔案清單(file path + 一句說明) | 對應 sub-STATUS.md |
| 未解決問題 / 已知妥協 | 對應 sub-STATUS.md |
| 給下一棒的 starter context(必讀檔案、注意事項) | 對應 sub-STATUS.md |
| 給使用者的驗收 checklist | 主 STATUS.md(勾完即移除) |

下一棒**只讀對應 sub-STATUS.md** 的「starter context」段,不會被無關訊息污染 context。

#### 各 Agent 讀寫邊界

| Agent | 可寫 | 嚴禁直接改(發現問題只能回報) |
|---|---|---|
| spec-architect | `docs/spec/`、`docs/adr/`、`CHANGELOG.md` | `backend/`、`frontend/`、`docs/data/` |
| mongodb-modeler | `docs/data/`、`backend/src/main/kotlin/**/domain/`、`backend/src/main/resources/db/` | `docs/spec/`、application/ 與 interfaces/ 層 |
| quarkus-backend-builder | `backend/`(除 domain/ 外) | `docs/spec/`(spec 漏 → 回報停下)、`docs/data/` |
| react-frontend-builder | `frontend/` | `backend/`、`docs/spec/` |
| test-engineer | `backend/src/test/`、`frontend/src/**/__tests__/`、`e2e/`、`docs/test/` | production code(發現 bug → 回報) |
| code-reviewer | **唯讀**,只寫 `docs/review/` | 任何 production code |
| doc-devops | `docs/`(除 spec/、data/、backend/、frontend/、test/、review/ 各 STATUS 外)、`.github/`、`docker/`、`Dockerfile`、`docker-compose.*`、`scripts/`、`docs/release/` | backend/、frontend/ source code |

每個 agent 對自己的 domain 是**權威**;跨 domain 必須**回報、不自改**。

#### Stuck Protocol(sub-agent 卡住怎辦)

1. 在自己 sub-STATUS.md 開 `⚠️ Blocked` 段,列問題 + context
2. **立刻終止本次任務**(不嘗試 workaround、不自行跨域修改)
3. 主 agent 讀到 Blocked → 跟使用者對話釐清
4. 使用者答覆後,主 agent 決定:
   - 答案在 sub-agent 範圍內可解 → 帶答案重啟同一個 sub-agent
   - 需修上游 domain → 啟動上游 agent 修,再回頭重啟原 agent
   - 是設計層級問題 → 開 ADR 或新 Q-NN 等使用者拍板

#### 主 agent vs sub-agent 分工

主 agent(Claude Code 主對話):
- ✅ 調度(決定啟動哪個 sub-agent、傳什麼 prompt、讀哪份 sub-STATUS)
- ✅ 判斷完成(對照驗收 checklist)
- ✅ 跟使用者對話、回報摘要
- ✅ 寫 `CLAUDE.md`、主 `STATUS.md`
- ❌ **不寫 production code**(避免 context 污染、避免與 sub-agent 衝突)

#### 並行 vs 序列

| 情境 | 規則 |
|---|---|
| spec → schema → backend → frontend pipeline | **必序列**(下游依賴上游) |
| backend test ‖ frontend test | **可並行**(讀寫邊界互不交集) |
| test-engineer ‖ code-reviewer | code-reviewer baseline 不含新測試 → **排在 test 之後**(或註明審查範圍) |
| doc-devops vs test/review | **後置**(產出依賴測試結果與審查報告) |

並行用主 agent 的 `Agent` tool 一次發多個 call;**只在所有讀寫邊界互不交集時才並行**。

---

### ADR 門檻(何時該寫)

**核心定義**:ADR = 架構決策快照。**不是**教學文件、規範手冊、決策日誌或 TODO。

#### 5-Lens 判斷準則

| Lens | 該寫 ADR | 不用 ADR |
|---|---|---|
| **影響範圍** | 跨 ≥ 2 個 bounded context / aggregate / module | 單一 class / 單一 method |
| **反轉成本** | 需 schema migration / 影響 external client | 純內部 refactor 可改 |
| **替代方案** | 有 ≥ 2 個 plausible 選項 | 唯一路徑 |
| **跨時間溝通** | 半年後新人會問「為何這樣選」 | 看 code 就知道 |
| **推翻引爆** | 推翻會引爆 prod / data / client | 純內部影響 |

**2 道以上「該寫」就寫**。

#### 白名單(這類**幾乎都該** ADR)

預設都寫:
- 資料模型(embed vs reference、aggregate boundary、schemaVersion 策略)
- 多租戶 / 安全模型(rootOrgId 隔離、認證、RBAC 粒度)
- 第三方整合(HR、NATS、S3、APNs/FCM 等)
- API 契約(REST/GraphQL、event schema、版本策略)
- 效能權衡(cache、index、async)
- 可擴展性架構(polymorphism、tree 結構)
- 時間 / 一致性(UTC vs local、事務邊界、最終一致性)
- 部署架構(JWT key rotation、deployment topology)

#### 黑名單(**不該** 寫成 ADR)

| 不該寫 | 該放哪 |
|---|---|
| 單一函式重構 | PR description / commit message |
| 特定 bug fix | issue / commit message |
| 命名、風格、格式規約 | `CONTRIBUTING.md` / style guide |
| 工具操作說明 | `README.md` / `docs/operations.md` |
| 系統怎麼運作的教學 | `docs/architecture.md` |
| 暫時 workaround / TODO | issue tracker |
| 「實作要照 spec 寫」執行細節 | 實作 PR |
| 第三方 lib 純偏好 | `README.md` 技術棧 |

#### Decision Tree

```
要寫 ADR?

1. 屬於白名單?
   ↓ 是 → 寫    ↓ 否 → 進 2

2. 5-Lens 至少 2 道「該寫」?
   ↓ 是 → 寫    ↓ 否 → 進 3

3. 屬於黑名單?
   ↓ 是 → 改放對應位置(CONTRIBUTING / issue / PR / README)
   ↓ 否 → 預設不寫,改 spec amendment 或留 PR description
```

#### ADR 必含五要素

- **Status**(Proposed / Accepted / Deprecated / Superseded by ADR-NNNN)
- **Context**(背景、為何要做這個決策)
- **Decision**(決策本身,**一句話可以講完最好**)
- **Alternatives Considered**(替代方案 + 為何不選)
- **Consequences**(後果:正面、負面、風險、後續工作)

寫之前自問:**一句話講得出 Decision?Alternatives 至少 1 個?**講不出 = 還沒決策清楚,先想再寫。

---

## 目錄結構

```
factory-ops/
├── CLAUDE.md                    # 你正在讀的這個檔案
├── README.md
├── CHANGELOG.md
├── STATUS.md                    # 主儀表板(依 Compact 原則維持精簡)
├── docker-compose.yml
├── docker-compose.dev.yml
├── docker-compose.prod.yml
├── .env.example
├── .github/workflows/
├── .claude/
│   └── agents/                  # 7 個 agent 定義
├── docs/
│   ├── spec/                    # requirements / domain-model / openapi + sub-STATUS
│   ├── data/                    # schema + indexes + sub-STATUS + migrations/
│   ├── adr/                     # 架構決策
│   ├── release/                 # impact-matrix.md + checklist.md
│   ├── architecture.md
│   ├── api/
│   ├── backend/                 # backend sub-STATUS
│   ├── frontend/                # frontend sub-STATUS
│   ├── test/                    # test sub-STATUS
│   ├── review/                  # code review reports + sub-STATUS
│   ├── devops/                  # devops sub-STATUS
│   ├── deployment.md
│   ├── operations.md
│   └── user-manual/
├── scripts/
│   └── verify.sh                # 一鍵本地驗證(Definition of Done)
├── e2e/                         # Cucumber + Playwright BDD
├── docker/                      # init scripts(mongo-rs / minio)
├── backend/                     # Kotlin + Quarkus
│   ├── build.gradle.kts
│   └── src/
└── frontend/                    # React + TypeScript
    ├── package.json
    └── src/
```

# 專案:工廠值班工作管理系統

## 專案簡介

一個給工廠值班團隊使用的工作管理系統:
- 建立 **Project**(專案),設定負責人與時程
- 在 Project 下建立 **Task** 或 **ActionRequest**(動作需求)
- Task 有**多種型態**(可擴展),用 polymorphic 設計
- Task 可指派給**多人**,但**必須有單一負責人**(`ownerId`)
- Task 內容是多媒體文字內容，使用md格式儲存，並可以使用貼圖
- 部署:Web (React, 行動裝置友善) + 未來 Native App
- 後端:Kotlin + Quarkus + MongoDB

## 開發流程(里程碑模式)

本專案採用**里程碑式自動開發** — agent 完成一個里程碑後**停下來**讓使用者驗收,確認後再啟動下一棒。

### 里程碑 1:規格 + 領域設計
**負責 agent**:`spec-architect`
**產出**:
- `docs/spec/requirements.md`
- `docs/spec/domain-model.md`(含 Mermaid 圖)
- `docs/spec/openapi.yaml`
- `docs/adr/*.md`(架構決策)

**驗收重點**:user stories 是否完整、domain model 是否合理、API 設計是否符合預期

---

### 里程碑 2:資料模型
**負責 agent**:`mongodb-modeler`
**產出**:
- `docs/data/schema.md`
- `docs/data/indexes.md`
- `backend/src/main/kotlin/<group>/domain/`(Kotlin data class)
- `backend/src/main/resources/db/init-indexes.js`

**驗收重點**:embed/reference 取捨是否合理、索引是否覆蓋查詢、schemaVersion 預留

---

### 里程碑 3:後端 + 前端骨架
**負責 agent**:`quarkus-backend-builder` → `react-frontend-builder`
**產出**:
- 後端可啟動,Swagger UI 顯示完整 API
- 前端可登入、瀏覽 Project / Task、建立與指派
- E2E 流程通(建 project → 建 task → 指派 → 變更狀態)

**驗收重點**:`docker compose up` 能起完整環境,主要流程能跑通

---

### 里程碑 4:測試 + 審查 + 文件 + 部署
**負責 agent**:`test-engineer` → `code-reviewer` → `doc-devops`
**產出**:
- 測試覆蓋率報告
- 審查報告
- 完整 README、API 文件、CI/CD pipeline

**驗收重點**:CI 全綠、文件齊全、可上線

## Agent 啟動方式

### 啟動里程碑 1
```
> 請用 spec-architect 開始進行規格分析與領域建模。
> 業務描述見 README,完成後停下來等我審查。
```

### 啟動里程碑 2
```
> spec 已驗收,請用 mongodb-modeler 進行資料建模。
```

### 啟動里程碑 3
```
> Schema 已驗收,請用 quarkus-backend-builder 實作後端。
> 後端完成後,接著用 react-frontend-builder 實作前端。
```

### 啟動里程碑 4
```
> 後端與前端已完成,請依序執行:
> 1. test-engineer 補強測試
> 2. code-reviewer 全面審查
> 3. doc-devops 完成文件與 CI/CD
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
- 每個 agent 完成階段在對應 `STATUS.md` 留訊息
- 重要決策寫 ADR(Architecture Decision Record)

### 自主程度設定
- **完成大功能才回報**(level 2)
- 階段內可自主跑,完成後**必須停下來**等使用者驗收
- 卡住的時候列出問題清單,不要假設

## 目錄結構(完成後)

```
factory-ops/
├── CLAUDE.md                    # 你正在讀的這個檔案
├── README.md
├── CHANGELOG.md
├── docker-compose.yml
├── .env.example
├── .github/workflows/
├── .claude/
│   └── agents/                  # 7 個 agent 定義
├── docs/
│   ├── spec/
│   ├── data/
│   ├── adr/
│   ├── architecture.md
│   ├── api/
│   ├── backend/
│   ├── frontend/
│   ├── test/
│   └── review/
├── backend/                     # Kotlin + Quarkus
│   ├── build.gradle.kts
│   └── src/
└── frontend/                    # React + TypeScript
    ├── package.json
    └── src/
```

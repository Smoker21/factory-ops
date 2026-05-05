# Factory Ops — 工廠值班工作管理系統

[![CI](https://github.com/smoker21/factory-ops/actions/workflows/ci.yml/badge.svg)](https://github.com/smoker21/factory-ops/actions/workflows/ci.yml)

服務工廠值班團隊的工作管理系統:**追蹤跨班別 Project / Task / 異常處置 / 交班事項**,把現場「口頭交辦」「便利貼」「Excel 工單」改成可稽核的數位工作流。

採 **API-first** 設計、**多租戶**、**多型 Task / Group / Organization**、**HR 整合**、**NATS + Webhook 事件**、**行動裝置友善**。

---

## 30 秒快速開始

```bash
git clone https://github.com/<your-org>/factory-ops.git && cd factory-ops
cp .env.example .env
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
# 等待 ~60 秒服務就緒後：
# 前端  → http://localhost:5173
# API   → http://localhost:8080/q/swagger-ui
# MinIO → http://localhost:9001
```

預設 dev 帳號：`admin.system` / `Admin@123456789`（orgCode: `fab-alpha`）

---

## 開發進度

| 里程碑 | 內容 | 狀態 |
|---|---|---|
| **M1** 規格與架構 | requirements v1.3.0 / domain-model / openapi 3.1 / 13 ADR | ✅ 已完成 |
| **M2** 資料模型 | 16 collections schema、40 Kotlin domain class、index script | ✅ 已完成 |
| **M3** 後端 + 前端骨架 | Quarkus REST(~85 端點)+ React/TS UI(13 頁面)+ MSW mock | ✅ 已完成 |
| **M4** 測試 + 審查 + CI/CD | 測試覆蓋、code review、docker compose、CI pipeline | ✅ 已完成 |

---

## 技術棧

| 層 | 技術 |
|---|---|
| 後端 | Kotlin 2.0、Quarkus 3.17、JVM 21、MongoDB 7、NATS JetStream、MinIO(S3 相容) |
| 前端 | React 18、TypeScript 5、Vite、Mantine 7、TanStack Query、React Router v6、i18next |
| 認證 | JWT(access + refresh,RSA)、Bcrypt 密碼雜湊 |
| 整合 | HR Mock REST(可替換為實際 HR 服務) |
| 文件 | OpenAPI 3.1(Swagger UI)、Mermaid、ADR |

---

## 系統概觀

### 領域模型重點

- **Organization** — 多型樹狀組織(`FAB → DIVISION → DEPARTMENT → SECTION`,只有 leaf SECTION 承載工作)
- **Group** — 平面工作群組,屬於唯一 leaf Organization,可多型(`DEFAULT / LINE / TEAM / SHIFT`)
- **Project** — 工作集合,屬於唯一 leaf Org,可掛多 Group
- **Task** — 多型(`EQUIPMENT_INSPECTION / INCIDENT_RESPONSE / SHIFT_HANDOVER / …`),可指派多人但**單一 owner**
- **ActionRequest** — 動作需求,支援**單跳跨層派工**(上級 Org 直接派至 leaf)
- **Template** — Project / Task 範本,GLOBAL(Admin)+ ORG(Org 內)雙 scope,版本化、實例化即 clone

### 9 個角色

`OPERATOR / SHIFT_LEAD / ENGINEER / QA / GROUP_ADMIN / GROUP_MANAGER / ORG_MANAGER / ORG_ADMIN / ADMIN`

詳見 [`docs/spec/requirements.md` §6 RBAC](docs/spec/requirements.md)。

### 多租戶

所有資料以 `rootOrgId` 隔離,索引第一欄一律 `rootOrgId`(ADR-0005)。一位 User 屬於唯一 root Org(MVP 限制)。

---

## 快速開始

### Docker Compose（推薦，5 分鐘啟動完整環境）

先備：Docker Engine 24.0+（4GB 可用記憶體）

```bash
cp .env.example .env
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

服務就緒後（約 60 秒）：
- 前端：http://localhost:5173
- Swagger UI：http://localhost:8080/q/swagger-ui
- MinIO Console：http://localhost:9001

預設 dev 帳號：`admin.system` / `Admin@123456789`（orgCode: `fab-alpha`）

### 切換 HR 後端

```bash
HR_MODE=mock      # 預設，5 筆寫死員工
HR_MODE=h2        # 從 test_data/hr_employees.csv 載入 H2 in-memory DB（demo / QA 推薦）
HR_MODE=external  # 真實 HR REST API（待實作）
```

CSV 格式與切換細節見 [test_data/README.md](test_data/README.md) 與 [ADR-0014](docs/adr/0014-hr-backend-feature-toggle.md)。

詳細部署說明（production checklist / JWT key rotation / 備份）：[docs/deployment.md](docs/deployment.md)

---

## 本機開發（不用 Docker）

### 先備

- JDK 21（本案以 Microsoft OpenJDK 21 為準）
- Node.js 20+
- MongoDB 7（本機啟動，需要 replica set）
- MinIO（本機啟動）
- NATS（可選，dev profile 預設停用）

### 後端

```bash
cd backend
# Windows PowerShell:
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat quarkusDev

# Bash:
export JAVA_HOME='/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot'
./gradlew quarkusDev
```

啟動後：
- API：http://localhost:8080
- Swagger UI：http://localhost:8080/q/swagger-ui
- Health：http://localhost:8080/q/health

`dev` profile 會自動 seed 一份 4 層 Org 樹 + 5 位假員工 + 1 個 Project + 數個 Task，便於 UI 立刻可用。

### 前端

```bash
cd frontend
npm install
npm run dev
```

預設開 http://localhost:5173。若後端尚未啟動，前端會 fallback 到 MSW mock（可離線跑 E2E）。

### 環境變數

複製 `frontend/.env.local.example` 為 `frontend/.env.local`，設定 `VITE_API_BASE_URL`。

後端用 env / `application-dev.properties`，主要 keys：

```properties
quarkus.mongodb.connection-string=mongodb://localhost:27017
quarkus.mongodb.database=factory_ops
minio.endpoint=http://localhost:9000
nats.url=nats://localhost:4222
hr.mode=mock
```

### 索引初始化

```bash
mongosh factory_ops backend/src/main/resources/db/init-indexes.js
```

---

## 專案結構

```
factory-ops/
├── README.md                       # 你正在讀的這個
├── CHANGELOG.md                    # 版本紀錄（Keep a Changelog）
├── CONTRIBUTING.md                 # 貢獻指南
├── AGENTS_PACK.md                  # Agent 團隊安裝包說明
├── CLAUDE.md                       # 給 Claude Code 的協調設定
├── STATUS.md                       # 全專案里程碑狀態
├── .env.example                    # 環境變數範例（複製為 .env）
├── docker-compose.yml              # 共用服務圖（mongo / nats / minio / backend / frontend）
├── docker-compose.dev.yml          # Dev 覆寫（seed data / Swagger / dev JWT keys）
├── docker-compose.prod.yml         # Prod 覆寫（resource limits / restart:always）
├── docker/                         # Docker 初始化腳本
│   ├── mongo-rs-init.js            # MongoDB replica set 初始化（冪等）
│   └── minio-init.sh               # MinIO bucket 建立（冪等）
├── .github/workflows/
│   ├── ci.yml                      # PR + push to main CI pipeline
│   ├── release.yml                 # tag v* → GHCR push + GitHub Release
│   └── codeql.yml                  # 每週 CodeQL 靜態分析
├── docs/
│   ├── architecture.md             # 系統架構圖 + 模組職責 + 資料流
│   ├── deployment.md               # 部署指引 + JWT rotation + 備份
│   ├── operations.md               # 維運手冊（health / log / 排查）
│   ├── spec/                       # 需求 / 領域模型 / OpenAPI
│   ├── data/                       # MongoDB schema / indexes
│   ├── adr/                        # 13 份架構決策紀錄
│   ├── api/
│   │   ├── index.html              # Redoc 靜態 API 文件（自動生成）
│   │   └── README.md               # API 文件入口 + 認證 + 錯誤代碼
│   ├── devops/                     # DevOps agent STATUS
│   ├── test/                       # 測試覆蓋率報告
│   └── review/                     # Code review 報告
├── backend/                        # Kotlin + Quarkus + MongoDB
│   ├── Dockerfile
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/factoryops/
│       ├── domain/                 # 純資料 Domain class（無框架依賴）
│       ├── persistence/            # Document + Repository + Mapper
│       ├── application/            # Service + Auth + Policy
│       ├── interfaces/             # REST + DTO + Exception + Filter
│       └── infrastructure/         # NATS / Webhook / Outbox / MinIO / HR
└── frontend/                       # React + TypeScript + Vite
    ├── Dockerfile
    ├── nginx.conf
    └── src/
        ├── routes/                 # 13 頁面
        ├── components/             # 17 元件
        ├── api/                    # 11 個 API client
        ├── hooks/, store/, theme/, i18n/, mocks/, rbac/
        └── auth/
```

---

## 重要文件入口

- 系統架構圖與模組說明：[docs/architecture.md](docs/architecture.md)
- 部署指引（Docker / JWT / 備份）：[docs/deployment.md](docs/deployment.md)
- 維運手冊（Health check / 排查 / Metric）：[docs/operations.md](docs/operations.md)
- API 文件（靜態 HTML）：[docs/api/index.html](docs/api/index.html)
- API 文件入口：[docs/api/README.md](docs/api/README.md)
- 系統概述與需求：[docs/spec/requirements.md](docs/spec/requirements.md)
- 領域模型（含 Mermaid 類圖）：[docs/spec/domain-model.md](docs/spec/domain-model.md)
- API 合約（OpenAPI 3.1）：[docs/spec/openapi.yaml](docs/spec/openapi.yaml)
- 資料模型（16 collections）：[docs/data/schema.md](docs/data/schema.md)
- 索引設計：[docs/data/indexes.md](docs/data/indexes.md)
- 架構決策（ADR）：[docs/adr/](docs/adr/)
- 全域進度：[STATUS.md](STATUS.md)
- 版本紀錄：[CHANGELOG.md](CHANGELOG.md)
- 貢獻指南：[CONTRIBUTING.md](CONTRIBUTING.md)

### 關鍵 ADR

| ID | 主題 |
|---|---|
| ADR-0001 | Polymorphic Task design |
| ADR-0002 | Multi-assignee + single owner |
| ADR-0003 | Attachment & markdown storage(MinIO + presigned URL) |
| ADR-0004 | Organization 樹 + 平面 Group |
| ADR-0005 | Multi-tenancy(rootOrgId 隔離) |
| ADR-0006 | Template versioning + GLOBAL/ORG scope |
| ADR-0007 | User-HR integration |
| ADR-0008 | Single-hop cross-org dispatch |
| ADR-0009 | NATS + Webhook event distribution |
| ADR-0010 | Single org manager + multi leaders |
| ADR-0011 | Group settings QA dual-sign |
| ADR-0012 | Organization tree materialized path |
| ADR-0013 | Collection naming + ID strategy |

---

## 開發規約

- **文件**:繁體中文
- **程式碼識別字、commit、註解**:英文
- **時間**:全用 UTC ISO 8601;UI 依 root Org timezone 顯示
- **多租戶**:每個 query 必有 `rootOrgId` 條件,索引第一欄一律 `rootOrgId`
- **敏感資料**:`passwordHash` / `webhook.secret` / JWT 私鑰**永不入 log**
- **不留** `TODO` / `FIXME` / 註解掉的程式碼;debug 用 logger 不用 `println` / `console.log`
- 詳細規則見 [`CLAUDE.md`](CLAUDE.md)

---

## 安全須知

- `backend/src/main/resources/jwt/*.pem` 為**僅供本機開發**的 RSA 金鑰對(已隨 repo 公開)。**部署 production 前必須輪換**,詳見 [backend/src/main/resources/jwt/README.md](backend/src/main/resources/jwt/README.md)。
- `frontend/.env.local` 不入版控(已 gitignore)。
- 預設 dev 帳號由 `DevDataSeeder` 寫入 — production 啟動前先停用 seeder。

---

## 自動化開發流程(Agent Team)

本專案以 **Claude Code subagent 團隊**自動化開發,採里程碑式驗收。詳見 [AGENTS_PACK.md](AGENTS_PACK.md) 與 [CLAUDE.md](CLAUDE.md)。

7 個 subagent:`spec-architect / mongodb-modeler / quarkus-backend-builder / react-frontend-builder / test-engineer / code-reviewer / doc-devops`,各自專責一個階段。

---

## 常用指令

### 後端

```bash
cd backend
./gradlew test                      # 執行測試（需要 Docker for DevServices）
./gradlew test jacocoTestReport      # 測試 + 產生覆蓋率報告
./gradlew build -x test              # 編譯（跳過測試）
./gradlew quarkusDev                 # 開發模式（hot reload）
```

### 前端

```bash
cd frontend
npm test                 # 執行 unit tests
npm run test:coverage    # tests + coverage report
npm run typecheck        # TypeScript 型別檢查
npm run lint             # ESLint 靜態分析
npm run build            # 生產 build
```

### Docker

```bash
# Dev 啟動（含 seed data + Swagger）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# 查看 logs
docker compose logs -f backend

# 重啟單一服務
docker compose restart backend

# 完整清除（含 volumes）
docker compose down -v
```

---

## License

TBD（待使用者確認 MIT 或 Apache 2.0）

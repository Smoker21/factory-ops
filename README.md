# Factory Ops — 工廠值班工作管理系統

服務工廠值班團隊的工作管理系統:**追蹤跨班別 Project / Task / 異常處置 / 交班事項**,把現場「口頭交辦」「便利貼」「Excel 工單」改成可稽核的數位工作流。

採 **API-first** 設計、**多租戶**、**多型 Task / Group / Organization**、**HR 整合**、**NATS + Webhook 事件**、**行動裝置友善**。

---

## 開發進度

| 里程碑 | 內容 | 狀態 |
|---|---|---|
| **M1** 規格與架構 | requirements v1.3.0 / domain-model / openapi 3.1 / 13 ADR | ✅ 已完成 |
| **M2** 資料模型 | 16 collections schema、40 Kotlin domain class、index script | ✅ 已完成 |
| **M3** 後端 + 前端骨架 | Quarkus REST(~85 端點)+ React/TS UI(13 頁面)+ MSW mock | ✅ 已完成(編譯/測試通過,runtime 尚未驗證) |
| **M4** 測試 + 審查 + CI/CD | 測試覆蓋、code review、docker compose、CI pipeline | ⏳ 規劃中 |

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

## 快速開始(本機開發)

### 先備

- JDK 21(本案以 Microsoft OpenJDK 21 為準)
- Node.js 20+
- Docker(待 M4 提供 docker compose)
- 暫時手動跑 MongoDB 7 / MinIO / NATS(M4 會打包)

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

啟動後:
- API:http://localhost:8080
- Swagger UI:http://localhost:8080/q/swagger-ui
- Health:http://localhost:8080/q/health

`dev` profile 會自動 seed 一份 4 層 Org 樹 + 5 位假員工 + 1 個 Project + 數個 Task,便於 UI 立刻可用。

### 前端

```bash
cd frontend
npm install
npm run dev
```

預設開 http://localhost:5173。若後端尚未啟動,前端會 fallback 到 MSW mock(可離線跑 E2E)。

### 環境變數

複製 `frontend/.env.local.example` 為 `frontend/.env.local`,設定 `VITE_API_BASE_URL`。

後端用 env / `application-dev.properties`,主要 keys:

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
├── AGENTS_PACK.md                  # Agent 團隊安裝包說明(自動化開發流程)
├── CLAUDE.md                       # 給 Claude Code 的協調設定
├── STATUS.md                       # 全專案里程碑狀態
├── .claude/agents/                 # 7 個 subagent 定義(spec / data / backend / frontend / test / review / docs)
├── docs/
│   ├── spec/                       # 需求 / 領域模型 / OpenAPI / spec STATUS
│   ├── data/                       # MongoDB schema / indexes / 範例文件
│   ├── adr/                        # 13 份架構決策紀錄
│   ├── backend/                    # 後端銜接訊息與啟動指引
│   └── frontend/                   # 前端銜接訊息
├── backend/                        # Kotlin + Quarkus + MongoDB
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/factoryops/
│       ├── domain/                 # 純資料 Domain class(無 BSON / Panache)
│       ├── persistence/            # Document + Repository + Mapper
│       ├── application/            # Service + Auth + Policy
│       ├── interfaces/             # REST + DTO + Exception + Filter
│       └── infrastructure/         # NATS / Webhook / Outbox / MinIO / HR
└── frontend/                       # React + TypeScript + Vite
    └── src/
        ├── routes/                 # 13 頁面
        ├── components/             # 17 元件
        ├── api/                    # 11 個 API client
        ├── hooks/, store/, theme/, i18n/, mocks/, rbac/
        └── auth/
```

---

## 重要文件入口

- 系統概述與需求:[docs/spec/requirements.md](docs/spec/requirements.md)
- 領域模型(含 Mermaid 類圖):[docs/spec/domain-model.md](docs/spec/domain-model.md)
- API 合約(OpenAPI 3.1):[docs/spec/openapi.yaml](docs/spec/openapi.yaml)
- 資料模型(16 collections):[docs/data/schema.md](docs/data/schema.md)
- 索引設計:[docs/data/indexes.md](docs/data/indexes.md)
- 架構決策(ADR):[docs/adr/](docs/adr/)
- 全域進度:[STATUS.md](STATUS.md)

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

## License

待定。

# 工廠值班工作管理系統 — Agent 團隊安裝包

這個 zip 包含一組為你客製的 **Claude Code subagent 團隊**,可從規格自動完成整個專案開發。

## 內容物

```
factory-ops-agents/
├── CLAUDE.md                       # 主協調檔案(專案說明 + 開發流程)
└── .claude/agents/
    ├── spec-architect.md           # 規格分析 + 領域建模 + API 設計
    ├── mongodb-modeler.md          # MongoDB schema + Kotlin entity
    ├── quarkus-backend-builder.md  # Kotlin + Quarkus 後端
    ├── react-frontend-builder.md   # React + TS 前端
    ├── test-engineer.md            # 測試
    ├── code-reviewer.md            # 唯讀審查
    └── doc-devops.md               # 文件 + Docker + CI/CD
```

## 快速開始(3 步驟)

### 1. 建立你的專案資料夾

```bash
mkdir factory-ops
cd factory-ops
git init
```

### 2. 把這個 zip 解壓進去

把 `CLAUDE.md` 和 `.claude/` 整個資料夾放到 `factory-ops/` 根目錄。最終結構:

```
factory-ops/
├── CLAUDE.md
└── .claude/
    └── agents/
        └── (7 個 .md 檔)
```

### 3. 啟動 Claude Code

```bash
cd factory-ops
claude
```

進入後輸入:

```
> 請用 spec-architect 開始進行規格分析與領域建模。
> 業務描述參照 CLAUDE.md,完成後停下來等我審查。
```

---

## 開發流程(4 個里程碑)

每個里程碑完成後 **agent 會停下來**,你檢視沒問題再啟動下一棒。

### 里程碑 1 — 規格與架構
```
> 請用 spec-architect 開始
```
**檢視重點**: `docs/spec/requirements.md`、`docs/spec/openapi.yaml`

### 里程碑 2 — 資料模型
```
> 規格已驗收,請用 mongodb-modeler 接手
```
**檢視重點**: `docs/data/schema.md` 的設計理由

### 里程碑 3 — 後端 + 前端
```
> Schema 已驗收,請用 quarkus-backend-builder 實作後端,
> 完成後接著用 react-frontend-builder 實作前端
```
**檢視重點**: `docker compose up` 能跑、主要流程能用

### 里程碑 4 — 測試 + 審查 + 文件
```
> 程式碼已驗收,請依序:
> 1. test-engineer 補強測試
> 2. code-reviewer 全面審查
> 3. doc-devops 完成文件與 CI/CD
```
**檢視重點**: 測試覆蓋率、審查報告

---

## 重要提示

### 修改 agent 設定
直接編輯 `.claude/agents/<name>.md` 即可,Claude Code 重啟後生效。

### 個人 vs 專案範圍
這組 agent 放在**專案範圍**(`.claude/agents/`),只在這個專案有效。
若要全域使用,複製到 `~/.claude/agents/` 即可。

### 看 agent 狀態
```
> /agents
```
會列出所有可用的 subagent。

### 自主程度調整
目前設定為「完成大功能才回報」(自主程度 2)。
- 想更謹慎 → 在每個 agent 的「完成標準」之前加更多 checkpoint
- 想更自主 → 把 CLAUDE.md 的「里程碑」說明改成可連續執行

### MongoDB 注意事項
這組 agent 假設用 **MongoDB 7+** 與 **Quarkus 3.x**。如果你的環境不同,記得到對應 agent 檔案的「技術棧」段落調整版本號。

---

## 客製化建議

每個 agent 都有預留客製化點,如:

- **多語系**: 在 `react-frontend-builder.md` 加入 i18n 套件
- **ISO 規範**: 在 `spec-architect.md` 加入工廠合規需求(ISO 9001、IATF 16949)
- **班別管理**: 在 `mongodb-modeler.md` 增加 Shift collection 設計
- **稽核軌跡**: 已有預留(每個 entity 都有 `history` embedded array)

---

## 疑難排解

**Q: agent 沒被自動觸發?**
A: 檢查該 agent 的 `description` 寫得夠不夠具體。Claude 是靠 description 判斷何時委派的。

**Q: agent 跑到一半停了?**
A: 用 `/agents` 確認沒被 disable,然後 `> 繼續` 即可。

**Q: 想換成 Spring Boot?**
A: 編輯 `quarkus-backend-builder.md` 的「技術棧」段落,把 Quarkus 換成 Spring Boot,工具鏈也對應換掉。其他 agent 不用動。

**Q: 想接其他資料庫?**
A: 編輯 `mongodb-modeler.md`,改成 PostgreSQL/Oracle 設計者。schema.md 的格式邏輯仍然適用。

---

祝開發順利!🛠️

# 貢獻指南

感謝你有興趣貢獻 Factory Ops 專案。請閱讀以下規範，以確保 PR 能順利合併。

---

## 分支策略（GitHub Flow）

```
main          — production-ready；保護分支，需 PR + review 才能合併
feature/xxx   — 功能開發
fix/xxx       — 問題修復
docs/xxx      — 文件更新
```

**工作流程**：

1. `git switch -c feature/your-feature-name`
2. 開發，commit（遵循 Conventional Commits，見下方）
3. `git push origin feature/your-feature-name`
4. 開 PR → main，等 CI 通過 + 至少 1 位 reviewer approve

---

## Commit Message 格式（Conventional Commits）

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Type**：

| type | 說明 |
|---|---|
| `feat` | 新功能 |
| `fix` | 問題修復 |
| `docs` | 文件修改 |
| `style` | 格式調整（不影響程式邏輯） |
| `refactor` | 重構（不新增功能、不修 bug） |
| `test` | 新增或修改測試 |
| `chore` | 建置工具、CI 設定、依賴更新 |
| `perf` | 效能改善 |
| `security` | 安全修補 |

**Scope**（可選）：`backend`、`frontend`、`docs`、`ci`、`docker`

**範例**：

```
feat(backend): add cursor pagination to listTasks endpoint

fix(frontend): prevent XSS in MarkdownRenderer attachment URL

security(backend): verify JWT refresh token signature with RSA public key

docs: update deployment.md with JWT key rotation steps
```

---

## PR Checklist

在提交 PR 之前，確認以下項目：

### 通用

- [ ] PR 標題遵循 Conventional Commits 格式
- [ ] 修改了相關文件（API 文件、README、CHANGELOG）
- [ ] 沒有留下 `TODO`、`FIXME`、console.log / println（CLAUDE.md 規定）
- [ ] 沒有 hardcode secret、URL、port（用 config / env）
- [ ] 敏感資料不會出現在 log

### 後端（Kotlin / Quarkus）

- [ ] 每個新 DTO 有 Bean Validation 注解
- [ ] 所有 Service method 有 `@Transactional`（多步驟操作）
- [ ] 新端點有 `@RolesAllowed`（沒有 `@PermitAll` 除非明確公開）
- [ ] Repository 查詢包含 `rootOrgId` 條件（多租戶隔離）
- [ ] 新增或修改了對應 unit test

### 前端（React / TypeScript）

- [ ] `npm run typecheck` 通過（無 TS error）
- [ ] `npm run lint` 通過（無 eslint warning）
- [ ] 有意義的元件有 RTL 測試
- [ ] 使用 `logger` 而非 `console.log`

### CI / Docker

- [ ] `docker compose build` 能成功建置
- [ ] `.env.example` 已更新（如有新環境變數）

---

## 開發環境設定

詳見 [README.md](README.md) 快速開始段落。

---

## 語言規範

- **文件、PR description、commit body**：繁體中文
- **程式碼識別字、commit subject、PR title、CI workflow 名稱**：英文
- 變數名稱要有意義，不用縮寫

---

## 回報 Issue

在開 Issue 前：

1. 搜尋現有 Issues 確認沒有重複
2. 安全漏洞請私下回報（email），不要開公開 Issue
3. Bug 報告請附上：重現步驟、預期行為、實際行為、環境資訊（OS、Docker 版本）

# 工廠值班工作管理系統 — 開發狀態

**最後更新**: 2026-05-09
**目前版本**: **v1.0.0-M5** / spec v1.5.0 / data-model v1.1.0
**目前里程碑**: ✅ M5 COMPLETED 2026-05-09

---

## 里程碑進度

| # | 里程碑 | 負責 agent | 狀態 | 詳細紀錄 |
|---|---|---|---|---|
| 1 | 規格 + 領域設計 | spec-architect | ✅ COMPLETED (2026-05-04) | `docs/spec/STATUS.md` |
| 2 | 資料模型 | mongodb-modeler | ✅ COMPLETED (2026-05-04) | `docs/data/STATUS.md` |
| 3 | 後端 + 前端骨架 | quarkus-backend-builder → react-frontend-builder | ✅ COMPLETED (2026-05-04) | `docs/backend/STATUS.md`、`docs/frontend/STATUS.md` |
| 4 | 測試 + 審查 + 文件 + 部署 | test-engineer → code-reviewer → doc-devops | ✅ COMPLETED (2026-05-04) | `docs/test/STATUS.md`、`docs/review/STATUS.md`、`docs/devops/STATUS.md`、CHANGELOG `[1.0.0-M4]` |
| 5 | Hardening + Spec Lock-in | spec-architect → mongodb-modeler → quarkus-backend-builder → react-frontend-builder → code-reviewer + doc-devops | ✅ COMPLETED (2026-05-09) | `docs/review/STATUS.md`、CHANGELOG `[1.0.0-M5]` |

各里程碑完成摘要見 `CHANGELOG.md`。

---

## P1 Backlog(M6+ 候選)

| 編號 | 說明 | 來源 |
|---|---|---|
| P1-1 | `LockoutStateWriter` 走 `UserRepository` 抽象（目前直呼叫 `persistOrUpdate()`） | M5.6 review |
| P1-2 | `OutboxPoller.pollAndProcess()` dead-letter 寫入 + 原 entry 標記非原子（最終一致性，冪等保護）— M6 補 `@Transactional` + ADR-0009 v1.5 Amendment | M5.6 review |
| P1-3 | CSRF dual-mode 強制截止點（ADR-0015 v1.6 Amendment + `security.csrf.strict-mode` toggle） | M5.6 review |
| P-002 | `?since=` / ETag / If-Modified-Since 增量同步 | M4 defer |

## P2 Backlog(後續迭代)

| 編號 | 說明 |
|---|---|
| P2-1 | ADR-0015 未文件化 401 vs 403 執行順序 |
| P2-2 | `TaskService.kt` Role.valueOf `mapNotNull` 路徑靜默丟棄 |
| P2-3 | CsrfFilter exempt path 純 prefix match（缺邊界檢查） |
| P2-4 | CSRF exempt-paths 邊界檢查（`path == it \|\| path.startsWith("$it/")`） |
| P2-5 | (已修) `%test.auth.cookie.secure=false` — M5.6 已補 |
| P2-6 | `OutboxPoller` retryCount > 10 邊界說明缺 doc 補充 |
| P2-7 | Swagger UI `%prod.quarkus.swagger-ui.always-include=false`（M6 加） |
| C-017 | transfer-manager 後 JWT orgManagerScopes 過期 |
| C-018 | addAssignees 後未重做 INV-1 owner 檢查 |
| P-004 ~ P-010 | 效能項（in-memory filter、bundle size 等） |
| S-014 / S-018 / S-019 / S-020 | 安全雜項 |
| M-001 / M-002 / M-004 ~ M-007 | 雜項清理 |

---

## M6+ 候選主題

- **Notification / Webhook 實作**（spec 預留 Notification context）
- **Daily Work Board UI**（Q-8 拍板的四區塊儀表板）
- **P-002 增量同步**（`?since=` / ETag / If-Modified-Since）
- **行動裝置 native app skeleton**
- **P1-1 ~ P1-3 架構債收整**（LockoutStateWriter / OutboxPoller / CSRF strict-mode）

啟動 M6 前參考 `docs/release/m5-plan.md` §M6+ 候選說明與 `CLAUDE.md` M6+ 區塊。

---

## 下一里程碑啟動指令 placeholder

```text
> 啟動里程碑 M6(<主題名>)。
> 負責 agent:<agent-name>。
> 接手起點:<sub-STATUS.md path>。
> 產出:<列出檔案 / 文件清單>。
> 完成後依「Agent 協作協定」交付 handoff 四件套，並停下等驗收。
```

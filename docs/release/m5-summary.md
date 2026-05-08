# M5 完成總結

**版本**: v1.0.0-M5
**完成日期**: 2026-05-09
**里程碑主題**: Hardening + Spec Lock-in

---

## 整體成果

### 條目完成率

| 分類 | 計畫條目 | 完成 |
|---|---|---|
| Spec lock-in（Q-18 ~ Q-24） | 7 | 7 ✅ |
| Security P1（S-009 ~ S-017） | 7 | 7 ✅ |
| Domain Invariants P1（C-005 ~ C-015 + Q-23 OR） | 10 + 1 = 11 | 11 ✅ |
| **合計** | **25** | **25** ✅ |

### 測試量級

| 指標 | M4 baseline | M5 完成 | 成長 |
|---|---|---|---|
| 後端 tests | 425 | **654** | +229（+54%） |
| 前端 tests | 66 | **102** | +36（+55%） |
| BDD scenarios | 18 | 20+ | +2 新情境 |

### 版本變化

| 文件 | M4 版本 | M5 版本 |
|---|---|---|
| `requirements.md` | v1.3.0 | **v1.5.0** |
| `openapi.yaml` | v1.3.0 | **v1.5.0** |
| `schema.md` / `indexes.md` | v1.0.0 | **v1.1.0** |
| ADR | ADR-0001 ~ 0014 | **ADR-0001 ~ 0015**（新建 0015） |

---

## 關鍵技術決策（本期新增）

| 決策 | 落地 |
|---|---|
| JWT Cookie + CSRF Model | ADR-0015（Accepted 2026-05-08）|
| Q-23 OR 白名單語意（非 AND） | ADR-0011 v1.4 Amendment；`TaskService.kt:424-425` |
| S-016 `@Transactional(REQUIRES_NEW)` 繞開 JTA rollback | `LockoutStateWriter.kt`（P1-1 留 M6 重整）|
| OutboxDeadLetter 選項 X（獨立 collection） | `docs/data/migrations/0002-outbox-dead-letter.md` |
| Frontend cookie-first + Bearer fallback（雙模兼容） | `client.ts` + `vite.config.ts` proxy |

---

## M6+ 候選主題（彙整自 P1/P2 backlog + m5-plan.md §M6+）

### 架構債優先（建議 M6.1 先收）

1. **P1-1** `LockoutStateWriter` 走 `UserRepository` 抽象
2. **P1-2** `OutboxPoller` dead-letter 原子性 + ADR-0009 v1.5 Amendment
3. **P1-3** CSRF strict-mode toggle + ADR-0015 v1.6 Amendment

### 新功能候選

4. **Daily Work Board UI**（Q-8 拍板的四區塊儀表板）
5. **Notification / Webhook 實作**（spec 已預留 Notification context）
6. **P-002 增量同步**（`?since=` / ETag / If-Modified-Since）
7. **行動裝置 native app skeleton**

---

## git tag 待 push

本地已建立兩個 tag：

```bash
# 使用者驗收後執行：
git push origin v1.0.0-M5
git push origin spec-v1.5
```

push 後 GitHub Actions `release.yml` 會自動觸發：
- Build + push Docker image 到 GHCR
- 從 `CHANGELOG.md [1.0.0-M5]` 產 release notes
- 重建 `docs/api/index.html`（Redoc HTML，openapi.yaml v1.5.0）

---

## 下一里程碑

```text
> 啟動里程碑 M6(<主題名>)。
> 負責 agent:<agent-name>。
> 接手起點:STATUS.md P1 backlog + docs/release/m5-summary.md M6+ 候選主題清單。
> 完成後依「Agent 協作協定」交付 handoff 四件套，並停下等驗收。
```

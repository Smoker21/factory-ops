# 後端狀態

**狀態**: ✅ M5 COMPLETED
**版本**: 1.5.0（對應 Spec v1.5.0）
**完成時間**: 2026-05-09
**負責 agent**: quarkus-backend-builder（M5.3 / M5.4）

---

## M5 baseline

**測試**: 654 tests, 0 failures（`./gradlew test`，全綠）
**覆蓋率（JaCoCo）**: LINE ≥ 60%（M4 baseline，M5 不退化）

### 主要新增 / 修改（M5.3 + M5.4）

| 分類 | 主要檔案 |
|---|---|
| S-009 cookie / CSRF | `CookieHelper.kt`、`CsrfFilter.kt`、`AuthResource.kt`（login/refresh/logout Set-Cookie）|
| S-016 lockout 持久化 fix | `LockoutStateWriter.kt`（`@Transactional(REQUIRES_NEW)` + `persistOrUpdate()`） |
| S-015 rate-limit | `RateLimiter.kt`、`AuthService.kt` |
| S-011 臨時密碼 | `UserService.kt`（SecureRandom 16 字元 + 強度驗證） |
| S-013 regex escape | `UserRepository.kt`（Pattern.quote + 前綴匹配） |
| S-010 CORS fail-fast | `CorsValidationOnStartup.kt` |
| C-005 ~ C-015 | `DispatchService.kt`、`TaskService.kt`、`ProjectService.kt`、`OrganizationService.kt`、`OutboxPoller.kt` |
| Q-23 OR 切換 | `TaskService.kt:424-425`（`size >= 2` 替換 `all { ... }`） |
| dead-letter | `OutboxDeadLetterDocument.kt`、`OutboxDeadLetterRepository.kt` |

**權威歷史**: `CHANGELOG.md [1.0.0-M5]`。

---

## 未解決問題（M6 backlog）

1. **P1-1** `LockoutStateWriter` 繞過 `UserRepository` 抽象 — M6 補 `UserRepository.updateLockoutState()` 方法
2. **P1-2** `OutboxPoller` dead-letter 寫入 + 原 entry 標記非原子（最終一致性，冪等保護）— M6 補 `@Transactional` 包覆 + ADR-0009 v1.5 Amendment
3. **P1-3** CSRF dual-mode 截止點不清晰 — M6 加 `security.csrf.strict-mode` toggle + ADR-0015 v1.6 Amendment
4. **RateLimiter in-memory**：不跨 JVM 實例共享；多 instance 擴展時需換 Redis

---

## 給下一棒 starter context

M5 全部 in-scope 條目已完成。M6 接手時請先讀 `STATUS.md`（主）P1 backlog 說明 + `docs/review/m5-review.md` P1-1 ~ P1-3 具體建議。

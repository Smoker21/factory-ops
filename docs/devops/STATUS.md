# DevOps STATUS

**狀態**: ✅ M5 COMPLETED
**完成時間**: 2026-05-09
**負責 agent**: doc-devops（M5.6.2）

---

## M5.6.2 產出清單

| 動作 | 說明 |
|---|---|
| `backend/src/main/resources/application.properties` | P1-4: `info-version` 1.3.0 → 1.5.0；P2-3: CSRF exempt-paths 移除 `/mock-hr`（`%dev` 補回）；P2-5: `%test.auth.cookie.secure=false` + `%test.auth.cookie.same-site=Lax` |
| `CHANGELOG.md` | `[Unreleased]` → `[1.0.0-M5] - 2026-05-09`；link references 更新 |
| `STATUS.md`（主） | M5 COMPLETED 標記；compact 至 ~90 行（M4 baseline 230 → M5 前 369 → compact 後 ~90） |
| 各 sub-STATUS.md | spec / data / backend / frontend / test / review / devops 全部 compact |
| `docs/release/m5-checklist.md` | Release checklist 全項勾完存檔 |
| `docs/release/m5-summary.md` | M5 完成總結 |
| git tag | `v1.0.0-M5` + `spec-v1.5`（local only，未 push） |
| `docs/api/index.html` | **待 CI 重產**（本機無 redoc-cli；CI release.yml tag v* 觸發時自動重建） |

**測試確認**: 654 backend tests, 0 failures（`./gradlew test`，all green）

---

## M4 baseline 摘要

M4 doc-devops（2026-05-04）：Docker / CI / CD / 文件 / README 全套建立。詳見 `CHANGELOG.md [1.0.0-M4]`。

---

## 給下一棒 starter context

M5.6.2 完成，git tag v1.0.0-M5 + spec-v1.5 已在 local 建立。使用者驗收後執行 `git push origin v1.0.0-M5 spec-v1.5` 完成 release。

# 階段:規格與架構

**狀態**: ✅ M5 COMPLETED
**版本**: **1.5.0**（2026-05-08 M5.1.5 — JWT cookie / CSRF spec）
**完成時間**: 2026-05-08
**負責 agent**: spec-architect

---

## M5 baseline(v1.5.0)

| 里程碑 | 主要產出 |
|---|---|
| M5.1 | Q-18 ~ Q-24 七題全拍板落地；requirements.md / openapi.yaml / domain-model.md bump 1.3.0 → 1.4.0；ADR-0007/0009/0011 各加 v1.4 Amendment |
| M5.1.5 | S-009 cookie/CSRF spec 補件；requirements.md 新增 §FR-1.5 ~ §FR-1.8；openapi.yaml bump 1.4.0 → 1.5.0；新建 ADR-0015（JWT Cookie + CSRF Model，Status: Accepted） |

**權威歷史**: `CHANGELOG.md [1.0.0-M5]`。

---

## 未解決問題 / 已知妥協

- `docs/api/index.html`（Redoc 靜態 HTML）待 CI release workflow（`release.yml` tag v*）重新生成；本機無 redoc-cli。
- ADR 編號重複：`docs/adr/0012-organization-tree-materialized-path.md` 與 `docs/adr/0012-frontend-ui-library.md` 均為 0012，屬既存問題，M6 清理。

---

## 給下一棒 starter context

spec v1.5.0 已 lock。M6 若需新功能，由 spec-architect 依 CLAUDE.md § Spec / ADR 變更原則 in-place bump 版本。

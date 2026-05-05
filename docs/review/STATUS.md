# Code Review Status

**狀態**: READY_FOR_BUILDER_FIX(P0 修復後重審)
**完成日期**: 2026-05-04
**版本**: M3 backend + frontend(對照 spec v1.3.0)

## 摘要

- **整體評分**: 4 / 10(規格與骨架完整,但**安全與多租戶嚴重缺陷,不可上線**)
- **發現總計**: 65 項
  - Critical: **8**
  - High: 18
  - Medium: 22
  - Low: 12
  - Info: 5

## 報告

詳見 [code-review-report.md](./code-review-report.md)。

## 給下一棒的指引

**下一棒**: `quarkus-backend-builder`(處理 Must-Fix 1-15 項)
**doc-devops 暫不啟動**,等 P0 修完再進。

### Must-Fix P0(必須在 M4 內修完)

1. S-001 — JWT refresh token 簽名驗證
2. S-002 — 全面補 `@RolesAllowed`
3. S-003 — Login 帶 rootOrgId
4. S-004 — User PATCH 限 ORG_ADMIN/ADMIN
5. S-005 — JWT 私鑰移出 git
6. S-012 — Repository 全帶 rootOrgId
7. C-001 — OrganizationService.createOrg 原子化
8. C-002 — TemplateService 版本單調 + active 檢查 + GLOBAL/ORG 分流
9. M-003 — 所有 service method 加 `@Transactional`
10. C-003 — Group settings INV-35 角色白名單
11. S-006 — MockHrResource `@IfBuildProfile("dev")`
12. S-008 — Logout 實作 token blacklist
13. P-001 — 列表 cursor pagination
14. C-013 / P-003 — Org move ancestorIds propagation
15. C-016 — EventPublisher 移除 try/catch

### Re-review 範圍

修完後只需重審:
- 5 個 Critical(S-001 ~ S-005)是否真的修好
- RBAC 矩陣 sample 10 個 endpoint 是否 work
- 不必整份 review 重跑

### 風險

目前狀態若部署到生產環境,**1 小時內**可被未授權使用者改寫整個系統的角色配置與組織結構。**強烈反對任何形式的 staging beyond local dev**。

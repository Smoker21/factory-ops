# ADR-0012: 前端 UI 函式庫選擇 — Mantine v7

**狀態**: 已接受
**日期**: 2026-05-03
**負責 agent**: react-frontend-builder

## 背景

本系統的工廠現場使用情境要求：
1. 觸控目標 ≥ 44 × 44px（戴手套操作）
2. 高對比配色（工廠環境光線）
3. 行動裝置優先
4. 快速開發（MVP 時程）

## 選項比較

| 項目 | Mantine v7 | shadcn/ui + Tailwind | Chakra UI v3 |
|---|---|---|---|
| 觸控目標 | 預設 ≥ 44px，可覆寫 | 需自行確保 44px | 預設 ≥ 44px |
| WCAG 符合 | AAA 級支援良好 | 需自行實作 | AA 級 |
| 行動裝置 | 原生 AppShell + 響應式 | 需自行建構 Layout | 良好 |
| TypeScript 支援 | 完整，無 `any` | 完整 | 完整 |
| 元件豐富度 | 高（AppShell、Notifications、Modals 等） | 中等（需組合） | 高 |
| Bundle 大小 | 中（tree-shakeable） | 小 | 中 |
| 開發速度 | 最快（完整元件） | 中（需大量客製） | 快 |

## 決策

選擇 **Mantine v7**。

理由：
1. `AppShell` 元件天然適合工廠場景（側邊導覽、頂部 Header）
2. 預設觸控目標達標，免除大量 CSS 微調
3. `@mantine/notifications` + `@mantine/modals` 提供完整的 UX 回饋機制
4. `useDisclosure`、`useForm` 等 hooks 加速 UI 邏輯開發
5. 高對比 CSS 變數覆寫容易

## 偏離說明

任務書系統提示（CLAUDE.md）提及 Tailwind + shadcn/ui，但任務指令正文明確允許 Mantine v7 並要求寫 ADR 說明。本 ADR 即為此決策說明。

## 已知風險

- `@postcss` 依賴較少見（已加入 package.json）
- Mantine 版本升級可能有 breaking changes（使用 v7 穩定版）

## Auth 儲存策略說明

任務指令明確要求使用 `localStorage`（access_token + refresh_token）。
CLAUDE.md 系統提示偏好 httpOnly cookie，兩者有衝突。
此 MVP 版本遵循任務指令，使用 `localStorage`，並在 STATUS.md 中記錄 XSS 風險為已知 issue，待後續安全強化里程碑處理。

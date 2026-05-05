# 使用手冊(User Manual)

| 版本 | 連結 | 說明 |
|---|---|---|
| **繁體中文** | [zh-TW.md](./zh-TW.md) | 主要使用手冊 |

## 截圖

- 桌面版(1440 × 900):[`screenshots/desktop/`](./screenshots/desktop/)(12 張)
- 行動版(390 × 844,iPhone 12 Pro 寬度):[`screenshots/mobile/`](./screenshots/mobile/)(4 張)

所有截圖以 dev 環境的 React 前端搭配 MSW mock 資料拍攝;production 介面排版相同,內容不同。

## 重新產生

```bash
cd frontend
npm run dev                          # 視窗 A
npm run capture-screenshots          # 視窗 B(等 A 啟動完成)
```

腳本詳細邏輯見 [`frontend/scripts/capture-screenshots.ts`](../../frontend/scripts/capture-screenshots.ts)。

## 想新增 / 修改頁面?

編輯 `frontend/scripts/capture-screenshots.ts` 內的 `desktopSteps` 與 `mobileSteps` 陣列,加上新的 `{ name, url, fullPage }` 條目即可。

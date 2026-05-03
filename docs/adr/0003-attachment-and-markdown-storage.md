# ADR-0003: 附件、Markdown 內容與貼圖儲存策略

**狀態**: Accepted
**日期**: 2026-05-03
**決策者**: spec-architect
**相關需求**: FR-3.6、FR-3.7、FR-6、FR-9、US-D1、US-D2、US-D3

---

## Context(背景)

工廠值班場景需要在 Task / ActionRequest / 留言中:
- 撰寫 **markdown** 內容(支援標題、清單、表格)
- 插入 **照片**(現場異常)、**短影片**(噪音、振動)、**PDF**(SOP、規範)
- 用 **貼圖**(sticker)快速表達(「OK」「需確認」「等料中」)— 戴手套打字困難

需要決策:
1. **附件實體檔案存哪裡**?(MongoDB / GridFS / 物件儲存 / base64 直接塞進 markdown)
2. **markdown 怎麼引用附件**?
3. **貼圖怎麼管理**?(使用者上傳 vs 系統內建)
4. **上傳流程**(直傳物件儲存 vs 透過後端轉送)
5. **生命週期**(誰擁有 attachment、軟刪除策略、孤兒清理)

---

## Decision(決策)

### 1. 附件存 **物件儲存(S3 兼容)**,Production 用 AWS S3 / Azure Blob,Local 用 **MinIO**

**不採用**:
- GridFS — MongoDB 不是好的二進位儲存,讀寫效能、CDN 整合都不如 S3
- base64 內嵌 markdown — 文件膨脹、無法去重、無法 CDN 快取
- 直接放後端 filesystem — 不可擴展、不能多副本部署

### 2. 上傳採 **two-phase + presigned URL**(直傳物件儲存)

```
Phase 1: 客戶端 -> 後端
  POST /attachments
  body: { mimeType, sizeBytes, filename, ownerResourceType }
  resp: { attachment: { id, status: PENDING_UPLOAD, ... }, uploadUrl, uploadHeaders, expiresAt }

Phase 2: 客戶端 -> 物件儲存
  PUT {uploadUrl}        # 直傳,不經後端
  body: <file bytes>

Phase 3: 客戶端 -> 後端
  POST /attachments/{id}/finalize
  # 後端做 head check、checksum、(未來)病毒掃描;標記 status: READY
```

**為何不直接走後端轉送?**
- 50 MB 影片走後端會把 worker thread 卡爆
- 直傳到 S3/MinIO 可享 CDN、resumable upload
- 後端只負責 metadata 與授權

### 3. Attachment 為**獨立 aggregate**,以反向欄位連回擁有者

```kotlin
data class Attachment(
    val id: AttachmentId,
    val mimeType: String,
    val sizeBytes: Long,
    val storageKey: String,                 // S3 object key,內部用,前端不直接看
    val uploaderId: UserId,
    val ownerResourceType: ResourceType,    // TASK / ACTION_REQUEST / COMMENT / PROJECT
    val ownerResourceId: String?,           // 可空(先建檔再 attach 也可)
    val metadata: MediaMetadata,            // width/height/duration/checksum
    val status: AttachmentStatus,           // PENDING_UPLOAD / READY / FAILED / DELETED
    val createdAt: Instant,
    val deletedAt: Instant?
)
```

**擁有者反向引用**:
- Task / Comment 等內也存 `attachments: List<AttachmentRef>`(`{ attachmentId, alt, role }`)
- 雙向關聯但**以擁有者(Task / Comment)為主**:刪除 Task 時級聯標記 attachment 為 deleted。
- `ownerResourceType + ownerResourceId` 在 Attachment 上是**反向索引欄位**,方便孤兒清理與權限檢查。

### 4. Markdown 引用語法:`![alt](attachment://{id})`

- Markdown 中**不直接寫 S3 URL**(會過期、無法做權限),改用 custom URI scheme `attachment://`
- 前端 markdown renderer 攔截此 URI,改去 `/attachments/{id}` 取 presigned downloadUrl
- 貼圖類似:`![](sticker://{id})`,前端從本地快取 sticker 資料庫直接顯示

### 5. 貼圖(Sticker)= **系統預載資源**,不開放使用者上傳

- 由 ADMIN 在後台維護(MVP 直接 seed 一組固定的 ~30 個常用貼圖:OK、待確認、急、停線、等料、完工…)
- `Sticker { id, code, label, imageUrl, sortOrder }`
- 公開的 `GET /stickers` 端點,前端啟動時拉一次後快取
- 為何不開放使用者上傳?
  - 戴手套快速回應的場景,要的是**有限選項**(滑就選到),太多反而難用
  - 避免不雅/違規圖片管理成本
  - 後續可加「ADMIN 上傳貼圖」功能,不影響現有架構

### 6. 安全:URL、檢查、配額

| 項目 | 策略 |
|---|---|
| 上傳 presigned URL | 5 分鐘有效,單次使用 |
| 下載 presigned URL | 5 分鐘有效,呼叫 `GET /attachments/{id}` 即時生成 |
| MIME type 白名單 | 在後端 `attachment.policy` 設定,不在白名單拒絕 |
| 大小限制 | 預設 50 MB,可由系統配置調整 |
| 病毒掃描 | 預留 `finalize` 階段 hook,MVP 不接 ClamAV(可後加) |
| 授權檢查 | 取 downloadUrl 前必須對 ownerResource 有讀權限 |
| 配額 | 預留 user 與 project 配額機制(MVP 不限制) |

### 7. 軟刪除與孤兒清理

- Task 軟刪除時,**不立即** 動 attachments,僅在 attachment 上記錄 `deletedAt`(透過 reactor)
- 真正釋放 S3 空間由獨立 **GC job**(每日跑):
  - 找出 `status=DELETED` 且 `deletedAt < now - 30 days` 的 attachment
  - 刪除 S3 物件、把 metadata `status=PURGED`
- 孤兒清理:`PENDING_UPLOAD` 狀態 24 小時未 finalize 的視為失敗,標記 `FAILED`,7 天後 GC

### 8. Markdown 內容儲存

- `descriptionMarkdown`:**直接存原始 markdown 字串**在 Task / ActionRequest 文件內
- **不存 HTML**:渲染由前端負責(`react-markdown` + `remark-gfm`)
- **不做伺服器端 sanitize 後再存**:存原始,渲染時 sanitize(避免 sanitize 規則變更要重存)
- 全文檢索建在 `title + descriptionMarkdown` 上,markdown 語法的雜訊可接受
- 大小限制:預設 64 KB / 欄位(超大內容應拆 Task 或附 PDF)

---

## Consequences(後果)

### 正面
- **可擴展**:S3 兼容介面,production 換 AWS / Azure / GCS 都不改程式
- **效能**:大檔不過後端,後端 worker 不被卡
- **去重 / CDN**:S3 + CDN 自然支援
- **安全**:presigned URL 短期有效,不外洩 storage key
- **生命週期清楚**:三段式上傳 + GC job 處理孤兒
- **一致性**:統一以 `attachmentId` 引用,markdown / sticker / 留言 / 主附件清單共用一套

### 負面
- **多一個服務**:Local 開發要起 MinIO(納入 docker-compose,影響可控)
- **failure modes 增加**:上傳完成但 finalize 失敗時的補償邏輯需要處理(緩解:finalize idempotent + GC job 兜底)
- **下載要多一次 round-trip**:前端先 GET attachment metadata 再 GET 實檔(緩解:可在 markdown render 時批次預取)

---

## Alternatives Considered

### A. GridFS
- **優點**:不需額外服務、與 MongoDB 同備份策略
- **缺點**:效能不佳(尤其影片)、無 CDN 整合、佔用 MongoDB 資源、查詢與檔案 IO 互相影響
- **不採用**

### B. base64 內嵌進 markdown
- **優點**:單一文件,不需額外服務
- **缺點**:文件膨脹 33%、文件大小限制(MongoDB 16MB)、無快取、無去重、行動端流量浪費
- **不採用**

### C. 只走後端轉送(不用 presigned URL)
- **優點**:邏輯集中、不暴露 S3
- **缺點**:大檔卡 worker、無法 resumable、伺服器頻寬與 CPU 是瓶頸
- **不採用**

### D. 把附件 metadata embed 在 Task 內(不獨立 collection)
- **優點**:讀 Task 時所有資訊都在
- **缺點**:跨 Task 查 attachment 不便、刪除 Task 與 attachment 生命週期糾纏、留言內附件難管理
- **不採用**:Attachment 有自己的生命週期(上傳中 / READY / 刪除待 GC),獨立 aggregate 較合理

### E. 開放使用者上傳貼圖
- **優點**:個人化
- **缺點**:管理成本、不雅內容、戴手套場景需要的反而是「少而精」
- **延後**:架構保留可能,MVP 不做

---

## Compliance / Validation

- 後端拒絕**未在白名單**的 MIME type 與超過大小上限的檔案
- 上傳後**至少要 finalize**才算 READY,前端引用未 READY 的 attachment 在渲染時應顯示 placeholder
- 下載 URL 必須**每次重新生成**,不能快取在 client(快取 metadata 可以)
- GC job 必須有 **dry-run mode** 與審查 log

---

## Notes for Next Stage

- `attachments` collection 索引:
  - `{ ownerResourceType: 1, ownerResourceId: 1 }`(反向查詢)
  - `{ uploaderId: 1, createdAt: -1 }`
  - `{ status: 1, deletedAt: 1 }`(GC job 用)
- `stickers` collection 索引:`{ sortOrder: 1 }`
- 後端啟動時若 stickers 為空,從 `db/seed-stickers.json` 載入(`mongodb-modeler` 設計)
- MinIO 在 docker-compose 起(`doc-devops` 處理)

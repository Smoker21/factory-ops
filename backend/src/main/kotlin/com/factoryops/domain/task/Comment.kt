package com.factoryops.domain.task

import com.factoryops.domain.shared.AttachmentRef
import java.time.Instant

/**
 * Task 留言 Entity(內嵌於 Task document)。
 * 對應 spec §3 class diagram Comment Entity。
 *
 * embed 決策:工廠值班場景預估每個 Task ≤ 50 筆留言。
 * 門檻警告:超過 100 筆時應考慮拆為獨立 task_comments collection。
 * 詳見 schema.md §7 的決策說明。
 *
 * 注意:Comment 有自己的 id(在 Task document 內的識別子),
 * 但不是 ObjectId 型別 — 以 String(UUID 或 ULID)儲存即可,
 * 因 Comment 無獨立 MongoDB _id。
 */
data class Comment(
    /** Comment 內部 ID(UUID/ULID hex string;Task document 內唯一) */
    val id: String,
    /** 作者 userId */
    val authorId: String,
    /** 留言內容(Markdown;可用 attachment://{id} 語法引用附件) */
    val bodyMarkdown: String,
    /** 附件引用列表 */
    val attachments: List<AttachmentRef> = emptyList(),
    val createdAt: Instant = Instant.now(),
    /** 最後編輯時間;null = 未編輯 */
    val editedAt: Instant? = null,
    /** 軟刪除時間;null = 未刪除 */
    val deletedAt: Instant? = null
)

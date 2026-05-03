package com.factoryops.domain.shared

import java.time.Instant

/**
 * 通用稽核歷程 Value Object。
 * 所有 aggregate root 均內嵌 `history: List<AuditEntry>`,append-only。
 *
 * 對應 spec §4 各 aggregate 的 HistoryEntry VO。
 * Invariant:一旦 append 後不可修改(immutable data class)。
 * 上限警告:> 1000 筆時應冷儲歸檔。
 */
data class AuditEntry(
    /** 操作者 userId(ObjectId hex string) */
    val actorId: String,
    /** 操作代碼,例 TASK_STATUS_CHANGED、ORG_CREATED */
    val action: String,
    /** 操作時間(UTC Instant) */
    val at: Instant,
    /** 操作相關 payload(before/after 等),自由 Map */
    val payload: Map<String, Any?> = emptyMap()
)

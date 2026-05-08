package com.factoryops.domain.event

import java.time.Instant

/**
 * Outbox dead-letter aggregate root.
 * 對應 MongoDB collection: outbox_dead_letters
 *
 * 設計背景(ADR-0009 + M5.2 Block B):
 * - EventOutbox entry 在 retryCount > 10 後,由 OutboxPoller 移入此 collection。
 * - 這是通用 outbox dead-letter,適用於所有 outbox event(NATS 發布失敗、webhook 投遞失敗等)。
 * - 與 WebhookDeadLetter 並存:WebhookDeadLetter 屬 HTTP 投遞層語義(有 webhookId / targetUrl);
 *   本 document 屬 outbox transport 層語義(retryCount 超限),兩者邊界清晰(選項 X)。
 *
 * 索引:{ rootOrgId: 1, createdAt: -1 }(ops 查詢「最近 N 個失敗」)。
 * 無 TTL:dead-letter 屬調查證據,不自動過期。
 */
data class OutboxDeadLetter(
    val id: String? = null,
    /** 租戶鍵;GLOBAL event 可 null */
    val rootOrgId: String? = null,
    /**
     * 來源 EventOutbox 的 _id(hex string)。
     * 用於追蹤哪一筆 outbox entry 超限而進入 dead-letter。
     *
     * 冪等保證依賴:若 originalOutboxId 非 null,`idx_odl_original_outbox_unique`
     * 索引(partialFilterExpression + unique)確保同一個 outbox entry 不會被搬移兩次。
     * OutboxPoller 應在 insert 時 catch duplicate key error 並忽略(視為「已搬移過」)。
     *
     * 若 originalOutboxId = null,該 document 不受此 unique 索引限制,
     * 可插入多筆(適用於未來可能有的「無來源 outbox entry 的 dead-letter」場景)。
     */
    val originalOutboxId: String? = null,
    /** 事件型態;例 `factory-ops.task.assigned` */
    val eventType: String,
    /** 完整的 DomainEvent JSON payload(序列化後的 Map) */
    val payload: Map<String, Any?>,
    /** 進入 dead-letter 時的 retryCount(必定 > 10) */
    val retryCount: Int,
    /** 最後一次嘗試的失敗原因;可 null */
    val lastError: String? = null,
    /** EventOutbox 初次建立的時間(用於 latency 分析) */
    val createdAt: Instant = Instant.now(),
    /** 進入 dead-letter 的時間(M5.3 OutboxPoller 寫入) */
    val failedAt: Instant = Instant.now(),
    val schemaVersion: Int = 1
)

package com.factoryops.domain.event

import java.time.Instant

/**
 * Outbox 模式 Domain Event 儲存 aggregate root。
 * 對應 MongoDB collection: domain_event_outbox
 * 詳見 ADR-0009。
 *
 * 設計重點:
 * - aggregate 寫入與 outbox entry 寫入在同一 MongoDB transaction(MongoDB ≥ 4.0)。
 * - Outbox poller worker 定期 poll `{ processedAt: null }` 的 entry。
 * - 處理完成後設定 `processedAt`;TTL index 自動清除已處理記錄(7 天)。
 * - `retryCount` 與 `scheduledAt` 支援指數退避重試。
 *
 * Invariant:
 * - eventId 全局唯一(ULID);unique index 防止重複寫入。
 * - processedAt = null 表示待處理。
 */
data class EventOutbox(
    val id: String? = null,
    /** 租戶鍵;GLOBAL 事件可 null */
    val rootOrgId: String? = null,
    /** ULID;全局唯一 */
    val eventId: String,
    /** 事件型態;例 `factory-ops.task.assigned` */
    val eventType: String,
    /** Aggregate 型別 */
    val aggregateType: String,
    /** Aggregate ID */
    val aggregateId: String,
    /** 完整的 DomainEvent JSON payload(序列化後的 Map) */
    val payload: Map<String, Any?>,
    /** 已發佈時間;null = 待處理 */
    val processedAt: Instant? = null,
    /** 已重試次數;初始 0 */
    val retryCount: Int = 0,
    /**
     * 可被 poll 的時間;支援退避重試。
     * 正常情況下 = createdAt;失敗後設為 createdAt + 退避時間。
     */
    val scheduledAt: Instant = Instant.now(),
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now()
)

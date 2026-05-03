package com.factoryops.domain.event

import java.time.Instant

/**
 * Domain Event 共用結構。
 * 對應 spec §6 Domain Events + openapi.yaml components/schemas/DomainEventPayload。
 *
 * 統一 topic 命名:`factory-ops.<context>.<event-kebab>`(ADR-0009)。
 * wire format:NATS message body 與 Webhook POST body 共用此結構。
 */
data class DomainEvent(
    /** ULID;訂閱端用於去重 */
    val eventId: String,
    /** 事件型態;例 `factory-ops.task.assigned` */
    val eventType: String,
    /** 事件發生時間(UTC Instant;wire format 轉 ISO 8601 + offset) */
    val occurredAt: Instant,
    /** 租戶鍵;GLOBAL 事件可 null */
    val rootOrgId: String? = null,
    /** 從 root 到事件所屬 leaf 的 OrgId 路徑(若適用) */
    val orgPath: List<String> = emptyList(),
    /** Aggregate 型別 */
    val aggregateType: String,
    /** Aggregate ID(ObjectId hex string) */
    val aggregateId: String,
    /** 操作者 userId;系統觸發時可 null */
    val actorId: String? = null,
    /** 事件 payload(event-specific) */
    val payload: Map<String, Any?> = emptyMap()
)

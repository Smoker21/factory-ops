package com.factoryops.domain.webhook

import java.time.Instant

/**
 * Webhook 投遞失敗死信 aggregate root。
 * 對應 MongoDB collection: webhook_dead_letters
 * 詳見 ADR-0009。
 *
 * TTL 策略:已處理(resolvedAt 非 null)的死信 30 天後自動清除。
 */
data class WebhookDeadLetter(
    val id: String? = null,
    val rootOrgId: String,
    val webhookId: String,
    /** ULID */
    val eventId: String,
    val eventType: String,
    /** 投遞時使用的完整 payload */
    val payload: Map<String, Any?>,
    val targetUrl: String,
    /** 已嘗試投遞次數 */
    val attemptCount: Int,
    val lastAttemptAt: Instant,
    /** 最後失敗原因 */
    val lastError: String? = null,
    /** 手動標記為已處理的時間;null = 未處理 */
    val resolvedAt: Instant? = null,
    val schemaVersion: Int = 1,
    /** TTL index 基準欄位 */
    val createdAt: Instant = Instant.now()
)

package com.factoryops.domain.webhook

import java.time.Instant

/**
 * Webhook 訂閱 aggregate root。
 * 對應 spec Notification Context(預留)+ openapi.yaml components/schemas/Webhook。
 * 對應 MongoDB collection: webhooks
 * 詳見 ADR-0009。
 *
 * 注意:`secret` 欄位為 HMAC-SHA256 signing key,不得出現在任何 log 輸出。
 */
data class Webhook(
    val id: String? = null,
    val rootOrgId: String,
    /** 投遞目標 URL */
    val targetUrl: String,
    /**
     * 訂閱的 event type 清單。
     * 例 `["task.assigned", "action-request.dispatched"]`、wildcard `"task.*"`、全訂 `">"`。
     */
    val events: List<String> = emptyList(),
    /**
     * HMAC-SHA256 signing key。
     * 不得出現在任何 log 輸出;不回傳給前端(write-only)。
     */
    val secret: String,
    val active: Boolean = true,
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)

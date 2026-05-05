package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "webhooks")
class WebhookDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var targetUrl: String
    var events: List<String> = emptyList()
    lateinit var secret: String
    var active: Boolean = true
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

@MongoEntity(collection = "webhook_dead_letters")
class WebhookDeadLetterDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var webhookId: ObjectId
    lateinit var eventId: String
    lateinit var eventType: String
    var payload: Map<String, Any?> = emptyMap()
    lateinit var targetUrl: String
    var attemptCount: Int = 0
    var lastAttemptAt: Instant = Instant.now()
    var lastError: String? = null
    var resolvedAt: Instant? = null
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
}

@MongoEntity(collection = "domain_event_outbox")
class EventOutboxDocument : PanacheMongoEntity() {
    var rootOrgId: ObjectId? = null
    lateinit var eventId: String
    lateinit var eventType: String
    lateinit var aggregateType: String
    lateinit var aggregateId: ObjectId
    var payload: Map<String, Any?> = emptyMap()
    var processedAt: Instant? = null
    var retryCount: Int = 0
    var scheduledAt: Instant = Instant.now()
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
}

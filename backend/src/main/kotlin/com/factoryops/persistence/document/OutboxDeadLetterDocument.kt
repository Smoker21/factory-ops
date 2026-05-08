package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Persistence document for the outbox_dead_letters collection.
 * Corresponds to domain class com.factoryops.domain.event.OutboxDeadLetter.
 *
 * An outbox entry is moved here by OutboxPoller when retryCount exceeds the dead-letter threshold.
 * The original outbox entry's processedAt is set to now() after transfer (not deleted),
 * preserving audit trail in domain_event_outbox.
 *
 * Idempotency: idx_odl_original_outbox_unique (partialFilterExpression + unique) ensures
 * the same outbox entry is never moved twice. OutboxPoller catches DuplicateKeyException on insert
 * and treats it as "already moved".
 *
 * M5.2 Block B design: see docs/data/schema.md §16 and docs/data/indexes.md §17.
 */
@MongoEntity(collection = "outbox_dead_letters")
class OutboxDeadLetterDocument : PanacheMongoEntity() {
    /** Tenant key; null for GLOBAL events */
    var rootOrgId: ObjectId? = null
    /**
     * _id of the originating EventOutboxDocument (domain_event_outbox collection).
     * Used for idempotency: idx_odl_original_outbox_unique prevents double-insertion.
     */
    var originalOutboxId: ObjectId? = null
    lateinit var eventType: String
    var payload: Map<String, Any?> = emptyMap()
    /** retryCount at the time of dead-lettering (must be > 10) */
    var retryCount: Int = 0
    var lastError: String? = null
    /** When the original outbox entry was first created */
    var createdAt: Instant = Instant.now()
    /** When this document was written to dead_letters */
    var failedAt: Instant = Instant.now()
    var schemaVersion: Int = 1
}

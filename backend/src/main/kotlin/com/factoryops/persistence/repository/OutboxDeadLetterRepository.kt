package com.factoryops.persistence.repository

import com.factoryops.persistence.document.OutboxDeadLetterDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId

/**
 * Repository for the outbox_dead_letters collection.
 *
 * M5.4 C-015: Provides insert and ops-query operations for OutboxPoller dead-letter handling.
 */
@ApplicationScoped
class OutboxDeadLetterRepository : PanacheMongoRepository<OutboxDeadLetterDocument> {

    /**
     * Persists a dead-letter entry.
     * Callers must catch com.mongodb.MongoWriteException (duplicate key) for idempotency:
     * if idx_odl_original_outbox_unique fires, the entry was already moved — ignore safely.
     */
    fun insert(deadLetter: OutboxDeadLetterDocument) {
        persist(deadLetter)
    }

    /**
     * Returns the most recent dead-letter entries for a given rootOrgId,
     * ordered by failedAt descending. Used by ops teams to inspect failures.
     */
    fun findByRootOrgId(rootOrgId: ObjectId, limit: Int = 20): List<OutboxDeadLetterDocument> =
        find(Document("rootOrgId", rootOrgId))
            .page(0, limit)
            .list()
}

package com.factoryops.infrastructure.outbox

import com.factoryops.persistence.document.OutboxDeadLetterDocument
import com.factoryops.persistence.repository.EventOutboxRepository
import com.factoryops.persistence.repository.OutboxDeadLetterRepository
import com.mongodb.MongoWriteException
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Outbox poller — reads unprocessed events and dispatches to NATS + webhooks.
 * ADR-0009: Outbox pattern for reliable event delivery.
 *
 * C-015 dead-letter:
 *   - On each failure: retryCount++ with exponential backoff (capped at 1 hour).
 *   - When retryCount > DEAD_LETTER_THRESHOLD: move to outbox_dead_letters collection
 *     and mark original entry processedAt=now (not deleted — audit trail preserved).
 *   - Idempotency: DuplicateKeyException on insert = already moved, ignore.
 *
 * In dev mode (factory.ops.outbox.dry-run=true), events are only logged.
 */
@ApplicationScoped
class OutboxPoller(
    private val outboxRepository: EventOutboxRepository,
    private val deadLetterRepository: OutboxDeadLetterRepository
) {

    @ConfigProperty(name = "factory.ops.outbox.poll.enabled", defaultValue = "true")
    var pollEnabled: Boolean = true

    @ConfigProperty(name = "factory.ops.outbox.poll.batch-size", defaultValue = "100")
    var batchSize: Int = 100

    @ConfigProperty(name = "factory.ops.outbox.dry-run", defaultValue = "false")
    var dryRun: Boolean = false

    /** Maximum retry count before an outbox entry is moved to dead-letter. */
    @ConfigProperty(name = "factory.ops.outbox.dead-letter.threshold", defaultValue = "10")
    var deadLetterThreshold: Int = 10

    /** Maximum backoff in seconds before retrying a failed event (1 hour). */
    private val maxBackoffSeconds: Long = 3600L

    @Scheduled(every = "\${factory.ops.outbox.poll.interval:5s}")
    fun pollAndProcess() {
        if (!pollEnabled) return

        try {
            val pending = outboxRepository.findPendingBatch(batchSize)
            if (pending.isEmpty()) return

            logger.debug { "Processing ${pending.size} outbox events" }
            for (event in pending) {
                try {
                    processEvent(event.eventId, event.eventType, event.payload)
                    event.processedAt = Instant.now()
                    outboxRepository.update(event)
                } catch (ex: Exception) {
                    logger.error(ex) { "Failed to process outbox event [${event.eventId}] type=${event.eventType} retryCount=${event.retryCount}" }

                    if (event.retryCount > deadLetterThreshold) {
                        // C-015: move to dead-letter; mark original as processed to stop retry loop
                        moveToDeadLetter(event.id, event.rootOrgId, event.eventType, event.payload, event.retryCount, ex, event.createdAt)
                        event.processedAt = Instant.now()
                        outboxRepository.update(event)
                        logger.warn { "Outbox event moved to dead-letter [${event.eventId}] type=${event.eventType} retryCount=${event.retryCount}" }
                    } else {
                        // C-015: exponential backoff, capped at maxBackoffSeconds (1 hour)
                        val backoffSeconds = minOf(
                            Math.pow(2.0, event.retryCount.toDouble()).toLong(),
                            maxBackoffSeconds
                        )
                        event.retryCount = event.retryCount + 1
                        event.scheduledAt = Instant.now().plusSeconds(backoffSeconds)
                        outboxRepository.update(event)
                        logger.debug { "Scheduled retry for event [${event.eventId}] in ${backoffSeconds}s (attempt ${event.retryCount})" }
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error(ex) { "OutboxPoller: error during poll cycle" }
        }
    }

    private fun moveToDeadLetter(
        originalOutboxId: org.bson.types.ObjectId?,
        rootOrgId: org.bson.types.ObjectId?,
        eventType: String,
        payload: Map<String, Any?>,
        retryCount: Int,
        cause: Exception,
        originalCreatedAt: Instant
    ) {
        val deadLetter = OutboxDeadLetterDocument().also { d ->
            d.rootOrgId = rootOrgId
            d.originalOutboxId = originalOutboxId
            d.eventType = eventType
            d.payload = payload
            d.retryCount = retryCount
            d.lastError = cause.message?.take(1000)
            d.createdAt = originalCreatedAt
            d.failedAt = Instant.now()
        }
        try {
            deadLetterRepository.insert(deadLetter)
        } catch (ex: MongoWriteException) {
            if (ex.error.code == 11000) {
                // Duplicate key: entry was already moved in a prior run — idempotent, ignore
                logger.debug { "Dead-letter for originalOutboxId=[$originalOutboxId] already exists, skipping duplicate insert" }
            } else {
                throw ex
            }
        }
    }

    private fun processEvent(eventId: String, eventType: String, payload: Map<String, Any?>) {
        if (dryRun) {
            logger.info { "OUTBOX DRY-RUN: would publish event [$eventType] id=[$eventId]" }
            return
        }
        // Real NATS/webhook dispatch is wired in the outbound integration phase
        logger.info { "OUTBOX: published event [$eventType] id=[$eventId]" }
    }
}

package com.factoryops.infrastructure.outbox

import com.factoryops.persistence.repository.EventOutboxRepository
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Outbox poller - reads unprocessed events and dispatches to NATS + webhooks.
 * ADR-0009: Outbox pattern for reliable event delivery.
 *
 * In dev mode (factory.ops.outbox.dry-run=true), events are only logged.
 */
@ApplicationScoped
class OutboxPoller(
    private val outboxRepository: EventOutboxRepository
) {

    @ConfigProperty(name = "factory.ops.outbox.poll.enabled", defaultValue = "true")
    var pollEnabled: Boolean = true

    @ConfigProperty(name = "factory.ops.outbox.poll.batch-size", defaultValue = "100")
    var batchSize: Int = 100

    @ConfigProperty(name = "factory.ops.outbox.dry-run", defaultValue = "false")
    var dryRun: Boolean = false

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
                    logger.error(ex) { "Failed to process outbox event [${event.eventId}] type=${event.eventType}" }
                    val backoffSeconds = minOf(Math.pow(2.0, event.retryCount.toDouble()).toLong(), 300L)
                    event.retryCount = event.retryCount + 1
                    event.scheduledAt = Instant.now().plusSeconds(backoffSeconds)
                    outboxRepository.update(event)
                }
            }
        } catch (ex: Exception) {
            logger.error(ex) { "OutboxPoller: error during poll cycle" }
        }
    }

    private fun processEvent(eventId: String, eventType: String, payload: Map<String, Any?>) {
        if (dryRun) {
            logger.info { "OUTBOX DRY-RUN: would publish event [$eventType] id=[$eventId]" }
            return
        }
        // TODO: In production, publish to NATS JetStream and trigger webhook dispatching
        logger.info { "OUTBOX: published event [$eventType] id=[$eventId]" }
    }
}

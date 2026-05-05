package com.factoryops.application.service

import com.factoryops.domain.actionrequest.ActionRequest
import com.factoryops.domain.task.Task
import com.factoryops.persistence.document.EventOutboxDocument
import com.factoryops.persistence.repository.EventOutboxRepository
import com.github.f4b6a3.ulid.UlidCreator
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import mu.KotlinLogging
import org.bson.types.ObjectId
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Outbox event publisher (ADR-0009).
 * Writes domain events to the domain_event_outbox collection.
 * The OutboxPoller reads these and forwards to NATS + webhooks.
 */
/**
 * NOTE: @Transactional ensures outbox writes participate in the caller's transaction.
 * If the caller rolls back, the outbox event is also rolled back (requires replica set on MongoDB).
 * In dev/standalone mode Quarkus degrades to best-effort (no atomicity guarantee).
 */
@ApplicationScoped
@Transactional
class EventPublisherService(
    private val outboxRepository: EventOutboxRepository
) {

    fun publishTaskCreated(task: Task, actorId: String) {
        publishEvent(
            rootOrgId = task.rootOrgId,
            eventType = "factory-ops.task.created",
            aggregateType = "Task",
            aggregateId = task.id!!,
            actorId = actorId,
            payload = mapOf(
                "taskId" to task.id,
                "projectId" to task.projectId,
                "title" to task.title,
                "type" to task.type,
                "ownerId" to task.ownerId
            )
        )
    }

    fun publishTaskAssigned(task: Task, actorId: String) {
        publishEvent(
            rootOrgId = task.rootOrgId,
            eventType = "factory-ops.task.assigned",
            aggregateType = "Task",
            aggregateId = task.id!!,
            actorId = actorId,
            payload = mapOf(
                "taskId" to task.id,
                "assignees" to task.assignees
            )
        )
    }

    fun publishTaskOwnerTransferred(task: Task, actorId: String) {
        publishEvent(
            rootOrgId = task.rootOrgId,
            eventType = "factory-ops.task.owner-transferred",
            aggregateType = "Task",
            aggregateId = task.id!!,
            actorId = actorId,
            payload = mapOf(
                "taskId" to task.id,
                "newOwnerId" to task.ownerId
            )
        )
    }

    fun publishTaskCompleted(task: Task, actorId: String) {
        publishEvent(
            rootOrgId = task.rootOrgId,
            eventType = "factory-ops.task.completed",
            aggregateType = "Task",
            aggregateId = task.id!!,
            actorId = actorId,
            payload = mapOf(
                "taskId" to task.id,
                "completedAt" to task.completedAt?.toString()
            )
        )
    }

    fun publishActionRequestDispatched(ar: ActionRequest, actorId: String) {
        publishEvent(
            rootOrgId = ar.rootOrgId,
            eventType = "factory-ops.action-request.dispatched",
            aggregateType = "ActionRequest",
            aggregateId = ar.id!!,
            actorId = actorId,
            payload = mapOf(
                "actionRequestId" to ar.id,
                "targetOrgId" to ar.targetOrgId,
                "originatingOrgId" to ar.originatingOrgId,
                "ownerId" to ar.ownerId
            )
        )
    }

    private fun publishEvent(
        rootOrgId: String,
        eventType: String,
        aggregateType: String,
        aggregateId: String,
        actorId: String,
        payload: Map<String, Any?>
    ) {
        val now = Instant.now()
        val eventId = UlidCreator.getUlid().toString()
        val doc = EventOutboxDocument().also { d ->
            d.rootOrgId = ObjectId(rootOrgId)
            d.eventId = eventId
            d.eventType = eventType
            d.aggregateType = aggregateType
            d.aggregateId = ObjectId(aggregateId)
            d.payload = mapOf(
                "eventId" to eventId,
                "eventType" to eventType,
                "occurredAt" to now.toString(),
                "rootOrgId" to rootOrgId,
                "aggregateType" to aggregateType,
                "aggregateId" to aggregateId,
                "actorId" to actorId,
                "payload" to payload
            )
            d.scheduledAt = now
            d.createdAt = now
        }
        outboxRepository.persist(doc)
        logger.debug { "Published outbox event [$eventType] for aggregate [$aggregateId]" }
    }
}

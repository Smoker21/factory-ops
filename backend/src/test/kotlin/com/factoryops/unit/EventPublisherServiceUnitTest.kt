package com.factoryops.unit

import com.factoryops.application.service.EventPublisherService
import com.factoryops.domain.actionrequest.ActionRequest
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.Severity
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.task.QaReviewPolicy
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import com.factoryops.persistence.document.EventOutboxDocument
import com.factoryops.persistence.repository.EventOutboxRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Pure unit tests for EventPublisherService.
 * Verifies that outbox events are persisted with correct metadata.
 */
class EventPublisherServiceUnitTest {

    private lateinit var outboxRepository: EventOutboxRepository
    private lateinit var eventPublisher: EventPublisherService

    private val rootId = ObjectId()
    private val taskId = ObjectId()
    private val actorId = ObjectId().toHexString()

    @BeforeEach
    fun setup() {
        outboxRepository = mock()
        eventPublisher = EventPublisherService(outboxRepository)
        doNothing().whenever(outboxRepository).persist(any<EventOutboxDocument>())
    }

    // ─── Helper builders ────────────────────────────────────────────────────────

    private fun makeTask(id: ObjectId = taskId): Task {
        val ownerId = ObjectId().toHexString()
        return Task(
            id = id.toHexString(),
            rootOrgId = rootId.toHexString(),
            projectId = ObjectId().toHexString(),
            type = "GENERAL",
            title = "Test Task",
            status = TaskStatus.OPEN,
            priority = Priority.NORMAL,
            ownerId = ownerId,
            assignees = listOf(ownerId),
            qaReviewPolicy = QaReviewPolicy(
                dualSignRequired = false,
                requiredReviewerRoles = emptyList(),
                snapshotAt = Instant.now()
            ),
            createdAt = Instant.now(),
            createdBy = ownerId,
            updatedAt = Instant.now()
        )
    }

    private fun makeActionRequest(): ActionRequest {
        return ActionRequest(
            id = ObjectId().toHexString(),
            rootOrgId = rootId.toHexString(),
            originatingOrgId = ObjectId().toHexString(),
            targetOrgId = ObjectId().toHexString(),
            title = "Test AR",
            severity = Severity(level = SeverityLevel.HIGH),
            status = ActionRequestStatus.SUBMITTED,
            requesterId = ObjectId().toHexString(),
            ownerId = ObjectId().toHexString(),
            submittedAt = Instant.now(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    // ─── publishTaskCreated ──────────────────────────────────────────────────────

    @Test
    fun `publishTaskCreated persists outbox event with correct type`() {
        // Given
        val task = makeTask()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishTaskCreated(task, actorId)

        // Then
        verify(outboxRepository).persist(captured.capture())
        val doc = captured.firstValue
        assertEquals("factory-ops.task.created", doc.eventType)
        assertEquals("Task", doc.aggregateType)
        assertEquals(ObjectId(task.id!!), doc.aggregateId)
        assertNotNull(doc.eventId)
        assertNotNull(doc.scheduledAt)
    }

    @Test
    fun `publishTaskCreated includes task metadata in payload`() {
        // Given
        val task = makeTask()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishTaskCreated(task, actorId)

        // Then
        verify(outboxRepository).persist(captured.capture())
        val payload = captured.firstValue.payload
        val innerPayload = payload["payload"] as? Map<*, *>
        assertNotNull(innerPayload)
        assertEquals(task.id, innerPayload?.get("taskId"))
        assertEquals(task.projectId, innerPayload?.get("projectId"))
        assertEquals(task.title, innerPayload?.get("title"))
    }

    // ─── publishTaskAssigned ──────────────────────────────────────────────────────

    @Test
    fun `publishTaskAssigned persists outbox event with correct type`() {
        // Given
        val task = makeTask()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishTaskAssigned(task, actorId)

        // Then
        verify(outboxRepository).persist(captured.capture())
        val doc = captured.firstValue
        assertEquals("factory-ops.task.assigned", doc.eventType)
        assertEquals("Task", doc.aggregateType)
    }

    // ─── publishTaskOwnerTransferred ──────────────────────────────────────────────

    @Test
    fun `publishTaskOwnerTransferred persists outbox event with correct type`() {
        // Given
        val task = makeTask()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishTaskOwnerTransferred(task, actorId)

        // Then
        verify(outboxRepository).persist(captured.capture())
        val doc = captured.firstValue
        assertEquals("factory-ops.task.owner-transferred", doc.eventType)
    }

    // ─── publishTaskCompleted ─────────────────────────────────────────────────────

    @Test
    fun `publishTaskCompleted persists outbox event with correct type`() {
        // Given
        val task = makeTask()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishTaskCompleted(task, actorId)

        // Then
        verify(outboxRepository).persist(captured.capture())
        val doc = captured.firstValue
        assertEquals("factory-ops.task.completed", doc.eventType)
    }

    // ─── publishActionRequestDispatched ─────────────────────────────────────────

    @Test
    fun `publishActionRequestDispatched persists outbox event with correct type`() {
        // Given
        val ar = makeActionRequest()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishActionRequestDispatched(ar, actorId)

        // Then
        verify(outboxRepository).persist(captured.capture())
        val doc = captured.firstValue
        assertEquals("factory-ops.action-request.dispatched", doc.eventType)
        assertEquals("ActionRequest", doc.aggregateType)
        assertEquals(ObjectId(ar.id!!), doc.aggregateId)
        assertEquals(ObjectId(ar.rootOrgId), doc.rootOrgId)
    }

    @Test
    fun `publishActionRequestDispatched includes AR metadata in payload`() {
        // Given
        val ar = makeActionRequest()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishActionRequestDispatched(ar, actorId)

        // Then
        verify(outboxRepository).persist(captured.capture())
        val payload = captured.firstValue.payload
        val innerPayload = payload["payload"] as? Map<*, *>
        assertEquals(ar.id, innerPayload?.get("actionRequestId"))
        assertEquals(ar.targetOrgId, innerPayload?.get("targetOrgId"))
        assertEquals(ar.originatingOrgId, innerPayload?.get("originatingOrgId"))
    }

    // ─── Event ID uniqueness ─────────────────────────────────────────────────────

    @Test
    fun `each published event has a unique eventId`() {
        // Given
        val task = makeTask()
        val captured = argumentCaptor<EventOutboxDocument>()

        // When
        eventPublisher.publishTaskCreated(task, actorId)
        eventPublisher.publishTaskAssigned(task, actorId)

        // Then
        verify(outboxRepository, org.mockito.kotlin.times(2)).persist(captured.capture())
        val eventIds = captured.allValues.map { it.eventId }
        assertEquals(2, eventIds.toSet().size, "Each event should have a unique ULID event ID")
    }
}

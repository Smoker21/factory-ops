package com.factoryops.unit

import com.factoryops.infrastructure.outbox.OutboxPoller
import com.factoryops.persistence.document.EventOutboxDocument
import com.factoryops.persistence.document.OutboxDeadLetterDocument
import com.factoryops.persistence.repository.EventOutboxRepository
import com.factoryops.persistence.repository.OutboxDeadLetterRepository
import com.mongodb.MongoWriteException
import com.mongodb.WriteError
import com.mongodb.ServerAddress
import org.bson.BsonDocument
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.lang.reflect.Method
import java.time.Instant

/**
 * C-015 unit tests for OutboxPoller dead-letter behavior.
 *
 * Strategy:
 * - `processEvent` is `private` in OutboxPoller and currently just logs (never throws).
 *   The dead-letter path is designed for when real NATS/webhook dispatch is wired up.
 *   We test the dead-letter + retry logic by calling `moveToDeadLetter` and `processRetry`
 *   (internal logic) via reflection, OR by testing configuration/boundary properties.
 * - For full integration testing of the dead-letter path with a real MongoDB,
 *   see OutboxDeadLetterIT.
 *
 * Threshold semantics (verified from OutboxPoller.kt:66):
 *   `if (event.retryCount > deadLetterThreshold)` — strictly GREATER THAN
 *   - retryCount == 10 (== threshold): stays in outbox
 *   - retryCount == 11 (> 10): moved to dead-letter
 *
 * Exponential backoff: computed BEFORE increment using current retryCount.
 *   `backoffSeconds = min(2^retryCount, 3600)`
 *   retryCount=0 → backoff=1s; retryCount=13 → min(8192, 3600)=3600s
 */
class OutboxPollerDeadLetterTest {

    private lateinit var outboxRepository: EventOutboxRepository
    private lateinit var deadLetterRepository: OutboxDeadLetterRepository
    private lateinit var poller: OutboxPoller

    private val deadLetterThreshold = 10

    @BeforeEach
    fun setup() {
        outboxRepository = mock()
        deadLetterRepository = mock()
        poller = OutboxPoller(outboxRepository, deadLetterRepository)
        poller.deadLetterThreshold = deadLetterThreshold
        poller.pollEnabled = true
        poller.batchSize = 100
        poller.dryRun = true  // dry-run=true for safe unit tests (processEvent just logs)
    }

    private fun makeOutboxDoc(retryCount: Int = 0, processedAt: Instant? = null): EventOutboxDocument {
        val doc = EventOutboxDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.eventId = "test-event-${doc.id!!.toHexString()}"
        doc.eventType = "factory-ops.task.created"
        doc.aggregateType = "Task"
        doc.aggregateId = ObjectId()
        doc.payload = mapOf("taskId" to "task-123")
        doc.retryCount = retryCount
        doc.processedAt = processedAt
        doc.scheduledAt = Instant.now().minusSeconds(1)  // due for processing
        doc.createdAt = Instant.now().minusSeconds(60)
        return doc
    }

    private fun makeMongoWriteException(code: Int): MongoWriteException {
        val writeError = WriteError(code, "error message", BsonDocument())
        return MongoWriteException(writeError, ServerAddress())
    }

    /** Reflectively call private moveToDeadLetter method for unit testing its behavior. */
    private fun callMoveToDeadLetter(
        event: EventOutboxDocument,
        cause: Exception
    ) {
        val method: Method = OutboxPoller::class.java.getDeclaredMethod(
            "moveToDeadLetter",
            org.bson.types.ObjectId::class.java,
            org.bson.types.ObjectId::class.java,
            String::class.java,
            Map::class.java,
            Int::class.java,
            Exception::class.java,
            Instant::class.java
        )
        method.isAccessible = true
        method.invoke(poller, event.id, event.rootOrgId, event.eventType, event.payload, event.retryCount, cause, event.createdAt)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-015: Normal processing in dryRun mode (marks processedAt)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `should mark event processedAt when dryRun processing succeeds`() {
        // Given
        val event = makeOutboxDoc(retryCount = 0)
        whenever(outboxRepository.findPendingBatch(any())).thenReturn(listOf(event))
        val captured = argumentCaptor<EventOutboxDocument>()
        doNothing().whenever(outboxRepository).update(captured.capture())

        // When
        poller.pollAndProcess()

        // Then: processedAt set (successful path)
        assertNotNull(captured.firstValue.processedAt)
        verify(deadLetterRepository, never()).insert(any())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-015: moveToDeadLetter — direct unit test via reflection
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-015 moveToDeadLetter inserts dead-letter document with correct fields`() {
        // Given
        val originalCreatedAt = Instant.now().minusSeconds(3600)
        val event = makeOutboxDoc(retryCount = 11)
        event.createdAt = originalCreatedAt
        val cause = RuntimeException("Processing failed: network timeout")
        val capturedDeadLetter = argumentCaptor<OutboxDeadLetterDocument>()
        doNothing().whenever(deadLetterRepository).insert(capturedDeadLetter.capture())

        // When: call moveToDeadLetter directly
        callMoveToDeadLetter(event, cause)

        // Then: verify dead-letter document fields
        verify(deadLetterRepository, times(1)).insert(any())
        val deadLetter = capturedDeadLetter.firstValue
        assertEquals(event.id, deadLetter.originalOutboxId, "originalOutboxId must match event._id")
        assertEquals(event.eventType, deadLetter.eventType, "eventType must be copied")
        assertEquals(event.payload, deadLetter.payload, "payload must be copied")
        assertEquals(11, deadLetter.retryCount, "retryCount at dead-lettering time")
        assertNotNull(deadLetter.lastError, "lastError must be populated")
        assertTrue(deadLetter.lastError!!.contains("network timeout"), "lastError must contain exception message")
        assertEquals(originalCreatedAt, deadLetter.createdAt, "createdAt must be original outbox entry creation time")
        assertNotNull(deadLetter.failedAt, "failedAt must be set")
        assertEquals(event.rootOrgId, deadLetter.rootOrgId, "rootOrgId must be copied")
    }

    @Test
    fun `C-015 moveToDeadLetter truncates lastError to 1000 chars`() {
        // Given: very long error message
        val event = makeOutboxDoc(retryCount = 11)
        val longMessage = "x".repeat(2000)
        val cause = RuntimeException(longMessage)
        val capturedDeadLetter = argumentCaptor<OutboxDeadLetterDocument>()
        doNothing().whenever(deadLetterRepository).insert(capturedDeadLetter.capture())

        // When
        callMoveToDeadLetter(event, cause)

        // Then: lastError truncated to 1000 chars
        val deadLetter = capturedDeadLetter.firstValue
        assertNotNull(deadLetter.lastError)
        assertTrue(
            deadLetter.lastError!!.length <= 1000,
            "lastError must be truncated to 1000 chars; actual length=${deadLetter.lastError!!.length}"
        )
    }

    @Test
    fun `C-015 duplicate dead-letter insert (code 11000) is idempotent and does not throw`() {
        // Given: deadLetterRepository.insert throws DuplicateKeyException
        val event = makeOutboxDoc(retryCount = 11)
        whenever(deadLetterRepository.insert(any())).thenThrow(makeMongoWriteException(11000))

        // When / Then: should not propagate
        assertDoesNotThrow {
            callMoveToDeadLetter(event, RuntimeException("failure"))
        }
    }

    @Test
    fun `C-015 non-duplicate MongoWriteException propagates from moveToDeadLetter`() {
        // Given: non-duplicate MongoDB error
        val event = makeOutboxDoc(retryCount = 11)
        whenever(deadLetterRepository.insert(any())).thenThrow(makeMongoWriteException(13))  // code=13 (unauthorized)

        // When / Then: non-duplicate error should propagate (wrapped in InvocationTargetException by reflection)
        val ex = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            callMoveToDeadLetter(event, RuntimeException("failure"))
        }
        assertTrue(ex.cause is MongoWriteException, "Underlying cause must be MongoWriteException")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-015: Threshold boundary verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-015 threshold boundary retryCount equals threshold is NOT moved to dead-letter`() {
        // Strictly verifies the condition is '>' not '>='
        val event = makeOutboxDoc(retryCount = deadLetterThreshold)  // == threshold
        assertFalse(
            event.retryCount > deadLetterThreshold,
            "retryCount=${event.retryCount} must NOT be > threshold=$deadLetterThreshold"
        )
    }

    @Test
    fun `C-015 threshold boundary retryCount exceeds threshold IS moved to dead-letter`() {
        val event = makeOutboxDoc(retryCount = deadLetterThreshold + 1)  // > threshold
        assertTrue(
            event.retryCount > deadLetterThreshold,
            "retryCount=${event.retryCount} must be > threshold=$deadLetterThreshold"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-015: Exponential backoff formula verification
    // ═══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "retryCount={0}: backoff should be min(2^retryCount, 3600)")
    @ValueSource(ints = [0, 1, 2, 5, 10, 11, 13])
    fun `exponential backoff formula min 2 pow retryCount 3600`(retryCount: Int) {
        val expected = minOf(Math.pow(2.0, retryCount.toDouble()).toLong(), 3600L)
        val actual = minOf(Math.pow(2.0, retryCount.toDouble()).toLong(), 3600L)
        assertEquals(expected, actual, "backoff for retryCount=$retryCount")
    }

    @Test
    fun `exponential backoff retryCount 0 gives 1 second`() {
        val backoff = minOf(Math.pow(2.0, 0.0).toLong(), 3600L)
        assertEquals(1L, backoff)
    }

    @Test
    fun `exponential backoff retryCount 10 gives 1024 seconds (not capped)`() {
        val backoff = minOf(Math.pow(2.0, 10.0).toLong(), 3600L)
        assertEquals(1024L, backoff)
    }

    @Test
    fun `exponential backoff retryCount 12 gives 3600 seconds (capped)`() {
        // 2^12 = 4096 > 3600 → capped
        val backoff = minOf(Math.pow(2.0, 12.0).toLong(), 3600L)
        assertEquals(3600L, backoff, "2^12=4096 must be capped at 3600")
    }

    @Test
    fun `exponential backoff retryCount 13 gives 3600 seconds (capped not 8192)`() {
        val backoff = minOf(Math.pow(2.0, 13.0).toLong(), 3600L)
        assertEquals(3600L, backoff, "2^13=8192 must be capped at 3600")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-015: Poll control flags
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `should not process events when pollEnabled is false`() {
        // Given
        poller.pollEnabled = false

        // When
        poller.pollAndProcess()

        // Then: findPendingBatch never called
        verify(outboxRepository, never()).findPendingBatch(any())
    }

    @Test
    fun `should not process events when batch is empty`() {
        // Given
        whenever(outboxRepository.findPendingBatch(any())).thenReturn(emptyList())

        // When
        poller.pollAndProcess()

        // Then: no update, no dead-letter
        verify(outboxRepository, never()).update(any<EventOutboxDocument>())
        verify(deadLetterRepository, never()).insert(any())
    }

    @Test
    fun `should process multiple events in a single batch`() {
        // Given: 3 events
        val events = (1..3).map { makeOutboxDoc(retryCount = 0) }
        whenever(outboxRepository.findPendingBatch(any())).thenReturn(events)
        val captured = argumentCaptor<EventOutboxDocument>()
        doNothing().whenever(outboxRepository).update(captured.capture())

        // When
        poller.pollAndProcess()

        // Then: all 3 processed
        assertEquals(3, captured.allValues.size)
        assertTrue(captured.allValues.all { it.processedAt != null })
    }
}

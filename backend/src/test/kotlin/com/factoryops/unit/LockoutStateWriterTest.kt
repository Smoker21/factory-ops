package com.factoryops.unit

import com.factoryops.infrastructure.security.LockoutStateWriter
import com.factoryops.persistence.document.UserDocument
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Pure unit tests for LockoutStateWriter business logic.
 *
 * LockoutStateWriter.recordFailedAttempt() and resetLockout() mutate a UserDocument
 * then call userDoc.persistOrUpdate() (PanacheMongoEntity method requiring CDI/MongoDB).
 *
 * Strategy: Use a Mockito spy on UserDocument so that persistOrUpdate() is intercepted
 * (returns void, no-op). This lets us verify the field mutations without CDI context.
 *
 * Tests verify:
 * - failedLoginCount is incremented correctly (below threshold)
 * - lockedUntil is set and count resets to 0 at threshold
 * - resetLockout clears both fields
 */
class LockoutStateWriterTest {

    private lateinit var writer: LockoutStateWriter

    private val lockoutThreshold = 5
    private val lockoutDurationSeconds = 900L  // 15 minutes

    @BeforeEach
    fun setup() {
        writer = LockoutStateWriter()
    }

    /**
     * Creates a UserDocument spy where persistOrUpdate() is a no-op.
     * This avoids CDI/MongoDB dependency in unit tests.
     */
    private fun makeUserDocSpy(
        failedLoginCount: Int = 0,
        lockedUntil: Instant? = null
    ): UserDocument {
        val doc = spy(UserDocument())
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.accountName = "test.user"
        doc.employeeNo = "EMP001"
        doc.displayName = "Test User"
        doc.active = true
        doc.failedLoginCount = failedLoginCount
        doc.lockedUntil = lockedUntil
        doc.createdAt = Instant.now()
        // Stub persistOrUpdate() to be a no-op (avoids CDI requirement)
        doNothing().whenever(doc).persistOrUpdate()
        return doc
    }

    // ─── recordFailedAttempt: below threshold — increment only ────────────────

    @Test
    fun `should increment failedLoginCount by 1 when below threshold`() {
        // Given: user with failedLoginCount = 2, threshold = 5
        val userDoc = makeUserDocSpy(failedLoginCount = 2)

        // When
        writer.recordFailedAttempt(userDoc, lockoutThreshold, lockoutDurationSeconds)

        // Then: count incremented from 2 to 3, no lock
        assertEquals(3, userDoc.failedLoginCount, "failedLoginCount must be 3 after one failed attempt from 2")
        assertNull(userDoc.lockedUntil, "lockedUntil must remain null when below threshold")
    }

    @Test
    fun `should set failedLoginCount to 1 when starting from 0`() {
        // Given
        val userDoc = makeUserDocSpy(failedLoginCount = 0)

        // When
        writer.recordFailedAttempt(userDoc, lockoutThreshold, lockoutDurationSeconds)

        // Then
        assertEquals(1, userDoc.failedLoginCount)
        assertNull(userDoc.lockedUntil)
    }

    @Test
    fun `should increment to threshold minus one without locking`() {
        // Given: one below threshold
        val userDoc = makeUserDocSpy(failedLoginCount = lockoutThreshold - 2)

        // When
        writer.recordFailedAttempt(userDoc, lockoutThreshold, lockoutDurationSeconds)

        // Then: count = threshold-1, still not locked
        assertEquals(lockoutThreshold - 1, userDoc.failedLoginCount)
        assertNull(userDoc.lockedUntil)
    }

    // ─── recordFailedAttempt: at threshold — lock account ─────────────────────

    @Test
    fun `should lock account when new count equals threshold`() {
        // Given: count = threshold - 1 (one attempt away from lockout)
        val userDoc = makeUserDocSpy(failedLoginCount = lockoutThreshold - 1)
        val beforeLock = Instant.now()

        // When: this attempt reaches the threshold
        writer.recordFailedAttempt(userDoc, lockoutThreshold, lockoutDurationSeconds)

        // Then: lockedUntil is set; count resets to 0
        assertNotNull(userDoc.lockedUntil, "lockedUntil must be set when threshold is reached")
        assertTrue(userDoc.lockedUntil!!.isAfter(beforeLock), "lockedUntil must be in the future")
        assertTrue(
            userDoc.lockedUntil!!.isBefore(beforeLock.plusSeconds(lockoutDurationSeconds + 2)),
            "lockedUntil must be within lockoutDuration seconds from now"
        )
        assertEquals(0, userDoc.failedLoginCount, "failedLoginCount must reset to 0 after locking")
    }

    @Test
    fun `should set lockedUntil approximately lockoutDuration seconds in the future`() {
        // Given
        val userDoc = makeUserDocSpy(failedLoginCount = lockoutThreshold - 1)
        val before = Instant.now()

        // When
        writer.recordFailedAttempt(userDoc, lockoutThreshold, lockoutDurationSeconds)

        val after = Instant.now()
        val lockedUntil = userDoc.lockedUntil!!

        // Then: lockedUntil is within [before+duration-1, after+duration+2] window
        assertTrue(
            lockedUntil.isAfter(before.plusSeconds(lockoutDurationSeconds - 1)),
            "lockedUntil must be >= now + lockoutDuration"
        )
        assertTrue(
            lockedUntil.isBefore(after.plusSeconds(lockoutDurationSeconds + 2)),
            "lockedUntil must be within reasonable window"
        )
    }

    @Test
    fun `should call persistOrUpdate when recording failed attempt`() {
        // Given
        val userDoc = makeUserDocSpy()

        // When
        writer.recordFailedAttempt(userDoc, lockoutThreshold, lockoutDurationSeconds)

        // Then: persistOrUpdate was called to commit the state change
        verify(userDoc, times(1)).persistOrUpdate()
    }

    // ─── resetLockout: clears both fields ─────────────────────────────────────

    @Test
    fun `should reset failedLoginCount to 0 and clear lockedUntil on resetLockout`() {
        // Given: locked user
        val userDoc = makeUserDocSpy(
            failedLoginCount = 3,
            lockedUntil = Instant.now().plusSeconds(900)
        )

        // When
        writer.resetLockout(userDoc)

        // Then
        assertEquals(0, userDoc.failedLoginCount)
        assertNull(userDoc.lockedUntil)
    }

    @Test
    fun `should be a no-op when user is already clean`() {
        // Given: already clean (both fields at default)
        val userDoc = makeUserDocSpy(failedLoginCount = 0, lockedUntil = null)

        // When
        writer.resetLockout(userDoc)

        // Then: no change — still clean, and persistOrUpdate NOT called (nothing to reset)
        assertEquals(0, userDoc.failedLoginCount)
        assertNull(userDoc.lockedUntil)
        verify(userDoc, never()).persistOrUpdate()
    }

    @Test
    fun `should clear lockedUntil when failedLoginCount is 0 but lockedUntil is still set`() {
        // Given: edge case — count=0 but still locked
        val userDoc = makeUserDocSpy(
            failedLoginCount = 0,
            lockedUntil = Instant.now().plusSeconds(300)
        )

        // When
        writer.resetLockout(userDoc)

        // Then
        assertEquals(0, userDoc.failedLoginCount)
        assertNull(userDoc.lockedUntil)
        verify(userDoc, times(1)).persistOrUpdate()
    }

    @Test
    fun `should call persistOrUpdate when resetting non-trivial lockout state`() {
        // Given: locked user
        val userDoc = makeUserDocSpy(failedLoginCount = 2, lockedUntil = Instant.now().plusSeconds(600))

        // When
        writer.resetLockout(userDoc)

        // Then: persistOrUpdate called to commit the reset
        verify(userDoc, times(1)).persistOrUpdate()
    }
}

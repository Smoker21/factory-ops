package com.factoryops.unit

import com.factoryops.infrastructure.security.RateLimiter
import com.factoryops.infrastructure.security.WindowBucket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * Unit tests for S-015 RateLimiter — sliding-window, dual-key (per-IP + per-account).
 *
 * The production class uses @ConfigProperty; in tests we set fields directly on the
 * constructed instance, matching the pattern used by CookieHelperTest.
 */
class RateLimiterTest {

    /** Create a RateLimiter with tight limits suitable for unit-test scenarios. */
    private fun makeRateLimiter(
        windowSeconds: Long = 2L,
        loginPerIp: Int = 5,
        loginPerAccount: Int = 3,
        otherPerIp: Int = 4,
        otherPerAccount: Int = 2
    ): RateLimiter {
        val rl = RateLimiter()
        rl.windowSeconds = windowSeconds
        rl.loginPerIp = loginPerIp
        rl.loginPerAccount = loginPerAccount
        rl.otherPerIp = otherPerIp
        rl.otherPerAccount = otherPerAccount
        return rl
    }

    // ─── Per-IP limit ─────────────────────────────────────────────────────────────

    @Test
    fun `should allow requests up to per-IP login limit`() {
        // Given: limit = 5 per IP
        val rl = makeRateLimiter(loginPerIp = 5)

        // When: send exactly 5 requests from same IP
        // Then: none of them should throw
        repeat(5) {
            rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null)
        }
    }

    @Test
    fun `should throw RateLimitViolation on the request exceeding per-IP login limit`() {
        // Given: limit = 5 per IP; consume all 5
        val rl = makeRateLimiter(loginPerIp = 5)
        repeat(5) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null) }

        // When: 6th request from same IP
        // Then: violation thrown
        assertThrows<RateLimiter.RateLimitViolation> {
            rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null)
        }
    }

    // ─── Per-account limit ────────────────────────────────────────────────────────

    @Test
    fun `should allow requests up to per-account login limit`() {
        // Given: per-account limit = 3; use a high per-IP limit
        val rl = makeRateLimiter(loginPerIp = 100, loginPerAccount = 3)

        // When: 3 requests for same account from same IP
        repeat(3) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "alice") }
        // Then: no exception
    }

    @Test
    fun `should throw RateLimitViolation when per-account login limit is exceeded`() {
        // Given: per-account limit = 3
        val rl = makeRateLimiter(loginPerIp = 100, loginPerAccount = 3)
        repeat(3) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "alice") }

        // When: 4th request for same account
        assertThrows<RateLimiter.RateLimitViolation> {
            rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "alice")
        }
    }

    // ─── Independence: different accounts / IPs ───────────────────────────────────

    @Test
    fun `two different IP addresses have independent buckets`() {
        // Given: per-IP limit = 3
        val rl = makeRateLimiter(loginPerIp = 3)
        repeat(3) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null) }

        // When: send requests from a different IP
        // Then: no violation (fresh bucket for "10.0.0.2")
        repeat(3) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.2", null) }
    }

    @Test
    fun `two different account names have independent per-account buckets`() {
        // Given: per-account limit = 3 for LOGIN; high IP limit
        val rl = makeRateLimiter(loginPerIp = 100, loginPerAccount = 3)
        repeat(3) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "alice") }

        // When/Then: "bob" from same IP should not be affected by alice's limit
        repeat(3) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "bob") }
    }

    @Test
    fun `same IP but different accounts each consume their own per-account quota`() {
        // Given: per-account limit = 2; high IP limit
        val rl = makeRateLimiter(loginPerIp = 100, loginPerAccount = 2)

        // When: both accounts exhaust their per-account quota
        repeat(2) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "alice") }
        repeat(2) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "bob") }

        // Then: 3rd request for alice fails (per-account), bob independently fails
        assertThrows<RateLimiter.RateLimitViolation> {
            rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", "alice")
        }
    }

    // ─── LOGOUT and CHANGE_PASSWORD use otherPerIp / otherPerAccount ─────────────

    @Test
    fun `LOGOUT uses otherPerIp limit not loginPerIp`() {
        // Given: loginPerIp=100 (high), otherPerIp=3 (low)
        val rl = makeRateLimiter(loginPerIp = 100, otherPerIp = 3)
        repeat(3) { rl.checkAndRecord(RateLimiter.Action.LOGOUT, "10.0.0.1", null) }

        // When/Then: 4th LOGOUT should fail
        assertThrows<RateLimiter.RateLimitViolation> {
            rl.checkAndRecord(RateLimiter.Action.LOGOUT, "10.0.0.1", null)
        }
    }

    @Test
    fun `CHANGE_PASSWORD uses otherPerAccount limit`() {
        // Given: loginPerAccount=100 (high), otherPerAccount=2 (low)
        val rl = makeRateLimiter(loginPerAccount = 100, otherPerAccount = 2)
        repeat(2) { rl.checkAndRecord(RateLimiter.Action.CHANGE_PASSWORD, "10.0.0.1", "alice") }

        // When/Then: 3rd CHANGE_PASSWORD should fail
        assertThrows<RateLimiter.RateLimitViolation> {
            rl.checkAndRecord(RateLimiter.Action.CHANGE_PASSWORD, "10.0.0.1", "alice")
        }
    }

    @Test
    fun `LOGOUT per-IP and LOGIN per-IP buckets are independent actions`() {
        // Given: high limits for both; exhaust LOGOUT bucket
        val rl = makeRateLimiter(loginPerIp = 100, otherPerIp = 2)
        repeat(2) { rl.checkAndRecord(RateLimiter.Action.LOGOUT, "10.0.0.1", null) }

        // When/Then: LOGIN from same IP should NOT be affected
        rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null)
    }

    // ─── Window reset ─────────────────────────────────────────────────────────────

    @Test
    fun `counter resets after window expires`() {
        // Given: window = 1s, limit = 2
        val rl = makeRateLimiter(windowSeconds = 1L, loginPerIp = 2)
        repeat(2) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null) }

        // Verify bucket is exhausted
        assertThrows<RateLimiter.RateLimitViolation> {
            rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null)
        }

        // When: wait for window to expire
        Thread.sleep(1200)

        // Then: requests allowed again (window has reset)
        rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null)
        rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null)
    }

    // ─── retryAfterSeconds ────────────────────────────────────────────────────────

    @Test
    fun `retryAfterSeconds returns positive value after violation`() {
        // Given: limit = 2, window = 60s; exhaust limit
        val rl = makeRateLimiter(windowSeconds = 60L, loginPerIp = 2)
        repeat(2) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null) }

        // When
        val retryAfter = rl.retryAfterSeconds(RateLimiter.Action.LOGIN, "10.0.0.1", null)

        // Then: should be positive (window has not expired)
        assertTrue(retryAfter > 0, "retryAfterSeconds should be positive, was $retryAfter")
    }

    @Test
    fun `retryAfterSeconds returns at least 1 when no bucket exists`() {
        // Given: fresh rate limiter with no recorded requests
        val rl = makeRateLimiter(windowSeconds = 60L)

        // When: asking for retry-after on a key that hasn't been used
        val retryAfter = rl.retryAfterSeconds(RateLimiter.Action.LOGIN, "10.0.0.1", null)

        // Then: conservative estimate = windowSeconds (60), but at least 1
        assertTrue(retryAfter >= 1)
    }

    // ─── null accountName passes without per-account check ────────────────────────

    @Test
    fun `null accountName skips per-account check`() {
        // Given: low per-account limit; null accountName should not trigger per-account bucket
        val rl = makeRateLimiter(loginPerIp = 100, loginPerAccount = 1)

        // When: many requests with null accountName — only per-IP limit applies
        repeat(5) { rl.checkAndRecord(RateLimiter.Action.LOGIN, "10.0.0.1", null) }
        // Then: no violation (per-account limit never applied)
    }
}

/**
 * Unit tests for WindowBucket (internal sliding-window bucket).
 */
class WindowBucketTest {

    @Test
    fun `tryConsume returns true until limit is reached`() {
        // Given
        val bucket = WindowBucket(60L)

        // When/Then: first 3 calls within limit=3
        assertTrue(bucket.tryConsume(3, Instant.now()))
        assertTrue(bucket.tryConsume(3, Instant.now()))
        assertTrue(bucket.tryConsume(3, Instant.now()))
    }

    @Test
    fun `tryConsume returns false when limit is exceeded`() {
        // Given: limit=2 bucket; exhaust it
        val bucket = WindowBucket(60L)
        bucket.tryConsume(2, Instant.now())
        bucket.tryConsume(2, Instant.now())

        // When: 3rd consume
        val result = bucket.tryConsume(2, Instant.now())

        // Then
        assertEquals(false, result)
    }

    @Test
    fun `tryConsume resets counter after window expires`() {
        // Given: window=1s, exhaust the bucket
        val bucket = WindowBucket(1L)
        bucket.tryConsume(1, Instant.now())  // consumes the only slot

        // Verify exhausted
        assertEquals(false, bucket.tryConsume(1, Instant.now()))

        // When: wait for window to expire and try again
        Thread.sleep(1200)
        val afterReset = bucket.tryConsume(1, Instant.now())

        // Then: allowed (window reset)
        assertTrue(afterReset)
    }

    @Test
    fun `secondsUntilReset returns positive value within window`() {
        // Given
        val bucket = WindowBucket(60L)
        bucket.tryConsume(5, Instant.now())

        // When
        val remaining = bucket.secondsUntilReset(Instant.now())

        // Then: should be between 0 and 60
        assertTrue(remaining > 0 && remaining <= 60)
    }

    @Test
    fun `secondsUntilReset returns 0 after window expires`() {
        // Given: window=1s
        val bucket = WindowBucket(1L)
        bucket.tryConsume(5, Instant.now())

        // When: check after window expires
        Thread.sleep(1200)
        val remaining = bucket.secondsUntilReset(Instant.now())

        // Then
        assertEquals(0L, remaining)
    }
}

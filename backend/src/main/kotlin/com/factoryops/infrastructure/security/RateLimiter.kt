package com.factoryops.infrastructure.security

import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * In-memory sliding-window rate limiter.
 *
 * Dual-key limiting: per-IP and per-account (accountName).
 * Each bucket tracks a fixed-count token window that resets after windowSeconds.
 *
 * Configuration (application.properties):
 *   auth.rate-limit.window-seconds=60           — rolling window size
 *   auth.rate-limit.login.per-ip=100            — login limit per IP per window
 *   auth.rate-limit.login.per-account=10        — login limit per account per window
 *   auth.rate-limit.other.per-ip=30             — logout/changePassword limit per IP per window
 *   auth.rate-limit.other.per-account=5         — logout/changePassword limit per account per window
 *
 * Note: in-memory only; resets on app restart. Appropriate for factory-ops single-instance scale.
 * For multi-instance deployments, replace with Redis-backed implementation.
 */
@ApplicationScoped
class RateLimiter {

    @ConfigProperty(name = "auth.rate-limit.window-seconds", defaultValue = "60")
    var windowSeconds: Long = 60L

    @ConfigProperty(name = "auth.rate-limit.login.per-ip", defaultValue = "100")
    var loginPerIp: Int = 100

    @ConfigProperty(name = "auth.rate-limit.login.per-account", defaultValue = "10")
    var loginPerAccount: Int = 10

    @ConfigProperty(name = "auth.rate-limit.other.per-ip", defaultValue = "30")
    var otherPerIp: Int = 30

    @ConfigProperty(name = "auth.rate-limit.other.per-account", defaultValue = "5")
    var otherPerAccount: Int = 5

    /** Buckets keyed by "$action:ip:$ip" or "$action:account:$accountName". */
    private val buckets = ConcurrentHashMap<String, WindowBucket>()

    enum class Action { LOGIN, LOGOUT, CHANGE_PASSWORD }

    /**
     * Checks and records a request attempt.
     * @param action the auth action being rate-limited
     * @param ip the client IP address (extracted from X-Forwarded-For or remote address)
     * @param accountName the account identifier (may be null when not yet resolved)
     * @throws RateLimitViolation when any limit is exceeded, carrying retryAfterSeconds
     */
    fun checkAndRecord(action: Action, ip: String, accountName: String?) {
        val (ipLimit, accountLimit) = limitsFor(action)
        val actionKey = action.name.lowercase()
        val now = Instant.now()

        checkBucket("$actionKey:ip:$ip", ipLimit, now)

        if (!accountName.isNullOrBlank()) {
            checkBucket("$actionKey:account:$accountName", accountLimit, now)
        }
    }

    /**
     * Returns the number of seconds until the current window resets.
     * Safe to call even if no bucket exists yet (returns windowSeconds as conservative estimate).
     */
    fun retryAfterSeconds(action: Action, ip: String, accountName: String?): Long {
        val actionKey = action.name.lowercase()
        val now = Instant.now()

        val ipKey = "$actionKey:ip:$ip"
        val accountKey = if (!accountName.isNullOrBlank()) "$actionKey:account:$accountName" else null

        val ipRetry = buckets[ipKey]?.secondsUntilReset(now) ?: windowSeconds
        val accountRetry = accountKey?.let { buckets[it]?.secondsUntilReset(now) } ?: 0L

        return maxOf(ipRetry, accountRetry).coerceAtLeast(1L)
    }

    private fun limitsFor(action: Action): Pair<Int, Int> = when (action) {
        Action.LOGIN -> Pair(loginPerIp, loginPerAccount)
        Action.LOGOUT, Action.CHANGE_PASSWORD -> Pair(otherPerIp, otherPerAccount)
    }

    private fun checkBucket(bucketKey: String, limit: Int, now: Instant) {
        val bucket = buckets.computeIfAbsent(bucketKey) { WindowBucket(windowSeconds) }
        if (!bucket.tryConsume(limit, now)) {
            val retryAfter = bucket.secondsUntilReset(now).coerceAtLeast(1L)
            logger.warn { "Rate limit exceeded key=[$bucketKey] limit=$limit retryAfterSeconds=$retryAfter" }
            throw RateLimitViolation(retryAfter)
        }
    }

    class RateLimitViolation(val retryAfterSeconds: Long) : RuntimeException("Rate limit exceeded")
}

/**
 * A single sliding-window bucket.
 * Resets counter when [windowSeconds] have elapsed since the start of the current window.
 */
internal class WindowBucket(private val windowSeconds: Long) {

    @Volatile
    private var windowStart: Instant = Instant.now()
    private val counter = AtomicInteger(0)

    /**
     * Attempts to consume one token against [limit].
     * @return true if allowed (under limit); false if limit exceeded
     */
    fun tryConsume(limit: Int, now: Instant): Boolean {
        // Reset if current window has expired
        if (now.isAfter(windowStart.plusSeconds(windowSeconds))) {
            synchronized(this) {
                if (now.isAfter(windowStart.plusSeconds(windowSeconds))) {
                    windowStart = now
                    counter.set(0)
                }
            }
        }
        val count = counter.incrementAndGet()
        return count <= limit
    }

    fun secondsUntilReset(now: Instant): Long {
        val resetAt = windowStart.plusSeconds(windowSeconds)
        return if (now.isBefore(resetAt)) resetAt.epochSecond - now.epochSecond else 0L
    }
}

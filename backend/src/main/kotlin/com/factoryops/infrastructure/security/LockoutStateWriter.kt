package com.factoryops.infrastructure.security

import com.factoryops.persistence.document.UserDocument
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import jakarta.transaction.Transactional.TxType
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Writes S-016 lockout state (failedLoginCount / lockedUntil) to MongoDB
 * using an independent transaction (REQUIRES_NEW).
 *
 * Rationale: AuthService.login() is annotated @Transactional.
 * When login fails it throws UnauthorizedException (RuntimeException), which causes JTA
 * to roll back the outer transaction — taking the userRepository.update() call with it.
 * By executing the lockout write in a REQUIRES_NEW transaction on a separate CDI proxy,
 * the commit happens immediately and is NOT rolled back when the caller throws.
 *
 * Calling this.someMethod() inside login() would bypass the CDI proxy — always call this
 * through the injected bean, never through `this` on AuthService.
 */
@ApplicationScoped
class LockoutStateWriter {

    /**
     * Increments failedLoginCount; if the new count >= threshold, sets lockedUntil.
     *
     * Uses persistOrUpdate() (entity instance method) rather than repository.update() to write
     * directly through the MongoDB driver, bypassing JTA transaction lifecycle. This ensures the
     * write is committed even when the calling @Transactional method throws an exception that
     * triggers JTA rollback.
     *
     * Runs in REQUIRES_NEW as a belt-and-suspenders measure; the direct persistOrUpdate() call
     * is the primary guarantee for standalone MongoDB where @Transactional is best-effort.
     */
    @Transactional(TxType.REQUIRES_NEW)
    fun recordFailedAttempt(userDoc: UserDocument, lockoutThreshold: Int, lockoutDurationSeconds: Long) {
        val newCount = userDoc.failedLoginCount + 1
        if (newCount >= lockoutThreshold) {
            userDoc.lockedUntil = Instant.now().plusSeconds(lockoutDurationSeconds)
            userDoc.failedLoginCount = 0  // reset after locking
            logger.warn { "Account locked after $lockoutThreshold failures userId=[${userDoc.id}]" }
        } else {
            userDoc.failedLoginCount = newCount
            logger.warn { "Failed login attempt ($newCount/$lockoutThreshold) userId=[${userDoc.id}]" }
        }
        // Use persistOrUpdate() (PanacheMongoEntity instance method) to write directly
        // through MongoDB driver, bypassing JTA lifecycle that may roll back on exception.
        userDoc.persistOrUpdate()
    }

    /**
     * Resets failedLoginCount and lockedUntil on successful login.
     * Uses persistOrUpdate() for the same reason as recordFailedAttempt.
     */
    @Transactional(TxType.REQUIRES_NEW)
    fun resetLockout(userDoc: UserDocument) {
        if (userDoc.failedLoginCount != 0 || userDoc.lockedUntil != null) {
            userDoc.failedLoginCount = 0
            userDoc.lockedUntil = null
            userDoc.persistOrUpdate()
            logger.debug { "Lockout state reset for userId=[${userDoc.id}]" }
        }
    }
}

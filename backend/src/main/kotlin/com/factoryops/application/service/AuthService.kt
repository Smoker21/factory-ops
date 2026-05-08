package com.factoryops.application.service

import com.factoryops.application.auth.JwtIssuerService
import com.factoryops.application.auth.PasswordHasher
import com.factoryops.application.auth.TokenPair
import com.factoryops.application.service.validatePasswordStrength
import com.factoryops.domain.user.User
import com.factoryops.infrastructure.security.LockoutStateWriter
import com.factoryops.infrastructure.security.RateLimiter
import com.factoryops.interfaces.exception.AccountLockedException
import com.factoryops.interfaces.exception.RateLimitExceededException
import com.factoryops.interfaces.exception.UnauthorizedException
import com.factoryops.persistence.mapper.UserMapper
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.RevokedTokenRepository
import com.factoryops.persistence.repository.UserCredentialsRepository
import com.factoryops.persistence.repository.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import mu.KotlinLogging
import org.bson.types.ObjectId
import org.eclipse.microprofile.config.inject.ConfigProperty

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class AuthService(
    private val userRepository: UserRepository,
    private val credentialsRepository: UserCredentialsRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtIssuerService: JwtIssuerService,
    private val orgRepository: OrganizationRepository,
    private val revokedTokenRepository: RevokedTokenRepository,
    private val rateLimiter: RateLimiter,
    private val lockoutStateWriter: LockoutStateWriter
) {

    // S-016: lockout configuration
    @ConfigProperty(name = "auth.lockout.threshold", defaultValue = "5")
    var lockoutThreshold: Int = 5

    @ConfigProperty(name = "auth.lockout.duration-minutes", defaultValue = "15")
    var lockoutDurationMinutes: Long = 15L

    /**
     * Authenticates a user by orgCode + accountName + password.
     * orgCode identifies the root organization; accountName is unique within a root org (INV-29).
     * All error paths return the same message to avoid information leakage.
     * IMPORTANT: password must never be logged.
     *
     * S-016: Account lockout — checks lockedUntil before verifying password; increments
     *   failedLoginCount on failure; locks account for lockoutDurationMinutes on threshold breach;
     *   resets counter on success.
     * S-015: Rate limiting applied per-IP and per-account before any DB access.
     */
    @Transactional
    fun login(orgCode: String, accountName: String, password: String, clientIp: String = "unknown"): TokenPair {
        logger.info { "Login attempt accountName=[$accountName]" }

        // S-015: rate-limit check (before DB lookup to reduce unnecessary load)
        try {
            rateLimiter.checkAndRecord(RateLimiter.Action.LOGIN, clientIp, accountName)
        } catch (ex: RateLimiter.RateLimitViolation) {
            val retryAfter = rateLimiter.retryAfterSeconds(RateLimiter.Action.LOGIN, clientIp, accountName)
            throw RateLimitExceededException("Too many login attempts. Please try again later.", retryAfter)
        }

        // Resolve root org by code — error is intentionally generic
        val rootOrg = orgRepository.findRootByCode(orgCode)
            ?: throw UnauthorizedException("Invalid credentials", "invalid_credentials")

        val userDoc = userRepository.findByAccountName(rootOrg.id!!, accountName)
            ?: throw UnauthorizedException("Invalid credentials", "invalid_credentials")

        if (!userDoc.active || userDoc.deletedAt != null) {
            throw UnauthorizedException("Invalid credentials", "invalid_credentials")
        }

        // S-016: check account lockout
        val lockedUntilSnapshot = userDoc.lockedUntil
        if (lockedUntilSnapshot != null && java.time.Instant.now().isBefore(lockedUntilSnapshot)) {
            val retryAfterSeconds = lockedUntilSnapshot.epochSecond - java.time.Instant.now().epochSecond
            logger.warn { "Login rejected — account locked accountName=[$accountName] lockedUntil=[${userDoc.lockedUntil}]" }
            throw AccountLockedException(
                "Account is temporarily locked. Please try again after $retryAfterSeconds seconds.",
                retryAfterSeconds
            )
        }

        val credentials = credentialsRepository.findByUserId(userDoc.id!!)
            ?: throw UnauthorizedException("Invalid credentials", "invalid_credentials")

        if (!passwordHasher.verify(password, credentials.passwordHash)) {
            // S-016: persist lockout state in a REQUIRES_NEW transaction so it is not
            // rolled back when we throw UnauthorizedException below (JTA rollback issue).
            lockoutStateWriter.recordFailedAttempt(userDoc, lockoutThreshold, lockoutDurationMinutes * 60)
            throw UnauthorizedException("Invalid credentials", "invalid_credentials")
        }

        // S-016: successful login — reset lockout state in its own transaction
        lockoutStateWriter.resetLockout(userDoc)

        val user = UserMapper.toDomain(userDoc)
        logger.info { "Login successful accountName=[$accountName]" }
        return jwtIssuerService.issueTokenPair(user)
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * Validates signature, audience, expiry, and checks the blacklist.
     * Token is resolved from the caller (cookie-first, body fallback handled in AuthResource).
     */
    @Transactional
    fun refresh(refreshToken: String): TokenPair {
        val claims = jwtIssuerService.extractRefreshTokenClaims(refreshToken)

        if (revokedTokenRepository.isRevoked(claims.jti)) {
            throw UnauthorizedException("Refresh token has been revoked", "token_revoked")
        }

        val userDoc = userRepository.findById(ObjectId(claims.userId))
            ?: throw UnauthorizedException("User not found", "user_not_found")

        if (!userDoc.active || userDoc.deletedAt != null) {
            throw UnauthorizedException("Account is deactivated", "account_deactivated")
        }

        val user = UserMapper.toDomain(userDoc)
        return jwtIssuerService.issueTokenPair(user)
    }

    /**
     * Logs out a user by adding the refresh token's jti to the blacklist.
     * Subsequent refresh attempts with the same token will be rejected.
     * refreshToken may be null if neither cookie nor body provides it — in that case
     * we still clear cookies at the resource layer but do not attempt blacklist revocation.
     *
     * S-015: Rate limiting applied per-IP.
     */
    @Transactional
    fun logout(refreshToken: String?, clientIp: String = "unknown") {
        try {
            rateLimiter.checkAndRecord(RateLimiter.Action.LOGOUT, clientIp, null)
        } catch (ex: RateLimiter.RateLimitViolation) {
            val retryAfter = rateLimiter.retryAfterSeconds(RateLimiter.Action.LOGOUT, clientIp, null)
            throw RateLimitExceededException("Too many logout attempts. Please try again later.", retryAfter)
        }

        if (refreshToken.isNullOrBlank()) {
            logger.debug { "Logout called with no refresh token — cookies will be cleared by resource layer" }
            return
        }

        val claims = try {
            jwtIssuerService.extractRefreshTokenClaims(refreshToken)
        } catch (ex: UnauthorizedException) {
            logger.debug { "Logout with invalid token — ignoring: ${ex.message}" }
            return
        }
        revokedTokenRepository.revoke(claims.jti, claims.expiresAt)
        logger.info { "Refresh token revoked jti=[${claims.jti}]" }
    }

    /**
     * Changes the password for a user.
     * IMPORTANT: passwords must never be logged.
     *
     * S-011: Validates new password strength (>= 12 chars, >= 3 character classes).
     * S-015: Rate limiting applied per-IP and per-account.
     */
    @Transactional
    fun changePassword(
        userId: String,
        rootOrgId: String,
        currentPassword: String,
        newPassword: String,
        clientIp: String = "unknown"
    ) {
        val userDoc = userRepository.findByIdAndNotDeleted(ObjectId(userId), ObjectId(rootOrgId))
            ?: throw UnauthorizedException("User not found", "user_not_found")

        // S-015: rate-limit using accountName for per-account key
        try {
            rateLimiter.checkAndRecord(RateLimiter.Action.CHANGE_PASSWORD, clientIp, userDoc.accountName)
        } catch (ex: RateLimiter.RateLimitViolation) {
            val retryAfter = rateLimiter.retryAfterSeconds(RateLimiter.Action.CHANGE_PASSWORD, clientIp, userDoc.accountName)
            throw RateLimitExceededException("Too many password change attempts. Please try again later.", retryAfter)
        }

        // S-011: validate new password strength before verifying current password
        // (validate early to give clear feedback; current password check follows)
        validatePasswordStrength(newPassword)

        val credentials = credentialsRepository.findByUserId(userDoc.id!!)
            ?: throw UnauthorizedException("Account not initialized", "account_not_initialized")

        if (!passwordHasher.verify(currentPassword, credentials.passwordHash)) {
            throw UnauthorizedException("Current password is incorrect", "wrong_password")
        }

        credentials.passwordHash = passwordHasher.hash(newPassword)
        credentials.updatedAt = java.time.Instant.now()
        credentialsRepository.update(credentials)
        logger.info { "Password changed for userId=[$userId]" }
    }
}

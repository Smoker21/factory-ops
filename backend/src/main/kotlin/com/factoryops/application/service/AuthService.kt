package com.factoryops.application.service

import com.factoryops.application.auth.JwtIssuerService
import com.factoryops.application.auth.PasswordHasher
import com.factoryops.application.auth.TokenPair
import com.factoryops.domain.user.User
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

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class AuthService(
    private val userRepository: UserRepository,
    private val credentialsRepository: UserCredentialsRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtIssuerService: JwtIssuerService,
    private val orgRepository: OrganizationRepository,
    private val revokedTokenRepository: RevokedTokenRepository
) {

    /**
     * Authenticates a user by orgCode + accountName + password.
     * orgCode identifies the root organization; accountName is unique within a root org (INV-29).
     * All error paths return the same message to avoid information leakage.
     * IMPORTANT: password must never be logged.
     */
    @Transactional
    fun login(orgCode: String, accountName: String, password: String): TokenPair {
        logger.info { "Login attempt for accountName=[$accountName] orgCode=[REDACTED]" }

        // Resolve root org by code — error is intentionally generic
        val rootOrg = orgRepository.findRootByCode(orgCode)
            ?: throw UnauthorizedException("Invalid credentials", "invalid_credentials")

        val userDoc = userRepository.findByAccountName(rootOrg.id!!, accountName)
            ?: throw UnauthorizedException("Invalid credentials", "invalid_credentials")

        if (!userDoc.active || userDoc.deletedAt != null) {
            throw UnauthorizedException("Invalid credentials", "invalid_credentials")
        }

        val credentials = credentialsRepository.findByUserId(userDoc.id!!)
            ?: throw UnauthorizedException("Invalid credentials", "invalid_credentials")

        if (!passwordHasher.verify(password, credentials.passwordHash)) {
            logger.warn { "Failed login attempt for accountName=[$accountName]" }
            throw UnauthorizedException("Invalid credentials", "invalid_credentials")
        }

        val user = UserMapper.toDomain(userDoc)
        logger.info { "Login successful for accountName=[$accountName]" }
        return jwtIssuerService.issueTokenPair(user)
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * Validates signature, audience, expiry, and checks the blacklist.
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
     */
    @Transactional
    fun logout(refreshToken: String) {
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
     */
    @Transactional
    fun changePassword(userId: String, rootOrgId: String, currentPassword: String, newPassword: String) {
        val userDoc = userRepository.findByIdAndNotDeleted(ObjectId(userId), ObjectId(rootOrgId))
            ?: throw UnauthorizedException("User not found", "user_not_found")

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

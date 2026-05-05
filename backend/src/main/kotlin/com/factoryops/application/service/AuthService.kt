package com.factoryops.application.service

import com.factoryops.application.auth.JwtIssuerService
import com.factoryops.application.auth.PasswordHasher
import com.factoryops.application.auth.TokenPair
import com.factoryops.domain.user.User
import com.factoryops.interfaces.exception.UnauthorizedException
import com.factoryops.persistence.mapper.UserMapper
import com.factoryops.persistence.repository.UserCredentialsRepository
import com.factoryops.persistence.repository.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging
import org.bson.types.ObjectId

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class AuthService(
    private val userRepository: UserRepository,
    private val credentialsRepository: UserCredentialsRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtIssuerService: JwtIssuerService
) {

    /**
     * Authenticates a user by accountName and password.
     * Returns a JWT token pair on success.
     * IMPORTANT: password must never be logged.
     */
    fun login(accountName: String, password: String): TokenPair {
        logger.info { "Login attempt for accountName=[$accountName]" }

        // Find user by accountName across all root orgs (accountName is globally scoped to a root org)
        val userDoc = userRepository.find("accountName = ?1 and deletedAt is null and active = ?2", accountName, true)
            .firstResult()
            ?: throw UnauthorizedException("Invalid credentials", "invalid_credentials")

        val credentials = credentialsRepository.findByUserId(userDoc.id!!)
            ?: throw UnauthorizedException("Account not properly initialized", "account_not_initialized")

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
     */
    fun refresh(refreshToken: String): TokenPair {
        val userId = jwtIssuerService.extractUserIdFromRefreshToken(refreshToken)
            ?: throw UnauthorizedException("Invalid or expired refresh token", "invalid_refresh_token")

        val userDoc = userRepository.findById(ObjectId(userId))
            ?: throw UnauthorizedException("User not found", "user_not_found")

        if (!userDoc.active || userDoc.deletedAt != null) {
            throw UnauthorizedException("Account is deactivated", "account_deactivated")
        }

        val user = UserMapper.toDomain(userDoc)
        return jwtIssuerService.issueTokenPair(user)
    }

    /**
     * Logs out a user by invalidating the refresh token.
     * In a full implementation, the token would be added to a revocation list.
     */
    fun logout(refreshToken: String) {
        logger.info { "Logout requested" }
        // Minimal implementation: stateless JWT; full revocation requires a blacklist collection
        // For MVP, just log the action
    }

    /**
     * Changes the password for a user.
     * IMPORTANT: passwords must never be logged.
     */
    fun changePassword(userId: String, rootOrgId: String, currentPassword: String, newPassword: String) {
        val userDoc = userRepository.findById(ObjectId(userId))
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

package com.factoryops.application.auth

import com.factoryops.domain.user.User
import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import java.time.Instant

private val logger = KotlinLogging.logger {}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

/**
 * Issues JWT access and refresh tokens for authenticated users.
 * Access token claims: userId, accountName, rootOrgId, orgPath[], groups(roles[]), groupIds[], orgManagerScopes[]
 * Refresh token claims: userId, rootOrgId, tokenType=refresh
 *
 * NOTE: JWT private/public keys in src/main/resources/jwt/ are DEV ONLY.
 * Replace with real keys in production via JWT_PRIVATE_KEY_LOCATION / JWT_PUBLIC_KEY_LOCATION env vars.
 */
@ApplicationScoped
class JwtIssuerService {

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "factory-ops")
    lateinit var issuer: String

    @ConfigProperty(name = "factory.ops.jwt.access.expiry.seconds", defaultValue = "900")
    var accessExpirySeconds: Long = 900L

    @ConfigProperty(name = "factory.ops.jwt.refresh.expiry.seconds", defaultValue = "604800")
    var refreshExpirySeconds: Long = 604800L

    fun issueTokenPair(user: User): TokenPair {
        val now = Instant.now()
        val accessToken = buildAccessToken(user, now)
        val refreshToken = buildRefreshToken(user, now)
        logger.debug { "Issued token pair for user [${user.accountName}]" }
        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = accessExpirySeconds
        )
    }

    private fun buildAccessToken(user: User, now: Instant): String {
        val roles = user.roles.map { it.name }.toMutableSet()
        if (user.orgManagerScopes.isNotEmpty()) {
            roles.add("ORG_MANAGER")
        }
        return Jwt.issuer(issuer)
            .subject(user.id ?: "")
            .upn(user.accountName)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(accessExpirySeconds))
            .audience("factory-ops-api")
            .claim("userId", user.id ?: "")
            .claim("accountName", user.accountName)
            .claim("rootOrgId", user.rootOrgId)
            .claim("orgPath", user.primaryOrgPath)
            .claim("groups", roles.toList())
            .claim("groupIds", user.groupIds)
            .claim("orgManagerScopes", user.orgManagerScopes)
            .sign()
    }

    private fun buildRefreshToken(user: User, now: Instant): String {
        return Jwt.issuer(issuer)
            .subject(user.id ?: "")
            .upn(user.accountName)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(refreshExpirySeconds))
            .audience("factory-ops-refresh")
            .claim("userId", user.id ?: "")
            .claim("rootOrgId", user.rootOrgId)
            .claim("tokenType", "refresh")
            .sign()
    }

    /**
     * Parses a refresh token and extracts the userId (no signature verification in dev mode).
     * In production, signature must be verified via SmallRye JWT.
     */
    fun extractUserIdFromRefreshToken(token: String): String? {
        return try {
            val consumer = JwtConsumerBuilder()
                .setSkipAllValidators()
                .setDisableRequireSignature()
                .setSkipSignatureVerification()
                .build()
            val claims = consumer.processToClaims(token)
            val tokenType = claims.getClaimValue("tokenType") as? String
            if (tokenType == "refresh") {
                claims.getClaimValue("userId") as? String
            } else {
                null
            }
        } catch (ex: Exception) {
            logger.debug { "Could not parse refresh token: ${ex.message}" }
            null
        }
    }
}

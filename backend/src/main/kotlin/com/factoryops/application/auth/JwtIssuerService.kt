package com.factoryops.application.auth

import com.factoryops.domain.user.User
import com.factoryops.interfaces.exception.UnauthorizedException
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo
import io.smallrye.jwt.auth.principal.JWTParser
import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.util.UUID

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
 * Refresh token claims: userId, rootOrgId, tokenType=refresh, jti
 *
 * NOTE: JWT private/public keys in src/main/resources/jwt/ are DEV ONLY.
 * In production, supply JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH environment variables.
 */
@ApplicationScoped
class JwtIssuerService {

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "factory-ops")
    lateinit var issuer: String

    @ConfigProperty(name = "factory.ops.jwt.access.expiry.seconds", defaultValue = "900")
    var accessExpirySeconds: Long = 900L

    @ConfigProperty(name = "factory.ops.jwt.refresh.expiry.seconds", defaultValue = "604800")
    var refreshExpirySeconds: Long = 604800L

    @ConfigProperty(name = "mp.jwt.verify.publickey.location", defaultValue = "jwt/publicKey.pem")
    lateinit var publicKeyLocation: String

    @Inject
    lateinit var jwtParser: JWTParser

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
            .audience("factory-ops-access")
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
        val jti = UUID.randomUUID().toString()
        return Jwt.issuer(issuer)
            .subject(user.id ?: "")
            .upn(user.accountName)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(refreshExpirySeconds))
            .audience("factory-ops-refresh")
            .claim("jti", jti)
            .claim("userId", user.id ?: "")
            .claim("rootOrgId", user.rootOrgId)
            .claim("tokenType", "refresh")
            .sign()
    }

    data class RefreshTokenClaims(val userId: String, val jti: String, val expiresAt: Instant)

    /**
     * Parses and validates a refresh token using the configured public key.
     * Uses a refresh-specific JWTAuthContextInfo so audience=factory-ops-refresh is enforced
     * independently of the global mp.jwt.verify.audiences=factory-ops-access config.
     * Enforces: issuer, audience=factory-ops-refresh, expiry, tokenType=refresh.
     */
    fun extractRefreshTokenClaims(token: String): RefreshTokenClaims {
        val refreshCtx = JWTAuthContextInfo().also { info ->
            info.publicKeyLocation = publicKeyLocation
            info.issuedBy = issuer
            info.expectedAudience = setOf("factory-ops-refresh")
        }

        val jwt = try {
            jwtParser.parse(token, refreshCtx)
        } catch (ex: Exception) {
            logger.debug { "Refresh token parse/verify failed: ${ex.message}" }
            throw UnauthorizedException("Invalid or expired refresh token", "invalid_refresh_token")
        }

        val tokenType = jwt.getClaim<String>("tokenType")
        if (tokenType != "refresh") {
            throw UnauthorizedException("Token type is not refresh", "invalid_refresh_token")
        }

        val userId = jwt.getClaim<String>("userId")
            ?: throw UnauthorizedException("Refresh token missing userId claim", "invalid_refresh_token")

        val jti = jwt.tokenID
            ?: throw UnauthorizedException("Refresh token missing jti claim", "invalid_refresh_token")

        val expiresAt = Instant.ofEpochSecond(jwt.expirationTime)

        return RefreshTokenClaims(userId = userId, jti = jti, expiresAt = expiresAt)
    }
}

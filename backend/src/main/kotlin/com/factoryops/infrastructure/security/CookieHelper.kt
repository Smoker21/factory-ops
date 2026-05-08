package com.factoryops.infrastructure.security

import jakarta.ws.rs.core.NewCookie
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.security.SecureRandom
import java.util.Base64
import jakarta.enterprise.context.ApplicationScoped

private val logger = KotlinLogging.logger {}

/**
 * Centralised factory for the three auth cookies.
 *
 * Cookie design (ADR-0015 / §FR-1.5 ~ §FR-1.8):
 *  - `refresh_token`  — httpOnly, Path=/v1/auth,  Max-Age=7d
 *  - `access_token`   — httpOnly, Path=/v1/,       Max-Age=15m
 *  - `XSRF-TOKEN`     — NOT httpOnly (JS-readable for double-submit), Path=/v1/, Max-Age=7d
 *
 * SameSite and Secure are read from config so dev profile can override to Lax / false
 * (localhost cross-port: frontend :5173 ↔ backend :8080).
 *
 * Usage:
 *   val xsrfValue = cookieHelper.generateXsrfToken()
 *   val cookies   = cookieHelper.buildLoginCookies(accessToken, refreshToken, xsrfValue)
 *   val clearCookies = cookieHelper.buildClearCookies()
 */
@ApplicationScoped
class CookieHelper {

    @ConfigProperty(name = "auth.cookie.same-site", defaultValue = "Strict")
    lateinit var sameSiteConfig: String

    @ConfigProperty(name = "auth.cookie.secure", defaultValue = "true")
    var secureFlagConfig: Boolean = true

    @ConfigProperty(name = "factory.ops.jwt.access.expiry.seconds", defaultValue = "900")
    var accessExpirySeconds: Int = 900

    @ConfigProperty(name = "auth.refresh.token.lifespan-seconds", defaultValue = "604800")
    var refreshExpirySeconds: Int = 604800

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically random 32-byte base64url-encoded XSRF token.
     * Suitable for double-submit cookie pattern (ADR-0015 §FR-1.7).
     */
    fun generateXsrfToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Builds the three Set-Cookie headers for login and refresh responses.
     */
    fun buildLoginCookies(accessToken: String, refreshToken: String, xsrfToken: String): List<NewCookie> {
        val sameSite = sameSiteConfig
        val secure = secureFlagConfig
        return listOf(
            buildCookie(
                name = "refresh_token",
                value = refreshToken,
                path = "/v1/auth",
                maxAge = refreshExpirySeconds,
                httpOnly = true,
                secure = secure,
                sameSite = sameSite
            ),
            buildCookie(
                name = "access_token",
                value = accessToken,
                path = "/v1/",
                maxAge = accessExpirySeconds,
                httpOnly = true,
                secure = secure,
                sameSite = sameSite
            ),
            buildCookie(
                name = "XSRF-TOKEN",
                value = xsrfToken,
                path = "/v1/",
                maxAge = refreshExpirySeconds,
                httpOnly = false,
                secure = secure,
                sameSite = sameSite
            )
        )
    }

    /**
     * Builds the three Set-Cookie headers with Max-Age=0 to clear all auth cookies on logout.
     */
    fun buildClearCookies(): List<NewCookie> {
        val sameSite = sameSiteConfig
        val secure = secureFlagConfig
        return listOf(
            buildCookie("refresh_token", "", "/v1/auth", 0, httpOnly = true, secure = secure, sameSite = sameSite),
            buildCookie("access_token", "", "/v1/", 0, httpOnly = true, secure = secure, sameSite = sameSite),
            buildCookie("XSRF-TOKEN", "", "/v1/", 0, httpOnly = false, secure = secure, sameSite = sameSite)
        )
    }

    private fun buildCookie(
        name: String,
        value: String,
        path: String,
        maxAge: Int,
        httpOnly: Boolean,
        secure: Boolean,
        sameSite: String
    ): NewCookie {
        // NewCookie.Builder is available in Jakarta EE 10 / Quarkus 3.x.
        // We use the full constructor to set SameSite via the comment workaround or via builder.
        // Jakarta EE 10 NewCookie.Builder supports .sameSite(NewCookie.SameSite)
        val sameSiteEnum = when (sameSite.lowercase()) {
            "strict" -> NewCookie.SameSite.STRICT
            "lax" -> NewCookie.SameSite.LAX
            "none" -> NewCookie.SameSite.NONE
            else -> {
                logger.warn { "Unknown SameSite value [$sameSite], defaulting to STRICT" }
                NewCookie.SameSite.STRICT
            }
        }

        return NewCookie.Builder(name)
            .value(value)
            .path(path)
            .maxAge(maxAge)
            .httpOnly(httpOnly)
            .secure(secure)
            .sameSite(sameSiteEnum)
            .build()
    }
}

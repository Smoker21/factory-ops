package com.factoryops.interfaces.rest

import com.factoryops.application.service.AuthService
import com.factoryops.infrastructure.security.CookieHelper
import com.factoryops.interfaces.dto.ChangePasswordRequest
import com.factoryops.interfaces.dto.LoginRequest
import com.factoryops.interfaces.dto.LogoutRequest
import com.factoryops.interfaces.dto.RefreshRequest
import com.factoryops.interfaces.dto.TokenPairResponse
import com.factoryops.interfaces.filter.RequestContext
import com.factoryops.interfaces.exception.UnauthorizedException
import jakarta.annotation.security.PermitAll
import jakarta.inject.Inject
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KotlinLogging
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag

private val logger = KotlinLogging.logger {}

@Path("/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Authentication and token management")
class AuthResource {

    @Inject
    lateinit var authService: AuthService

    @Inject
    lateinit var requestContext: RequestContext

    @Inject
    lateinit var cookieHelper: CookieHelper

    /**
     * Extracts the client IP address.
     * Checks X-Forwarded-For first (for proxy/load-balancer scenarios).
     */
    private fun resolveClientIp(headers: HttpHeaders): String {
        val forwarded = headers.getHeaderString("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            "unknown"
        }
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(
        summary = "Login with accountName and password",
        description = "Authenticates a user. On success sets three httpOnly cookies: " +
            "access_token (Path=/v1/, 15m), refresh_token (Path=/v1/auth, 7d), " +
            "XSRF-TOKEN (non-httpOnly, Path=/v1/, 7d) for double-submit CSRF protection. " +
            "Response body retains accessToken/refreshToken for backward compatibility (M6 will remove). " +
            "Applies per-IP and per-account rate limiting. " +
            "Accounts are temporarily locked after repeated failures (S-015, S-016)."
    )
    @APIResponse(responseCode = "200", description = "Login successful, returns token pair + sets three auth cookies")
    @APIResponse(responseCode = "401", description = "Invalid credentials or account locked (ACCOUNT_LOCKED)")
    @APIResponse(responseCode = "422", description = "Validation error")
    @APIResponse(responseCode = "429", description = "Rate limit exceeded — see Retry-After header")
    fun login(@Valid request: LoginRequest, @Context headers: HttpHeaders): Response {
        val clientIp = resolveClientIp(headers)
        val tokenPair = authService.login(request.orgCode, request.accountName, request.password, clientIp)

        val xsrfToken = cookieHelper.generateXsrfToken()
        val cookies = cookieHelper.buildLoginCookies(tokenPair.accessToken, tokenPair.refreshToken, xsrfToken)

        val responseBuilder = Response.ok(
            TokenPairResponse(
                accessToken = tokenPair.accessToken,
                refreshToken = tokenPair.refreshToken,
                expiresIn = tokenPair.expiresIn
            )
        )
        cookies.forEach { responseBuilder.cookie(it) }
        return responseBuilder.build()
    }

    @POST
    @Path("/refresh")
    @PermitAll
    @Operation(
        summary = "Refresh access token",
        description = "Refreshes the access token. Cookie-first: reads refresh_token from httpOnly cookie. " +
            "Falls back to body refreshToken field for backward compatibility (M6 will remove body path). " +
            "On success re-issues all three auth cookies. On failure clears all three cookies and returns 401."
    )
    @APIResponse(responseCode = "200", description = "New token pair + refreshed auth cookies")
    @APIResponse(responseCode = "401", description = "Invalid or expired refresh token")
    fun refresh(request: RefreshRequest?, @Context headers: HttpHeaders): Response {
        // Cookie-first: prefer the httpOnly cookie over any body-supplied token (§FR-1.8)
        val cookieToken = headers.cookies["refresh_token"]?.value
        val bodyToken = request?.refreshToken

        val refreshTokenValue = cookieToken?.takeIf { it.isNotBlank() }
            ?: bodyToken?.takeIf { it.isNotBlank() }

        if (refreshTokenValue == null) {
            logger.debug { "Refresh attempted with no token in cookie or body" }
            val clearCookies = cookieHelper.buildClearCookies()
            val builder = Response.status(401)
                .type(MediaType.APPLICATION_JSON)
                .entity(
                    mapOf(
                        "type" to "https://factory-ops.example.com/errors/invalid_refresh_token",
                        "title" to "Unauthorized",
                        "status" to 401,
                        "detail" to "No refresh token provided"
                    )
                )
            clearCookies.forEach { builder.cookie(it) }
            return builder.build()
        }

        return try {
            val tokenPair = authService.refresh(refreshTokenValue)
            val xsrfToken = cookieHelper.generateXsrfToken()
            val cookies = cookieHelper.buildLoginCookies(tokenPair.accessToken, tokenPair.refreshToken, xsrfToken)

            val responseBuilder = Response.ok(
                TokenPairResponse(
                    accessToken = tokenPair.accessToken,
                    refreshToken = tokenPair.refreshToken,
                    expiresIn = tokenPair.expiresIn
                )
            )
            cookies.forEach { responseBuilder.cookie(it) }
            responseBuilder.build()
        } catch (ex: UnauthorizedException) {
            // On any refresh failure, clear all auth cookies (§FR-1.8)
            logger.debug { "Refresh token invalid — clearing cookies: ${ex.message}" }
            val clearCookies = cookieHelper.buildClearCookies()
            val builder = Response.status(401)
                .type(MediaType.APPLICATION_JSON)
                .entity(
                    mapOf(
                        "type" to "https://factory-ops.example.com/errors/${ex.errorCode}",
                        "title" to "Unauthorized",
                        "status" to 401,
                        "detail" to "Refresh token is invalid or expired"
                    )
                )
            clearCookies.forEach { builder.cookie(it) }
            builder.build()
        }
    }

    @POST
    @Path("/logout")
    @Operation(
        summary = "Logout and revoke refresh token",
        description = "Clears all three auth cookies (Max-Age=0) and blacklists the refresh token jti. " +
            "Cookie-first: reads refresh_token from httpOnly cookie; falls back to body. " +
            "Applies per-IP rate limiting (S-015)."
    )
    @APIResponse(responseCode = "204", description = "Logged out — all auth cookies cleared")
    @APIResponse(responseCode = "429", description = "Rate limit exceeded — see Retry-After header")
    fun logout(request: LogoutRequest?, @Context headers: HttpHeaders): Response {
        val clientIp = resolveClientIp(headers)

        // Cookie-first resolution (§FR-1.8)
        val cookieToken = headers.cookies["refresh_token"]?.value
        val bodyToken = request?.refreshToken
        val refreshTokenValue = cookieToken?.takeIf { it.isNotBlank() }
            ?: bodyToken?.takeIf { it.isNotBlank() }

        authService.logout(refreshTokenValue, clientIp)

        val clearCookies = cookieHelper.buildClearCookies()
        val builder = Response.noContent()
        clearCookies.forEach { builder.cookie(it) }
        return builder.build()
    }

    @PUT
    @Path("/password")
    @Operation(
        summary = "Change own password",
        description = "Validates current password and enforces new password strength " +
            "(>= 12 chars, >= 3 character classes). Applies per-IP and per-account rate limiting (S-011, S-015). " +
            "Does not affect auth cookies (§FR-1.8)."
    )
    @APIResponse(responseCode = "204", description = "Password changed")
    @APIResponse(responseCode = "401", description = "Wrong current password")
    @APIResponse(responseCode = "422", description = "New password does not meet strength requirements")
    @APIResponse(responseCode = "429", description = "Rate limit exceeded — see Retry-After header")
    fun changePassword(@Valid request: ChangePasswordRequest, @Context headers: HttpHeaders): Response {
        val userId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val clientIp = resolveClientIp(headers)
        authService.changePassword(userId, rootOrgId, request.currentPassword, request.newPassword, clientIp)
        return Response.noContent().build()
    }
}

package com.factoryops.infrastructure.security

import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

private val logger = KotlinLogging.logger {}

/**
 * CSRF protection filter — double-submit cookie pattern (ADR-0015 / §FR-1.7).
 *
 * Logic:
 *  1. Safe methods (GET, HEAD, OPTIONS) — always pass through.
 *  2. Exempt paths (configurable) — always pass through.
 *  3. Requests without an XSRF-TOKEN cookie — pass through (Bearer-only mode;
 *     browsers never auto-attach Authorization headers, so CSRF is irrelevant).
 *  4. Requests WITH an XSRF-TOKEN cookie — enforce double-submit:
 *       cookie value == X-XSRF-TOKEN header value → allow
 *       mismatch or missing header → abort 403 with RFC 7807 problem detail.
 *
 * Rule 3 is the dual-mode key: existing Bearer-only clients (integration tests, Postman)
 * are unaffected.  Once the frontend switches to cookie mode (M5.5), the XSRF cookie will
 * be present and the filter will enforce header presence.
 *
 * Priority 900 (< Priorities.AUTHENTICATION = 1000) so this runs before JAX-RS auth
 * for endpoints that have an XSRF cookie, giving a clean 403 before a possible 401.
 * For cookie-mode requests, JAX-RS auth can still override with 401 if the access_token
 * cookie itself is invalid.
 *
 * Exempt paths are prefix-matched.  The default list covers health checks, login,
 * OpenAPI and Swagger UI.  Override via security.csrf.exempt-paths config property.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION - 100)
class CsrfFilter : ContainerRequestFilter {

    @ConfigProperty(
        name = "security.csrf.exempt-paths",
        defaultValue = "/q/health,/q/health/live,/q/health/ready,/v1/auth/login,/q/openapi,/q/swagger-ui,/openapi,/mock-hr"
    )
    lateinit var exemptPathsConfig: String

    private val safeMethods = setOf("GET", "HEAD", "OPTIONS")

    private val exemptPaths: List<String> by lazy {
        exemptPathsConfig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun filter(ctx: ContainerRequestContext) {
        val method = ctx.method.uppercase()
        if (method in safeMethods) return

        val path = ctx.uriInfo.path
        if (exemptPaths.any { path.startsWith(it) }) {
            logger.debug { "CSRF exempt path=[$path]" }
            return
        }

        // Cookie-mode detection: only enforce double-submit when XSRF-TOKEN cookie is present.
        // Bearer-only requests (no cookie) are CSRF-safe by definition (browsers cannot
        // auto-attach Authorization headers).
        val xsrfCookieValue = ctx.cookies["XSRF-TOKEN"]?.value
        if (xsrfCookieValue.isNullOrBlank()) {
            // No XSRF cookie → Bearer-mode request; skip CSRF check.
            return
        }

        val xsrfHeaderValue = ctx.getHeaderString("X-XSRF-TOKEN")
        if (xsrfHeaderValue.isNullOrBlank() || xsrfCookieValue != xsrfHeaderValue) {
            logger.warn { "CSRF token mismatch method=[$method] path=[$path]" }
            ctx.abortWith(buildCsrfErrorResponse(ctx.uriInfo.requestUri.toString()))
            return
        }

        logger.debug { "CSRF token validated method=[$method] path=[$path]" }
    }

    private fun buildCsrfErrorResponse(instance: String): Response {
        val body = mapOf(
            "type" to "urn:problem:csrf-token-mismatch",
            "title" to "CSRF token mismatch",
            "status" to 403,
            "detail" to "Request rejected: CSRF token is missing or does not match.",
            "instance" to instance
        )
        // RFC 7807 Problem Details content-type — matches GlobalExceptionMapper pattern
        return Response.status(403)
            .type("application/problem+json")
            .entity(body)
            .build()
    }
}

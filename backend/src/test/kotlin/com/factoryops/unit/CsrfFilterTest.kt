package com.factoryops.unit

import com.factoryops.infrastructure.security.CsrfFilter
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI

/**
 * Unit tests for CsrfFilter — double-submit cookie pattern (ADR-0015 / §FR-1.7).
 *
 * Key behaviour under test:
 *  - Safe methods (GET, HEAD, OPTIONS) always pass through.
 *  - Exempt paths always pass through.
 *  - Requests WITHOUT XSRF-TOKEN cookie pass through (Bearer-only mode, dual-mode compat).
 *  - Requests WITH matching XSRF-TOKEN cookie + X-XSRF-TOKEN header pass through.
 *  - Requests WITH XSRF-TOKEN cookie but missing header → 403.
 *  - Requests WITH XSRF-TOKEN cookie but mismatched header → 403.
 */
class CsrfFilterTest {

    private lateinit var filter: CsrfFilter
    private lateinit var ctx: ContainerRequestContext
    private lateinit var uriInfo: UriInfo

    @BeforeEach
    fun setup() {
        filter = CsrfFilter()
        filter.exemptPathsConfig = "/q/health,/q/health/live,/q/health/ready,/v1/auth/login,/q/openapi,/q/swagger-ui,/openapi,/mock-hr"

        ctx = mock()
        uriInfo = mock()
        whenever(ctx.uriInfo).thenReturn(uriInfo)
        whenever(uriInfo.requestUri).thenReturn(URI.create("http://localhost:8080/v1/tasks"))
    }

    private fun setupMethod(method: String, path: String) {
        whenever(ctx.method).thenReturn(method)
        whenever(uriInfo.path).thenReturn(path)
    }

    private fun setupCookies(vararg pairs: Pair<String, String>) {
        val cookieMap = pairs.associate { (name, value) ->
            name to Cookie.valueOf("$name=$value")
        }
        whenever(ctx.cookies).thenReturn(cookieMap)
    }

    private fun noCookies() {
        whenever(ctx.cookies).thenReturn(emptyMap())
    }

    // ─── Safe methods: always pass through ────────────────────────────────────────

    @Test
    fun `GET request is not CSRF-checked`() {
        setupMethod("GET", "/v1/tasks")
        noCookies()

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    @Test
    fun `HEAD request is not CSRF-checked`() {
        setupMethod("HEAD", "/v1/tasks")
        noCookies()

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    @Test
    fun `OPTIONS request is not CSRF-checked`() {
        setupMethod("OPTIONS", "/v1/tasks")
        noCookies()

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    // ─── Exempt paths ─────────────────────────────────────────────────────────────

    @Test
    fun `POST to login endpoint is exempt from CSRF check`() {
        setupMethod("POST", "/v1/auth/login")
        noCookies()

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    @Test
    fun `POST to health endpoint is exempt from CSRF check`() {
        setupMethod("POST", "/q/health/live")
        noCookies()

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    // ─── Bearer-only mode (no XSRF cookie): pass through ─────────────────────────

    @Test
    fun `POST without XSRF-TOKEN cookie passes (Bearer-only mode)`() {
        setupMethod("POST", "/v1/tasks")
        noCookies()
        // No X-XSRF-TOKEN header either
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn(null)

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    @Test
    fun `PATCH without XSRF-TOKEN cookie passes (Bearer-only mode)`() {
        setupMethod("PATCH", "/v1/tasks/123")
        noCookies()
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn(null)

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    @Test
    fun `DELETE without XSRF-TOKEN cookie passes (Bearer-only mode)`() {
        setupMethod("DELETE", "/v1/tasks/123")
        noCookies()
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn(null)

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    @Test
    fun `PUT without XSRF-TOKEN cookie passes (Bearer-only mode)`() {
        setupMethod("PUT", "/v1/tasks/123")
        noCookies()
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn(null)

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    // ─── Cookie-mode: double-submit validation ────────────────────────────────────

    @Test
    fun `POST with matching XSRF cookie and header passes`() {
        val token = "aBcDeFgHiJkLmNoPqRsTuVwXyZ123456"
        setupMethod("POST", "/v1/tasks")
        setupCookies("XSRF-TOKEN" to token)
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn(token)

        filter.filter(ctx)

        verify(ctx, never()).abortWith(org.mockito.kotlin.any())
    }

    @Test
    fun `POST with XSRF cookie but missing header is rejected with 403 and problem+json content type`() {
        val token = "aBcDeFgHiJkLmNoPqRsTuVwXyZ123456"
        setupMethod("POST", "/v1/tasks")
        setupCookies("XSRF-TOKEN" to token)
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn(null)

        filter.filter(ctx)

        val responseCaptor = argumentCaptor<Response>()
        verify(ctx).abortWith(responseCaptor.capture())
        val resp = responseCaptor.firstValue
        assert(resp.status == 403)
        // RFC 7807 requires application/problem+json content type
        assert(resp.mediaType.toString().contains("application/problem+json")) {
            "Expected application/problem+json content type, got: ${resp.mediaType}"
        }
    }

    @Test
    fun `POST with XSRF cookie but mismatched header is rejected with 403`() {
        setupMethod("POST", "/v1/tasks")
        setupCookies("XSRF-TOKEN" to "token-from-cookie")
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn("different-token-in-header")

        filter.filter(ctx)

        val responseCaptor = argumentCaptor<Response>()
        verify(ctx).abortWith(responseCaptor.capture())
        assert(responseCaptor.firstValue.status == 403)
    }

    @Test
    fun `POST with XSRF cookie but blank header is rejected with 403`() {
        val token = "aBcDeFgHiJkLmNoPqRsTuVwXyZ123456"
        setupMethod("POST", "/v1/tasks")
        setupCookies("XSRF-TOKEN" to token)
        whenever(ctx.getHeaderString("X-XSRF-TOKEN")).thenReturn("")

        filter.filter(ctx)

        val responseCaptor = argumentCaptor<Response>()
        verify(ctx).abortWith(responseCaptor.capture())
        assert(responseCaptor.firstValue.status == 403)
    }
}

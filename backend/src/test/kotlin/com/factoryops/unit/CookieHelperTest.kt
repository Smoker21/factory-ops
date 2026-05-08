package com.factoryops.unit

import com.factoryops.infrastructure.security.CookieHelper
import jakarta.ws.rs.core.NewCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for CookieHelper — cookie factory for login/logout (ADR-0015 / §FR-1.5 ~ §FR-1.8).
 */
class CookieHelperTest {

    private lateinit var cookieHelper: CookieHelper

    @BeforeEach
    fun setup() {
        cookieHelper = CookieHelper()
        cookieHelper.sameSiteConfig = "Strict"
        cookieHelper.secureFlagConfig = true
        cookieHelper.accessExpirySeconds = 900
        cookieHelper.refreshExpirySeconds = 604800
    }

    // ─── generateXsrfToken ────────────────────────────────────────────────────────

    @Test
    fun `generateXsrfToken returns non-blank 43-char base64url string`() {
        val token = cookieHelper.generateXsrfToken()
        assertFalse(token.isBlank())
        // 32 bytes base64url-encoded without padding = 43 chars
        assertEquals(43, token.length, "Expected 43-char base64url for 32 bytes")
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' },
            "Token should be URL-safe base64 (no padding)")
    }

    @Test
    fun `generateXsrfToken produces unique values`() {
        val tokens = (1..10).map { cookieHelper.generateXsrfToken() }.toSet()
        assertEquals(10, tokens.size, "All 10 generated tokens should be unique")
    }

    // ─── buildLoginCookies ────────────────────────────────────────────────────────

    @Test
    fun `buildLoginCookies returns three cookies`() {
        val cookies = cookieHelper.buildLoginCookies("access-jwt", "refresh-jwt", "xsrf-value")
        assertEquals(3, cookies.size)
    }

    @Test
    fun `buildLoginCookies sets refresh_token cookie with correct attributes`() {
        val cookies = cookieHelper.buildLoginCookies("access-jwt", "refresh-jwt", "xsrf-value")
        val refreshCookie = cookies.first { it.name == "refresh_token" }

        assertEquals("refresh-jwt", refreshCookie.value)
        assertEquals("/v1/auth", refreshCookie.path)
        assertEquals(604800, refreshCookie.maxAge)
        assertTrue(refreshCookie.isHttpOnly, "refresh_token must be httpOnly")
        assertTrue(refreshCookie.isSecure, "refresh_token must be Secure")
        assertEquals(NewCookie.SameSite.STRICT, refreshCookie.sameSite)
    }

    @Test
    fun `buildLoginCookies sets access_token cookie with correct attributes`() {
        val cookies = cookieHelper.buildLoginCookies("access-jwt", "refresh-jwt", "xsrf-value")
        val accessCookie = cookies.first { it.name == "access_token" }

        assertEquals("access-jwt", accessCookie.value)
        assertEquals("/v1/", accessCookie.path)
        assertEquals(900, accessCookie.maxAge)
        assertTrue(accessCookie.isHttpOnly, "access_token must be httpOnly")
        assertTrue(accessCookie.isSecure, "access_token must be Secure")
        assertEquals(NewCookie.SameSite.STRICT, accessCookie.sameSite)
    }

    @Test
    fun `buildLoginCookies sets XSRF-TOKEN cookie as non-httpOnly`() {
        val cookies = cookieHelper.buildLoginCookies("access-jwt", "refresh-jwt", "xsrf-value")
        val xsrfCookie = cookies.first { it.name == "XSRF-TOKEN" }

        assertEquals("xsrf-value", xsrfCookie.value)
        assertEquals("/v1/", xsrfCookie.path)
        assertEquals(604800, xsrfCookie.maxAge)
        assertFalse(xsrfCookie.isHttpOnly, "XSRF-TOKEN must NOT be httpOnly (JS-readable for double-submit)")
        assertTrue(xsrfCookie.isSecure, "XSRF-TOKEN must be Secure")
        assertEquals(NewCookie.SameSite.STRICT, xsrfCookie.sameSite)
    }

    // ─── buildClearCookies ────────────────────────────────────────────────────────

    @Test
    fun `buildClearCookies returns three cookies all with maxAge=0`() {
        val clearCookies = cookieHelper.buildClearCookies()
        assertEquals(3, clearCookies.size)
        clearCookies.forEach { cookie ->
            assertEquals(0, cookie.maxAge, "Clear cookie ${cookie.name} must have maxAge=0")
        }
    }

    @Test
    fun `buildClearCookies covers all three cookie names`() {
        val clearCookies = cookieHelper.buildClearCookies()
        val names = clearCookies.map { it.name }.toSet()
        assertTrue("refresh_token" in names)
        assertTrue("access_token" in names)
        assertTrue("XSRF-TOKEN" in names)
    }

    // ─── dev profile: SameSite=Lax ───────────────────────────────────────────────

    @Test
    fun `buildLoginCookies uses Lax when configured for dev profile`() {
        cookieHelper.sameSiteConfig = "Lax"
        cookieHelper.secureFlagConfig = false
        val cookies = cookieHelper.buildLoginCookies("a", "r", "x")

        cookies.forEach { cookie ->
            assertEquals(NewCookie.SameSite.LAX, cookie.sameSite,
                "Cookie ${cookie.name} should be Lax in dev profile")
            assertFalse(cookie.isSecure, "Cookie ${cookie.name} should not require Secure on localhost")
        }
    }
}

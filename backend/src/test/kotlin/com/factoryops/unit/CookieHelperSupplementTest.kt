package com.factoryops.unit

import com.factoryops.infrastructure.security.CookieHelper
import jakarta.ws.rs.core.NewCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Supplementary S-009 CookieHelper tests covering gaps not in CookieHelperTest:
 *  - buildClearCookies path attributes (each cookie must use its correct path)
 *  - SameSite=None and unknown SameSite fallback to STRICT
 *  - 100 unique XSRF tokens (distribution sanity check beyond 10)
 */
class CookieHelperSupplementTest {

    private lateinit var helper: CookieHelper

    @BeforeEach
    fun setup() {
        helper = CookieHelper()
        helper.sameSiteConfig = "Strict"
        helper.secureFlagConfig = true
        helper.accessExpirySeconds = 900
        helper.refreshExpirySeconds = 604800
    }

    // ─── buildClearCookies: path correctness ──────────────────────────────────────

    @Test
    fun `buildClearCookies sets refresh_token clear cookie with correct path`() {
        val clearCookies = helper.buildClearCookies()
        val refreshClear = clearCookies.first { it.name == "refresh_token" }

        // Must use the same path as the original cookie so the browser clears the right one
        assertEquals("/v1/auth", refreshClear.path, "refresh_token clear cookie must use Path=/v1/auth")
        assertEquals(0, refreshClear.maxAge)
    }

    @Test
    fun `buildClearCookies sets access_token clear cookie with correct path`() {
        val clearCookies = helper.buildClearCookies()
        val accessClear = clearCookies.first { it.name == "access_token" }

        assertEquals("/v1/", accessClear.path, "access_token clear cookie must use Path=/v1/")
        assertEquals(0, accessClear.maxAge)
    }

    @Test
    fun `buildClearCookies sets XSRF-TOKEN clear cookie with correct path`() {
        val clearCookies = helper.buildClearCookies()
        val xsrfClear = clearCookies.first { it.name == "XSRF-TOKEN" }

        assertEquals("/v1/", xsrfClear.path, "XSRF-TOKEN clear cookie must use Path=/v1/")
        assertEquals(0, xsrfClear.maxAge)
    }

    @Test
    fun `buildClearCookies preserves httpOnly attribute on access_token clear cookie`() {
        val clearCookies = helper.buildClearCookies()
        val accessClear = clearCookies.first { it.name == "access_token" }
        // access_token is httpOnly; clear cookie should maintain the same attribute
        assertTrue(accessClear.isHttpOnly, "access_token clear cookie should keep httpOnly")
    }

    @Test
    fun `buildClearCookies preserves non-httpOnly for XSRF-TOKEN clear cookie`() {
        val clearCookies = helper.buildClearCookies()
        val xsrfClear = clearCookies.first { it.name == "XSRF-TOKEN" }
        assertFalse(xsrfClear.isHttpOnly, "XSRF-TOKEN clear cookie must NOT be httpOnly")
    }

    // ─── SameSite=None configuration ─────────────────────────────────────────────

    @Test
    fun `buildLoginCookies uses SameSite NONE when configured`() {
        helper.sameSiteConfig = "None"
        val cookies = helper.buildLoginCookies("a", "r", "x")

        cookies.forEach { cookie ->
            assertEquals(
                NewCookie.SameSite.NONE, cookie.sameSite,
                "Cookie ${cookie.name} should use SameSite=None"
            )
        }
    }

    // ─── Unknown SameSite fallback ────────────────────────────────────────────────

    @Test
    fun `buildLoginCookies falls back to STRICT for unknown SameSite value`() {
        helper.sameSiteConfig = "InvalidValue"
        val cookies = helper.buildLoginCookies("a", "r", "x")

        cookies.forEach { cookie ->
            assertEquals(
                NewCookie.SameSite.STRICT, cookie.sameSite,
                "Cookie ${cookie.name} should default to STRICT for unknown SameSite config"
            )
        }
    }

    // ─── 100 unique XSRF tokens ────────────────────────────────────────────────────

    @Test
    fun `100 consecutive XSRF tokens are all unique (SecureRandom distribution sanity check)`() {
        val tokens = (1..100).map { helper.generateXsrfToken() }.toSet()
        assertEquals(100, tokens.size, "All 100 XSRF tokens should be unique (SecureRandom)")
    }

    // ─── Max-Age matches spec §FR-1.5 ~ §FR-1.6 ──────────────────────────────────

    @Test
    fun `access_token Max-Age equals configured accessExpirySeconds per FR-1-6`() {
        helper.accessExpirySeconds = 900
        val cookies = helper.buildLoginCookies("a", "r", "x")
        val accessCookie = cookies.first { it.name == "access_token" }
        assertEquals(900, accessCookie.maxAge, "access_token Max-Age must be 900 (15 min, §FR-1.6)")
    }

    @Test
    fun `refresh_token Max-Age equals configured refreshExpirySeconds per FR-1-5`() {
        helper.refreshExpirySeconds = 604800
        val cookies = helper.buildLoginCookies("a", "r", "x")
        val refreshCookie = cookies.first { it.name == "refresh_token" }
        assertEquals(604800, refreshCookie.maxAge, "refresh_token Max-Age must be 604800 (7d, §FR-1.5)")
    }

    @Test
    fun `XSRF-TOKEN Max-Age equals refreshExpirySeconds per FR-1-7`() {
        helper.refreshExpirySeconds = 604800
        val cookies = helper.buildLoginCookies("a", "r", "x")
        val xsrfCookie = cookies.first { it.name == "XSRF-TOKEN" }
        assertEquals(604800, xsrfCookie.maxAge, "XSRF-TOKEN Max-Age should match refresh token lifespan (§FR-1.7)")
    }
}

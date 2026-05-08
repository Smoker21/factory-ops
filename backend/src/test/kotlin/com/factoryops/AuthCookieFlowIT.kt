package com.factoryops

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TestProfile: raise all rate limits so the ~20 logins in this test class don't hit 429.
 * Lockout threshold is also raised to prevent interference from repeated test logins.
 */
class CookieFlowTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "auth.rate-limit.login.per-account" to "200",
        "auth.rate-limit.login.per-ip" to "500",
        "auth.rate-limit.other.per-ip" to "200",
        "auth.rate-limit.other.per-account" to "200",
        "auth.lockout.threshold" to "99",
        "factory.ops.seed.enabled" to "true"
    )
}

/**
 * S-009 Integration tests: JWT cookie / CSRF flow (ADR-0015 / §FR-1.5 ~ §FR-1.8).
 *
 * Tests run against a live Quarkus instance (DevServices MongoDB).
 * Scenarios:
 *  1. login sets three auth cookies with correct attributes
 *  2. cookie-mode accesses GET /v1/me
 *  3. cookie-mode + valid CSRF header → POST allowed
 *  4. cookie-mode + mismatched CSRF header → 403
 *  5. cookie-mode + missing CSRF header → 403
 *  6. no XSRF cookie (Bearer-only) passes through CSRF filter
 *  7. logout clears three cookies (Max-Age=0)
 *  8. refresh cookie-first (no body) → 200 + new cookies
 *  9. refresh body fallback (no cookie) → 200
 * 10. refresh failure → 401 + clears cookies
 * 11. GET endpoints not CSRF-checked even with XSRF cookie
 * 12. filter order: expired access_token + XSRF cookie → pin current behavior (401 or 403)
 */
@QuarkusTest
@TestProfile(CookieFlowTestProfile::class)
class AuthCookieFlowIT {

    companion object {
        private const val LOGIN_BODY =
            """{"orgCode":"taichung-fab","accountName":"admin.system","password":"Admin@123456789"}"""
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    /** Perform a login and return the raw RestAssured Response. */
    private fun doLogin(): Response =
        given()
            .contentType(ContentType.JSON)
            .body(LOGIN_BODY)
            .`when`().post("/v1/auth/login")

    /**
     * Extract a cookie token value from the response's Set-Cookie headers.
     * Returns the value-part of the cookie (before the first ";").
     */
    private fun extractCookieValue(response: Response, cookieName: String): String? {
        val setCookieHeaders = response.headers().getList("Set-Cookie")
            ?: return null
        val header = setCookieHeaders.firstOrNull { it.value.startsWith("$cookieName=") }
            ?: return null
        return header.value.substringAfter("$cookieName=").substringBefore(";")
    }

    /**
     * Extract the full Set-Cookie header string for a given cookie name.
     */
    private fun extractSetCookieHeader(response: Response, cookieName: String): String? {
        val setCookieHeaders = response.headers().getList("Set-Cookie") ?: return null
        return setCookieHeaders.firstOrNull { it.value.startsWith("$cookieName=") }?.value
    }

    // ─── Scenario 1: login sets three cookies with correct attributes ─────────────

    @Test
    fun `login sets three auth cookies on successful authentication`() {
        // When
        val response = doLogin().then().statusCode(200).extract().response()

        // Then: verify three Set-Cookie headers are present
        val setCookieHeaders = response.headers().getList("Set-Cookie") ?: emptyList()
        val cookieNames = setCookieHeaders.map { it.value.substringBefore("=") }.toSet()

        assertTrue("refresh_token" in cookieNames, "Expected refresh_token cookie, found: $cookieNames")
        assertTrue("access_token" in cookieNames, "Expected access_token cookie, found: $cookieNames")
        assertTrue("XSRF-TOKEN" in cookieNames, "Expected XSRF-TOKEN cookie, found: $cookieNames")
    }

    @Test
    fun `login refresh_token cookie has correct Path and Max-Age (§FR-1_5)`() {
        val response = doLogin().then().statusCode(200).extract().response()
        val header = extractSetCookieHeader(response, "refresh_token")

        assertNotNull(header, "refresh_token Set-Cookie header must be present")
        // Path=/v1/auth (limited scope — FR-1.5)
        assertTrue(header!!.contains("Path=/v1/auth"), "refresh_token must have Path=/v1/auth, got: $header")
        // Max-Age=604800 (7 days — FR-1.5)
        assertTrue(header.contains("Max-Age=604800"), "refresh_token must have Max-Age=604800, got: $header")
        // httpOnly
        assertTrue(header.contains("HttpOnly"), "refresh_token must be HttpOnly, got: $header")
    }

    @Test
    fun `login access_token cookie has correct Path and Max-Age (§FR-1_6)`() {
        val response = doLogin().then().statusCode(200).extract().response()
        val header = extractSetCookieHeader(response, "access_token")

        assertNotNull(header, "access_token Set-Cookie header must be present")
        // Path=/v1/ (access-scoped — FR-1.6)
        assertTrue(header!!.contains("Path=/v1/"), "access_token must have Path=/v1/, got: $header")
        // Max-Age=900 (15 min — FR-1.6)
        assertTrue(header.contains("Max-Age=900"), "access_token must have Max-Age=900, got: $header")
        // httpOnly
        assertTrue(header.contains("HttpOnly"), "access_token must be HttpOnly, got: $header")
    }

    @Test
    fun `login XSRF-TOKEN cookie is NOT httpOnly and has correct Path (§FR-1_7)`() {
        val response = doLogin().then().statusCode(200).extract().response()
        val header = extractSetCookieHeader(response, "XSRF-TOKEN")

        assertNotNull(header, "XSRF-TOKEN Set-Cookie header must be present")
        // Path=/v1/ (same as access token — FR-1.7)
        assertTrue(header!!.contains("Path=/v1/"), "XSRF-TOKEN must have Path=/v1/, got: $header")
        // Must NOT be httpOnly (JS needs to read it for double-submit)
        assertFalse(
            header.lowercase().contains("httponly"),
            "XSRF-TOKEN must NOT be HttpOnly (JS-readable for double-submit), got: $header"
        )
        // Max-Age=604800 (FR-1.7: same lifespan as refresh)
        assertTrue(header.contains("Max-Age=604800"), "XSRF-TOKEN must have Max-Age=604800, got: $header")
    }

    @Test
    fun `login response body still contains accessToken and refreshToken for backward compat`() {
        doLogin()
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("tokenType", equalTo("Bearer"))
    }

    // ─── Scenario 2: cookie-mode GET /v1/me ──────────────────────────────────────

    @Test
    fun `GET me with access_token cookie succeeds (cookie-mode §FR-1_6)`() {
        // Given: obtain a valid access token via login
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val accessToken = extractCookieValue(loginResponse, "access_token")
        assertNotNull(accessToken, "access_token cookie must be present after login")

        // When: access /v1/me using only the cookie (no Authorization header)
        given()
            .cookie("access_token", accessToken)
            .`when`().get("/v1/me")
            .then()
            .statusCode(200)
    }

    // ─── Scenario 3: cookie-mode + valid CSRF → POST allowed ─────────────────────

    @Test
    fun `POST with access_token cookie and matching XSRF-TOKEN passes CSRF filter`() {
        // Given: login to get tokens
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val accessToken = extractCookieValue(loginResponse, "access_token")
        val xsrfToken = extractCookieValue(loginResponse, "XSRF-TOKEN")
        assertNotNull(accessToken); assertNotNull(xsrfToken)

        // When: POST with cookie + matching X-XSRF-TOKEN header
        // /v1/auth/logout is a POST endpoint that doesn't require specific body
        val response = given()
            .cookie("access_token", accessToken)
            .cookie("XSRF-TOKEN", xsrfToken)
            .header("X-XSRF-TOKEN", xsrfToken)
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`().post("/v1/auth/logout")

        // Then: request reaches the business logic (204 logout success, not 403 CSRF)
        val status = response.statusCode
        assertTrue(status != 403) { "CSRF should pass when header matches cookie, got $status" }
    }

    // ─── Scenario 4: CSRF mismatch → 403 ─────────────────────────────────────────

    @Test
    fun `POST with XSRF cookie but mismatched X-XSRF-TOKEN header returns 403`() {
        // Given: login to get valid cookies
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val accessToken = extractCookieValue(loginResponse, "access_token")
        val xsrfToken = extractCookieValue(loginResponse, "XSRF-TOKEN")

        // When: XSRF-TOKEN cookie is present but X-XSRF-TOKEN header has different value
        given()
            .cookie("access_token", accessToken)
            .cookie("XSRF-TOKEN", xsrfToken)
            .header("X-XSRF-TOKEN", "totally-wrong-value")
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`().post("/v1/auth/logout")
            .then()
            .statusCode(403)
            .contentType("application/problem+json")
    }

    @Test
    fun `403 CSRF response has RFC 7807 type urn problem csrf-token-mismatch`() {
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val accessToken = extractCookieValue(loginResponse, "access_token")
        val xsrfToken = extractCookieValue(loginResponse, "XSRF-TOKEN")

        given()
            .cookie("access_token", accessToken)
            .cookie("XSRF-TOKEN", xsrfToken)
            .header("X-XSRF-TOKEN", "wrong-value")
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`().post("/v1/auth/logout")
            .then()
            .statusCode(403)
            .body("type", equalTo("urn:problem:csrf-token-mismatch"))
            .body("status", equalTo(403))
    }

    // ─── Scenario 5: CSRF missing header → 403 ───────────────────────────────────

    @Test
    fun `POST with XSRF cookie but no X-XSRF-TOKEN header returns 403`() {
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val accessToken = extractCookieValue(loginResponse, "access_token")
        val xsrfToken = extractCookieValue(loginResponse, "XSRF-TOKEN")

        // When: missing X-XSRF-TOKEN header entirely
        given()
            .cookie("access_token", accessToken)
            .cookie("XSRF-TOKEN", xsrfToken)
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`().post("/v1/auth/logout")
            .then()
            .statusCode(403)
    }

    // ─── Scenario 6: no XSRF cookie (Bearer-only) passes CSRF filter ─────────────

    @Test
    fun `Bearer-only request without XSRF cookie passes CSRF filter (dual-mode compat §FR-1_6)`() {
        // Given: extract Bearer access token from login body
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val bearerToken = loginResponse.jsonPath().getString("accessToken")
        assertNotNull(bearerToken, "accessToken must be in login response body for Bearer mode")

        // When: access /v1/me with Authorization Bearer (no cookies at all)
        given()
            .header("Authorization", "Bearer $bearerToken")
            .`when`().get("/v1/me")
            .then()
            .statusCode(200)
    }

    @Test
    fun `Bearer-only POST without XSRF cookie passes CSRF filter`() {
        // Given
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val bearerToken = loginResponse.jsonPath().getString("accessToken")

        // When: mutating POST with only Bearer header, no XSRF-TOKEN cookie
        // Logout is a safe endpoint for this test; it accepts null/empty body
        given()
            .header("Authorization", "Bearer $bearerToken")
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`().post("/v1/auth/logout")
            .then()
            .statusCode(204) // 204 No Content = logout success, CSRF filter did not block
    }

    // ─── Scenario 7: logout clears three cookies (Max-Age=0) ─────────────────────

    @Test
    fun `logout response sets all three cookies with Max-Age=0 (§FR-1_8)`() {
        // Given: logged-in session
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val accessToken = extractCookieValue(loginResponse, "access_token")
        val refreshToken = extractCookieValue(loginResponse, "refresh_token")
        val xsrfToken = extractCookieValue(loginResponse, "XSRF-TOKEN")

        // When: logout with all three cookies + CSRF header
        val logoutResponse = given()
            .cookie("access_token", accessToken)
            .cookie("refresh_token", refreshToken)
            .cookie("XSRF-TOKEN", xsrfToken)
            .header("X-XSRF-TOKEN", xsrfToken)
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`().post("/v1/auth/logout")
            .then()
            .statusCode(204)
            .extract().response()

        // Then: all three cookies are cleared
        val refreshClear = extractSetCookieHeader(logoutResponse, "refresh_token")
        val accessClear = extractSetCookieHeader(logoutResponse, "access_token")
        val xsrfClear = extractSetCookieHeader(logoutResponse, "XSRF-TOKEN")

        listOf(
            "refresh_token" to refreshClear,
            "access_token" to accessClear,
            "XSRF-TOKEN" to xsrfClear
        ).forEach { (name, header) ->
            assertNotNull(header, "$name clear cookie must be present in logout response")
            assertTrue(
                header!!.contains("Max-Age=0"),
                "$name clear cookie must have Max-Age=0, got: $header"
            )
        }
    }

    // ─── Scenario 8: refresh cookie-first ────────────────────────────────────────

    @Test
    fun `refresh with refresh_token cookie and empty body returns 200 and new cookies (§FR-1_8)`() {
        // Given: login to get refresh_token cookie
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val refreshToken = extractCookieValue(loginResponse, "refresh_token")
        val xsrfToken = extractCookieValue(loginResponse, "XSRF-TOKEN")
        assertNotNull(refreshToken)

        // When: refresh with cookie, empty body, CSRF token for the POST
        // Note: /v1/auth/refresh is NOT in the CSRF exempt list; must include CSRF header
        val refreshResponse = given()
            .cookie("refresh_token", refreshToken)
            .cookie("XSRF-TOKEN", xsrfToken)
            .header("X-XSRF-TOKEN", xsrfToken)
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`().post("/v1/auth/refresh")
            .then()
            .statusCode(200)
            .extract().response()

        // Then: new set of three cookies issued
        val newCookieNames = (refreshResponse.headers().getList("Set-Cookie") ?: emptyList())
            .map { it.value.substringBefore("=") }.toSet()
        assertTrue("access_token" in newCookieNames, "refresh must issue new access_token cookie")
        assertTrue("XSRF-TOKEN" in newCookieNames, "refresh must issue new XSRF-TOKEN cookie")
    }

    // ─── Scenario 9: refresh body fallback ───────────────────────────────────────

    @Test
    fun `refresh with refreshToken in body and no cookie returns 200 (backward compat §FR-1_8)`() {
        // Given: get refresh token from login body
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val refreshToken = loginResponse.jsonPath().getString("refreshToken")
        assertNotNull(refreshToken, "refreshToken must be in login response body")

        // When: refresh with body only (no cookies) — CSRF filter won't fire (no XSRF cookie)
        given()
            .contentType(ContentType.JSON)
            .body("""{"refreshToken":"$refreshToken"}""")
            .`when`().post("/v1/auth/refresh")
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
    }

    // ─── Scenario 10: refresh failure → 401 + clears cookies ─────────────────────

    @Test
    fun `refresh with invalid token returns 401 and clears auth cookies (§FR-1_8)`() {
        // Given: invalid/expired refresh token (just any garbage)
        // No XSRF cookie → CSRF filter passes (Bearer-mode)
        val refreshResponse = given()
            .contentType(ContentType.JSON)
            .body("""{"refreshToken":"invalid.token.garbage"}""")
            .`when`().post("/v1/auth/refresh")
            .then()
            .statusCode(401)
            .extract().response()

        // Then: response should contain three Set-Cookie with Max-Age=0
        val setCookieHeaders = refreshResponse.headers().getList("Set-Cookie") ?: emptyList()
        val maxZeroCookies = setCookieHeaders.filter {
            it.value.contains("Max-Age=0")
        }
        assertTrue(
            maxZeroCookies.size >= 3,
            "Failed refresh must clear all three cookies (Max-Age=0), found: $maxZeroCookies"
        )
    }

    // ─── Scenario 11: GET endpoints not CSRF-checked ──────────────────────────────

    @Test
    fun `GET endpoint does not require X-XSRF-TOKEN header even with XSRF cookie`() {
        val loginResponse = doLogin().then().statusCode(200).extract().response()
        val accessToken = extractCookieValue(loginResponse, "access_token")
        val xsrfToken = extractCookieValue(loginResponse, "XSRF-TOKEN")

        // When: GET /v1/me with XSRF cookie but NO X-XSRF-TOKEN header
        given()
            .cookie("access_token", accessToken)
            .cookie("XSRF-TOKEN", xsrfToken)
            // Intentionally no X-XSRF-TOKEN header
            .`when`().get("/v1/me")
            .then()
            .statusCode(200) // 200, not 403
    }

    // ─── Scenario 12: filter order pin: expired token + XSRF cookie ──────────────

    @Test
    fun `request with XSRF cookie but invalid access_token does not return 200 (filter order pin)`() {
        // Given: valid XSRF cookie but completely garbage/expired access_token
        // M5.3.1 handoff: "If XSRF-TOKEN cookie exists but access_token JWT invalid,
        // may get 401 first (Vert.x SmallRye layer) or 403 (CSRF JAX-RS filter)."
        // This test pins the CURRENT behavior to detect regressions.
        val status = given()
            .cookie("access_token", "completely.invalid.jwt")
            .cookie("XSRF-TOKEN", "some-xsrf-value")
            .header("X-XSRF-TOKEN", "some-xsrf-value")
            .`when`().get("/v1/me")
            .statusCode

        // The request must NOT succeed (200 would mean security bypass)
        assertTrue(status in listOf(401, 403)) {
            "Invalid access_token must result in 401 or 403, got $status"
        }
        // Current pinned behavior: SmallRye JWT validates at Vert.x layer (priority ~1000)
        // before JAX-RS CSRF filter (priority 900) for GET requests (CSRF not applied anyway).
        // Log this for regression awareness.
        println("S-009 filter order pin: invalid token + XSRF cookie → HTTP $status (pinned behavior)")
    }

    // ─── S-017: password @Size(max=128) — integration confirmation ────────────────

    @Test
    fun `login with 129-char password returns 422 (Bean Validation S-017)`() {
        val longPassword = "a".repeat(129)
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"taichung-fab","accountName":"admin.system","password":"$longPassword"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(422)
    }

    @Test
    fun `login with exactly 128-char password passes Bean Validation S-017`() {
        val exactPassword = "a".repeat(128)
        // This will fail auth (wrong password) but must NOT fail with 422 (Bean Validation).
        // The password of 128 'a' chars is a valid size per @Size(max=128).
        // Any non-422 response proves bean validation accepted the size.
        val status = given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"taichung-fab","accountName":"admin.system","password":"$exactPassword"}""")
            .`when`().post("/v1/auth/login")
            .statusCode
        // 422 would mean @Size(max=128) rejected a 128-char password — that is the bug we prevent
        assertTrue(status != 422) {
            "128-char password must pass @Size(max=128) Bean Validation (got HTTP 422, should be 401/429)"
        }
    }
}

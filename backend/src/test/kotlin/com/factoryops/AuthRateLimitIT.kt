package com.factoryops

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * S-015 Integration tests: Rate limiting via real HTTP.
 *
 * Uses a test profile with very tight per-account login limit (2) and a short window (2s)
 * so tests complete quickly without waiting a full minute.
 *
 * NOTE: The lockout threshold is set high (99) to avoid S-016 lockout interfering with
 * the rate-limit tests. The rate-limit and lockout are independent mechanisms.
 *
 * Business rules verified end-to-end:
 *  - After per-account login limit (2) is reached, 429 is returned
 *  - 429 response includes Retry-After header
 *  - 429 response has RFC 7807 problem detail with retryAfterSeconds
 */
class TightRateLimitTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "auth.rate-limit.window-seconds" to "4",
        "auth.rate-limit.login.per-account" to "2",
        "auth.rate-limit.login.per-ip" to "200",   // high IP limit to isolate account limit
        "auth.lockout.threshold" to "99",            // avoid lockout interfering with rate-limit test
        "factory.ops.seed.enabled" to "true"
    )
}

@QuarkusTest
@TestProfile(TightRateLimitTestProfile::class)
class AuthRateLimitIT {

    companion object {
        private const val ORG_CODE = "taichung-fab"
        private const val ACCOUNT = "admin.system"
    }

    // ─── per-account rate limit on login ─────────────────────────────────────────

    @Test
    fun `login returns 429 after per-account limit is exceeded`() {
        // Given: per-account limit = 2; consume both attempts
        repeat(2) {
            given()
                .contentType(ContentType.JSON)
                .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"wrong"}""")
                .`when`().post("/v1/auth/login")
        }

        // When: 3rd attempt within the window
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"wrong"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(429)
    }

    @Test
    fun `429 login response has Retry-After header`() {
        // Given: exhaust per-account limit
        repeat(2) {
            given()
                .contentType(ContentType.JSON)
                .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"wrong"}""")
                .`when`().post("/v1/auth/login")
        }

        // When
        val response = given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"wrong"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(429)
            .extract().response()

        // Then: Retry-After header must be present
        val retryAfterHeader = response.header("Retry-After")
        assertTrue(
            !retryAfterHeader.isNullOrBlank(),
            "429 response must include Retry-After header, got: $retryAfterHeader"
        )
        val retryAfterValue = retryAfterHeader.toLongOrNull() ?: -1L
        assertTrue(retryAfterValue > 0, "Retry-After header value must be positive, got: $retryAfterHeader")
    }

    @Test
    fun `429 login response has RFC 7807 problem detail with retryAfterSeconds`() {
        // Given: exhaust per-account limit
        repeat(2) {
            given()
                .contentType(ContentType.JSON)
                .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"wrong"}""")
                .`when`().post("/v1/auth/login")
        }

        // When
        val response = given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"wrong"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(429)
            .extract().response()

        // Then: retryAfterSeconds in body
        val retryAfterSeconds = response.jsonPath().getLong("retryAfterSeconds")
        assertTrue(retryAfterSeconds > 0, "retryAfterSeconds must be > 0 in 429 response body")
    }
}

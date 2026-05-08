package com.factoryops

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import com.factoryops.infrastructure.security.LockoutStateWriter
import com.factoryops.persistence.repository.UserRepository

/**
 * Test profile: threshold=2, duration=1min, high rate-limit to isolate S-016 behavior.
 */
class FastLockoutTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "auth.lockout.threshold" to "2",
        "auth.lockout.duration-minutes" to "1",
        "auth.rate-limit.login.per-account" to "200",
        "auth.rate-limit.login.per-ip" to "500",
        "factory.ops.seed.enabled" to "true"
    )
}

/**
 * S-016 Integration tests: Account lockout via real HTTP + MongoDB (DevServices).
 *
 * M5.4 fix confirmed: LockoutStateWriter uses persistOrUpdate() (entity instance method)
 * to write directly through the MongoDB driver, bypassing JTA lifecycle rollback on exception.
 *
 * Isolation strategy (Method B): @BeforeEach calls lockoutStateWriter.resetLockout()
 * to clear failedLoginCount and lockedUntil on the test account before each test.
 * This removes the need for @Order and @TestMethodOrder and makes each test independent.
 */
@QuarkusTest
@TestProfile(FastLockoutTestProfile::class)
class AuthLockoutIT {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var lockoutStateWriter: LockoutStateWriter

    companion object {
        private const val ORG_CODE = "taichung-fab"
        private const val ACCOUNT = "admin.system"
        private const val CORRECT_PW = "Admin@123456789"
        private const val WRONG_PW = "definitely-wrong-password"
        private const val threshold = 2  // matches FastLockoutTestProfile auth.lockout.threshold
    }

    /**
     * Method B isolation: reset lockout state before each test so tests are fully independent.
     * Uses the same resetLockout() path as production to guarantee the reset actually works.
     * listAll() scans all users — acceptable for test teardown.
     */
    @BeforeEach
    fun resetAccountLockoutState() {
        val userDoc = userRepository.listAll().firstOrNull { it.accountName == ACCOUNT } ?: return
        lockoutStateWriter.resetLockout(userDoc)
    }

    // ─── Test 1: wrong password returns 401 ───────────────────────────────────

    @Test
    fun `failed login with wrong password returns 401`() {
        // Given / When / Then
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$WRONG_PW"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(401)
    }

    // ─── Test 2: correct password returns 200 ─────────────────────────────────

    @Test
    fun `successful login returns 200 with token pair`() {
        // Given / When / Then
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$CORRECT_PW"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
    }

    // ─── Test 3: S-016 lockout state persisted after threshold failures ────────

    @Test
    fun `S-016 account is locked after threshold consecutive wrong passwords`() {
        // Given: account starts unlocked (reset by @BeforeEach)

        // When: send exactly threshold=2 wrong-password requests
        repeat(threshold) {
            given()
                .contentType(ContentType.JSON)
                .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$WRONG_PW"}""")
                .`when`().post("/v1/auth/login")
                .then().statusCode(401)
        }

        // Then: correct password must now return 401 (account locked)
        assertEquals(
            401,
            given()
                .contentType(ContentType.JSON)
                .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$CORRECT_PW"}""")
                .`when`().post("/v1/auth/login")
                .statusCode,
            "S-016: account must be locked (401 AccountLockedException) after $threshold threshold failures"
        )
    }

    // ─── Test 4: S-016 below threshold — not locked yet ───────────────────────

    @Test
    fun `S-016 account is NOT locked after threshold minus one wrong passwords`() {
        // Given: account starts unlocked (reset by @BeforeEach)

        // When: send threshold-1=1 wrong-password requests
        repeat(threshold - 1) {
            given()
                .contentType(ContentType.JSON)
                .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$WRONG_PW"}""")
                .`when`().post("/v1/auth/login")
                .then().statusCode(401)
        }

        // Then: correct password still returns 200 (not yet locked)
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$CORRECT_PW"}""")
            .`when`().post("/v1/auth/login")
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
    }

    // ─── Test 5: S-016 failedLoginCount resets on successful login ────────────

    @Test
    fun `S-016 failedLoginCount resets to zero after successful login`() {
        // Given: send one wrong password (count=1, below threshold=2)
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$WRONG_PW"}""")
            .`when`().post("/v1/auth/login")
            .then().statusCode(401)

        // When: login with correct password (resets count)
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$CORRECT_PW"}""")
            .`when`().post("/v1/auth/login")
            .then().statusCode(200)

        // Then: sending wrong password again should still not be locked (count reset to 0)
        // Sending threshold-1=1 wrong password must not lock the account
        repeat(threshold - 1) {
            given()
                .contentType(ContentType.JSON)
                .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$WRONG_PW"}""")
                .`when`().post("/v1/auth/login")
                .then().statusCode(401)
        }
        // Should still be accessible with correct password
        given()
            .contentType(ContentType.JSON)
            .body("""{"orgCode":"$ORG_CODE","accountName":"$ACCOUNT","password":"$CORRECT_PW"}""")
            .`when`().post("/v1/auth/login")
            .then().statusCode(200)
    }
}

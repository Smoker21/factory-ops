package com.factoryops.unit

import com.factoryops.application.auth.JwtIssuerService
import com.factoryops.application.auth.PasswordHasher
import com.factoryops.application.auth.TokenPair
import com.factoryops.application.service.AuthService
import com.factoryops.infrastructure.security.LockoutStateWriter
import com.factoryops.infrastructure.security.RateLimiter
import com.factoryops.interfaces.exception.AccountLockedException
import com.factoryops.interfaces.exception.UnauthorizedException
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.UserCredentialsDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.RevokedTokenRepository
import com.factoryops.persistence.repository.UserCredentialsRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * S-016: Account lockout unit tests.
 *
 * Business rules under test:
 *  - 4 wrong passwords (threshold-1=5 so count=4): NOT yet locked
 *  - 5th wrong password: account locked, failedLoginCount reset to 0, lockedUntil set
 *  - During lockout period: 401 AccountLockedException regardless of password correctness
 *  - After lockout expires: login proceeds normally
 *  - Successful login: failedLoginCount reset to 0, lockedUntil cleared
 *  - Lockout threshold and duration are configurable (via config fields)
 *
 * Kept separate from AuthServiceUnitTest to group all lockout logic in one place
 * and allow a threshold=2 configuration for edge-case testing.
 */
class AuthServiceLockoutTest {

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: UserCredentialsRepository
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var jwtIssuerService: JwtIssuerService
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var revokedTokenRepository: RevokedTokenRepository
    private lateinit var rateLimiter: RateLimiter
    private lateinit var lockoutStateWriter: LockoutStateWriter
    private lateinit var authService: AuthService

    private val rootOrgId = ObjectId()
    private val userId = ObjectId()

    @BeforeEach
    fun setup() {
        userRepository = mock()
        credentialsRepository = mock()
        passwordHasher = mock()
        jwtIssuerService = mock()
        orgRepository = mock()
        revokedTokenRepository = mock()
        rateLimiter = mock()
        doNothing().whenever(rateLimiter).checkAndRecord(any(), any(), any())
        lockoutStateWriter = mock()
        doNothing().whenever(lockoutStateWriter).recordFailedAttempt(any(), any(), any())
        doNothing().whenever(lockoutStateWriter).resetLockout(any())
        authService = AuthService(
            userRepository, credentialsRepository, passwordHasher,
            jwtIssuerService, orgRepository, revokedTokenRepository, rateLimiter, lockoutStateWriter
        )
        // Use threshold=5 (production default) unless a test overrides
        authService.lockoutThreshold = 5
        authService.lockoutDurationMinutes = 15L
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private fun orgDoc() = OrganizationDocument().also {
        it.id = rootOrgId; it.rootOrgId = rootOrgId
        it.type = "FAB"; it.name = "台中廠"; it.code = "taichung-fab"
        it.createdAt = Instant.now(); it.updatedAt = Instant.now()
    }

    private fun userDoc(
        failedCount: Int = 0,
        lockedUntil: Instant? = null
    ) = UserDocument().also {
        it.id = userId; it.rootOrgId = rootOrgId
        it.accountName = "alice"; it.employeeNo = "EMP-100"
        it.displayName = "Alice"; it.active = true
        it.createdAt = Instant.now(); it.failedLoginCount = failedCount
        it.lockedUntil = lockedUntil
    }

    private fun credDoc() = UserCredentialsDocument().also {
        it.id = ObjectId(); it.userId = userId; it.rootOrgId = rootOrgId
        it.passwordHash = "hashed"; it.algorithm = "BCRYPT"; it.updatedAt = Instant.now()
    }

    private fun tokenPair() = TokenPair("access.tok", "refresh.tok", 900L)

    // ─── failedLoginCount = threshold-1 (4): NOT locked yet ──────────────────────

    @Test
    fun `should call recordFailedAttempt when login fails below threshold`() {
        // Given: threshold=5, current count=3 (one more fail makes 4, still below threshold)
        val user = userDoc(failedCount = 3)
        val cred = credDoc()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc())
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(user)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(cred)
        whenever(passwordHasher.verify(any(), any())).thenReturn(false)

        // When
        assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "alice", "wrong-pass")
        }

        // Then: lockoutStateWriter.recordFailedAttempt was called
        // Actual counter logic tested in LockoutStateWriter unit tests
        org.mockito.kotlin.verify(lockoutStateWriter).recordFailedAttempt(eq(user), eq(5), any())
    }

    // ─── 5th wrong password → lock ────────────────────────────────────────────────

    @Test
    fun `should call recordFailedAttempt on the exact threshold failure`() {
        // Given: threshold=5, current count=4 (next failure triggers lockout)
        val user = userDoc(failedCount = 4)
        val cred = credDoc()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc())
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(user)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(cred)
        whenever(passwordHasher.verify(any(), any())).thenReturn(false)

        // When: 5th failure
        assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "alice", "wrong-pass")
        }

        // Then: lockoutStateWriter handles persistence (actual locking logic in LockoutStateWriter)
        org.mockito.kotlin.verify(lockoutStateWriter).recordFailedAttempt(eq(user), eq(5), eq(15L * 60))
    }

    // ─── Locked account rejects even correct password ─────────────────────────────

    @Test
    fun `should reject login with correct password while account is locked`() {
        // Given: account is locked for 15 more minutes
        val user = userDoc(lockedUntil = Instant.now().plusSeconds(900))
        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc())
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(user)

        // When/Then: correct password is irrelevant — locked account always fails
        val ex = assertThrows<AccountLockedException> {
            authService.login("taichung-fab", "alice", "correct-pass")
        }
        // RFC 7807 retryAfterSeconds must be positive
        assertTrue(ex.retryAfterSeconds > 0, "retryAfterSeconds must be > 0")
    }

    // ─── Lockout expires → login succeeds ────────────────────────────────────────

    @Test
    fun `should allow login when lockout duration has expired`() {
        // Given: lockedUntil is 1 second in the past (lock expired)
        val user = userDoc(lockedUntil = Instant.now().minusSeconds(1))
        val cred = credDoc()
        val tp = tokenPair()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc())
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(user)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(cred)
        whenever(passwordHasher.verify(any(), any())).thenReturn(true)
        whenever(jwtIssuerService.issueTokenPair(any())).thenReturn(tp)

        // When
        val result = authService.login("taichung-fab", "alice", "correct-pass")

        // Then: login succeeds, lockout reset delegated to LockoutStateWriter
        assertEquals("access.tok", result.accessToken)
        org.mockito.kotlin.verify(lockoutStateWriter).resetLockout(eq(user))
    }

    // ─── Successful login clears prior failedLoginCount ───────────────────────────

    @Test
    fun `should reset failedLoginCount to 0 on successful login after prior failures`() {
        // Given: user had 3 prior failures but is not yet locked
        val user = userDoc(failedCount = 3)
        val cred = credDoc()
        val tp = tokenPair()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc())
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(user)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(cred)
        whenever(passwordHasher.verify(any(), any())).thenReturn(true)
        whenever(jwtIssuerService.issueTokenPair(any())).thenReturn(tp)

        // When
        authService.login("taichung-fab", "alice", "correct-pass")

        // Then: lockout reset delegated to LockoutStateWriter
        org.mockito.kotlin.verify(lockoutStateWriter).resetLockout(eq(user))
    }

    // ─── Configurable threshold ────────────────────────────────────────────────────

    @Test
    fun `should call recordFailedAttempt with correct threshold when configured to 2`() {
        // Given: threshold=2 (simulates config override in test profile)
        authService.lockoutThreshold = 2
        authService.lockoutDurationMinutes = 1L

        val user = userDoc(failedCount = 1) // one more failure triggers lockout
        val cred = credDoc()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc())
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(user)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(cred)
        whenever(passwordHasher.verify(any(), any())).thenReturn(false)

        // When: 2nd failure
        assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "alice", "wrong-pass")
        }

        // Then: called with threshold=2, duration=60s
        org.mockito.kotlin.verify(lockoutStateWriter).recordFailedAttempt(eq(user), eq(2), eq(60L))
    }

    // ─── AccountLockedException retryAfterSeconds ────────────────────────────────

    @Test
    fun `AccountLockedException should contain retryAfterSeconds derived from lockedUntil`() {
        // Given: locked for exactly 600 more seconds
        val lockedUntil = Instant.now().plusSeconds(600)
        val user = userDoc(lockedUntil = lockedUntil)

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc())
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(user)

        // When
        val ex = assertThrows<AccountLockedException> {
            authService.login("taichung-fab", "alice", "pass")
        }

        // Then: retryAfterSeconds should be approximately 600 (within 10s tolerance)
        assertTrue(
            ex.retryAfterSeconds in 590..610,
            "retryAfterSeconds should be ~600, was ${ex.retryAfterSeconds}"
        )
    }
}

package com.factoryops.unit

import com.factoryops.application.auth.JwtIssuerService
import com.factoryops.application.auth.PasswordHasher
import com.factoryops.application.auth.TokenPair
import com.factoryops.application.service.AuthService
import com.factoryops.infrastructure.security.LockoutStateWriter
import com.factoryops.infrastructure.security.RateLimiter
import com.factoryops.interfaces.exception.AccountLockedException
import com.factoryops.interfaces.exception.RateLimitExceededException
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Pure unit tests for AuthService business logic.
 * JwtIssuerService is mocked to avoid needing CDI / key injection.
 * Covers: login validation, refresh validation, logout, changePassword.
 */
class AuthServiceUnitTest {

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
        lockoutStateWriter = mock()
        // S-015: default to no-op — does not throw RateLimitViolation
        doNothing().whenever(rateLimiter).checkAndRecord(any(), any(), any())
        // S-016: default to no-op for lockout state writer
        doNothing().whenever(lockoutStateWriter).recordFailedAttempt(any(), any(), any())
        doNothing().whenever(lockoutStateWriter).resetLockout(any())
        authService = AuthService(
            userRepository, credentialsRepository, passwordHasher,
            jwtIssuerService, orgRepository, revokedTokenRepository, rateLimiter, lockoutStateWriter
        )
    }

    // ─── Helper builders ────────────────────────────────────────────────────────

    private fun makeRootOrgDoc(code: String = "taichung-fab"): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = rootOrgId
        doc.rootOrgId = rootOrgId
        doc.type = "FAB"
        doc.name = "台中廠"
        doc.code = code
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeUserDoc(active: Boolean = true, deletedAt: Instant? = null): UserDocument {
        val doc = UserDocument()
        doc.id = userId
        doc.rootOrgId = rootOrgId
        doc.accountName = "admin.system"
        doc.employeeNo = "EMP-001"
        doc.displayName = "System Admin"
        doc.active = active
        doc.createdAt = Instant.now()
        doc.deletedAt = deletedAt
        return doc
    }

    private fun makeCredDoc(hash: String = "hashed-password"): UserCredentialsDocument {
        val doc = UserCredentialsDocument()
        doc.id = ObjectId()
        doc.userId = userId
        doc.rootOrgId = rootOrgId
        doc.passwordHash = hash
        doc.algorithm = "BCRYPT"
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeTokenPair(): TokenPair = TokenPair(
        accessToken = "access.token.value",
        refreshToken = "refresh.token.value",
        expiresIn = 900L
    )

    // ─── login: success path ─────────────────────────────────────────────────────

    @Test
    fun `should return token pair on successful login`() {
        // Given
        val orgDoc = makeRootOrgDoc()
        val userDoc = makeUserDoc(active = true)
        val credDoc = makeCredDoc()
        val tokenPair = makeTokenPair()

        whenever(orgRepository.findRootByCode(eq("taichung-fab"))).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(eq(rootOrgId), eq("admin.system"))).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(eq(userId))).thenReturn(credDoc)
        whenever(passwordHasher.verify(eq("Admin@123"), eq("hashed-password"))).thenReturn(true)
        whenever(jwtIssuerService.issueTokenPair(any())).thenReturn(tokenPair)

        // When
        val result = authService.login("taichung-fab", "admin.system", "Admin@123")

        // Then
        assertEquals("access.token.value", result.accessToken)
        assertEquals("refresh.token.value", result.refreshToken)
        assertEquals(900L, result.expiresIn)
    }

    // ─── login: org not found ────────────────────────────────────────────────────

    @Test
    fun `should throw UnauthorizedException when org code is invalid`() {
        // Given
        whenever(orgRepository.findRootByCode(any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("invalid-org", "admin.system", "Admin@123")
        }
    }

    // ─── login: user not found ───────────────────────────────────────────────────

    @Test
    fun `should throw UnauthorizedException when user not found in org`() {
        // Given
        val orgDoc = makeRootOrgDoc()
        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "nonexistent", "password")
        }
    }

    // ─── login: user inactive ────────────────────────────────────────────────────

    @Test
    fun `should throw UnauthorizedException when user is inactive`() {
        // Given
        val orgDoc = makeRootOrgDoc()
        val inactiveUser = makeUserDoc(active = false)
        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(inactiveUser)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "admin.system", "Admin@123")
        }
    }

    @Test
    fun `should throw UnauthorizedException when user is soft-deleted`() {
        // Given
        val orgDoc = makeRootOrgDoc()
        val deletedUser = makeUserDoc(active = true, deletedAt = Instant.now())
        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(deletedUser)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "admin.system", "Admin@123")
        }
    }

    // ─── login: wrong password ──────────────────────────────────────────────────

    @Test
    fun `should throw UnauthorizedException when password is wrong`() {
        // Given
        val orgDoc = makeRootOrgDoc()
        val userDoc = makeUserDoc(active = true)
        val credDoc = makeCredDoc()
        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(credDoc)
        whenever(passwordHasher.verify(any(), any())).thenReturn(false)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "admin.system", "WrongPassword")
        }
    }

    // ─── login: credentials not found ───────────────────────────────────────────

    @Test
    fun `should throw UnauthorizedException when credentials not found`() {
        // Given
        val orgDoc = makeRootOrgDoc()
        val userDoc = makeUserDoc(active = true)
        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "admin.system", "Admin@123")
        }
    }

    // ─── refresh: success path ───────────────────────────────────────────────────

    @Test
    fun `should return new token pair on successful refresh`() {
        // Given
        val claims = JwtIssuerService.RefreshTokenClaims(
            userId = userId.toHexString(),
            jti = "unique-jti-001",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        val userDoc = makeUserDoc(active = true)
        val tokenPair = makeTokenPair()

        whenever(jwtIssuerService.extractRefreshTokenClaims(any())).thenReturn(claims)
        whenever(revokedTokenRepository.isRevoked(eq("unique-jti-001"))).thenReturn(false)
        whenever(userRepository.findById(eq(userId))).thenReturn(userDoc)
        whenever(jwtIssuerService.issueTokenPair(any())).thenReturn(tokenPair)

        // When
        val result = authService.refresh("valid-refresh-token")

        // Then
        assertEquals("access.token.value", result.accessToken)
    }

    // ─── refresh: revoked token ──────────────────────────────────────────────────

    @Test
    fun `should throw UnauthorizedException when refresh token is revoked`() {
        // Given
        val claims = JwtIssuerService.RefreshTokenClaims(
            userId = userId.toHexString(),
            jti = "revoked-jti",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        whenever(jwtIssuerService.extractRefreshTokenClaims(any())).thenReturn(claims)
        whenever(revokedTokenRepository.isRevoked(eq("revoked-jti"))).thenReturn(true)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.refresh("revoked-refresh-token")
        }
    }

    // ─── refresh: user not found after claims extraction ─────────────────────────

    @Test
    fun `should throw UnauthorizedException when user not found during refresh`() {
        // Given
        val claims = JwtIssuerService.RefreshTokenClaims(
            userId = userId.toHexString(),
            jti = "valid-jti",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        whenever(jwtIssuerService.extractRefreshTokenClaims(any())).thenReturn(claims)
        whenever(revokedTokenRepository.isRevoked(any())).thenReturn(false)
        whenever(userRepository.findById(any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.refresh("valid-refresh-token")
        }
    }

    // ─── refresh: deactivated user ───────────────────────────────────────────────

    @Test
    fun `should throw UnauthorizedException when user is deactivated during refresh`() {
        // Given
        val claims = JwtIssuerService.RefreshTokenClaims(
            userId = userId.toHexString(),
            jti = "valid-jti",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        val inactiveUser = makeUserDoc(active = false)
        whenever(jwtIssuerService.extractRefreshTokenClaims(any())).thenReturn(claims)
        whenever(revokedTokenRepository.isRevoked(any())).thenReturn(false)
        whenever(userRepository.findById(any())).thenReturn(inactiveUser)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.refresh("valid-refresh-token")
        }
    }

    // ─── logout ──────────────────────────────────────────────────────────────────

    @Test
    fun `should revoke refresh token on logout`() {
        // Given
        val expiresAt = Instant.now().plusSeconds(3600)
        val claims = JwtIssuerService.RefreshTokenClaims(
            userId = userId.toHexString(),
            jti = "logout-jti",
            expiresAt = expiresAt
        )
        whenever(jwtIssuerService.extractRefreshTokenClaims(any())).thenReturn(claims)
        doNothing().whenever(revokedTokenRepository).revoke(any(), any())

        // When
        authService.logout("valid-refresh-token")

        // Then
        verify(revokedTokenRepository).revoke(eq("logout-jti"), eq(expiresAt))
    }

    @Test
    fun `should silently ignore invalid refresh token on logout`() {
        // Given
        whenever(jwtIssuerService.extractRefreshTokenClaims(any()))
            .thenThrow(UnauthorizedException("Invalid token", "invalid_refresh_token"))

        // When (should not throw)
        authService.logout("invalid-refresh-token")

        // Then: revokedTokenRepository.revoke should NOT be called
        verify(revokedTokenRepository, org.mockito.kotlin.never()).revoke(any(), any())
    }

    // ─── changePassword ──────────────────────────────────────────────────────────

    @Test
    fun `should update password hash on successful changePassword`() {
        // Given
        val userDoc = makeUserDoc(active = true)
        val credDoc = makeCredDoc(hash = "old-hash")
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootOrgId))).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(eq(userId))).thenReturn(credDoc)
        whenever(passwordHasher.verify(eq("OldPass@123"), eq("old-hash"))).thenReturn(true)
        whenever(passwordHasher.hash(eq("NewPass@123456"))).thenReturn("new-hashed-password")
        doNothing().whenever(credentialsRepository).update(any<UserCredentialsDocument>())

        // When
        authService.changePassword(
            userId = userId.toHexString(),
            rootOrgId = rootOrgId.toHexString(),
            currentPassword = "OldPass@123",
            newPassword = "NewPass@123456"
        )

        // Then
        val captured = argumentCaptor<UserCredentialsDocument>()
        verify(credentialsRepository).update(captured.capture())
        assertEquals("new-hashed-password", captured.firstValue.passwordHash)
    }

    @Test
    fun `should throw UnauthorizedException when current password is wrong on changePassword`() {
        // Given
        val userDoc = makeUserDoc(active = true)
        val credDoc = makeCredDoc(hash = "old-hash")
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootOrgId))).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(eq(userId))).thenReturn(credDoc)
        whenever(passwordHasher.verify(any(), any())).thenReturn(false)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.changePassword(
                userId = userId.toHexString(),
                rootOrgId = rootOrgId.toHexString(),
                currentPassword = "WrongOldPassword",
                newPassword = "NewPass@123456"
            )
        }
    }

    @Test
    fun `should throw UnauthorizedException when user not found on changePassword`() {
        // Given
        whenever(userRepository.findByIdAndNotDeleted(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.changePassword(
                userId = userId.toHexString(),
                rootOrgId = rootOrgId.toHexString(),
                currentPassword = "OldPass@123",
                newPassword = "NewPass@123456"
            )
        }
    }

    @Test
    fun `should throw UnauthorizedException when credentials not found on changePassword`() {
        // Given
        val userDoc = makeUserDoc(active = true)
        whenever(userRepository.findByIdAndNotDeleted(any(), any())).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.changePassword(
                userId = userId.toHexString(),
                rootOrgId = rootOrgId.toHexString(),
                currentPassword = "OldPass@123",
                newPassword = "NewPass@123456"
            )
        }
    }

    // ─── S-016: Account lockout ─────────────────────────────────────────────────

    @Test
    fun `should throw AccountLockedException when account is locked`() {
        // Given: account is locked until 15 minutes from now
        val orgDoc = makeRootOrgDoc()
        val lockedUserDoc = makeUserDoc(active = true)
        lockedUserDoc.lockedUntil = Instant.now().plusSeconds(900)

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(lockedUserDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<AccountLockedException> {
            authService.login("taichung-fab", "admin.system", "Admin@123")
        }
    }

    @Test
    fun `should allow login when lockout has expired`() {
        // Given: lockedUntil is in the past (lock expired)
        val orgDoc = makeRootOrgDoc()
        val unlockedUserDoc = makeUserDoc(active = true)
        unlockedUserDoc.lockedUntil = Instant.now().minusSeconds(1)
        val credDoc = makeCredDoc()
        val tokenPair = makeTokenPair()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(unlockedUserDoc)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(credDoc)
        whenever(passwordHasher.verify(any(), any())).thenReturn(true)
        whenever(jwtIssuerService.issueTokenPair(any())).thenReturn(tokenPair)
        doNothing().whenever(userRepository).update(any<UserDocument>())

        // When
        val result = authService.login("taichung-fab", "admin.system", "Admin@123")

        // Then
        assertEquals("access.token.value", result.accessToken)
    }

    @Test
    fun `should call lockoutStateWriter recordFailedAttempt on wrong password`() {
        // Given
        val orgDoc = makeRootOrgDoc()
        val userDoc = makeUserDoc(active = true)
        userDoc.failedLoginCount = 2
        val credDoc = makeCredDoc()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(credDoc)
        whenever(passwordHasher.verify(any(), any())).thenReturn(false)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "admin.system", "WrongPass")
        }

        // Verify lockoutStateWriter was called to persist the failure
        verify(lockoutStateWriter).recordFailedAttempt(eq(userDoc), any(), any())
    }

    @Test
    fun `should lock account when failedLoginCount reaches threshold (lockoutStateWriter delegates)`() {
        // Given: already at threshold - 1
        val orgDoc = makeRootOrgDoc()
        val userDoc = makeUserDoc(active = true)
        userDoc.failedLoginCount = 4  // next failure triggers lockout (threshold=5)
        val credDoc = makeCredDoc()

        // Configure threshold on the service
        authService.lockoutThreshold = 5
        authService.lockoutDurationMinutes = 15

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(credDoc)
        whenever(passwordHasher.verify(any(), any())).thenReturn(false)

        // When / Then
        org.junit.jupiter.api.assertThrows<UnauthorizedException> {
            authService.login("taichung-fab", "admin.system", "WrongPass")
        }

        // Lockout state write is delegated to LockoutStateWriter (REQUIRES_NEW);
        // actual persistence logic is tested in LockoutStateWriter unit tests.
        verify(lockoutStateWriter).recordFailedAttempt(eq(userDoc), eq(5), eq(15L * 60))
    }

    @Test
    fun `should call lockoutStateWriter resetLockout on successful login`() {
        // Given: user had previous failures
        val orgDoc = makeRootOrgDoc()
        val userDoc = makeUserDoc(active = true)
        userDoc.failedLoginCount = 3
        val credDoc = makeCredDoc()
        val tokenPair = makeTokenPair()

        whenever(orgRepository.findRootByCode(any())).thenReturn(orgDoc)
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(userDoc)
        whenever(credentialsRepository.findByUserId(any())).thenReturn(credDoc)
        whenever(passwordHasher.verify(any(), any())).thenReturn(true)
        whenever(jwtIssuerService.issueTokenPair(any())).thenReturn(tokenPair)

        // When
        authService.login("taichung-fab", "admin.system", "CorrectPass")

        // Lockout state reset is delegated to LockoutStateWriter
        verify(lockoutStateWriter).resetLockout(eq(userDoc))
    }

    // ─── S-015: Rate limiting ───────────────────────────────────────────────────

    @Test
    fun `should throw RateLimitExceededException when login rate limit is hit`() {
        // Given: rateLimiter throws RateLimitViolation
        whenever(rateLimiter.checkAndRecord(eq(RateLimiter.Action.LOGIN), any(), any()))
            .thenThrow(RateLimiter.RateLimitViolation(60L))
        whenever(rateLimiter.retryAfterSeconds(any(), any(), any())).thenReturn(60L)

        // When / Then
        org.junit.jupiter.api.assertThrows<RateLimitExceededException> {
            authService.login("taichung-fab", "admin.system", "Admin@123", "192.168.1.100")
        }
    }

    @Test
    fun `should throw RateLimitExceededException when logout rate limit is hit`() {
        // Given
        whenever(rateLimiter.checkAndRecord(eq(RateLimiter.Action.LOGOUT), any(), org.mockito.kotlin.isNull()))
            .thenThrow(RateLimiter.RateLimitViolation(30L))
        whenever(rateLimiter.retryAfterSeconds(any(), any(), org.mockito.kotlin.isNull())).thenReturn(30L)

        // When / Then
        org.junit.jupiter.api.assertThrows<RateLimitExceededException> {
            authService.logout("any-refresh-token", "192.168.1.100")
        }
    }
}

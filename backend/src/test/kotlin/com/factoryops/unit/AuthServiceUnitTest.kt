package com.factoryops.unit

import com.factoryops.application.auth.JwtIssuerService
import com.factoryops.application.auth.PasswordHasher
import com.factoryops.application.auth.TokenPair
import com.factoryops.application.service.AuthService
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
        authService = AuthService(
            userRepository, credentialsRepository, passwordHasher,
            jwtIssuerService, orgRepository, revokedTokenRepository
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
}

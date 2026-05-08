package com.factoryops.unit.dto

import com.factoryops.interfaces.dto.ChangePasswordRequest
import com.factoryops.interfaces.dto.LoginRequest
import com.factoryops.interfaces.dto.LogoutRequest
import com.factoryops.interfaces.dto.RefreshRequest
import com.factoryops.interfaces.dto.TokenPairResponse
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Unit tests for Auth DTO validation constraints and response construction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthDtoTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setup() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ─── LoginRequest ──────────────────────────────────────────────────────────

    @Test
    fun `LoginRequest happy path passes validation`() {
        // Given
        val dto = LoginRequest(orgCode = "taichung-fab", accountName = "admin.system", password = "Admin@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty(), "No violations expected for valid LoginRequest")
    }

    @Test
    fun `LoginRequest with blank orgCode fails validation`() {
        // Given
        val dto = LoginRequest(orgCode = "", accountName = "admin.system", password = "Admin@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "orgCode" })
    }

    @Test
    fun `LoginRequest with blank accountName fails validation`() {
        // Given
        val dto = LoginRequest(orgCode = "taichung-fab", accountName = "", password = "Admin@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "accountName" })
    }

    @Test
    fun `LoginRequest with blank password fails validation`() {
        // Given
        val dto = LoginRequest(orgCode = "taichung-fab", accountName = "admin.system", password = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "password" })
    }

    @Test
    fun `LoginRequest with orgCode exceeding 64 chars fails validation`() {
        // Given
        val longCode = "a".repeat(65)
        val dto = LoginRequest(orgCode = longCode, accountName = "admin.system", password = "Admin@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "orgCode" })
    }

    @Test
    fun `LoginRequest with accountName exceeding 30 chars fails validation`() {
        // Given
        val longName = "a".repeat(31)
        val dto = LoginRequest(orgCode = "taichung-fab", accountName = longName, password = "Admin@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "accountName" })
    }

    @Test
    fun `LoginRequest with password exceeding 128 chars fails validation`() {
        // Given
        val longPassword = "a".repeat(129)
        val dto = LoginRequest(orgCode = "taichung-fab", accountName = "admin.system", password = longPassword)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "password" })
    }

    // ─── RefreshRequest ─────────────────────────────────────────────────────────
    // M5.3 S-009: refreshToken is now optional (cookie-first mode, §FR-1.8).
    // Cookie path: refresh_token httpOnly cookie → no body needed.
    // Body path:   refreshToken field retained for backward compatibility (M6 will remove).

    @Test
    fun `RefreshRequest with token body passes validation`() {
        // Given
        val dto = RefreshRequest(refreshToken = "some-valid-token")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `RefreshRequest without token body passes validation (cookie-mode)`() {
        // Given: no body token — cookie path will supply the refresh token
        val dto = RefreshRequest(refreshToken = null)

        // When
        val violations = validator.validate(dto)

        // Then: no constraint violations; resource resolves from cookie
        assertTrue(violations.isEmpty(), "Null refreshToken is valid — cookie supplies the token")
    }

    @Test
    fun `RefreshRequest with empty string body passes validation (cookie-mode)`() {
        // Given: empty string treated as absent — resource will resolve from cookie
        val dto = RefreshRequest(refreshToken = "")

        // When
        val violations = validator.validate(dto)

        // Then: no Bean Validation constraint on the field
        assertTrue(violations.isEmpty(), "Empty refreshToken has no Bean Validation constraint — resource handles it")
    }

    // ─── LogoutRequest ──────────────────────────────────────────────────────────
    // M5.3 S-009: refreshToken is now optional (cookie-first mode, §FR-1.8).

    @Test
    fun `LogoutRequest with token body passes validation`() {
        // Given
        val dto = LogoutRequest(refreshToken = "some-token")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `LogoutRequest without token body passes validation (cookie-mode)`() {
        // Given: no body token — cookie path supplies the refresh token
        val dto = LogoutRequest(refreshToken = null)

        // When
        val violations = validator.validate(dto)

        // Then: no constraint violations; resource resolves from cookie
        assertTrue(violations.isEmpty(), "Null refreshToken is valid — cookie supplies the token")
    }

    // ─── ChangePasswordRequest ──────────────────────────────────────────────────

    @Test
    fun `ChangePasswordRequest happy path passes validation`() {
        // Given
        val dto = ChangePasswordRequest(currentPassword = "OldPass@123", newPassword = "NewPassword@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ChangePasswordRequest with blank currentPassword fails validation`() {
        // Given
        val dto = ChangePasswordRequest(currentPassword = "", newPassword = "NewPassword@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "currentPassword" })
    }

    @Test
    fun `ChangePasswordRequest with new password shorter than 12 chars fails validation`() {
        // Given
        val dto = ChangePasswordRequest(currentPassword = "OldPass@123", newPassword = "Short@1")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "newPassword" })
    }

    // ─── TokenPairResponse construction ────────────────────────────────────────

    @Test
    fun `TokenPairResponse is constructed with correct defaults`() {
        // Given / When
        val response = TokenPairResponse(
            accessToken = "access.token.here",
            refreshToken = "refresh.token.here",
            expiresIn = 3600L
        )

        // Then
        assertEquals("Bearer", response.tokenType)
        assertEquals(3600L, response.expiresIn)
        assertEquals("access.token.here", response.accessToken)
        assertEquals("refresh.token.here", response.refreshToken)
    }
}

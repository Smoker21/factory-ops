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

    @Test
    fun `RefreshRequest happy path passes validation`() {
        // Given
        val dto = RefreshRequest(refreshToken = "some-valid-token")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `RefreshRequest with blank token fails validation`() {
        // Given
        val dto = RefreshRequest(refreshToken = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "refreshToken" })
    }

    // ─── LogoutRequest ──────────────────────────────────────────────────────────

    @Test
    fun `LogoutRequest with blank token fails validation`() {
        // Given
        val dto = LogoutRequest(refreshToken = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "refreshToken" })
    }

    @Test
    fun `LogoutRequest happy path passes validation`() {
        // Given
        val dto = LogoutRequest(refreshToken = "some-token")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
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

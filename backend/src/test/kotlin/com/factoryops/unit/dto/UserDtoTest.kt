package com.factoryops.unit.dto

import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.user.User
import com.factoryops.interfaces.dto.CreateUserRequest
import com.factoryops.interfaces.dto.UpdateUserRequest
import com.factoryops.interfaces.dto.toResponse
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for User DTO validation constraints and domain-to-response converters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDtoTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setup() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ─── CreateUserRequest ───────────────────────────────────────────────────────

    @Test
    fun `CreateUserRequest happy path passes validation`() {
        // Given
        val dto = CreateUserRequest(
            accountName = "operator.li",
            defaultPassword = "Operator@123"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateUserRequest with blank accountName fails validation`() {
        // Given
        val dto = CreateUserRequest(accountName = "", defaultPassword = "Operator@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "accountName" })
    }

    @Test
    fun `CreateUserRequest with accountName exceeding 30 chars fails validation`() {
        // Given
        val longName = "a".repeat(31)
        val dto = CreateUserRequest(accountName = longName, defaultPassword = "Operator@123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "accountName" })
    }

    @Test
    fun `CreateUserRequest with blank password fails validation`() {
        // Given
        val dto = CreateUserRequest(accountName = "operator.li", defaultPassword = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "defaultPassword" })
    }

    @Test
    fun `CreateUserRequest with password shorter than 8 chars fails validation`() {
        // Given
        val dto = CreateUserRequest(accountName = "operator.li", defaultPassword = "Ab@1234")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "defaultPassword" })
    }

    @Test
    fun `CreateUserRequest with exactly 8 char password passes validation`() {
        // Given
        val dto = CreateUserRequest(accountName = "operator.li", defaultPassword = "Ab@12345")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── UpdateUserRequest (all optional) ────────────────────────────────────────

    @Test
    fun `UpdateUserRequest with all nulls passes validation`() {
        // Given
        val dto = UpdateUserRequest(roles = null, active = null)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `UpdateUserRequest with roles and active set passes validation`() {
        // Given
        val dto = UpdateUserRequest(roles = listOf("OPERATOR", "ENGINEER"), active = true)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── User.toResponse() ──────────────────────────────────────────────────────

    @Test
    fun `User toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val user = User(
            id = "user-001",
            rootOrgId = "root-001",
            accountName = "operator.li",
            employeeNo = "EMP-004",
            email = "operator@factory.example.com",
            displayName = "李作業員",
            roles = listOf(Role.OPERATOR),
            active = true,
            schemaVersion = 1,
            createdAt = now,
            deletedAt = null
        )

        // When
        val response = user.toResponse()

        // Then
        assertEquals("user-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("operator.li", response.accountName)
        assertEquals("EMP-004", response.employeeNo)
        assertEquals("operator@factory.example.com", response.email)
        assertEquals("李作業員", response.displayName)
        assertEquals(listOf("OPERATOR"), response.roles)
        assertEquals(true, response.active)
        assertEquals(now.atOffset(ZoneOffset.UTC), response.createdAt)
        assertNull(response.deletedAt)
    }

    @Test
    fun `User toResponse with null email maps correctly`() {
        // Given
        val now = Instant.now()
        val user = User(
            id = "user-002",
            rootOrgId = "root-001",
            accountName = "user.nomail",
            employeeNo = "EMP-005",
            email = null,
            displayName = "No Email User",
            active = true,
            createdAt = now
        )

        // When
        val response = user.toResponse()

        // Then
        assertNull(response.email)
    }

    @Test
    fun `User toResponse with deletedAt maps correctly`() {
        // Given
        val now = Instant.now()
        val deletedAt = now.minusSeconds(3600)
        val user = User(
            id = "user-003",
            rootOrgId = "root-001",
            accountName = "deleted.user",
            employeeNo = "EMP-006",
            displayName = "Deleted User",
            active = false,
            createdAt = now,
            deletedAt = deletedAt
        )

        // When
        val response = user.toResponse()

        // Then
        assertEquals(deletedAt.atOffset(ZoneOffset.UTC), response.deletedAt)
    }

    @Test
    fun `User toResponse with multiple roles maps role names correctly`() {
        // Given
        val now = Instant.now()
        val user = User(
            id = "user-004",
            rootOrgId = "root-001",
            accountName = "multi.role",
            employeeNo = "EMP-007",
            displayName = "Multi Role User",
            roles = listOf(Role.SHIFT_LEAD, Role.GROUP_MANAGER, Role.ENGINEER),
            active = true,
            createdAt = now
        )

        // When
        val response = user.toResponse()

        // Then
        assertEquals(listOf("SHIFT_LEAD", "GROUP_MANAGER", "ENGINEER"), response.roles)
    }
}

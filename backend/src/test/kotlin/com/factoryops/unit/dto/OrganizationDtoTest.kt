package com.factoryops.unit.dto

import com.factoryops.domain.organization.OrgSettings
import com.factoryops.domain.organization.Organization
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.user.User
import com.factoryops.domain.shared.enums.Role
import com.factoryops.interfaces.dto.AddLeaderRequest
import com.factoryops.interfaces.dto.CreateOrganizationRequest
import com.factoryops.interfaces.dto.InitialOrgAdminDto
import com.factoryops.interfaces.dto.OrgSettingsDto
import com.factoryops.interfaces.dto.TransferManagerRequest
import com.factoryops.interfaces.dto.UpdateOrganizationRequest
import com.factoryops.interfaces.dto.toDomain
import com.factoryops.interfaces.dto.toOrganizationLeaderResponse
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
 * Unit tests for Organization DTO validation and domain-to-response converters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationDtoTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setup() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ─── CreateOrganizationRequest ───────────────────────────────────────────────

    @Test
    fun `CreateOrganizationRequest happy path passes validation`() {
        // Given
        val dto = CreateOrganizationRequest(
            type = "FAB",
            name = "Test Factory",
            code = "test-fab"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateOrganizationRequest with blank type fails validation`() {
        // Given
        val dto = CreateOrganizationRequest(type = "", name = "Test Factory", code = "test-fab")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "type" })
    }

    @Test
    fun `CreateOrganizationRequest with blank name fails validation`() {
        // Given
        val dto = CreateOrganizationRequest(type = "FAB", name = "", code = "test-fab")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `CreateOrganizationRequest with name exceeding 120 chars fails validation`() {
        // Given
        val longName = "a".repeat(121)
        val dto = CreateOrganizationRequest(type = "FAB", name = longName, code = "test-fab")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `CreateOrganizationRequest with blank code fails validation`() {
        // Given
        val dto = CreateOrganizationRequest(type = "FAB", name = "Test Factory", code = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "code" })
    }

    @Test
    fun `CreateOrganizationRequest with code exceeding 64 chars fails validation`() {
        // Given
        val longCode = "a".repeat(65)
        val dto = CreateOrganizationRequest(type = "FAB", name = "Test Factory", code = longCode)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "code" })
    }

    @Test
    fun `CreateOrganizationRequest code with uppercase fails pattern validation`() {
        // Given
        val dto = CreateOrganizationRequest(type = "FAB", name = "Test Factory", code = "TestFab")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "code" }, "Uppercase letters should fail pattern validation")
    }

    @Test
    fun `CreateOrganizationRequest code with spaces fails pattern validation`() {
        // Given
        val dto = CreateOrganizationRequest(type = "FAB", name = "Test Factory", code = "test fab")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "code" })
    }

    @Test
    fun `CreateOrganizationRequest code with valid lowercase-digits-hyphens passes`() {
        // Given
        val dto = CreateOrganizationRequest(type = "FAB", name = "Test Factory", code = "test-fab-123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.none { it.propertyPath.toString() == "code" })
    }

    // ─── InitialOrgAdminDto ──────────────────────────────────────────────────────

    @Test
    fun `InitialOrgAdminDto with blank accountName fails validation`() {
        // Given
        val dto = InitialOrgAdminDto(accountName = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "accountName" })
    }

    @Test
    fun `InitialOrgAdminDto with accountName over 30 chars fails validation`() {
        // Given
        val dto = InitialOrgAdminDto(accountName = "a".repeat(31))

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "accountName" })
    }

    // ─── AddLeaderRequest ────────────────────────────────────────────────────────

    @Test
    fun `AddLeaderRequest happy path passes validation`() {
        // Given
        val dto = AddLeaderRequest(userId = "user123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `AddLeaderRequest with blank userId fails validation`() {
        // Given
        val dto = AddLeaderRequest(userId = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "userId" })
    }

    // ─── TransferManagerRequest (all fields optional) ────────────────────────────

    @Test
    fun `TransferManagerRequest with nulls passes validation`() {
        // Given
        val dto = TransferManagerRequest(newManagerId = null, reason = null)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── UpdateOrganizationRequest (all fields optional) ────────────────────────

    @Test
    fun `UpdateOrganizationRequest with all nulls passes validation`() {
        // Given
        val dto = UpdateOrganizationRequest()

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── OrgSettingsDto.toDomain() ───────────────────────────────────────────────

    @Test
    fun `OrgSettingsDto toDomain maps all fields correctly`() {
        // Given
        val dto = OrgSettingsDto(
            orgMaxDepth = 6,
            leafTypes = listOf("SECTION", "TEAM"),
            attachmentMaxBytes = 104857600L,
            extras = mapOf("key" to "val")
        )

        // When
        val domain = dto.toDomain()

        // Then
        assertEquals(6, domain.orgMaxDepth)
        assertEquals(listOf("SECTION", "TEAM"), domain.leafTypes)
        assertEquals(104857600L, domain.attachmentMaxBytes)
        assertEquals(mapOf("key" to "val"), domain.extras)
    }

    // ─── Organization.toResponse() ───────────────────────────────────────────────

    @Test
    fun `Organization toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val org = Organization(
            id = "org-001",
            rootOrgId = "org-001",
            parentId = null,
            type = "FAB",
            name = "Test Factory",
            code = "test-fab",
            managerId = "manager-001",
            leaderIds = listOf("leader-001"),
            ancestorIds = emptyList(),
            isLeaf = false,
            depth = 0,
            timezone = "Asia/Taipei",
            locale = "zh-TW",
            settings = OrgSettings(orgMaxDepth = 5, leafTypes = listOf("SECTION")),
            history = emptyList(),
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val response = org.toResponse()

        // Then
        assertEquals("org-001", response.id)
        assertEquals("org-001", response.rootOrgId)
        assertNull(response.parentId)
        assertEquals("FAB", response.type)
        assertEquals("Test Factory", response.name)
        assertEquals("test-fab", response.code)
        assertEquals("manager-001", response.managerId)
        assertEquals(listOf("leader-001"), response.leaderIds)
        assertEquals(emptyList<String>(), response.ancestorIds)
        assertEquals(false, response.isLeaf)
        assertEquals(0, response.depth)
        assertEquals("Asia/Taipei", response.timezone)
        assertEquals("zh-TW", response.locale)
        assertEquals(5, response.settings?.orgMaxDepth)
        assertNull(response.deletedAt)
    }

    @Test
    fun `Organization toResponse with deletedAt maps correctly`() {
        // Given
        val now = Instant.now()
        val deletedAt = now.minusSeconds(3600)
        val org = Organization(
            id = "org-002",
            rootOrgId = "org-002",
            type = "FAB",
            name = "Deleted Org",
            code = "deleted-org",
            createdAt = now,
            updatedAt = now,
            deletedAt = deletedAt
        )

        // When
        val response = org.toResponse()

        // Then
        assertEquals(deletedAt.atOffset(ZoneOffset.UTC), response.deletedAt)
    }

    // ─── AuditEntry.toResponse() ─────────────────────────────────────────────────

    @Test
    fun `AuditEntry toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val entry = AuditEntry(
            actorId = "actor-001",
            action = "ORG_CREATED",
            at = now,
            payload = mapOf("note" to "initial creation")
        )

        // When
        val response = entry.toResponse()

        // Then
        assertEquals("actor-001", response.actorId)
        assertEquals("ORG_CREATED", response.action)
        assertEquals(now.atOffset(ZoneOffset.UTC), response.at)
        assertEquals(mapOf("note" to "initial creation"), response.payload)
    }

    // ─── User.toOrganizationLeaderResponse() ────────────────────────────────────

    @Test
    fun `User toOrganizationLeaderResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val user = User(
            id = "user-001",
            rootOrgId = "root-001",
            accountName = "leader.chen",
            employeeNo = "EMP-003",
            email = "leader@factory.example.com",
            displayName = "陳組長",
            roles = listOf(Role.SHIFT_LEAD),
            active = true,
            createdAt = now
        )

        // When
        val response = user.toOrganizationLeaderResponse()

        // Then
        assertEquals("user-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("leader.chen", response.accountName)
        assertEquals("EMP-003", response.employeeNo)
        assertEquals("leader@factory.example.com", response.email)
        assertEquals("陳組長", response.displayName)
        assertEquals(listOf("SHIFT_LEAD"), response.roles)
        assertEquals(true, response.active)
    }
}

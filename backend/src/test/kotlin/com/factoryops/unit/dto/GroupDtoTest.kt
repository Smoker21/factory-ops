package com.factoryops.unit.dto

import com.factoryops.domain.group.Group
import com.factoryops.domain.group.GroupMembership
import com.factoryops.domain.group.GroupMembershipRole
import com.factoryops.domain.group.GroupSettings
import com.factoryops.domain.group.QaSettings
import com.factoryops.domain.shared.enums.Role
import com.factoryops.interfaces.dto.AddGroupMemberRequest
import com.factoryops.interfaces.dto.CreateGroupRequest
import com.factoryops.interfaces.dto.QaSettingsDto
import com.factoryops.interfaces.dto.TransferGroupLeaderRequest
import com.factoryops.interfaces.dto.UpdateGroupRequest
import com.factoryops.interfaces.dto.UpdateGroupSettingsRequest
import com.factoryops.interfaces.dto.toDomain
import com.factoryops.interfaces.dto.toResponse
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for Group DTO validation constraints and domain-to-response converters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupDtoTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setup() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ─── CreateGroupRequest ──────────────────────────────────────────────────────

    @Test
    fun `CreateGroupRequest happy path passes validation`() {
        // Given
        val dto = CreateGroupRequest(
            organizationId = "org123",
            type = "SHIFT",
            name = "Night Shift",
            code = "night-shift"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateGroupRequest with blank organizationId fails validation`() {
        // Given
        val dto = CreateGroupRequest(organizationId = "", type = "SHIFT", name = "Night Shift", code = "night-shift")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "organizationId" })
    }

    @Test
    fun `CreateGroupRequest with blank type fails validation`() {
        // Given
        val dto = CreateGroupRequest(organizationId = "org123", type = "", name = "Night Shift", code = "night-shift")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "type" })
    }

    @Test
    fun `CreateGroupRequest with blank name fails validation`() {
        // Given
        val dto = CreateGroupRequest(organizationId = "org123", type = "SHIFT", name = "", code = "night-shift")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `CreateGroupRequest with name exceeding 120 chars fails validation`() {
        // Given
        val longName = "a".repeat(121)
        val dto = CreateGroupRequest(organizationId = "org123", type = "SHIFT", name = longName, code = "night-shift")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `CreateGroupRequest with blank code fails validation`() {
        // Given
        val dto = CreateGroupRequest(organizationId = "org123", type = "SHIFT", name = "Night Shift", code = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "code" })
    }

    @Test
    fun `CreateGroupRequest with code exceeding 64 chars fails validation`() {
        // Given
        val longCode = "a".repeat(65)
        val dto = CreateGroupRequest(organizationId = "org123", type = "SHIFT", name = "Night Shift", code = longCode)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "code" })
    }

    // ─── AddGroupMemberRequest ───────────────────────────────────────────────────

    @Test
    fun `AddGroupMemberRequest happy path passes validation`() {
        // Given
        val dto = AddGroupMemberRequest(userId = "user123", role = "MEMBER")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `AddGroupMemberRequest with blank userId fails validation`() {
        // Given
        val dto = AddGroupMemberRequest(userId = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "userId" })
    }

    // ─── TransferGroupLeaderRequest ──────────────────────────────────────────────

    @Test
    fun `TransferGroupLeaderRequest happy path passes validation`() {
        // Given
        val dto = TransferGroupLeaderRequest(newLeaderId = "leader123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `TransferGroupLeaderRequest with blank newLeaderId fails validation`() {
        // Given
        val dto = TransferGroupLeaderRequest(newLeaderId = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "newLeaderId" })
    }

    // ─── UpdateGroupRequest (optional fields) ────────────────────────────────────

    @Test
    fun `UpdateGroupRequest with all nulls passes validation`() {
        // Given
        val dto = UpdateGroupRequest()

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── UpdateGroupSettingsRequest ──────────────────────────────────────────────

    @Test
    fun `UpdateGroupSettingsRequest with null qa passes validation`() {
        // Given
        val dto = UpdateGroupSettingsRequest(qa = null, extras = null)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── QaSettingsDto.toDomain() ────────────────────────────────────────────────

    @Test
    fun `QaSettingsDto toDomain maps dualSignRequired correctly`() {
        // Given
        val dto = QaSettingsDto(dualSignRequired = true, requiredReviewerRoles = listOf("QA", "SHIFT_LEAD"))

        // When
        val domain = dto.toDomain()

        // Then
        assertTrue(domain.dualSignRequired)
        assertEquals(2, domain.requiredReviewerRoles.size)
        assertTrue(domain.requiredReviewerRoles.contains(Role.QA))
        assertTrue(domain.requiredReviewerRoles.contains(Role.SHIFT_LEAD))
    }

    @Test
    fun `QaSettingsDto toDomain with unknown role skips it`() {
        // Given
        val dto = QaSettingsDto(dualSignRequired = false, requiredReviewerRoles = listOf("QA", "UNKNOWN_ROLE"))

        // When
        val domain = dto.toDomain()

        // Then
        assertFalse(domain.dualSignRequired)
        assertEquals(1, domain.requiredReviewerRoles.size)
        assertTrue(domain.requiredReviewerRoles.contains(Role.QA))
    }

    @Test
    fun `QaSettingsDto toDomain with empty roles`() {
        // Given
        val dto = QaSettingsDto(dualSignRequired = false, requiredReviewerRoles = emptyList())

        // When
        val domain = dto.toDomain()

        // Then
        assertFalse(domain.dualSignRequired)
        assertTrue(domain.requiredReviewerRoles.isEmpty())
    }

    // ─── Group.toResponse() ──────────────────────────────────────────────────────

    @Test
    fun `Group toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val group = Group(
            id = "group-001",
            rootOrgId = "root-001",
            organizationId = "org-001",
            type = "SHIFT",
            name = "Night Shift",
            code = "night-shift",
            leaderId = "leader-001",
            attributes = mapOf("shiftType" to "night"),
            settings = GroupSettings(
                qa = QaSettings(dualSignRequired = true, requiredReviewerRoles = listOf(Role.QA)),
                extras = mapOf("note" to "demo")
            ),
            history = emptyList(),
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val response = group.toResponse()

        // Then
        assertEquals("group-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("org-001", response.organizationId)
        assertEquals("SHIFT", response.type)
        assertEquals("Night Shift", response.name)
        assertEquals("night-shift", response.code)
        assertEquals("leader-001", response.leaderId)
        assertTrue(response.settings.qa.dualSignRequired)
        assertEquals(listOf("QA"), response.settings.qa.requiredReviewerRoles)
        assertNull(response.deletedAt)
    }

    @Test
    fun `Group toResponse with null leaderId`() {
        // Given
        val now = Instant.now()
        val group = Group(
            id = "group-002",
            rootOrgId = "root-001",
            organizationId = "org-001",
            type = "DEFAULT",
            name = "Default Group",
            code = "default-grp",
            leaderId = null,
            settings = GroupSettings(),
            createdAt = now,
            updatedAt = now
        )

        // When
        val response = group.toResponse()

        // Then
        assertNull(response.leaderId)
    }

    // ─── GroupMembership.toResponse() ────────────────────────────────────────────

    @Test
    fun `GroupMembership toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val leftAt = now.plusSeconds(3600)
        val membership = GroupMembership(
            id = "gmem-001",
            rootOrgId = "root-001",
            groupId = "group-001",
            userId = "user-001",
            role = GroupMembershipRole.LEAD,
            joinedAt = now,
            leftAt = leftAt,
            createdBy = "admin-001"
        )

        // When
        val response = membership.toResponse()

        // Then
        assertEquals("gmem-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("group-001", response.groupId)
        assertEquals("user-001", response.userId)
        assertEquals("LEAD", response.role)
        assertEquals(now.atOffset(ZoneOffset.UTC), response.joinedAt)
        assertEquals(leftAt.atOffset(ZoneOffset.UTC), response.leftAt)
    }

    @Test
    fun `GroupMembership toResponse with null leftAt`() {
        // Given
        val now = Instant.now()
        val membership = GroupMembership(
            id = "gmem-002",
            rootOrgId = "root-001",
            groupId = "group-001",
            userId = "user-002",
            role = GroupMembershipRole.MEMBER,
            joinedAt = now,
            leftAt = null,
            createdBy = "admin-001"
        )

        // When
        val response = membership.toResponse()

        // Then
        assertNull(response.leftAt)
    }
}
